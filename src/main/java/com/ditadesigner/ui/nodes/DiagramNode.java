package com.ditadesigner.ui.nodes;

/**
 * Contract for every draggable diagram node on the canvas.
 */
public interface DiagramNode {

    /** Model object id (TopicType.id or DomainDef.id). */
    String getModelId();

    /** Highlight/un-highlight this node. */
    void setSelected(boolean selected);

    boolean isSelected();

    /** X coordinate of the node's visual centre. */
    double getCenterX();

    /** Y coordinate of the node's visual centre. */
    double getCenterY();

    /** Refresh visual content after model changes. */
    void refresh();
}
