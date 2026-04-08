package com.ditadesigner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainDef {

    private String id;
    private String name;
    private String namespace;
    private String publicId;
    private String description;
    private List<ElementDef> elements = new ArrayList<>();
    private double x;
    private double y;

    public DomainDef() {
        this.id = UUID.randomUUID().toString();
    }

    public DomainDef(String name) {
        this();
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ElementDef> getElements() { return elements; }
    public void setElements(List<ElementDef> elements) { this.elements = elements; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public void addElement(ElementDef element) {
        element.setParentId(this.id);
        elements.add(element);
    }

    @Override
    public String toString() {
        return name + " (domain)";
    }
}
