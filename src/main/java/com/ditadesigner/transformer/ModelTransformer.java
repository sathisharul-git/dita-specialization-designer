package com.ditadesigner.transformer;

import com.ditadesigner.model.*;

import java.util.*;

/**
 * Validates and resolves a DitaModel before handing it to generators.
 * Returns a processed copy with all IDs resolved and inheritance chains built.
 */
public class ModelTransformer {

    /** Known DITA 1.3 base topic types in inheritance order. */
    private static final List<String> BASE_TYPES =
            List.of("topic", "concept", "task", "reference", "map", "bookmap", "glossentry");

    /** Standard DITA class attribute chain for a specialization. */
    public String buildClassAttribute(TopicType topicType) {
        String base = topicType.getBaseType();
        String name = topicType.getName();
        return "- topic/topic " + base + "/" + base + " " + name + "/" + name + " ";
    }

    /** Build class attribute for a specialized element. */
    public String buildElementClassAttribute(TopicType parent, ElementDef elem, String baseElem) {
        String base = parent.getBaseType();
        String topicName = parent.getName();
        String elemName = elem.getName();
        if (baseElem == null || baseElem.isBlank()) {
            baseElem = elemName;
        }
        return "- topic/" + baseElem + " " + base + "/" + baseElem + " " + topicName + "/" + elemName + " ";
    }

    /**
     * Validates the model and collects any issues.
     *
     * @return list of validation messages (empty = OK)
     */
    public List<String> validate(DitaModel model) {
        List<String> issues = new ArrayList<>();

        if (model.getName() == null || model.getName().isBlank()) {
            issues.add("Model has no name.");
        }

        Set<String> names = new HashSet<>();
        for (TopicType tt : model.getTopicTypes()) {
            if (tt.getName() == null || tt.getName().isBlank()) {
                issues.add("A TopicType has no name.");
                continue;
            }
            if (!names.add(tt.getName())) {
                issues.add("Duplicate TopicType name: " + tt.getName());
            }
            if (tt.getBaseType() == null || tt.getBaseType().isBlank()) {
                issues.add("TopicType '" + tt.getName() + "' has no base type.");
            }
            for (ElementDef elem : tt.getElements()) {
                if (elem.getName() == null || elem.getName().isBlank()) {
                    issues.add("TopicType '" + tt.getName() + "' has an element with no name.");
                }
            }
        }

        for (DomainDef dd : model.getDomains()) {
            if (dd.getName() == null || dd.getName().isBlank()) {
                issues.add("A Domain has no name.");
            }
        }

        for (Relationship rel : model.getRelationships()) {
            if (rel.getSourceId() == null || rel.getTargetId() == null) {
                issues.add("A Relationship has null source or target.");
            }
        }

        return issues;
    }

    /** Returns true if the given string is a recognised DITA base type. */
    public boolean isKnownBaseType(String type) {
        return BASE_TYPES.contains(type);
    }

    public List<String> knownBaseTypes() {
        return BASE_TYPES;
    }

    /**
     * Derives a public ID for a topic type if not already set.
     * Pattern: -//ORGNAME//ELEMENTS DITA {name}//EN
     */
    public String derivePublicId(TopicType tt, String orgName) {
        if (tt.getPublicId() != null && !tt.getPublicId().isBlank()) {
            return tt.getPublicId();
        }
        if (orgName == null || orgName.isBlank()) orgName = "LOCAL";
        return "-//" + orgName.toUpperCase() + "//ELEMENTS DITA " + tt.getName() + "//EN";
    }

    /**
     * Derives a system ID (file name) for the mod file.
     */
    public String deriveModSystemId(TopicType tt) {
        return tt.resolvedModule() + ".mod";
    }
}
