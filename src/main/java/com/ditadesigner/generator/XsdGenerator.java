package com.ditadesigner.generator;

import com.ditadesigner.model.*;
import com.ditadesigner.transformer.ModelTransformer;
import com.ditadesigner.util.FileUtil;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates W3C XML Schema (.xsd) artefacts for DITA specializations.
 *
 * <p>Two export modes are supported (see {@link ExportMode}):
 * <ul>
 *   <li><b>STANDALONE</b> — embeds minimal DITA base-type stubs inline so the
 *       schema validates without any external catalog or DITA-OT installation.</li>
 *   <li><b>OASIS_CATALOG</b> — uses standard OASIS DITA 1.3 URN {@code xs:import}s
 *       so the schema works with an existing DITA-OT / OASIS catalog registration.
 *       No inline stubs; no {@code targetNamespace}.</li>
 * </ul>
 */
public class XsdGenerator {

    // ── Export mode ────────────────────────────────────────────────────────────

    /**
     * Controls how the generated XSD references DITA base types.
     */
    public enum ExportMode {
        /**
         * Embed minimal DITA base-type stubs inline.
         * The XSD validates standalone — no DITA-OT or XML catalog required.
         */
        STANDALONE,

        /**
         * Use standard OASIS DITA 1.3 {@code xs:import} URNs.
         * Requires OASIS DITA 1.3 schemas registered in the system XML catalog.
         * No inline stubs; no {@code targetNamespace} (matches DITA-OT convention).
         */
        OASIS_CATALOG
    }

    private static final String NL = System.lineSeparator();
    private static final String XS = "http://www.w3.org/2001/XMLSchema";
    private final ModelTransformer transformer = new ModelTransformer();

    /**
     * DITA specialization inheritance chain (STANDALONE mode stub generation).
     * {@code null} value = root type.  Unknown types get a single standalone stub.
     */
    private static final Map<String, String> DITA_PARENT = new LinkedHashMap<>();

    /**
     * OASIS DITA 1.3 standard URNs for the base-type shell XSDs.
     * Used in OASIS_CATALOG mode {@code xs:import} declarations.
     */
    private static final Map<String, String> OASIS_SCHEMA_URN = new LinkedHashMap<>();

    static {
        DITA_PARENT.put("topic",               null);
        DITA_PARENT.put("concept",             "topic");
        DITA_PARENT.put("task",                "topic");
        DITA_PARENT.put("reference",           "topic");
        DITA_PARENT.put("troubleshooting",     "task");
        DITA_PARENT.put("map",                 null);
        DITA_PARENT.put("bookmap",             "map");
        DITA_PARENT.put("glossentry",          "concept");
        DITA_PARENT.put("glossgroup",          "topic");
        DITA_PARENT.put("learningBase",        "topic");
        DITA_PARENT.put("learningContent",     "learningBase");
        DITA_PARENT.put("learningPlan",        "topic");
        DITA_PARENT.put("learningAssessment",  "learningBase");
        DITA_PARENT.put("learningSummary",     "learningBase");
        DITA_PARENT.put("learningOverview",    "learningBase");

        OASIS_SCHEMA_URN.put("topic",            "urn:oasis:names:tc:dita:xsd:topic.xsd:1.3");
        OASIS_SCHEMA_URN.put("concept",          "urn:oasis:names:tc:dita:xsd:concept.xsd:1.3");
        OASIS_SCHEMA_URN.put("task",             "urn:oasis:names:tc:dita:xsd:task.xsd:1.3");
        OASIS_SCHEMA_URN.put("reference",        "urn:oasis:names:tc:dita:xsd:reference.xsd:1.3");
        OASIS_SCHEMA_URN.put("troubleshooting",  "urn:oasis:names:tc:dita:xsd:troubleshooting.xsd:1.3");
        OASIS_SCHEMA_URN.put("map",              "urn:oasis:names:tc:dita:xsd:map.xsd:1.3");
        OASIS_SCHEMA_URN.put("bookmap",          "urn:oasis:names:tc:dita:xsd:bookmap.xsd:1.3");
        OASIS_SCHEMA_URN.put("glossentry",       "urn:oasis:names:tc:dita:xsd:glossentry.xsd:1.3");
        OASIS_SCHEMA_URN.put("glossgroup",       "urn:oasis:names:tc:dita:xsd:glossgroup.xsd:1.3");
        OASIS_SCHEMA_URN.put("learningBase",     "urn:oasis:names:tc:dita:xsd:learningBase.xsd:1.3");
        OASIS_SCHEMA_URN.put("learningContent",  "urn:oasis:names:tc:dita:xsd:learningContent.xsd:1.3");
        OASIS_SCHEMA_URN.put("learningPlan",     "urn:oasis:names:tc:dita:xsd:learningPlan.xsd:1.3");
        OASIS_SCHEMA_URN.put("learningAssessment","urn:oasis:names:tc:dita:xsd:learningAssessment.xsd:1.3");
        OASIS_SCHEMA_URN.put("learningSummary",  "urn:oasis:names:tc:dita:xsd:learningSummary.xsd:1.3");
        OASIS_SCHEMA_URN.put("learningOverview", "urn:oasis:names:tc:dita:xsd:learningOverview.xsd:1.3");
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Generate XSD artefacts for all topic types using {@link ExportMode#STANDALONE}.
     * Kept for backward compatibility (Live Sync, existing callers).
     */
    public void generate(DitaModel model, File outputDir) throws IOException {
        generate(model, outputDir, ExportMode.STANDALONE);
    }

    /**
     * Generate XSD artefacts for all topic types in the specified mode.
     *
     * @param model     the DITA specialization model
     * @param outputDir root output directory (an {@code xsd/} subdirectory is created)
     * @param mode      {@link ExportMode#STANDALONE} or {@link ExportMode#OASIS_CATALOG}
     */
    public void generate(DitaModel model, File outputDir, ExportMode mode) throws IOException {
        File xsdDir = new File(outputDir, "xsd");
        FileUtil.ensureDir(xsdDir);
        for (TopicType tt : model.getTopicTypes()) {
            generateForTopicType(tt, model, xsdDir, mode);
        }
    }

    /**
     * F-107: Returns the XSD content for a single TopicType without writing files.
     * Always uses {@link ExportMode#STANDALONE} (suitable for live preview).
     */
    public String previewXsd(TopicType tt, DitaModel model) {
        return buildCopyrightComment(model) + buildXsd(tt, model, ExportMode.STANDALONE);
    }

    private void generateForTopicType(TopicType tt, DitaModel model,
                                       File xsdDir, ExportMode mode) throws IOException {
        String stem    = tt.resolvedModule();
        String content = buildCopyrightComment(model) + buildXsd(tt, model, mode);
        FileUtil.writeString(new File(xsdDir, stem + ".xsd"), content);
    }

    private String buildCopyrightComment(DitaModel model) {
        String owner = model.getCopyrightOwner();
        if (owner == null || owner.isBlank()) return "";
        int year = java.time.LocalDate.now().getYear();
        return "<!--" + NL
             + "  Copyright (c) " + year + " " + owner + NL
             + "  Project: " + model.getName() + " v" + (model.getVersion() != null ? model.getVersion() : "1.0") + NL
             + "  Generated by DITA Specialization Designer." + NL
             + "-->" + NL;
    }

    private String buildXsd(TopicType tt, DitaModel model, ExportMode mode) {
        StringBuilder sb = new StringBuilder();
        String stem      = tt.resolvedModule();
        String base      = tt.getBaseType();
        String ns        = tt.getNamespace() != null ? tt.getNamespace() : "urn:ditadesigner:" + stem;
        String classAttr = transformer.buildClassAttribute(tt);

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append(NL);
        sb.append("<!--").append(NL);
        sb.append("  ").append(tt.getName()).append(" XML Schema").append(NL);
        sb.append("  Specializes from: ").append(base).append(NL);
        sb.append("  Export mode: ").append(mode).append(NL);
        sb.append("  Generated by DITA Specialization Designer").append(NL);
        if (mode == ExportMode.OASIS_CATALOG) {
            sb.append("  Requires: OASIS DITA 1.3 schemas registered in your system XML catalog.").append(NL);
        }
        sb.append("-->").append(NL);

        // ── Schema declaration ────────────────────────────────────────────────
        sb.append("<xs:schema").append(NL);
        sb.append("    xmlns:xs=\"").append(XS).append("\"").append(NL);
        sb.append("    xmlns:ditaarch=\"http://dita.oasis-open.org/architecture/2005/\"").append(NL);
        if (mode == ExportMode.STANDALONE) {
            // STANDALONE: own targetNamespace — self-contained validation
            sb.append("    targetNamespace=\"").append(ns).append("\"").append(NL);
            sb.append("    xmlns=\"").append(ns).append("\"").append(NL);
        }
        // OASIS_CATALOG: no targetNamespace — matches DITA-OT chameleon convention
        // so unqualified type references (e.g. task.class) resolve correctly to
        // the no-namespace types imported from the OASIS schemas.
        sb.append("    elementFormDefault=\"qualified\"").append(NL);
        sb.append("    attributeFormDefault=\"unqualified\">").append(NL).append(NL);

        // ── Base-type imports / stubs ─────────────────────────────────────────
        if (mode == ExportMode.STANDALONE) {
            sb.append(buildBaseTypeStubs(base));
        } else {
            sb.append(buildOasisImports(base));
        }

        // ── Identify top-level vs nested elements ─────────────────────
        // F-160 fix: only include elements in xs:sequence that are not referenced
        // inside another element's content model (those are nested elements).
        Set<String> referencedByOthers = buildReferencedByOthers(tt.getElements());
        List<ElementDef> topLevelElements = tt.getElements().stream()
                .filter(e -> e.getName() != null && !referencedByOthers.contains(e.getName()))
                .collect(Collectors.toList());

        // ── Separate regular vs attribute-domain attrs ────────────────
        // F-158 fix: exclude 'id' from regularAttrs since it's emitted separately below.
        List<AttributeDef> regularAttrs = tt.getAttributes().stream()
                .filter(a -> !a.isAttributeDomain() && !"id".equalsIgnoreCase(a.getName()))
                .collect(Collectors.toList());
        List<AttributeDef> domainAttrs  = tt.getAttributes().stream()
                .filter(AttributeDef::isAttributeDomain).collect(Collectors.toList());

        // ── complex type via extension ────────────────────────────────
        sb.append("  <!-- ").append(tt.getName()).append(" complex type -->").append(NL);
        sb.append("  <xs:complexType name=\"").append(tt.getName()).append(".class\">").append(NL);
        sb.append("    <xs:complexContent>").append(NL);
        sb.append("      <xs:extension base=\"").append(base).append(".class\">").append(NL);

        if (!topLevelElements.isEmpty()) {
            sb.append("        <xs:sequence>").append(NL);
            for (ElementDef elem : topLevelElements) {
                sb.append(elementRef(elem));
            }
            sb.append("        </xs:sequence>").append(NL);
        }

        // id attribute (always required for DITA topics)
        sb.append("        <xs:attribute name=\"id\" type=\"xs:ID\" use=\"required\"/>").append(NL);
        sb.append("        <xs:attribute name=\"class\" type=\"xs:string\"").append(NL);
        sb.append("            default=\"").append(classAttr).append("\"/>").append(NL);

        // Build domains value
        StringBuilder domainsVal = new StringBuilder("(").append(stem).append(")");
        for (AttributeDef a : domainAttrs) {
            domainsVal.append(" a(").append(a.getSpecializesFrom()).append(" ").append(a.getName()).append(")");
        }
        sb.append("        <xs:attribute name=\"domains\" type=\"xs:string\"").append(NL);
        sb.append("            default=\"").append(domainsVal).append("\"/>").append(NL);

        for (AttributeDef a : domainAttrs) {
            sb.append("        <xs:attributeGroup ref=\"").append(a.getName()).append("-d-attribute\"/>").append(NL);
        }
        for (AttributeDef attr : regularAttrs) {
            sb.append(attributeDecl(attr));
        }
        // Allow xml:lang, ditaarch:*, and any other prefixed DITA attributes
        sb.append("        <xs:anyAttribute namespace=\"##other\" processContents=\"lax\"/>").append(NL);
        sb.append("      </xs:extension>").append(NL);
        sb.append("    </xs:complexContent>").append(NL);
        sb.append("  </xs:complexType>").append(NL).append(NL);

        // ── element declaration ───────────────────────────────────────
        sb.append("  <!-- ").append(tt.getName()).append(" element declaration -->").append(NL);
        sb.append("  <xs:element name=\"").append(stem).append("\"").append(NL);
        sb.append("              type=\"").append(tt.getName()).append(".class\"/>").append(NL).append(NL);

        // ── attribute domain attributeGroup declarations ─────────────
        Map<String, AttributeDef> allDomainAttrs = new LinkedHashMap<>();
        for (AttributeDef a : tt.getAttributes()) {
            if (a.isAttributeDomain()) allDomainAttrs.put(a.getName(), a);
        }
        for (ElementDef elem : tt.getElements()) {
            for (AttributeDef a : elem.getAttributes()) {
                if (a.isAttributeDomain()) allDomainAttrs.putIfAbsent(a.getName(), a);
            }
        }
        for (AttributeDef a : allDomainAttrs.values()) {
            sb.append("  <!--").append(NL);
            sb.append("    Attribute domain: @").append(a.getName()).append(NL);
            sb.append("    Specializes from: @").append(a.getSpecializesFrom()).append(NL);
            sb.append("  -->").append(NL);
            sb.append("  <xs:attributeGroup name=\"").append(a.getName()).append("-d-attribute\">").append(NL);
            sb.append("    <xs:annotation><xs:documentation>").append(NL);
            sb.append("      Attribute domain specializing from @").append(a.getSpecializesFrom()).append(".").append(NL);
            sb.append("    </xs:documentation></xs:annotation>").append(NL);
            String xsType = mapDtdTypeToXsd(a.getType());
            if (a.getEnumValues() != null && !a.getEnumValues().isEmpty()) {
                sb.append("    <xs:attribute name=\"").append(a.getName()).append("\">").append(NL);
                sb.append("      <xs:simpleType><xs:restriction base=\"xs:string\">").append(NL);
                for (String val : a.getEnumValues()) {
                    sb.append("        <xs:enumeration value=\"").append(val).append("\"/>").append(NL);
                }
                sb.append("      </xs:restriction></xs:simpleType>").append(NL);
                sb.append("    </xs:attribute>").append(NL);
            } else {
                sb.append("    <xs:attribute name=\"").append(a.getName()).append("\"");
                sb.append(" type=\"").append(xsType).append("\"");
                if (a.isRequired()) sb.append(" use=\"required\"");
                else if (a.getDefaultValue() != null && !a.getDefaultValue().isBlank())
                    sb.append(" default=\"").append(a.getDefaultValue()).append("\"");
                sb.append("/>").append(NL);
            }
            sb.append("  </xs:attributeGroup>").append(NL).append(NL);
        }

        // ── child element complex types ───────────────────────────────
        // F-162 fix: track declared element names to avoid duplicate global declarations.
        Set<String> declaredElemNames = new LinkedHashSet<>();
        for (ElementDef elem : tt.getElements()) {
            if (!declaredElemNames.add(elem.getName())) {
                continue; // skip duplicate
            }
            sb.append("  <!-- ").append(elem.getName()).append(" element -->").append(NL);
            sb.append("  <xs:element name=\"").append(elem.getName()).append("\">").append(NL);
            sb.append("    <xs:complexType mixed=\"true\">").append(NL);
            sb.append("      <xs:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">").append(NL);
            sb.append("        <xs:any processContents=\"lax\" minOccurs=\"0\"/>").append(NL);
            sb.append("      </xs:sequence>").append(NL);
            for (AttributeDef a : elem.getAttributes()) {
                if (a.isAttributeDomain()) {
                    sb.append("      <xs:attributeGroup ref=\"").append(a.getName()).append("-d-attribute\"/>").append(NL);
                } else {
                    sb.append(attributeDecl(a));
                }
            }
            String elemClass = transformer.buildElementClassAttribute(tt, elem, null);
            sb.append("      <xs:attribute name=\"class\" type=\"xs:string\" default=\"")
              .append(elemClass).append("\"/>").append(NL);
            sb.append("    </xs:complexType>").append(NL);
            sb.append("  </xs:element>").append(NL).append(NL);
        }

        sb.append("</xs:schema>").append(NL);
        return sb.toString();
    }

    /**
     * F-160 fix: build the set of element names that are referenced inside
     * any sibling element's content model string (i.e., they are "nested"
     * elements that should NOT appear in the parent topic's xs:sequence).
     */
    private Set<String> buildReferencedByOthers(List<ElementDef> elems) {
        Set<String> referenced = new HashSet<>();
        for (ElementDef elem : elems) {
            String cm = elem.getContentModel();
            if (cm == null || cm.isBlank()) continue;
            for (ElementDef other : elems) {
                if (!other.equals(elem) && other.getName() != null
                        && cm.contains(other.getName())) {
                    referenced.add(other.getName());
                }
            }
        }
        return referenced;
    }

    private String elementRef(ElementDef elem) {
        String min = elem.isRequired() ? "1" : "0";
        String max = switch (elem.getCardinality()) {
            case "+", "*" -> "unbounded";
            default -> "1";
        };
        return "          <xs:element ref=\"" + elem.getName()
                + "\" minOccurs=\"" + min + "\" maxOccurs=\"" + max + "\"/>" + NL;
    }

    private String attributeDecl(AttributeDef attr) {
        StringBuilder sb = new StringBuilder();
        String type = attr.getType() != null ? attr.getType() : "CDATA";
        sb.append("        <xs:attribute name=\"").append(attr.getName()).append("\"");

        // F-161 fix: detect DTD-style inline enum syntax (val1|val2|...) in the type field
        List<String> enumVals = attr.getEnumValues();
        if (enumVals == null || enumVals.isEmpty()) {
            enumVals = parseDtdEnumType(type);
        }

        if (!enumVals.isEmpty()) {
            sb.append(">").append(NL);
            sb.append("          <xs:simpleType><xs:restriction base=\"xs:string\">").append(NL);
            for (String val : enumVals) {
                sb.append("            <xs:enumeration value=\"").append(val).append("\"/>").append(NL);
            }
            sb.append("          </xs:restriction></xs:simpleType>").append(NL);
            sb.append("        </xs:attribute>").append(NL);
        } else {
            String xsType = mapDtdTypeToXsd(type);
            sb.append(" type=\"").append(xsType).append("\"");
            if (attr.isRequired()) sb.append(" use=\"required\"");
            else if (attr.getDefaultValue() != null && !attr.getDefaultValue().isBlank()) {
                sb.append(" default=\"").append(attr.getDefaultValue()).append("\"");
            }
            sb.append("/>").append(NL);
        }
        return sb.toString();
    }

    /** Map DTD attribute types to XSD built-in types. */
    private String mapDtdTypeToXsd(String dtdType) {
        if (dtdType == null) return "xs:string";
        return switch (dtdType) {
            case "ID"       -> "xs:ID";
            case "IDREF"    -> "xs:IDREF";
            case "IDREFS"   -> "xs:IDREFS";
            case "NMTOKEN"  -> "xs:NMTOKEN";
            case "NMTOKENS" -> "xs:NMTOKENS";
            default         -> "xs:string";
        };
    }

    /**
     * F-161 fix: parse DTD inline enum syntax "(val1|val2|val3)" from the type
     * field and return the list of values. Returns empty list if not an enum type.
     */
    private List<String> parseDtdEnumType(String type) {
        if (type == null || !type.startsWith("(") || !type.endsWith(")") || !type.contains("|")) {
            return List.of();
        }
        String inner = type.substring(1, type.length() - 1);
        return Arrays.stream(inner.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ── OASIS catalog imports ──────────────────────────────────────────────────

    /**
     * Builds the {@code xs:import} block for OASIS_CATALOG mode.
     *
     * <p>Always imports the DITA architecture namespace schema (provides
     * {@code ditaarch:DITAArchVersion}).  Then imports the base-type shell XSD
     * using the standard OASIS DITA 1.3 URN.  Both are resolved by the system
     * XML catalog — no local copy needed.
     *
     * <p>If the base type is not in {@link #OASIS_SCHEMA_URN}, a comment
     * placeholder is emitted so the user can supply the correct URN manually.
     */
    private String buildOasisImports(String baseType) {
        // Walk up the chain so we import the direct base type's shell (which
        // already includes its own ancestors via xs:include).
        String urn = OASIS_SCHEMA_URN.get(baseType);

        StringBuilder sb = new StringBuilder();
        sb.append("  <!-- ─────────────────────────────────────────────────────────────── -->").append(NL);
        sb.append("  <!-- OASIS DITA 1.3 imports — resolved by system XML catalog          -->").append(NL);
        sb.append("  <!-- ─────────────────────────────────────────────────────────────── -->").append(NL).append(NL);

        // Architecture namespace (always required for DITAArchVersion attribute)
        sb.append("  <xs:import").append(NL);
        sb.append("      namespace=\"http://dita.oasis-open.org/architecture/2005/\"").append(NL);
        sb.append("      schemaLocation=\"urn:oasis:names:tc:dita:xsd:ditaarch.xsd:1.3\"/>").append(NL).append(NL);

        // Base type shell schema
        if (urn != null) {
            sb.append("  <!-- Base DITA type schema: resolved via XML catalog -->").append(NL);
            sb.append("  <xs:import schemaLocation=\"").append(urn).append("\"/>").append(NL).append(NL);
        } else {
            sb.append("  <!-- TODO: add xs:import for base type '").append(baseType)
              .append("' — no standard OASIS URN known for this type. -->").append(NL)
              .append("  <!-- <xs:import schemaLocation=\"urn:your:catalog:entry:").append(baseType)
              .append(".xsd\"/> -->").append(NL).append(NL);
        }

        return sb.toString();
    }

    // ── Self-contained base-type stubs ─────────────────────────────────────────

    /**
     * Builds inline stub {@code xs:complexType} definitions for the full DITA
     * inheritance chain leading to {@code baseType}.
     *
     * <p>Example for {@code baseType = "task"}: emits {@code topic.class} (root)
     * then {@code task.class} (extends topic.class).  This makes the generated
     * XSD validate standalone — no DITA-OT or external {@code xs:import} needed.
     *
     * <p>Root stubs use an empty {@code <xs:sequence/>}; derived stubs use
     * {@code xs:extension} from their parent stub.  Both use abstract="true"
     * to signal they are structural base types, not instantiated directly.
     */
    private String buildBaseTypeStubs(String baseType) {
        List<String> chain = buildInheritanceChain(baseType);

        StringBuilder sb = new StringBuilder();
        sb.append("  <!-- ─────────────────────────────────────────────────────────────── -->").append(NL);
        sb.append("  <!-- Inline stubs for DITA base types — enables standalone validation -->").append(NL);
        sb.append("  <!-- No DITA-OT installation required to validate this schema.        -->").append(NL);
        sb.append("  <!-- ─────────────────────────────────────────────────────────────── -->").append(NL).append(NL);

        for (int i = 0; i < chain.size(); i++) {
            String typeName = chain.get(i);
            sb.append("  <xs:complexType name=\"").append(typeName).append(".class\" abstract=\"true\">").append(NL);
            sb.append("    <xs:annotation><xs:documentation>DITA ")
              .append(typeName).append(" base type stub (auto-generated).</xs:documentation></xs:annotation>").append(NL);
            if (i == 0) {
                // Root type: empty sequence, no parent.
                // xs:anyAttribute allows xml:lang, ditaarch:*, etc. at the base level.
                sb.append("    <xs:sequence/>").append(NL);
            } else {
                // Derived type: extends parent stub, adds nothing (specialization adds content).
                sb.append("    <xs:complexContent>").append(NL);
                sb.append("      <xs:extension base=\"").append(chain.get(i - 1)).append(".class\"/>").append(NL);
                sb.append("    </xs:complexContent>").append(NL);
            }
            sb.append("  </xs:complexType>").append(NL).append(NL);
        }

        return sb.toString();
    }

    /**
     * Builds the ordered inheritance chain from the DITA root type down to
     * {@code baseType}, e.g. {@code "task"} → {@code ["topic", "task"]}.
     *
     * <p>If {@code baseType} is not in {@link #DITA_PARENT} (custom or unknown),
     * a single-element list {@code [baseType]} is returned so a standalone root
     * stub is emitted.
     */
    private List<String> buildInheritanceChain(String baseType) {
        List<String> chain = new ArrayList<>();
        String current = baseType;
        Set<String> visited = new LinkedHashSet<>(); // cycle guard
        while (current != null && visited.add(current)) {
            chain.add(0, current); // prepend → ancestor-first order
            current = DITA_PARENT.containsKey(current) ? DITA_PARENT.get(current) : null;
        }
        return chain;
    }
}
