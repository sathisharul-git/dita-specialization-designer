package com.ditadesigner.schema.canvas;

import com.ditadesigner.model.RelationshipType;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;

/**
 * Arrow drawn between two {@link TopicTypeSchemaNode}s on the schema canvas.
 * Inheritance → solid thick line with filled arrowhead (oXygen thick-line convention).
 * Containment/Domain → dashed thin line with open arrowhead.
 */
public class SchemaConnectionLine extends Group {

    private final Line    shaft = new Line();
    private final Polygon head  = new Polygon();

    private final TopicTypeSchemaNode source;
    private final TopicTypeSchemaNode target;
    private final RelationshipType    relType;

    public SchemaConnectionLine(TopicTypeSchemaNode source,
                                TopicTypeSchemaNode target,
                                RelationshipType relType) {
        this.source  = source;
        this.target  = target;
        this.relType = relType;
        styleForType();
        getChildren().addAll(shaft, head);
        update();
    }

    /** Recompute line endpoints from current node positions. */
    public void update() {
        double sx = source.getCenterX();
        double sy = source.getCenterY();
        double tx = target.getCenterX();
        double ty = target.getCenterY();

        shaft.setStartX(sx); shaft.setStartY(sy);
        shaft.setEndX(tx);   shaft.setEndY(ty);

        // Arrowhead triangle at target end
        double angle = Math.atan2(ty - sy, tx - sx);
        double len = 12, wing = Math.PI / 6;
        double x1 = tx - len * Math.cos(angle - wing);
        double y1 = ty - len * Math.sin(angle - wing);
        double x2 = tx - len * Math.cos(angle + wing);
        double y2 = ty - len * Math.sin(angle + wing);
        head.getPoints().setAll(tx, ty, x1, y1, x2, y2);
    }

    public TopicTypeSchemaNode getSource() { return source; }
    public TopicTypeSchemaNode getTarget() { return target; }

    private void styleForType() {
        boolean isInheritance = relType == RelationshipType.INHERITANCE;
        Color   color  = isInheritance ? Color.web("#1565C0") : Color.web("#7b3ad5");
        double  width  = isInheritance ? 2.0 : 1.2;

        shaft.setStroke(color);
        shaft.setStrokeWidth(width);
        shaft.setMouseTransparent(true);
        if (!isInheritance) shaft.getStrokeDashArray().addAll(7.0, 4.0);

        head.setFill(isInheritance ? color : Color.TRANSPARENT);
        head.setStroke(color);
        head.setStrokeWidth(1.2);
        head.setMouseTransparent(true);
    }
}
