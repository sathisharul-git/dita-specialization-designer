package com.ditadesigner.parser;

import com.ditadesigner.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

/**
 * Parses W3C XSD files and extracts complex types and element declarations
 * for population of the OASIS library browser.
 */
public class XsdParser {

    private static final String XS_NS = "http://www.w3.org/2001/XMLSchema";

    public List<TopicType> parseFile(File xsdFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Disable external entity resolution for security
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xsdFile);
        doc.getDocumentElement().normalize();

        List<TopicType> results = new ArrayList<>();

        // Extract xs:complexType with xs:extension (= specializations)
        NodeList complexTypes = doc.getElementsByTagNameNS(XS_NS, "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element ct = (Element) complexTypes.item(i);
            String name = ct.getAttribute("name");
            if (name.isEmpty()) continue;

            TopicType tt = new TopicType();
            tt.setName(name.replace(".class", ""));

            // Find xs:extension to determine base type
            NodeList extensions = ct.getElementsByTagNameNS(XS_NS, "extension");
            if (extensions.getLength() > 0) {
                Element ext = (Element) extensions.item(0);
                String base = ext.getAttribute("base");
                tt.setBaseType(base.replace(".class", ""));

                // Extract child elements from sequence
                NodeList seqElems = ext.getElementsByTagNameNS(XS_NS, "element");
                for (int j = 0; j < seqElems.getLength(); j++) {
                    Element el = (Element) seqElems.item(j);
                    String ref = el.getAttribute("ref");
                    String elName = el.getAttribute("name");
                    String elemName = !ref.isEmpty() ? ref : elName;
                    if (elemName.isEmpty()) continue;

                    ElementDef ed = new ElementDef(elemName);
                    String minOccurs = el.getAttribute("minOccurs");
                    String maxOccurs = el.getAttribute("maxOccurs");
                    String cardinality = deriveCardinality(minOccurs, maxOccurs);
                    ed.setCardinality(cardinality);
                    ed.setRequired("1".equals(minOccurs));
                    tt.addElement(ed);
                }

                // Extract attributes
                NodeList attrNodes = ext.getElementsByTagNameNS(XS_NS, "attribute");
                for (int j = 0; j < attrNodes.getLength(); j++) {
                    Element atEl = (Element) attrNodes.item(j);
                    String atName = atEl.getAttribute("name");
                    if (atName.isEmpty() || "class".equals(atName)) continue;
                    String atType = atEl.getAttribute("type");
                    String use = atEl.getAttribute("use");
                    AttributeDef ad = new AttributeDef(atName, xsTypeToDtd(atType));
                    ad.setRequired("required".equals(use));
                    tt.addAttribute(ad);
                }
            }

            results.add(tt);
        }

        return results;
    }

    private String deriveCardinality(String min, String max) {
        boolean zero = min.isEmpty() || "0".equals(min);
        boolean many = "unbounded".equals(max);
        if (zero && !many) return "?";
        if (!zero && many) return "+";
        if (zero && many) return "*";
        return "1";
    }

    private String xsTypeToDtd(String xsType) {
        return switch (xsType) {
            case "xs:ID" -> "ID";
            case "xs:IDREF" -> "IDREF";
            case "xs:IDREFS" -> "IDREFS";
            case "xs:NMTOKEN" -> "NMTOKEN";
            case "xs:NMTOKENS" -> "NMTOKENS";
            default -> "CDATA";
        };
    }

    /**
     * Scans a directory for .xsd files and collects all complex type names.
     */
    public Set<String> collectTypeNames(File dir) {
        Set<String> names = new LinkedHashSet<>();
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".xsd"));
        if (files == null) return names;
        for (File f : files) {
            try {
                List<TopicType> types = parseFile(f);
                for (TopicType t : types) names.add(t.getName());
            } catch (Exception ignored) {
            }
        }
        return names;
    }
}
