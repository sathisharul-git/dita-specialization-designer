package com.ditadesigner.schema.outline;

import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.model.DitaModel;
import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Tree-based Outline panel showing all TopicTypes → elements → attributes (F-275–F-277).
 * A search field filters the tree to matching component names.
 */
public class SchemaOutlinePanel extends VBox {

    private final TreeView<SchemaOutlineItem> tree = new TreeView<>();
    private final TextField searchField = new TextField();
    private       DitaModel model;

    private Consumer<SchemaOutlineItem> onSelectCallback;

    public SchemaOutlinePanel() {
        setSpacing(4);

        searchField.setPromptText("Find component…");
        searchField.setStyle("-fx-font-size: 11px;");
        searchField.textProperty().addListener((obs, o, text) -> applyFilter(text));

        tree.setShowRoot(false);
        tree.setRoot(new TreeItem<>());
        tree.setCellFactory(tv -> new OutlineCell());
        VBox.setVgrow(tree, Priority.ALWAYS);

        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null && sel.getValue() != null && onSelectCallback != null) {
                onSelectCallback.accept(sel.getValue());
            }
        });

        getChildren().addAll(searchField, tree);
        setPrefWidth(220);
    }

    public void setOnSelectCallback(Consumer<SchemaOutlineItem> cb) { this.onSelectCallback = cb; }

    /** Rebuild the full tree from the model (F-275). */
    public void refresh(DitaModel model) {
        this.model = model;
        applyFilter(searchField.getText());
    }

    private void applyFilter(String filter) {
        if (model == null) return;
        TreeItem<SchemaOutlineItem> root = new TreeItem<>();
        String lf = filter == null ? "" : filter.toLowerCase();

        for (TopicType tt : model.getTopicTypes()) {
            if (!lf.isEmpty() && !tt.getName().toLowerCase().contains(lf)) {
                // Still include if any child matches
                boolean childMatch = tt.getElements().stream()
                        .anyMatch(e -> e.getName().toLowerCase().contains(lf))
                        || tt.getAttributes().stream()
                        .anyMatch(a -> a.getName().toLowerCase().contains(lf));
                if (!childMatch) continue;
            }

            TreeItem<SchemaOutlineItem> ttItem =
                    new TreeItem<>(new SchemaOutlineItem.TopicTypeItem(tt));
            ttItem.setExpanded(true);

            for (ElementDef elem : tt.getElements()) {
                if (!lf.isEmpty() && !elem.getName().toLowerCase().contains(lf)) continue;
                ttItem.getChildren().add(
                        new TreeItem<>(new SchemaOutlineItem.ElementItem(elem, tt)));
            }
            for (AttributeDef attr : tt.getAttributes()) {
                if (!lf.isEmpty() && !attr.getName().toLowerCase().contains(lf)) continue;
                ttItem.getChildren().add(
                        new TreeItem<>(new SchemaOutlineItem.AttributeItem(attr, tt)));
            }
            root.getChildren().add(ttItem);
        }
        tree.setRoot(root);
    }

    // ── Cell renderer ─────────────────────────────────────────────────

    private static class OutlineCell extends TreeCell<SchemaOutlineItem> {
        @Override
        protected void updateItem(SchemaOutlineItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setStyle(null); return; }
            if (item instanceof SchemaOutlineItem.TopicTypeItem tti) {
                setText("⬛ " + tti.topicType().getName());
                setStyle("-fx-font-weight: bold; -fx-text-fill: #1565C0;");
            } else if (item instanceof SchemaOutlineItem.ElementItem ei) {
                setText("▸ " + ei.element().getName() + " " + ei.element().getCardinality());
                setStyle("-fx-font-size: 11px; -fx-text-fill: #333;");
            } else if (item instanceof SchemaOutlineItem.AttributeItem ai) {
                setText("● " + ai.attribute().getName() + " : " + ai.attribute().getType());
                setStyle("-fx-font-size: 11px; -fx-text-fill: #6a1b9a;");
            }
        }
    }
}
