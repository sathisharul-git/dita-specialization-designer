package com.ditadesigner.ui.nodes;

import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Visual canvas node for a {@link TopicType}.
 * Features: drag-and-drop, selection highlight, colour by base type,
 * double-click inline rename, right-click context menu, element/attribute badges.
 *
 * Implements F-001, F-007, F-008, F-014, F-016, F-017, F-033, F-034, F-036,
 * F-037, F-038, F-039, F-040.
 */
public class TopicTypeNode extends VBox implements DiagramNode {

    // ── base-type → header colour ─────────────────────────────────────
    private static final java.util.Map<String, String> BASE_COLORS = java.util.Map.of(
        "task",      "#1565C0",
        "concept",   "#2e7d32",
        "reference", "#6a1b9a",
        "map",       "#e65100",
        "bookmap",   "#bf360c",
        "domain",    "#7b3ad5"
    );
    private static final String DEFAULT_COLOR = "#37474f";

    private final TopicType topicType;
    private boolean selected = false;

    private final Label nameLabel;
    private final Label baseLabel;
    private final Label badgeLabel;
    private final VBox elementsBox;
    private final VBox attributesBox;
    private final TextField renameField = new TextField();
    private VBox titleBar;

    private double dragOrigX;
    private double dragOrigY;

    // Callbacks wired by MainController
    private Consumer<TopicTypeNode>          onClickCallback;
    private BiConsumer<TopicTypeNode,Boolean> onDragStopCallback;
    private Consumer<TopicTypeNode>          onDeleteCallback;
    private Consumer<TopicTypeNode>          onDuplicateCallback;
    private Consumer<TopicTypeNode>          onGenerateDtdCallback;

    public TopicTypeNode(TopicType topicType) {
        this.topicType = topicType;

        setSpacing(0);
        setPrefWidth(210);
        setMinWidth(210);
        setMaxWidth(210);

        // ── Title bar ─────────────────────────────────────────────────
        titleBar = new VBox(2);
        titleBar.setPadding(new Insets(6, 8, 6, 8));

        Label stereotype = new Label("«TopicType»");
        stereotype.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 9px; -fx-font-style: italic;");

        nameLabel = new Label(topicType.getName());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(190);

        titleBar.getChildren().addAll(stereotype, nameLabel);
        setupDoubleClickRename();

        // ── Base-type row ─────────────────────────────────────────────
        baseLabel = new Label();
        baseLabel.setStyle("-fx-text-fill: white; -fx-padding: 3 8 3 8; -fx-font-size: 11px;");
        baseLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Badge (element + attribute count) ─────────────────────────
        badgeLabel = new Label();
        badgeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.18); -fx-text-fill: white;" +
                " -fx-padding: 1 8 1 8; -fx-font-size: 10px;");
        badgeLabel.setMaxWidth(Double.MAX_VALUE);

        // ── Elements section ──────────────────────────────────────────
        Label elemHeader = sectionHeader("Elements");
        elementsBox = new VBox(1);
        elementsBox.setPadding(new Insets(2, 8, 4, 8));

        // ── Attributes section ────────────────────────────────────────
        Label attrHeader = sectionHeader("Attributes");
        attributesBox = new VBox(1);
        attributesBox.setPadding(new Insets(2, 8, 4, 8));

        getChildren().addAll(titleBar, baseLabel, badgeLabel, elemHeader, elementsBox, attrHeader, attributesBox);

        setLayoutX(topicType.getX());
        setLayoutY(topicType.getY());

        applyBaseTypeColor();
        applyBorder(false);
        refresh();
        setupDrag();
        setupClick();
        setupContextMenu();
        setupTooltip();
    }

    // ── DiagramNode ───────────────────────────────────────────────────

    @Override public String getModelId() { return topicType.getId(); }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        applyBorder(selected);
    }

    @Override public boolean isSelected() { return selected; }

    @Override public double getCenterX() { return getLayoutX() + getPrefWidth() / 2.0; }

    @Override
    public double getCenterY() {
        double h = getHeight() > 0 ? getHeight() : 160;
        return getLayoutY() + h / 2.0;
    }

    @Override
    public void refresh() {
        nameLabel.setText(topicType.getName());
        String color = baseColor();
        baseLabel.setText("base: " + topicType.getBaseType());
        baseLabel.setStyle("-fx-background-color: " + lighten(color) + "; -fx-text-fill: white;" +
                " -fx-padding: 3 8 3 8; -fx-font-size: 11px;");

        int ec = topicType.getElements().size();
        int ac = topicType.getAttributes().size();
        badgeLabel.setText(ec + " element" + (ec != 1 ? "s" : "") +
                "  ·  " + ac + " attr" + (ac != 1 ? "s" : ""));

        elementsBox.getChildren().clear();
        for (ElementDef elem : topicType.getElements()) {
            Label lbl = row("+ " + elem.getName() + " " + elem.getCardinality(),
                    "#333", "#f0f7ff");
            elementsBox.getChildren().add(lbl);
        }
        if (topicType.getElements().isEmpty()) elementsBox.getChildren().add(grayLabel("(none)"));

        attributesBox.getChildren().clear();
        for (var attr : topicType.getAttributes()) {
            Label lbl = row("• " + attr.getName() + " : " + attr.getType(), "#555", "#f5f5ff");
            attributesBox.getChildren().add(lbl);
        }
        if (topicType.getAttributes().isEmpty()) attributesBox.getChildren().add(grayLabel("(none)"));

        applyBaseTypeColor();
    }

    /** Mark this node invalid (red border flash). */
    public void markInvalid(boolean invalid) {
        if (invalid) {
            setStyle(getStyle()
                    .replaceAll("-fx-border-color:[^;]+;", "-fx-border-color: #e53935;")
                    .replaceAll("-fx-border-width:[^;]+;", "-fx-border-width: 2.5;"));
        } else {
            applyBorder(selected);
        }
    }

    // ── Model accessor ────────────────────────────────────────────────

    public TopicType getTopicType() { return topicType; }

    // ── Callback wiring ───────────────────────────────────────────────

    public void setOnClickCallback(Consumer<TopicTypeNode> cb)          { this.onClickCallback = cb; }
    public void setOnDragStopCallback(BiConsumer<TopicTypeNode,Boolean> cb) { this.onDragStopCallback = cb; }
    public void setOnDeleteCallback(Consumer<TopicTypeNode> cb)         { this.onDeleteCallback = cb; }
    public void setOnDuplicateCallback(Consumer<TopicTypeNode> cb)      { this.onDuplicateCallback = cb; }
    public void setOnGenerateDtdCallback(Consumer<TopicTypeNode> cb)    { this.onGenerateDtdCallback = cb; }

    // ── Private helpers ───────────────────────────────────────────────

    private void applyBaseTypeColor() {
        String color = baseColor();
        titleBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 6 6 0 0;");
    }

    private String baseColor() {
        String bt = topicType.getBaseType();
        return BASE_COLORS.getOrDefault(bt != null ? bt.toLowerCase() : "", DEFAULT_COLOR);
    }

    /** Slightly lighter version of the base colour for the sub-header strip. */
    private String lighten(String hex) {
        // Map each base colour to a lighter shade
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

    private void applyBorder(boolean sel) {
        String colour = sel ? "#ff8800" : baseColor();
        String width  = sel ? "2.5" : "1.5";
        setStyle("-fx-border-color: " + colour + "; -fx-border-width: " + width + ";" +
                " -fx-border-radius: 6; -fx-background-radius: 6;" +
                " -fx-background-color: white;" +
                " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 6, 0, 2, 2);");
    }

    private Label sectionHeader(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-background-color: #d0e8ff; -fx-text-fill: #2255aa;" +
                " -fx-padding: 2 8 2 8; -fx-font-size: 10px; -fx-font-weight: bold;");
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private Label row(String text, String fg, String bg) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: " + fg + ";" +
                " -fx-background-color: " + bg + ";" +
                " -fx-padding: 1 0 1 0;");
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private Label grayLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #aaa; -fx-font-style: italic;");
        return lbl;
    }

    // ── Drag ─────────────────────────────────────────────────────────

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
            double newX = Math.max(0, e.getSceneX() - dragOrigX);
            double newY = Math.max(0, e.getSceneY() - dragOrigY);
            setLayoutX(newX);
            setLayoutY(newY);
            topicType.setX(newX);
            topicType.setY(newY);
            if (onDragStopCallback != null) onDragStopCallback.accept(this, false);
            e.consume();
        });
        setOnMouseReleased(e -> {
            if (onDragStopCallback != null) onDragStopCallback.accept(this, true);
        });
    }

    // ── Click ─────────────────────────────────────────────────────────

    private void setupClick() {
        setOnMouseClicked(e -> {
            if (e.isSecondaryButtonDown()) return;
            if (e.getClickCount() == 1 && onClickCallback != null) {
                onClickCallback.accept(this);
            }
            e.consume();
        });
    }

    // ── Double-click rename (F-016) ───────────────────────────────────

    private void setupDoubleClickRename() {
        nameLabel.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                startInlineRename();
                e.consume();
            }
        });
    }

    public void startInlineRename() {
        renameField.setText(topicType.getName());
        renameField.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;" +
                " -fx-background-color: white; -fx-text-fill: #1a1a1a;");
        renameField.setPrefWidth(185);

        // Swap label → field
        int idx = titleBar.getChildren().indexOf(nameLabel);
        if (idx >= 0) {
            titleBar.getChildren().set(idx, renameField);
            renameField.requestFocus();
            renameField.selectAll();
        }

        Runnable commit = () -> {
            String newName = renameField.getText().trim();
            if (!newName.isEmpty()) topicType.setName(newName);
            int i = titleBar.getChildren().indexOf(renameField);
            if (i >= 0) titleBar.getChildren().set(i, nameLabel);
            refresh();
        };
        renameField.setOnAction(e -> commit.run());
        renameField.focusedProperty().addListener((obs, was, is) -> { if (!is) commit.run(); });
    }

    // ── Right-click context menu (F-014, F-017, F-036, F-040) ────────

    private void setupContextMenu() {
        ContextMenu cm = new ContextMenu();

        MenuItem rename    = new MenuItem("✏  Rename");
        MenuItem duplicate = new MenuItem("⎘  Duplicate");
        MenuItem genDtd    = new MenuItem("⚙  Generate DTD for this type");
        MenuItem sep1      = new SeparatorMenuItem();
        MenuItem delete    = new MenuItem("✕  Delete");
        delete.setStyle("-fx-text-fill: #c0392b;");

        rename.setOnAction(e -> startInlineRename());
        duplicate.setOnAction(e -> { if (onDuplicateCallback != null) onDuplicateCallback.accept(this); });
        genDtd.setOnAction(e -> { if (onGenerateDtdCallback != null) onGenerateDtdCallback.accept(this); });
        delete.setOnAction(e -> { if (onDeleteCallback != null) onDeleteCallback.accept(this); });

        cm.getItems().addAll(rename, duplicate, genDtd, sep1, delete);

        setOnContextMenuRequested(e -> {
            cm.show(this, e.getScreenX(), e.getScreenY());
            e.consume();
        });
    }

    // ── Tooltip (F-038) ───────────────────────────────────────────────

    private void setupTooltip() {
        Tooltip tip = new Tooltip();
        tip.setStyle("-fx-font-size: 11px;");
        tip.textProperty().addListener((obs, o, n) -> {});

        // Update tooltip text dynamically
        setOnMouseEntered(e -> {
            String desc = topicType.getDescription();
            String cls  = "- topic/topic " + topicType.getBaseType() + "/" + topicType.getBaseType()
                        + " " + topicType.getName() + "/" + topicType.getName() + " ";
            tip.setText(
                topicType.getName() + " : " + topicType.getBaseType() + "\n" +
                (desc != null && !desc.isBlank() ? desc + "\n" : "") +
                "class: " + cls
            );
            Tooltip.install(this, tip);
        });
    }
}
