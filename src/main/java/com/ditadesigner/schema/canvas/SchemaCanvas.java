package com.ditadesigner.schema.canvas;

import com.ditadesigner.model.DitaModel;
import com.ditadesigner.model.Relationship;
import com.ditadesigner.model.RelationshipType;
import com.ditadesigner.model.TopicType;
import com.ditadesigner.schema.SchemaEditService;
import com.ditadesigner.schema.SchemaModelChangeEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Zoomable/pannable canvas that renders schema component boxes (F-259, F-260).
 * Uses a {@link Pane} with a {@link Scale} transform — same pattern as the main canvas.
 */
public class SchemaCanvas extends ScrollPane {

    private final Pane  innerPane = new Pane();
    private final Scale scaleXf   = new Scale(1, 1, 0, 0);
    private double zoomLevel = 1.0;

    private DitaModel         model;
    private SchemaEditService editService;

    private final Map<String, TopicTypeSchemaNode> nodeMap  = new HashMap<>();
    private final List<SchemaConnectionLine>       lines    = new ArrayList<>();

    // Selection state
    private TopicTypeSchemaNode selectedNode = null;

    // Callbacks wired by SchemaDesignController
    private Consumer<TopicTypeSchemaNode>  onNodeSelect;
    private BiConsumer<TopicTypeSchemaNode, Object> onElementSelect;
    private BiConsumer<TopicTypeSchemaNode, Object> onAttributeSelect;

    // ── Pan support ───────────────────────────────────────────────────
    private double panStartX, panStartY, panOrigH, panOrigV;
    private boolean panning = false;

    public SchemaCanvas() {
        innerPane.getTransforms().add(scaleXf);
        innerPane.setBackground(new Background(new BackgroundFill(Color.web("#f8f9fa"), CornerRadii.EMPTY, Insets.EMPTY)));
        drawGrid();

        setContent(innerPane);
        setFitToWidth(false);
        setFitToHeight(false);
        setPannable(false);

        // Ctrl+Scroll to zoom
        setOnScroll(e -> {
            if (e.isControlDown()) {
                double factor = e.getDeltaY() > 0 ? 1.1 : 0.91;
                zoomLevel = Math.max(0.2, Math.min(3.0, zoomLevel * factor));
                scaleXf.setX(zoomLevel);
                scaleXf.setY(zoomLevel);
                e.consume();
            }
        });

        // Space+drag to pan
        setOnMousePressed(e -> {
            if (e.isMiddleButtonDown() || (e.isPrimaryButtonDown() && e.isAltDown())) {
                panning = true;
                panStartX = e.getX();
                panStartY = e.getY();
                panOrigH = getHvalue();
                panOrigV = getVvalue();
            }
        });
        setOnMouseDragged(e -> {
            if (panning) {
                double dx = (e.getX() - panStartX) / innerPane.getWidth();
                double dy = (e.getY() - panStartY) / innerPane.getHeight();
                setHvalue(Math.max(0, Math.min(1, panOrigH - dx)));
                setVvalue(Math.max(0, Math.min(1, panOrigV - dy)));
            }
        });
        setOnMouseReleased(e -> panning = false);
    }

    // ── Public API ────────────────────────────────────────────────────

    public void init(DitaModel model, SchemaEditService editService) {
        this.model       = model;
        this.editService = editService;
        setupDropTarget();
        loadFromModel();
    }

    public void setOnNodeSelect(Consumer<TopicTypeSchemaNode> cb)               { this.onNodeSelect = cb; }
    public void setOnElementSelect(BiConsumer<TopicTypeSchemaNode,Object> cb)   { this.onElementSelect = cb; }
    public void setOnAttributeSelect(BiConsumer<TopicTypeSchemaNode,Object> cb) { this.onAttributeSelect = cb; }

    /** Full reload from model (called after NODE_ADDED / NODE_DELETED). */
    public void loadFromModel() {
        innerPane.getChildren().clear();
        nodeMap.clear();
        lines.clear();

        for (TopicType tt : model.getTopicTypes()) {
            addNodeForTopicType(tt);
        }
        for (Relationship rel : model.getRelationships()) {
            TopicTypeSchemaNode src = nodeMap.get(rel.getSourceId());
            TopicTypeSchemaNode tgt = nodeMap.get(rel.getTargetId());
            if (src != null && tgt != null
                    && (rel.getType() == RelationshipType.INHERITANCE
                     || rel.getType() == RelationshipType.CONTAINMENT)) {
                SchemaConnectionLine line = new SchemaConnectionLine(src, tgt, rel.getType());
                lines.add(line);
                innerPane.getChildren().add(0, line); // behind nodes
            }
        }
        updateLines();
    }

    /** Refresh a single node after a property change. */
    public void refreshNode(String modelObjectId) {
        TopicTypeSchemaNode node = nodeMap.get(modelObjectId);
        if (node != null) {
            node.refresh();
            updateLines();
        }
    }

    /** Handle change events from SchemaEditService. */
    public void onModelChanged(SchemaModelChangeEvent event) {
        switch (event.kind()) {
            case NODE_ADDED, NODE_DELETED, TOPOLOGY_CHANGED -> loadFromModel();
            case RENAMED, ELEMENT_ADDED, ELEMENT_UPDATED, ELEMENT_DELETED,
                 ATTRIBUTE_UPDATED, ATTRIBUTE_DELETED -> refreshNode(event.modelObjectId());
            case POSITION_CHANGED -> updateLines();
        }
    }

    /** Fit all nodes into view (F-260). */
    public void fitAll() {
        if (nodeMap.isEmpty()) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        for (TopicTypeSchemaNode n : nodeMap.values()) {
            minX = Math.min(minX, n.getLayoutX());
            minY = Math.min(minY, n.getLayoutY());
            maxX = Math.max(maxX, n.getLayoutX() + n.getPrefWidth());
            maxY = Math.max(maxY, n.getLayoutY() + (n.getHeight() > 0 ? n.getHeight() : 200));
        }
        double w = getWidth()  - 40;
        double h = getHeight() - 40;
        double scaleX = w / (maxX - minX + 60);
        double scaleY = h / (maxY - minY + 60);
        zoomLevel = Math.max(0.2, Math.min(1.5, Math.min(scaleX, scaleY)));
        scaleXf.setX(zoomLevel);
        scaleXf.setY(zoomLevel);
        setHvalue(0); setVvalue(0);
    }

    public void resetZoom() { zoomLevel = 1.0; scaleXf.setX(1); scaleXf.setY(1); }

    public void selectById(String modelObjectId) {
        TopicTypeSchemaNode node = nodeMap.get(modelObjectId);
        selectNode(node);
    }

    public Optional<TopicTypeSchemaNode> findSchemaNode(String modelObjectId) {
        return Optional.ofNullable(nodeMap.get(modelObjectId));
    }

    public void scrollToNode(String modelObjectId) {
        TopicTypeSchemaNode node = nodeMap.get(modelObjectId);
        if (node == null) return;
        double cx = node.getLayoutX() * zoomLevel;
        double cy = node.getLayoutY() * zoomLevel;
        setHvalue(cx / Math.max(1, innerPane.getWidth()  * zoomLevel));
        setVvalue(cy / Math.max(1, innerPane.getHeight() * zoomLevel));
    }

    // ── Internal ──────────────────────────────────────────────────────

    private void addNodeForTopicType(TopicType tt) {
        TopicTypeSchemaNode node = new TopicTypeSchemaNode(tt, editService);
        node.setOnSelectCallback(n -> {
            selectNode(n);
            if (onNodeSelect != null) onNodeSelect.accept(n);
        });
        node.setOnElementSelectCallback((n, elem) -> {
            selectNode(n);
            if (onElementSelect != null) onElementSelect.accept(n, elem);
        });
        node.setOnAttributeSelectCallback((n, attr) -> {
            selectNode(n);
            if (onAttributeSelect != null) onAttributeSelect.accept(n, attr);
        });
        nodeMap.put(tt.getId(), node);
        innerPane.getChildren().add(node);
    }

    private void selectNode(TopicTypeSchemaNode node) {
        if (selectedNode != null) selectedNode.setSelected(false);
        selectedNode = node;
        if (node != null) node.setSelected(true);
    }

    private void updateLines() {
        for (SchemaConnectionLine line : lines) line.update();
    }

    private void drawGrid() {
        // Subtle dot-grid using CSS background
        innerPane.setStyle("-fx-background-color: #f8f9fa;" +
                " -fx-background-image: radial-gradient(circle, #d0d4da 1px, transparent 1px);" +
                " -fx-background-size: 24px 24px;");
        innerPane.setPrefSize(3000, 2000);
    }

    // ── Palette drop target (F-267, F-268) ────────────────────────────

    private void setupDropTarget() {
        innerPane.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        innerPane.setOnDragDropped(e -> {
            String type = e.getDragboard().getString();
            double x = e.getX() / zoomLevel;
            double y = e.getY() / zoomLevel;
            handlePaletteDrop(type, x, y);
            e.setDropCompleted(true);
            e.consume();
        });
    }

    private void handlePaletteDrop(String type, double x, double y) {
        if (model == null) return;
        switch (type) {
            case "TOPIC_TYPE" -> editService.addTopicType(x, y);
            case "ELEMENT" -> {
                // If a node is selected, add to it; otherwise add to first topic type
                TopicTypeSchemaNode target = selectedNode;
                if (target == null && !nodeMap.isEmpty())
                    target = nodeMap.values().iterator().next();
                if (target != null)
                    editService.addElement(target.getModelId(), "newElement");
            }
        }
    }
}
