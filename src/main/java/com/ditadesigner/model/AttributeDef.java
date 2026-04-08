package com.ditadesigner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeDef {

    private String id;
    private String name;
    private String type;        // CDATA, NMTOKEN, NMTOKENS, ID, IDREF, IDREFS, or enum string
    private String defaultValue;
    private boolean required;
    private String fixedValue;
    private List<String> enumValues = new ArrayList<>();

    public AttributeDef() {
        this.id = UUID.randomUUID().toString();
        this.type = "CDATA";
        this.required = false;
    }

    public AttributeDef(String name, String type) {
        this();
        this.name = name;
        this.type = type;
    }

    public AttributeDef(String name, String type, String defaultValue, boolean required) {
        this(name, type);
        this.defaultValue = defaultValue;
        this.required = required;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public String getFixedValue() { return fixedValue; }
    public void setFixedValue(String fixedValue) { this.fixedValue = fixedValue; }

    public List<String> getEnumValues() { return enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }

    public void addEnumValue(String value) {
        if (!enumValues.contains(value)) {
            enumValues.add(value);
        }
    }

    /** DTD attribute declaration fragment: name type #REQUIRED|#IMPLIED|"default" */
    public String toDtdFragment() {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(name).append(" ");
        if (!enumValues.isEmpty()) {
            sb.append("(").append(String.join("|", enumValues)).append(")");
        } else {
            sb.append(type);
        }
        sb.append(" ");
        if (fixedValue != null && !fixedValue.isEmpty()) {
            sb.append("#FIXED \"").append(fixedValue).append("\"");
        } else if (required) {
            sb.append("#REQUIRED");
        } else if (defaultValue != null && !defaultValue.isEmpty()) {
            sb.append("\"").append(defaultValue).append("\"");
        } else {
            sb.append("#IMPLIED");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " : " + type + (required ? " [R]" : "");
    }
}
