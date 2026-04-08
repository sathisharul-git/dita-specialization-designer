package com.ditadesigner.ui.nodes;

import com.ditadesigner.model.ElementDef;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Visual node representing a standalone {@link ElementDef} on the canvas.
 */
public class ElementNode extends VBox implements DiagramNode {

    private final ElementDef elementDef;
    private boolean selected = false;

    private final Label nameLabel;
    private final Label detailLabel;

    private double dragOrigX;
    private double dragOrigY;

    private Consumer<ElementNode> onClickCallback;
    private BiConsumer<ElementNode, Boolean> onDragStopCallback;

    public ElementNode(ElementDef elementDef) {
        this.elementDef = elementDef;

        setSpacing(0);
        setPrefWidth(160);
        setMinWidth(160);

        // Header
        VBox header = new VBox(2);
        header.setPadding(new Insets(5, 8, 5, 8));
        header.setStyle("-fx-background-color: #27ae60; -fx-background-radius: 5 5 0 0;");

        Label stereotype = new Label("«Element»");
        stereotype.setStyle("-fx-text-fill: #a8f0c8; -fx-font-size: 9px; -fx-font-style: italic;");

        nameLabel = new Label(elementDef.getName());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");

        header.getChildren().addAll(stereotype, nameLabel);

        // Detail
        detailLabel = new Label();
        detailLabel.setStyle("-fx-background-color: #eafaf1; -fx-text-fill: #555;" +
                " -fx-padding: 4 8 4 8; -fx-font-size: 10px;");
        detailLabel.setMaxWidth(Double.MAX_VALUE);
        detailLabel.setWrapText(true);

        getChildren().addAll(header, detailLabel);
        applyBorder(false);
        setLayoutX(elementDef.getX());
        setLayoutY(elementDef.getY());

        refresh();
        setupDrag();
        setupClick();
    }

    // ── DiagramNode ───────────────────────────────────────────────────

    @Override
    public String getModelId() { return elementDef.getId(); }

    @Override
    public void setSelected(boolean selected) {
        this.selected = selected;
        applyBorder(selected);
    }

    @Override
    public boolean isSelected() { return selected; }

    @Override
    public double getCenterX() { return getLayoutX() + getPrefWidth() / 2; }

    @Override
    public double getCenterY() { return getLayoutY() + getHeight() / 2; }

    @Override
    public void refresh() {
        nameLabel.setText(elementDef.getName());
        String cm = elementDef.getContentModel() != null ? elementDef.getContentModel() : "";
        String card = elementDef.getCardinality();
        String req = elementDef.isRequired() ? " [required]" : "";
        detailLabel.setText("cardinality: " + card + req + "\n" +
                (cm.isEmpty() ? "" : "model: " + cm));
    }

    public ElementDef getElementDef() { return elementDef; }

    public void setOnClickCallback(Consumer<ElementNode> cb) { this.onClickCallback = cb; }
    public void setOnDragStopCallback(BiConsumer<ElementNode, Boolean> cb) { this.onDragStopCallback = cb; }

    // ── helpers ───────────────────────────────────────────────────────

    private void applyBorder(boolean sel) {
        String colour = sel ? "#ff8800" : "#27ae60";
        String width = sel ? "2.5" : "1.5";
        setStyle("-fx-border-color: " + colour + "; -fx-border-width: " + width + ";" +
                " -fx-border-radius: 5; -fx-background-radius: 5;" +
                " -fx-background-color: white;" +
                " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 5, 0, 2, 2);");
    }

    private void setupDrag() {
        setOnMousePressed(e -> {
            dragOrigX = e.getSceneX() - getLayoutX();
            dragOrigY = e.getSceneY() - getLayoutY();
            toFront();
            e.consume();
        });
        setOnMouseDragged(e -> {
            double newX = Math.max(0, e.getSceneX() - dragOrigX);
            double newY = Math.max(0, e.getSceneY() - dragOrigY);
            setLayoutX(newX);
            setLayoutY(newY);
            elementDef.setX(newX);
            elementDef.setY(newY);
            if (onDragStopCallback != null) {
                onDragStopCallback.accept(this, false);
            }
            e.consume();
        });
        setOnMouseReleased(e -> {
            if (onDragStopCallback != null) {
                onDragStopCallback.accept(this, true);
            }
        });
    }

    private void setupClick() {
        setOnMouseClicked(e -> {
            if (onClickCallback != null) {
                onClickCallback.accept(this);
            }
            e.consume();
        });
    }
}
