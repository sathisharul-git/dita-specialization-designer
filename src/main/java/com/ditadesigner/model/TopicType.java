package com.ditadesigner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicType {

    private String id;
    private String name;
    private String baseType;        // "topic", "task", "concept", "reference", "map"
    private String namespace;
    private String publicId;        // e.g. "-//MYCO//ELEMENTS DITA myTask//EN"
    private String systemId;        // e.g. "myTask.dtd"
    private String module;          // .mod file name stem
    private String description;
    private List<ElementDef> elements = new ArrayList<>();
    private List<AttributeDef> attributes = new ArrayList<>();
    private double x;
    private double y;

    public TopicType() {
        this.id = UUID.randomUUID().toString();
        this.baseType = "topic";
    }

    public TopicType(String name, String baseType) {
        this();
        this.name = name;
        this.baseType = baseType;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBaseType() { return baseType; }
    public void setBaseType(String baseType) { this.baseType = baseType; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getPublicId() { return publicId; }
    public void setPublicId(String publicId) { this.publicId = publicId; }

    public String getSystemId() { return systemId; }
    public void setSystemId(String systemId) { this.systemId = systemId; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ElementDef> getElements() { return elements; }
    public void setElements(List<ElementDef> elements) { this.elements = elements; }

    public List<AttributeDef> getAttributes() { return attributes; }
    public void setAttributes(List<AttributeDef> attributes) { this.attributes = attributes; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public void addElement(ElementDef element) {
        element.setParentId(this.id);
        elements.add(element);
    }

    public void addAttribute(AttributeDef attribute) {
        attributes.add(attribute);
    }

    public ElementDef findElementByName(String name) {
        return elements.stream()
                .filter(e -> e.getName().equals(name))
                .findFirst().orElse(null);
    }

    /** Derive module name: name lowercased. */
    public String resolvedModule() {
        return (module != null && !module.isBlank()) ? module : name.toLowerCase();
    }

    @Override
    public String toString() {
        return name + " (:" + baseType + ")";
    }
}
