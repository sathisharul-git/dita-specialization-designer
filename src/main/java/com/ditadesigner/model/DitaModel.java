package com.ditadesigner.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DitaModel {

    private String id;
    private String name;
    private String version;
    private String description;
    private String targetNamespace;
    private String outputDir;
    private List<TopicType> topicTypes = new ArrayList<>();
    private List<DomainDef> domains = new ArrayList<>();
    private List<Relationship> relationships = new ArrayList<>();

    public DitaModel() {
        this.id = UUID.randomUUID().toString();
        this.version = "1.0";
        this.name = "Untitled";
    }

    public DitaModel(String name) {
        this();
        this.name = name;
    }

    // ── getters / setters ────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetNamespace() { return targetNamespace; }
    public void setTargetNamespace(String targetNamespace) { this.targetNamespace = targetNamespace; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public List<TopicType> getTopicTypes() { return topicTypes; }
    public void setTopicTypes(List<TopicType> topicTypes) { this.topicTypes = topicTypes; }

    public List<DomainDef> getDomains() { return domains; }
    public void setDomains(List<DomainDef> domains) { this.domains = domains; }

    public List<Relationship> getRelationships() { return relationships; }
    public void setRelationships(List<Relationship> relationships) { this.relationships = relationships; }

    // ── mutators ─────────────────────────────────────────────────────

    public void addTopicType(TopicType topicType) {
        topicTypes.add(topicType);
    }

    public void addDomain(DomainDef domain) {
        domains.add(domain);
    }

    public void addRelationship(Relationship relationship) {
        // Avoid duplicates
        boolean exists = relationships.stream()
                .anyMatch(r -> r.getSourceId().equals(relationship.getSourceId())
                        && r.getTargetId().equals(relationship.getTargetId())
                        && r.getType() == relationship.getType());
        if (!exists) {
            relationships.add(relationship);
        }
    }

    public void removeTopicType(String id) {
        topicTypes.removeIf(t -> t.getId().equals(id));
        relationships.removeIf(r -> r.getSourceId().equals(id) || r.getTargetId().equals(id));
    }

    public void removeDomain(String id) {
        domains.removeIf(d -> d.getId().equals(id));
        relationships.removeIf(r -> r.getSourceId().equals(id) || r.getTargetId().equals(id));
    }

    public void removeRelationship(String id) {
        relationships.removeIf(r -> r.getId().equals(id));
    }

    // ── lookup helpers ────────────────────────────────────────────────

    public TopicType findTopicTypeById(String id) {
        return topicTypes.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst().orElse(null);
    }

    public DomainDef findDomainById(String id) {
        return domains.stream()
                .filter(d -> d.getId().equals(id))
                .findFirst().orElse(null);
    }

    public TopicType findTopicTypeByName(String name) {
        return topicTypes.stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    @Override
    public String toString() {
        return name + " v" + version;
    }
}
