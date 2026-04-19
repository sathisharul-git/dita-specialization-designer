package com.ditadesigner.schema.panel;

import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;
import com.ditadesigner.schema.SchemaEditService;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/**
 * Right-side always-visible panel — equivalent to oXygen's Facets View (F-270).
 * Shows {@link TopicTypeConstraints}, {@link ElementConstraints}, or {@link AttributeConstraints}
 * depending on what is selected on the schema canvas.
 */
public class ConstraintsPanel extends ScrollPane {

    private final VBox content = new VBox(8);

    public ConstraintsPanel() {
        content.setStyle("-fx-padding: 8;");
        setContent(content);
        setFitToWidth(true);
        setPrefWidth(270);
        setMinWidth(220);
        showEmpty();
    }

    /** Show TopicType-level constraints (F-271). */
    public void showTopicType(TopicType tt, SchemaEditService svc) {
        setContent(new TopicTypeConstraints(tt, svc));
        setFitToWidth(true);
    }

    /** Show ElementDef constraints (F-272). */
    public void showElement(String topicTypeId, ElementDef elem, SchemaEditService svc) {
        setContent(new ElementConstraints(topicTypeId, elem, svc));
        setFitToWidth(true);
    }

    /** Show AttributeDef constraints (F-273). */
    public void showAttribute(String topicTypeId, AttributeDef attr, SchemaEditService svc) {
        setContent(new AttributeConstraints(topicTypeId, attr, svc));
        setFitToWidth(true);
    }

    public void showEmpty() {
        content.getChildren().setAll(new Label("Select a component to view its constraints."));
        setContent(content);
    }
}
