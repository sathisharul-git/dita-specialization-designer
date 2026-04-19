package com.ditadesigner.schema.canvas;

import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;
import com.ditadesigner.schema.SchemaEditService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Visual box for one {@link TopicType} on the schema design canvas (F-255–F-257).
 * Layout (top-to-bottom):
 *   • Coloured title bar  — «TopicType» stereotype + name (inline-editable)
 *   • Base-type strip     — "extends: taskType" in lighter shade
 *   • Elements section    — one row per ElementDef (name · cardinality · content model)
 *   • Attributes section  — one row per AttributeDef (name · type · required badge)
 */
public class TopicTypeSchemaNode extends VBox {

    private static final java.util.Map<String, String> BASE_COLORS = java.util.Map.of(
        "task",      "#1565C0",
        "concept",   "#2e7d32",
        "reference", "#6a1b9a",
        "map",       "#e65100",
        "bookmap",   "#bf360c",
        "domain",    "#7b3ad5"
    );
    private static final String DEFAULT_COLOR = "#37474f";

    private final TopicType       topicType;
    private final SchemaEditService editService;
    private       boolean         selected = false;

    private final Label     nameLabel;
    private final VBox      titleBar;
    private final VBox      elementsBox;
    private final VBox      attributesBox;

    // Callbacks wired by SchemaCanvas
    private Consumer<TopicTypeSchemaNode>          onSelectCallback;
    private BiConsumer<TopicTypeSchemaNode, Object> onElementSelectCallback;
    private BiConsumer<TopicTypeSchemaNode, Object> onAttributeSelectCallback;

    private double dragOrigX, dragOrigY;

    public TopicTypeSchemaNode(TopicType topicType, SchemaEditService editService) {
        this.topicType   = topicType;
        this.editService = editService;

        setSpacing(0);
        setPrefWidth(240);
        setMinWidth(200);
        setMaxWidth(300);

        // ── Title bar ─────────────────────────────────────────────────
        titleBar = new VBox(2);
        titleBar.setPadding(new Insets(6, 8, 6, 8));

        Label stereotype = new Label("«TopicType»");
        stereotype.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 9px; -fx-font-style: italic;");

        nameLabel = new Label(topicType.getName());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(220);
        nameLabel.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                startNameInlineEdit();
                e.consume();
            }
        });

        titleBar.getChildren().addAll(stereotype, nameLabel);

        // ── Base-type strip ───────────────────────────────────────────
        Label baseLabel = new Label();
        baseLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Elements section ──────────────────────────────────────────
        Label elemHeader = sectionHeader("Elements");
        elementsBox = new VBox(1);
        elementsBox.setPadding(new Insets(2, 8, 4, 8));

        // ── Attributes section ────────────────────────────────────────
        Label attrHeader = sectionHeader("Attributes");
        attributesBox = new VBox(1);
        attributesBox.setPadding(new Insets(2, 8, 4, 8));

        getChildren().addAll(titleBar, baseLabel, elemHeader, elementsBox, attrHeader, attributesBox);

        setLayoutX(topicType.getX());
        setLayoutY(topicType.getY());

        refresh();
        applyBorder(false);
        setupDrag();
        setupClick();
    }

    // ── Public API ────────────────────────────────────────────────────

    public String  getModelId()  { return topicType.getId(); }
    public boolean isSelected()  { return selected; }
    public TopicType getTopicType() { return topicType; }

    public double getCenterX() { return getLayoutX() + getPrefWidth() / 2.0; }
    public double getCenterY() {
        double h = getHeight() > 0 ? getHeight() : 180;
        return getLayoutY() + h / 2.0;
    }

    public void setSelected(boolean sel) {
        this.selected = sel;
        applyBorder(sel);
    }

    public void setOnSelectCallback(Consumer<TopicTypeSchemaNode> cb)               { this.onSelectCallback = cb; }
    public void setOnElementSelectCallback(BiConsumer<TopicTypeSchemaNode,Object> cb) { this.onElementSelectCallback = cb; }
    public void setOnAttributeSelectCallback(BiConsumer<TopicTypeSchemaNode,Object> cb) { this.onAttributeSelectCallback = cb; }

    /** Rebuild all visual rows from the model. */
    public void refresh() {
        nameLabel.setText(topicType.getName());

        String color = baseColor();
        titleBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6 6 0 0;");

        // Update base-type strip (second child)
        if (getChildren().size() > 1 && getChildren().get(1) instanceof Label bl) {
            String lighter = lighten(color);
            bl.setText("extends: " + topicType.getBaseType());
            bl.setStyle("-fx-background-color: " + lighter + "; -fx-text-fill: white;" +
                    " -fx-padding: 2 8 2 8; -fx-font-size: 10px;");
        }

        // Elements rows
        elementsBox.getChildren().clear();
        for (ElementDef elem : topicType.getElements()) {
            elementsBox.getChildren().add(buildElementRow(elem));
        }
        if (topicType.getElements().isEmpty())
            elementsBox.getChildren().add(grayLabel("(none)"));

        // Attribute rows
        attributesBox.getChildren().clear();
        for (AttributeDef attr : topicType.getAttributes()) {
            attributesBox.getChildren().add(buildAttributeRow(attr));
        }
        if (topicType.getAttributes().isEmpty())
            attributesBox.getChildren().add(grayLabel("(none)"));
    }

    // ── Inline editing (F-261) ────────────────────────────────────────

    public void startNameInlineEdit() {
        TextField tf = new TextField(topicType.getName());
        tf.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-background-color: white;");
        tf.setPrefWidth(220);

        int idx = titleBar.getChildren().indexOf(nameLabel);
        if (idx < 0) return;
        titleBar.getChildren().set(idx, tf);
        tf.requestFocus();
        tf.selectAll();

        Runnable commit = () -> {
            String val = tf.getText().trim();
            if (!val.isEmpty()) editService.renameTopicType(topicType.getId(), val);
            int i = titleBar.getChildren().indexOf(tf);
            if (i >= 0) titleBar.getChildren().set(i, nameLabel);
            refresh();
        };
        tf.setOnAction(e -> commit.run());
        tf.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            int i = titleBar.getChildren().indexOf(tf);
            if (i >= 0) titleBar.getChildren().set(i, nameLabel);
        }});
        tf.focusedProperty().addListener((obs, was, is) -> { if (!is) commit.run(); });
    }

    private void startElementInlineEdit(ElementDef elem, HBox rowBox) {
        TextField nameTf = new TextField(elem.getName());
        nameTf.setPrefWidth(110);
        nameTf.setStyle("-fx-font-size: 10px;");

        javafx.scene.control.ComboBox<String> cardCombo = new javafx.scene.control.ComboBox<>();
        cardCombo.getItems().addAll("1", "?", "+", "*");
        cardCombo.setValue(elem.getCardinality());
        cardCombo.setPrefWidth(52);
        cardCombo.setStyle("-fx-font-size: 10px;");

        HBox editor = new HBox(4, nameTf, cardCombo);
        editor.setAlignment(Pos.CENTER_LEFT);

        int idx = elementsBox.getChildren().indexOf(rowBox);
        if (idx < 0) return;
        elementsBox.getChildren().set(idx, editor);
        nameTf.requestFocus();

        Runnable commit = () -> {
            String name = nameTf.getText().trim();
            String card = cardCombo.getValue();
            if (!name.isEmpty()) editService.updateElementName(topicType.getId(), elem.getId(), name);
            if (card != null)    editService.updateElementCardinality(topicType.getId(), elem.getId(), card);
            int i = elementsBox.getChildren().indexOf(editor);
            if (i >= 0) elementsBox.getChildren().set(i, buildElementRow(elem));
        };
        nameTf.setOnAction(e -> commit.run());
        nameTf.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            int i = elementsBox.getChildren().indexOf(editor);
            if (i >= 0) elementsBox.getChildren().set(i, buildElementRow(elem));
        }});
        nameTf.focusedProperty().addListener((obs, was, is) -> { if (!is) commit.run(); });
        cardCombo.setOnAction(e -> commit.run());
    }

    private void startAttributeInlineEdit(AttributeDef attr, HBox rowBox) {
        TextField nameTf = new TextField(attr.getName());
        nameTf.setPrefWidth(100);
        nameTf.setStyle("-fx-font-size: 10px;");

        javafx.scene.control.ComboBox<String> typeCombo = new javafx.scene.control.ComboBox<>();
        typeCombo.getItems().addAll("CDATA","NMTOKEN","NMTOKENS","ID","IDREF","IDREFS");
        typeCombo.setValue(attr.getType());
        typeCombo.setPrefWidth(80);
        typeCombo.setStyle("-fx-font-size: 10px;");

        javafx.scene.control.CheckBox reqCb = new javafx.scene.control.CheckBox("req");
        reqCb.setSelected(attr.isRequired());
        reqCb.setStyle("-fx-font-size: 9px;");

        HBox editor = new HBox(4, nameTf, typeCombo, reqCb);
        editor.setAlignment(Pos.CENTER_LEFT);

        int idx = attributesBox.getChildren().indexOf(rowBox);
        if (idx < 0) return;
        attributesBox.getChildren().set(idx, editor);
        nameTf.requestFocus();

        Runnable commit = () -> {
            String name = nameTf.getText().trim();
            String type = typeCombo.getValue();
            if (!name.isEmpty()) editService.updateAttributeName(topicType.getId(), attr.getId(), name);
            if (type != null)    editService.updateAttributeType(topicType.getId(), attr.getId(), type);
            editService.updateAttributeRequired(topicType.getId(), attr.getId(), reqCb.isSelected());
            int i = attributesBox.getChildren().indexOf(editor);
            if (i >= 0) attributesBox.getChildren().set(i, buildAttributeRow(attr));
        };
        nameTf.setOnAction(e -> commit.run());
        nameTf.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
            int i = attributesBox.getChildren().indexOf(editor);
            if (i >= 0) attributesBox.getChildren().set(i, buildAttributeRow(attr));
        }});
        nameTf.focusedProperty().addListener((obs, was, is) -> { if (!is) commit.run(); });
        typeCombo.setOnAction(e -> commit.run());
        reqCb.setOnAction(e -> commit.run());
    }

    // ── Row builders ──────────────────────────────────────────────────

    private HBox buildElementRow(ElementDef elem) {
        // oXygen convention: thick line = required (minOccurs≥1), thin = optional
        boolean req = "1".equals(elem.getCardinality()) || "+".equals(elem.getCardinality());
        String weight = req ? "bold" : "normal";

        Label icon = new Label("▸");
        icon.setStyle("-fx-font-size: 9px; -fx-text-fill: #1565C0;");
        Label name = new Label(elem.getName());
        name.setStyle("-fx-font-size: 10px; -fx-text-fill: #222; -fx-font-weight: " + weight + ";");
        HBox.setHgrow(name, Priority.ALWAYS);
        Label card = new Label(elem.getCardinality());
        card.setStyle("-fx-font-size: 9px; -fx-text-fill: #555; -fx-background-color: #e8f0fe;" +
                " -fx-padding: 0 3 0 3; -fx-background-radius: 3;");

        HBox row = new HBox(4, icon, name, card);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 2, 1, 2));
        row.setStyle("-fx-background-color: #f5f9ff;");

        row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                startElementInlineEdit(elem, row);
                e.consume();
            } else if (e.getClickCount() == 1 && onElementSelectCallback != null) {
                onElementSelectCallback.accept(this, elem);
            }
        });
        return row;
    }

    private HBox buildAttributeRow(AttributeDef attr) {
        Label dot = new Label(attr.isRequired() ? "●" : "○");
        dot.setStyle("-fx-font-size: 9px; -fx-text-fill: " + (attr.isRequired() ? "#c62828" : "#888") + ";");

        Label name = new Label(attr.getName());
        name.setStyle("-fx-font-size: 10px; -fx-text-fill: #444;" +
                (attr.isRequired() ? " -fx-font-weight: bold;" : ""));
        HBox.setHgrow(name, Priority.ALWAYS);

        Label type = new Label(attr.getType());
        type.setStyle("-fx-font-size: 9px; -fx-text-fill: #6a1b9a; -fx-background-color: #f3e5f5;" +
                " -fx-padding: 0 3 0 3; -fx-background-radius: 3;");

        HBox row = new HBox(4, dot, name, type);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 2, 1, 2));
        row.setStyle("-fx-background-color: #fdf5ff;");

        row.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                startAttributeInlineEdit(attr, row);
                e.consume();
            } else if (e.getClickCount() == 1 && onAttributeSelectCallback != null) {
                onAttributeSelectCallback.accept(this, attr);
            }
        });
        return row;
    }

    // ── Styling helpers ───────────────────────────────────────────────

    private void applyBorder(boolean sel) {
        String colour = sel ? "#ff8800" : baseColor();
        String width  = sel ? "2.5" : "1.5";
        setStyle("-fx-border-color: " + colour + "; -fx-border-width: " + width + ";" +
                " -fx-border-radius: 6; -fx-background-radius: 6;" +
                " -fx-background-color: white;" +
                " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 2, 2);");
    }

    private String baseColor() {
        String bt = topicType.getBaseType();
        return BASE_COLORS.getOrDefault(bt != null ? bt.toLowerCase() : "", DEFAULT_COLOR);
    }

    private String lighten(String hex) {
        return switch (hex) {
            case "#1565C0" -> "#1976D2";
            case "#2e7d32" -> "#388e3c";
            case "#6a1b9a" -> "#7b1fa2";
            case "#e65100" -> "#ef6c00";
            case "#bf360c" -> "#d84315";
            case "#7b3ad5" -> "#8e44ad";
            default        -> "#546e7a";
        };
    }

    private Label sectionHeader(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-background-color: #e3eeff; -fx-text-fill: #1a3a6b;" +
                " -fx-padding: 2 8 2 8; -fx-font-size: 10px; -fx-font-weight: bold;");
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private Label grayLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #bbb; -fx-font-style: italic;");
        return lbl;
    }

    // ── Drag to reposition (F-269) ────────────────────────────────────

    private void setupDrag() {
        setOnMousePressed(e -> {
            if (e.isSecondaryButtonDown()) return;
            dragOrigX = e.getSceneX() - getLayoutX();
            dragOrigY = e.getSceneY() - getLayoutY();
            toFront();
            e.consume();
        });
        setOnMouseDragged(e -> {
            if (e.isSecondaryButtonDown()) return;
            double nx = Math.max(0, e.getSceneX() - dragOrigX);
            double ny = Math.max(0, e.getSceneY() - dragOrigY);
            setLayoutX(nx);
            setLayoutY(ny);
            topicType.setX(nx);
            topicType.setY(ny);
            e.consume();
        });
        setOnMouseReleased(e -> editService.updateNodePosition(
                topicType.getId(), getLayoutX(), getLayoutY()));
    }

    private void setupClick() {
        setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 && e.getButton() == MouseButton.PRIMARY
                    && onSelectCallback != null) {
                onSelectCallback.accept(this);
            }
        });
    }
}
