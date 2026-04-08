package com.ditadesigner.ui.nodes;

import com.ditadesigner.model.Relationship;
import com.ditadesigner.model.RelationshipType;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.paint.Color;

/**
 * Visual connector between two {@link DiagramNode}s.
 * Draws a directed line with an arrowhead and a label.
 */
public class ConnectionLine extends Group {

    private final Relationship relationship;
    private final DiagramNode sourceNode;
    private final DiagramNode targetNode;

    private final Line line;
    private final Polygon arrowHead;
    private final Label label;

    public ConnectionLine(Relationship relationship, DiagramNode source, DiagramNode target) {
        this.relationship = relationship;
        this.sourceNode = source;
        this.targetNode = target;

        line = new Line();
        arrowHead = new Polygon();
        label = new Label();
        label.setStyle("-fx-font-size: 9px; -fx-background-color: rgba(255,255,255,0.8); " +
                "-fx-padding: 1 3 1 3; -fx-border-color: #ccc; -fx-border-radius: 2;");

        applyStyle();
        getChildren().addAll(line, arrowHead, label);
        update();
        setMouseTransparent(true);
    }

    /** Recalculate positions — call after any node moves. */
    public void update() {
        double sx = sourceNode.getCenterX();
        double sy = sourceNode.getCenterY();
        double tx = targetNode.getCenterX();
        double ty = targetNode.getCenterY();

        line.setStartX(sx);
        line.setStartY(sy);
        line.setEndX(tx);
        line.setEndY(ty);

        drawArrowHead(sx, sy, tx, ty);

        // Position label at midpoint
        label.setLayoutX((sx + tx) / 2 + 4);
        label.setLayoutY((sy + ty) / 2 - 12);
        label.setText(relationship.getType() != null ? relationship.getType().getLabel() : "");
    }

    public Relationship getRelationship() { return relationship; }

    // ── helpers ───────────────────────────────────────────────────────

    private void applyStyle() {
        RelationshipType type = relationship.getType();
        if (type == null) type = RelationshipType.CONTAINMENT;

        switch (type) {
            case INHERITANCE -> {
                line.setStroke(Color.web("#1565C0"));
                line.setStrokeWidth(2.0);
                line.getStrokeDashArray().clear();
                arrowHead.setFill(Color.web("#1565C0"));
            }
            case CONTAINMENT -> {
                line.setStroke(Color.web("#2e7d32"));
                line.setStrokeWidth(1.8);
                line.getStrokeDashArray().setAll(6.0, 3.0);
                arrowHead.setFill(Color.web("#2e7d32"));
            }
            case DOMAIN_INCLUSION -> {
                line.setStroke(Color.web("#6a1b9a"));
                line.setStrokeWidth(1.5);
                line.getStrokeDashArray().setAll(4.0, 4.0);
                arrowHead.setFill(Color.web("#6a1b9a"));
            }
            default -> {
                line.setStroke(Color.GRAY);
                line.setStrokeWidth(1.5);
                arrowHead.setFill(Color.GRAY);
            }
        }
    }

    private void drawArrowHead(double sx, double sy, double tx, double ty) {
        double arrowLen = 12;
        double arrowWidth = 6;

        double dx = tx - sx;
        double dy = ty - sy;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) { arrowHead.getPoints().clear(); return; }

        double ux = dx / len;
        double uy = dy / len;

        // Arrow tip sits at target centre
        double tipX = tx;
        double tipY = ty;
        double baseX = tipX - ux * arrowLen;
        double baseY = tipY - uy * arrowLen;
        double perpX = -uy * arrowWidth;
        double perpY = ux * arrowWidth;

        arrowHead.getPoints().setAll(
                tipX, tipY,
                baseX + perpX, baseY + perpY,
                baseX - perpX, baseY - perpY
        );
    }
}
