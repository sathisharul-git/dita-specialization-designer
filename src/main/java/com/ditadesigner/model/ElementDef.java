package com.ditadesigner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElementDef {

    private String id;
    private String name;
    private String contentModel;   // e.g. "(#PCDATA|ph|b|i)*" or "EMPTY" or "ANY"
    private String cardinality;    // "1", "?", "+", "*"
    private boolean required;
    private String parentId;       // owning TopicType or parent ElementDef id
    private String description;
    private List<AttributeDef> attributes = new ArrayList<>();
    private double x;
    private double y;

    public ElementDef() {
        this.id = UUID.randomUUID().toString();
        this.cardinality = "?";
        this.contentModel = "(#PCDATA)*";
    }

    public ElementDef(String name) {
        this();
        this.name = name;
    }

    public ElementDef(String name, String contentModel, String cardinality) {
        this(name);
        this.contentModel = contentModel;
        this.cardinality = cardinality;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContentModel() { return contentModel; }
    public void setContentModel(String contentModel) { this.contentModel = contentModel; }

    public String getCardinality() { return cardinality; }
    public void setCardinality(String cardinality) { this.cardinality = cardinality; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<AttributeDef> getAttributes() { return attributes; }
    public void setAttributes(List<AttributeDef> attributes) { this.attributes = attributes; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public void addAttribute(AttributeDef attr) {
        attributes.add(attr);
    }

    /** Returns the cardinality symbol for DTD content model use. */
    public String cardinalitySuffix() {
        return switch (cardinality) {
            case "?" -> "?";
            case "+" -> "+";
            case "*" -> "*";
            default -> "";
        };
    }

    @Override
    public String toString() {
        return name + " " + cardinality;
    }
}
