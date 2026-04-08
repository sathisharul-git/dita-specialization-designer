package com.ditadesigner.model;

public enum RelationshipType {
    INHERITANCE("inherits from"),
    CONTAINMENT("contains"),
    DOMAIN_INCLUSION("includes domain"),
    CONSTRAINT("constrained by");

    private final String label;

    RelationshipType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
