package com.ditadesigner.xslt;

import com.ditadesigner.xml.XmlCoreService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Visual XPath expression builder.
 *
 * <p>Lets the developer compose an XPath step-by-step:
 * <ol>
 *   <li>Choose an <b>axis</b> (child, descendant, ancestor, sibling, attribute, …)</li>
 *   <li>Choose a <b>node test</b> (element name from the XML model, *, text(), @attr, …)</li>
 *   <li>Add one or more <b>predicates</b> (attribute test, position, boolean, custom)</li>
 *   <li>Add the step to the expression list</li>
 *   <li>Preview the assembled XPath with a live match count against the loaded XML</li>
 *   <li>Click <b>Insert</b> to paste the expression into the XSLT editor</li>
 * </ol>
 *
 * <p>Returns the built XPath string via {@link Dialog#showAndWait()} → {@code Optional<String>}.
 */
public class XPathBuilderDialog extends Dialog<String> {

    // ── Axis catalogue ────────────────────────────────────────────────────────

    private enum Axis {
        CHILD            ("child",             "child::"),
        DESCENDANT       ("descendant",        "descendant::"),
        DESCENDANT_SELF  ("descendant-or-self","//"),
        ANCESTOR         ("ancestor",          "ancestor::"),
        ANCESTOR_SELF    ("ancestor-or-self",  "ancestor-or-self::"),
        PARENT           ("parent",            "parent::"),
        SELF             ("self",              "self::"),
        FOLLOWING_SIB    ("following-sibling", "following-sibling::"),
        PRECEDING_SIB    ("preceding-sibling", "preceding-sibling::"),
        ATTRIBUTE        ("attribute (@)",     "@");

        final String label;
        final String prefix;
        Axis(String label, String prefix) { this.label = label; this.prefix = prefix; }

        @Override public String toString() { return label; }

        String apply(String nodeTest) {
            return switch (this) {
                case PARENT          -> "..";
                case SELF            -> ".";
                case DESCENDANT_SELF -> "//" + nodeTest;
                case ATTRIBUTE       -> "@" + nodeTest;
                default              -> prefix + nodeTest;
            };
        }
    }

    // ── Predicate type ────────────────────────────────────────────────────────

    private enum PredicateType {
        ATTRIBUTE_EQUALS  ("Attribute equals"),
        ATTRIBUTE_CONTAINS("Attribute contains"),
        ATTRIBUTE_EXISTS  ("Attribute exists"),
        POSITION          ("Position [N]"),
        LAST              ("Last position [last()]"),
        CHILD_COUNT       ("Child count"),
        CUSTOM            ("Custom expression");

        final String label;
        PredicateType(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final XmlStructureModel          xmlModel;   // null if no XML loaded
    private final XmlCoreService             xmlCore;
    private final ExecutorService            executor;
    private final ObservableList<String>     steps       = FXCollections.observableArrayList();

    // Step editor controls
    private ComboBox<Axis>          axisCb;
    private ComboBox<String>        nodeCb;
    private ComboBox<PredicateType> predTypeCb;
    private ComboBox<String>        predAttrCb;
    private ComboBox<String>        predOpCb;
    private TextField               predValueField;
    private TextField               predCustomField;
    private VBox                    predAttrRow;
    private VBox                    predCustomRow;

    // Expression area
    private TextField exprField;
    private Label     matchLabel;
    private ListView<String> stepList;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * @param owner    owner window (for modality)
     * @param xmlModel structure model of the loaded XML (may be {@code null})
     * @param xmlCore  Saxon service for match counting
     */
    public XPathBuilderDialog(Window owner, XmlStructureModel xmlModel, XmlCoreService xmlCore) {
        this.xmlModel = xmlModel;
        this.xmlCore  = xmlCore;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "xpath-builder-worker");
            t.setDaemon(true);
            return t;
        });

        initOwner(owner);
        setTitle("XPath Builder");
        setHeaderText(null);
        setResizable(true);
        getDialogPane().setPrefSize(680, 580);

        getDialogPane().setContent(buildContent());
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Insert into Editor");
        okBtn.disableProperty().bind(exprField.textProperty().isEmpty());

        setResultConverter(btn -> btn == ButtonType.OK ? exprField.getText() : null);

        getDialogPane().getStylesheets().add(
                XPathBuilderDialog.class.getResource("/css/styles.css").toExternalForm());

        setOnHidden(e -> executor.shutdown());
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private VBox buildContent() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(14));

        root.getChildren().addAll(
            buildExpressionBar(),
            new Separator(),
            buildStepEditor(),
            new Separator(),
            buildStepList()
        );
        return root;
    }

    /** Top bar: assembled expression + match count. */
    private VBox buildExpressionBar() {
        exprField = new TextField();
        exprField.setPromptText("XPath expression appears here…");
        exprField.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 13px;");
        exprField.setEditable(true);   // allow manual editing too
        HBox.setHgrow(exprField, Priority.ALWAYS);

        exprField.textProperty().addListener((obs, o, text) -> scheduleMatchCount(text));

        Button clearAllBtn = new Button("Clear All");
        clearAllBtn.setOnAction(e -> { steps.clear(); rebuildExpression(); });

        matchLabel = new Label();
        matchLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 11px;");

        HBox row = new HBox(6, new Label("XPath:"), exprField, clearAllBtn);
        row.setStyle("-fx-alignment: CENTER_LEFT;");

        Label hdr = new Label("Built Expression");
        hdr.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        return new VBox(4, hdr, row, matchLabel);
    }

    /** Middle section: axis + node + predicate builder. */
    private VBox buildStepEditor() {
        Label hdr = new Label("Step Builder");
        hdr.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        // Axis
        axisCb = new ComboBox<>(FXCollections.observableArrayList(Axis.values()));
        axisCb.setValue(Axis.CHILD);
        axisCb.setPrefWidth(180);

        // Node test
        nodeCb = new ComboBox<>();
        nodeCb.setEditable(true);
        nodeCb.setPrefWidth(200);
        populateNodeTests();
        axisCb.setOnAction(e -> populateNodeTests());

        Button addStepBtn = new Button("Add Step →");
        addStepBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold;");
        addStepBtn.setOnAction(e -> addStep());

        HBox axisRow = new HBox(8,
            new Label("Axis:"), axisCb,
            new Label("Node:"), nodeCb,
            addStepBtn);
        axisRow.setStyle("-fx-alignment: CENTER_LEFT;");

        // Predicate type
        predTypeCb = new ComboBox<>(
                FXCollections.observableArrayList(PredicateType.values()));
        predTypeCb.setValue(PredicateType.ATTRIBUTE_EQUALS);
        predTypeCb.setPrefWidth(180);
        predTypeCb.setOnAction(e -> updatePredicateForm());

        // Attribute-test row (attr name + op + value)
        predAttrCb = new ComboBox<>();
        predAttrCb.setEditable(true);
        predAttrCb.setPrefWidth(140);
        predAttrCb.setPromptText("@attribute");
        populateAttrNames();

        predOpCb = new ComboBox<>(FXCollections.observableArrayList(
                "=", "!=", "contains", "starts-with", "not contains"));
        predOpCb.setValue("=");
        predOpCb.setPrefWidth(110);

        predValueField = new TextField();
        predValueField.setPromptText("'value'");
        predValueField.setPrefWidth(130);
        HBox.setHgrow(predValueField, Priority.ALWAYS);

        predAttrRow = new VBox(4,
            new HBox(6, new Label("@Attr:"), predAttrCb,
                        new Label("Op:"),   predOpCb,
                        new Label("Value:"), predValueField));
        predAttrRow.setStyle("-fx-padding: 0 0 0 16;");

        // Custom predicate row
        predCustomField = new TextField();
        predCustomField.setPromptText("e.g.  position() = 1  or  count(child::*) > 2");
        predCustomField.setStyle("-fx-font-family: 'Consolas','Courier New',monospace;");
        HBox.setHgrow(predCustomField, Priority.ALWAYS);
        predCustomRow = new VBox(4, new HBox(6, new Label("Expression:"), predCustomField));
        predCustomRow.setStyle("-fx-padding: 0 0 0 16;");
        predCustomRow.setVisible(false);
        predCustomRow.setManaged(false);

        Button addPredBtn = new Button("Add Predicate to Step");
        addPredBtn.setOnAction(e -> addPredicate());

        Label predHdr = new Label("Predicate (optional):");
        predHdr.setStyle("-fx-font-style: italic; -fx-text-fill: #555;");

        VBox predBox = new VBox(6,
            predHdr,
            new HBox(8, new Label("Type:"), predTypeCb),
            predAttrRow, predCustomRow,
            addPredBtn);
        predBox.setStyle("-fx-padding: 6 0 0 0;");

        VBox editor = new VBox(8, hdr, axisRow, predBox);
        editor.setStyle("-fx-background-color: #f8f8f8; -fx-padding: 10; "
                + "-fx-border-color: #ddd; -fx-border-radius: 4; -fx-background-radius: 4;");
        return editor;
    }

    /** Bottom section: step list with reorder/remove controls. */
    private VBox buildStepList() {
        Label hdr = new Label("Expression Steps  (drag to reorder or use ↑ ↓)");
        hdr.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");

        stepList = new ListView<>(steps);
        stepList.setPrefHeight(120);
        stepList.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 12px;");

        Button upBtn = new Button("↑");
        upBtn.setOnAction(e -> moveStep(-1));

        Button downBtn = new Button("↓");
        downBtn.setOnAction(e -> moveStep(1));

        Button removeBtn = new Button("Remove");
        removeBtn.setStyle("-fx-text-fill: #c0392b;");
        removeBtn.setOnAction(e -> {
            int idx = stepList.getSelectionModel().getSelectedIndex();
            if (idx >= 0) { steps.remove(idx); rebuildExpression(); }
        });

        HBox controls = new HBox(6, upBtn, downBtn, new Separator(Orientation.VERTICAL), removeBtn);
        controls.setStyle("-fx-alignment: CENTER_LEFT;");

        return new VBox(6, hdr, stepList, controls);
    }

    // ── Step building ─────────────────────────────────────────────────────────

    private void addStep() {
        Axis   axis     = axisCb.getValue();
        String nodeTest = nodeTestValue();
        if (nodeTest.isBlank()) return;
        steps.add(axis.apply(nodeTest));
        rebuildExpression();
    }

    private String nodeTestValue() {
        String v = nodeCb.getEditor().getText();
        return v == null ? "" : v.strip();
    }

    /** Current inline predicate — returns empty string if nothing meaningful entered. */
    private String buildPredicate() {
        PredicateType type = predTypeCb.getValue();
        return switch (type) {
            case ATTRIBUTE_EQUALS -> {
                String attr  = attrName();
                String op    = predOpCb.getValue();
                String value = predValueField.getText().strip();
                if (attr.isBlank()) yield "";
                yield switch (op) {
                    case "contains"     -> "contains(@" + attr + ", '" + value + "')";
                    case "starts-with"  -> "starts-with(@" + attr + ", '" + value + "')";
                    case "not contains" -> "not(contains(@" + attr + ", '" + value + "'))";
                    default             -> "@" + attr + " = '" + value + "'";
                };
            }
            case ATTRIBUTE_CONTAINS -> {
                String attr  = attrName();
                String value = predValueField.getText().strip();
                yield attr.isBlank() ? "" : "contains(@" + attr + ", '" + value + "')";
            }
            case ATTRIBUTE_EXISTS  -> {
                String attr = attrName();
                yield attr.isBlank() ? "" : "@" + attr;
            }
            case POSITION          -> {
                String n = predValueField.getText().strip();
                yield n.isBlank() ? "position() = 1" : "position() = " + n;
            }
            case LAST              -> "last()";
            case CHILD_COUNT       -> {
                String n = predValueField.getText().strip();
                yield "count(child::*) > " + (n.isBlank() ? "0" : n);
            }
            case CUSTOM            -> predCustomField.getText().strip();
        };
    }

    private void addPredicate() {
        int idx = stepList.getSelectionModel().getSelectedIndex();
        if (idx < 0 && !steps.isEmpty()) idx = steps.size() - 1;
        if (idx < 0) return;

        String pred = buildPredicate();
        if (pred.isBlank()) return;

        String current = steps.get(idx);
        steps.set(idx, current + "[" + pred + "]");
        rebuildExpression();
    }

    private void moveStep(int delta) {
        int idx = stepList.getSelectionModel().getSelectedIndex();
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= steps.size()) return;
        String tmp = steps.get(idx);
        steps.set(idx, steps.get(target));
        steps.set(target, tmp);
        stepList.getSelectionModel().select(target);
        rebuildExpression();
    }

    private void rebuildExpression() {
        String expr = buildFromSteps();
        exprField.setText(expr);
    }

    private String buildFromSteps() {
        if (steps.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i);
            if (i == 0) {
                sb.append(step);
            } else if (step.startsWith("//")) {
                sb.append(step);
            } else {
                sb.append("/").append(step);
            }
        }
        return sb.toString();
    }

    // ── Node / attribute population ───────────────────────────────────────────

    private void populateNodeTests() {
        List<String> items = new ArrayList<>();
        Axis axis = axisCb.getValue();

        if (axis == Axis.ATTRIBUTE) {
            items.add("*");
            if (xmlModel != null) {
                xmlModel.getSuggestions(XmlStructureModel.AttributeContext.SELECT, "@")
                        .stream()
                        .filter(s -> s.startsWith("@"))
                        .map(s -> s.substring(1))
                        .forEach(items::add);
            }
        } else {
            items.addAll(List.of("*", "text()", "node()", "processing-instruction()", "comment()"));
            if (xmlModel != null) {
                xmlModel.getSuggestions(XmlStructureModel.AttributeContext.MATCH, "")
                        .stream()
                        .filter(s -> !s.startsWith("@") && !s.startsWith("//")
                                && !s.equals("*") && !s.equals("text()") && !s.equals("node()"))
                        .forEach(items::add);
            }
        }

        nodeCb.setItems(FXCollections.observableArrayList(items));
        if (!items.isEmpty()) nodeCb.setValue(items.get(0));
    }

    private void populateAttrNames() {
        List<String> attrs = new ArrayList<>();
        attrs.add("id");
        attrs.add("class");
        attrs.add("type");
        attrs.add("name");
        attrs.add("href");
        if (xmlModel != null) {
            xmlModel.getSuggestions(XmlStructureModel.AttributeContext.SELECT, "@")
                    .stream()
                    .filter(s -> s.startsWith("@"))
                    .map(s -> s.substring(1))
                    .filter(s -> !attrs.contains(s))
                    .forEach(attrs::add);
        }
        predAttrCb.setItems(FXCollections.observableArrayList(attrs));
        if (!attrs.isEmpty()) predAttrCb.setValue(attrs.get(0));
    }

    private String attrName() {
        String v = predAttrCb.getEditor().getText();
        return v == null ? "" : v.strip().replaceFirst("^@", "");
    }

    // ── Predicate form switching ──────────────────────────────────────────────

    private void updatePredicateForm() {
        PredicateType type = predTypeCb.getValue();
        boolean attrMode   = type == PredicateType.ATTRIBUTE_EQUALS
                          || type == PredicateType.ATTRIBUTE_CONTAINS
                          || type == PredicateType.ATTRIBUTE_EXISTS;
        boolean customMode = type == PredicateType.CUSTOM;

        predAttrRow.setVisible(attrMode);
        predAttrRow.setManaged(attrMode);
        predCustomRow.setVisible(customMode);
        predCustomRow.setManaged(customMode);

        // Reuse predValueField label for position / child-count prompts
        predValueField.setPromptText(switch (type) {
            case POSITION   -> "position number (default: 1)";
            case CHILD_COUNT-> "min count (default: 0)";
            default         -> "'value'";
        });

        // Show/hide op combo depending on predicate type
        predOpCb.setVisible(type == PredicateType.ATTRIBUTE_EQUALS);
        predOpCb.setManaged(type == PredicateType.ATTRIBUTE_EQUALS);
    }

    // ── Live match count ──────────────────────────────────────────────────────

    private void scheduleMatchCount(String expr) {
        if (expr == null || expr.isBlank() || xmlCore == null) {
            matchLabel.setText("");
            return;
        }
        matchLabel.setText("Evaluating…");
        executor.submit(() -> {
            int count = evaluate(expr);
            Platform.runLater(() -> {
                if (count < 0) matchLabel.setText("Match count: — (invalid XPath or no XML loaded)");
                else           matchLabel.setText("Match count in loaded XML: " + count + " node(s)");
            });
        });
    }

    // Match counting is provided externally via setMatchCounter().
    // This fallback is intentionally minimal — no cached Saxon document is available here.
    private int evaluate(String expr) {
        return -1;
    }

    /**
     * Wire an external match-count function so the dialog can evaluate expressions
     * against the cached Saxon document from {@link XPathSuggestionService}.
     *
     * @param counter function that takes an XPath expression and returns node count (or -1)
     */
    public void setMatchCounter(java.util.function.ToIntFunction<String> counter) {
        exprField.textProperty().addListener((obs, o, expr) -> {
            if (expr == null || expr.isBlank()) { matchLabel.setText(""); return; }
            matchLabel.setText("Evaluating…");
            executor.submit(() -> {
                int count = counter.applyAsInt(expr);
                Platform.runLater(() -> matchLabel.setText(
                        count < 0 ? "Match count: — (no XML loaded or invalid XPath)"
                                  : "Match count: " + count + " node(s)"));
            });
        });
    }
}
