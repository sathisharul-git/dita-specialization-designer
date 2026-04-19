package com.ditadesigner.schema;

import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.model.DitaModel;
import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;
import javafx.application.Platform;

import java.util.ArrayList;
import java.util.List;

/**
 * Single write-path for all DitaModel mutations originating from the Schema Design Workbench.
 * Fires {@link SchemaModelChangeEvent} on the FX thread after every mutation so that
 * MainController and SchemaDesignController can refresh their respective views.
 */
public final class SchemaEditService {

    public enum ChangeKind {
        RENAMED, TOPOLOGY_CHANGED,
        ELEMENT_ADDED, ELEMENT_UPDATED, ELEMENT_DELETED,
        ATTRIBUTE_UPDATED, ATTRIBUTE_DELETED,
        NODE_ADDED, NODE_DELETED, POSITION_CHANGED
    }

    private final DitaModel model;
    private final List<SchemaModelListener> listeners = new ArrayList<>();

    public SchemaEditService(DitaModel model) {
        this.model = model;
    }

    public void addListener(SchemaModelListener l)    { listeners.add(l); }
    public void removeListener(SchemaModelListener l) { listeners.remove(l); }

    // ── TopicType mutations ───────────────────────────────────────────────────

    public void renameTopicType(String topicTypeId, String newName) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null || newName.isBlank()) return;
        tt.setName(newName.trim());
        fire(topicTypeId, ChangeKind.RENAMED);
    }

    public void setTopicTypeBaseType(String topicTypeId, String baseType) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.setBaseType(baseType);
        fire(topicTypeId, ChangeKind.TOPOLOGY_CHANGED);
    }

    public void setTopicTypeNamespace(String topicTypeId, String namespace) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.setNamespace(namespace);
        fire(topicTypeId, ChangeKind.TOPOLOGY_CHANGED);
    }

    public void setTopicTypePublicId(String topicTypeId, String publicId) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.setPublicId(publicId);
        fire(topicTypeId, ChangeKind.TOPOLOGY_CHANGED);
    }

    public void setTopicTypeSystemId(String topicTypeId, String systemId) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.setSystemId(systemId);
        fire(topicTypeId, ChangeKind.TOPOLOGY_CHANGED);
    }

    public void setTopicTypeDescription(String topicTypeId, String description) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.setDescription(description);
        fire(topicTypeId, ChangeKind.TOPOLOGY_CHANGED);
    }

    public TopicType addTopicType(double canvasX, double canvasY) {
        TopicType tt = new TopicType("NewType", "topic");
        tt.setX(canvasX);
        tt.setY(canvasY);
        model.addTopicType(tt);
        fire(tt.getId(), ChangeKind.NODE_ADDED);
        return tt;
    }

    public void deleteTopicType(String topicTypeId) {
        model.removeTopicType(topicTypeId);
        fire(topicTypeId, ChangeKind.NODE_DELETED);
    }

    public void updateNodePosition(String topicTypeId, double x, double y) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.setX(x);
        tt.setY(y);
        fire(topicTypeId, ChangeKind.POSITION_CHANGED);
    }

    // ── Element mutations ────────────────────────────────────────────────────

    public ElementDef addElement(String topicTypeId, String name) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return null;
        ElementDef elem = new ElementDef();
        elem.setName(name.isBlank() ? "newElement" : name.trim());
        elem.setCardinality("?");
        tt.addElement(elem);
        fire(topicTypeId, ChangeKind.ELEMENT_ADDED);
        return elem;
    }

    public void updateElementName(String topicTypeId, String elementId, String newName) {
        ElementDef elem = findElement(topicTypeId, elementId);
        if (elem == null || newName.isBlank()) return;
        elem.setName(newName.trim());
        fire(topicTypeId, ChangeKind.ELEMENT_UPDATED);
    }

    public void updateElementCardinality(String topicTypeId, String elementId, String cardinality) {
        ElementDef elem = findElement(topicTypeId, elementId);
        if (elem == null) return;
        elem.setCardinality(cardinality);
        fire(topicTypeId, ChangeKind.ELEMENT_UPDATED);
    }

    public void updateElementContentModel(String topicTypeId, String elementId, String contentModel) {
        ElementDef elem = findElement(topicTypeId, elementId);
        if (elem == null) return;
        elem.setContentModel(contentModel);
        fire(topicTypeId, ChangeKind.ELEMENT_UPDATED);
    }

    public void updateElementRequired(String topicTypeId, String elementId, boolean required) {
        ElementDef elem = findElement(topicTypeId, elementId);
        if (elem == null) return;
        elem.setRequired(required);
        fire(topicTypeId, ChangeKind.ELEMENT_UPDATED);
    }

    public void deleteElement(String topicTypeId, String elementId) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.getElements().removeIf(e -> e.getId().equals(elementId));
        fire(topicTypeId, ChangeKind.ELEMENT_DELETED);
    }

    // ── Attribute mutations ──────────────────────────────────────────────────

    public void updateAttributeName(String topicTypeId, String attributeId, String newName) {
        AttributeDef attr = findAttribute(topicTypeId, attributeId);
        if (attr == null || newName.isBlank()) return;
        attr.setName(newName.trim());
        fire(topicTypeId, ChangeKind.ATTRIBUTE_UPDATED);
    }

    public void updateAttributeType(String topicTypeId, String attributeId, String type) {
        AttributeDef attr = findAttribute(topicTypeId, attributeId);
        if (attr == null) return;
        attr.setType(type);
        fire(topicTypeId, ChangeKind.ATTRIBUTE_UPDATED);
    }

    public void updateAttributeRequired(String topicTypeId, String attributeId, boolean required) {
        AttributeDef attr = findAttribute(topicTypeId, attributeId);
        if (attr == null) return;
        attr.setRequired(required);
        fire(topicTypeId, ChangeKind.ATTRIBUTE_UPDATED);
    }

    public void updateAttributeDefaultValue(String topicTypeId, String attributeId, String value) {
        AttributeDef attr = findAttribute(topicTypeId, attributeId);
        if (attr == null) return;
        attr.setDefaultValue(value);
        fire(topicTypeId, ChangeKind.ATTRIBUTE_UPDATED);
    }

    public void updateAttributeFixedValue(String topicTypeId, String attributeId, String value) {
        AttributeDef attr = findAttribute(topicTypeId, attributeId);
        if (attr == null) return;
        attr.setFixedValue(value);
        fire(topicTypeId, ChangeKind.ATTRIBUTE_UPDATED);
    }

    public void updateAttributeEnumValues(String topicTypeId, String attributeId, List<String> values) {
        AttributeDef attr = findAttribute(topicTypeId, attributeId);
        if (attr == null) return;
        attr.setEnumValues(new ArrayList<>(values));
        fire(topicTypeId, ChangeKind.ATTRIBUTE_UPDATED);
    }

    public void deleteAttribute(String topicTypeId, String attributeId) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return;
        tt.getAttributes().removeIf(a -> a.getId().equals(attributeId));
        fire(topicTypeId, ChangeKind.ATTRIBUTE_DELETED);
    }

    // ── Lookup helpers ────────────────────────────────────────────────────────

    private ElementDef findElement(String topicTypeId, String elementId) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return null;
        return tt.getElements().stream()
                .filter(e -> e.getId().equals(elementId))
                .findFirst().orElse(null);
    }

    private AttributeDef findAttribute(String topicTypeId, String attributeId) {
        TopicType tt = model.findTopicTypeById(topicTypeId);
        if (tt == null) return null;
        return tt.getAttributes().stream()
                .filter(a -> a.getId().equals(attributeId))
                .findFirst().orElse(null);
    }

    private void fire(String id, ChangeKind kind) {
        SchemaModelChangeEvent event = new SchemaModelChangeEvent(id, kind);
        if (Platform.isFxApplicationThread()) {
            listeners.forEach(l -> l.onModelChanged(event));
        } else {
            Platform.runLater(() -> listeners.forEach(l -> l.onModelChanged(event)));
        }
    }
}
