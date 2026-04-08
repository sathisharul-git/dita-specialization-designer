package com.ditadesigner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Relationship {

    private String id;
    private RelationshipType type;
    private String sourceId;   // model object id (TopicType, Domain, etc.)
    private String targetId;
    private String label;

    public Relationship() {
        this.id = UUID.randomUUID().toString();
    }

    public Relationship(RelationshipType type, String sourceId, String targetId) {
        this();
        this.type = type;
        this.sourceId = sourceId;
        this.targetId = targetId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public RelationshipType getType() { return type; }
    public void setType(RelationshipType type) { this.type = type; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    @Override
    public String toString() {
        return sourceId + " --[" + (type != null ? type.getLabel() : "?") + "]--> " + targetId;
    }
}
