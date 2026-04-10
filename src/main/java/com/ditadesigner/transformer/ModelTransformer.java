package com.ditadesigner.transformer;

import com.ditadesigner.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates and resolves a DitaModel before handing it to generators.
 */
public class ModelTransformer {

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
        if (baseElem == null || baseElem.isBlank()) baseElem = elemName;
        return "- topic/" + baseElem + " " + base + "/" + baseElem + " " + topicName + "/" + elemName + " ";
    }

    /**
     * Validates the model and collects all issues.
     * Includes: missing names, duplicate names, circular inheritance,
     * orphaned elements (F-124), missing required attr defaults (F-123).
     *
     * @return list of validation messages (empty = OK)
     */
    public List<String> validate(DitaModel model) {
        List<String> issues = new ArrayList<>();

        if (model.getName() == null || model.getName().isBlank()) {
            issues.add("Model has no name.");
        }

        // ── Duplicate + missing names ─────────────────────────────────
        Set<String> ttNames = new HashSet<>();
        for (TopicType tt : model.getTopicTypes()) {
            if (tt.getName() == null || tt.getName().isBlank()) {
                issues.add("A TopicType has no name.");
                continue;
            }
            if (!ttNames.add(tt.getName())) {
                issues.add("Duplicate TopicType name: '" + tt.getName() + "'");
            }
            if (tt.getBaseType() == null || tt.getBaseType().isBlank()) {
                issues.add("TopicType '" + tt.getName() + "' has no base type.");
            }
            for (ElementDef elem : tt.getElements()) {
                if (elem.getName() == null || elem.getName().isBlank()) {
                    issues.add("TopicType '" + tt.getName() + "' has an element with no name.");
                }
            }
            // F-123: required attributes with no default
            for (AttributeDef attr : tt.getAttributes()) {
                if (attr.isRequired() && (attr.getDefaultValue() == null || attr.getDefaultValue().isBlank())
                        && (attr.getFixedValue() == null || attr.getFixedValue().isBlank())) {
                    if (!"ID".equals(attr.getType()) && !"IDREF".equals(attr.getType())) {
                        issues.add("TopicType '" + tt.getName() + "' → attribute '" + attr.getName()
                                + "' is required but has no default value.");
                    }
                }
            }
        }

        // ── Circular inheritance detection (F-119) ────────────────────
        issues.addAll(detectCircularInheritance(model));

        // ── Domain validation ─────────────────────────────────────────
        Set<String> domainNames = new HashSet<>();
        for (DomainDef dd : model.getDomains()) {
            if (dd.getName() == null || dd.getName().isBlank()) {
                issues.add("A Domain has no name.");
            } else if (!domainNames.add(dd.getName())) {
                issues.add("Duplicate Domain name: '" + dd.getName() + "'");
            }
        }

        // ── Relationship validation ───────────────────────────────────
        for (Relationship rel : model.getRelationships()) {
            if (rel.getSourceId() == null || rel.getTargetId() == null) {
                issues.add("A Relationship has null source or target.");
            }
        }

        // ── F-124: Orphaned elements ──────────────────────────────────
        issues.addAll(detectOrphanedElements(model));

        return issues;
    }

    /**
     * F-124: Detect elements that are defined in a TopicType but not referenced
     * in any sibling element's content model AND have no structural content of
     * their own (i.e., they look like terminal placeholders).
     *
     * <p>Note: Elements that are direct children of a TopicType are ALWAYS included
     * in the parent's generated content model (see DtdGenerator.buildTopicContentModel),
     * so they are never truly orphaned. Only leaf elements with a blank or pure-PCDATA
     * content model that are also unreferenced by any sibling are flagged.</p>
     */
    public List<String> detectOrphanedElements(DitaModel model) {
        List<String> issues = new ArrayList<>();
        for (TopicType tt : model.getTopicTypes()) {
            List<ElementDef> elems = tt.getElements();
            if (elems.size() < 2) continue;

            for (ElementDef elem : elems) {
                String name = elem.getName();
                if (name == null || name.isBlank()) continue;

                // Elements with a non-trivial content model define their own structure
                // and are never considered orphaned — they contribute to the hierarchy.
                String cm = elem.getContentModel();
                if (cm != null && !cm.isBlank() && !cm.equals("(#PCDATA)*")) continue;

                // Check if this element is referenced by any sibling's content model
                boolean referencedBySibling = elems.stream()
                        .filter(e -> !e.equals(elem))
                        .anyMatch(e -> e.getContentModel() != null
                                && e.getContentModel().contains(name));

                if (!referencedBySibling) {
                    issues.add("Possible orphaned element: '" + name + "' in TopicType '"
                            + tt.getName() + "' has no content model and is not referenced "
                            + "by any sibling element.");
                }
            }
        }
        return issues;
    }

    /**
     * F-119: Detect circular inheritance chains in relationships.
     */
    private List<String> detectCircularInheritance(DitaModel model) {
        List<String> issues = new ArrayList<>();
        // Build inheritance adjacency: sourceId inherits-from targetId
        Map<String, String> parentOf = new HashMap<>();
        for (Relationship rel : model.getRelationships()) {
            if (rel.getType() == RelationshipType.INHERITANCE) {
                parentOf.put(rel.getSourceId(), rel.getTargetId());
            }
        }
        // Check each node for cycles
        for (String start : parentOf.keySet()) {
            Set<String> visited = new HashSet<>();
            String cur = start;
            while (cur != null && parentOf.containsKey(cur)) {
                if (!visited.add(cur)) {
                    TopicType tt = model.findTopicTypeById(start);
                    String name = tt != null ? tt.getName() : start;
                    issues.add("Circular inheritance detected involving '" + name + "'.");
                    break;
                }
                cur = parentOf.get(cur);
            }
        }
        return issues;
    }

    public boolean isKnownBaseType(String type) { return BASE_TYPES.contains(type); }
    public List<String> knownBaseTypes() { return BASE_TYPES; }

    public String derivePublicId(TopicType tt, String orgName) {
        if (tt.getPublicId() != null && !tt.getPublicId().isBlank()) return tt.getPublicId();
        if (orgName == null || orgName.isBlank()) orgName = "LOCAL";
        return "-//" + orgName.toUpperCase() + "//ELEMENTS DITA " + tt.getName() + "//EN";
    }

    public String deriveModSystemId(TopicType tt) { return tt.resolvedModule() + ".mod"; }
}
