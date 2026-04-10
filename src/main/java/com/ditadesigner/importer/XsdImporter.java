package com.ditadesigner.importer;

import com.ditadesigner.model.AttributeDef;
import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/**
 * Imports a DITA specialization XSD file and creates a {@link TopicType} model from it.
 * Understands the XSD structure produced by {@link com.ditadesigner.generator.XsdGenerator}.
 */
public class XsdImporter {

    private static final Set<String> SKIP_ATTRS =
            Set.of("id", "class", "domains", "ditaarch:DITAArchVersion", "xml:lang", "translate", "outputclass");

    /**
     * Parse an XSD file and return a populated TopicType.
     * Returns null if the file cannot be parsed as a DITA specialization XSD.
     */
    public TopicType importXsd(File xsdFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity loading for safety
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = factory.newDocumentBuilder().parse(xsdFile);
        doc.getDocumentElement().normalize();

        // ── namespace from schema element ────────────────────────────
        String targetNs = doc.getDocumentElement().getAttribute("targetNamespace");

        // ── find the primary complexType (name ends with .class) ─────
        NodeList complexTypes = doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        if (complexTypes.getLength() == 0) {
            // Try without namespace (some XSDs use xs: prefix without namespace awareness)
            complexTypes = doc.getElementsByTagName("xs:complexType");
        }

        Element primaryType = null;
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element ct = (Element) complexTypes.item(i);
            String ctName = ct.getAttribute("name");
            if (ctName.endsWith(".class")) {
                primaryType = ct;
                break;
            }
        }

        // Fall back: first complexType
        if (primaryType == null && complexTypes.getLength() > 0) {
            primaryType = (Element) complexTypes.item(0);
        }
        if (primaryType == null) {
            throw new Exception("No xs:complexType found in " + xsdFile.getName());
        }

        // ── extract topic type name ──────────────────────────────────
        String rawTypeName = primaryType.getAttribute("name");
        String typeName = rawTypeName.endsWith(".class")
                ? rawTypeName.substring(0, rawTypeName.length() - 6)
                : rawTypeName;
        if (typeName.isBlank()) {
            // Derive from file name
            String fn = xsdFile.getName();
            typeName = fn.contains(".") ? fn.substring(0, fn.lastIndexOf('.')) : fn;
            typeName = capitalize(typeName);
        }

        // ── extract base type from xs:extension ──────────────────────
        String baseType = "topic";
        Element extension = findFirst(primaryType, "extension");
        if (extension != null) {
            String base = extension.getAttribute("base");
            if (!base.isBlank()) {
                baseType = base.endsWith(".class") ? base.substring(0, base.length() - 6) : base;
            }
        }

        // ── build TopicType model ────────────────────────────────────
        TopicType tt = new TopicType(typeName, baseType);
        // Module: derive from file name (stem)
        String stem = xsdFile.getName().replaceAll("\\.xsd$", "");
        tt.setModule(stem);
        if (!targetNs.isBlank()) tt.setNamespace(targetNs);

        // ── extract topic-level attributes from extension ────────────
        if (extension != null) {
            NodeList attrNodes = extension.getElementsByTagNameNS(
                    "http://www.w3.org/2001/XMLSchema", "attribute");
            if (attrNodes.getLength() == 0) {
                attrNodes = extension.getChildNodes();
            }
            collectAttributes(attrNodes, tt.getAttributes(), true);
        }

        // ── extract child elements ────────────────────────────────────
        // Global xs:element declarations (not the primary topic element)
        NodeList globalElements = doc.getElementsByTagNameNS(
                "http://www.w3.org/2001/XMLSchema", "element");
        if (globalElements.getLength() == 0) {
            globalElements = doc.getElementsByTagName("xs:element");
        }

        Set<String> seenElemNames = new LinkedHashSet<>();
        for (int i = 0; i < globalElements.getLength(); i++) {
            Element el = (Element) globalElements.item(i);
            // Only top-level xs:element children of xs:schema
            if (!el.getParentNode().equals(doc.getDocumentElement())) continue;

            String elName = el.getAttribute("name");
            if (elName.isBlank() || elName.equals(stem) || elName.equalsIgnoreCase(typeName)) continue;
            // Skip the primary element declaration (same name as module)
            if (seenElemNames.contains(elName)) continue;
            seenElemNames.add(elName);

            ElementDef elemDef = new ElementDef(elName);
            elemDef.setContentModel("(#PCDATA)*");
            elemDef.setCardinality("?");

            // Extract attributes from this element's complexType
            Element elCt = findFirst(el, "complexType");
            if (elCt != null) {
                NodeList elemAttrNodes = elCt.getElementsByTagNameNS(
                        "http://www.w3.org/2001/XMLSchema", "attribute");
                if (elemAttrNodes.getLength() == 0) {
                    elemAttrNodes = elCt.getElementsByTagName("xs:attribute");
                }
                collectAttributes(elemAttrNodes, elemDef.getAttributes(), false);
            }

            tt.addElement(elemDef);
        }

        return tt;
    }

    // ── helpers ───────────────────────────────────────────────────────

    private void collectAttributes(NodeList nodes, List<AttributeDef> target, boolean skipStandard) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (!(n instanceof Element el)) continue;
            String localName = el.getLocalName();
            if (localName == null) localName = el.getNodeName();
            if (!localName.equals("attribute") && !localName.endsWith(":attribute")) continue;

            String name = el.getAttribute("name");
            if (name.isBlank()) continue;
            if (skipStandard && SKIP_ATTRS.contains(name)) continue;

            String type = el.getAttribute("type");
            if (type.isBlank()) type = "CDATA";
            else type = xsTypeToDtd(type);

            String use = el.getAttribute("use");
            boolean required = "required".equals(use);
            String defaultVal = el.getAttribute("default");

            AttributeDef attr = new AttributeDef(name, type, defaultVal, required);
            target.add(attr);
        }
    }

    /** Find the first descendant element matching the local name (ignoring namespace prefix). */
    private Element findFirst(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element el) {
                String ln = el.getLocalName() != null ? el.getLocalName() : el.getNodeName();
                if (ln.equals(localName) || ln.endsWith(":" + localName)) return el;
                Element found = findFirst(el, localName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private String xsTypeToDtd(String xsType) {
        // Strip namespace prefix (xs:string → string)
        if (xsType.contains(":")) xsType = xsType.substring(xsType.indexOf(':') + 1);
        return switch (xsType) {
            case "ID"     -> "ID";
            case "IDREF"  -> "IDREF";
            case "IDREFS" -> "IDREFS";
            case "NMTOKEN" -> "NMTOKEN";
            case "NMTOKENS" -> "NMTOKENS";
            default       -> "CDATA";
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
