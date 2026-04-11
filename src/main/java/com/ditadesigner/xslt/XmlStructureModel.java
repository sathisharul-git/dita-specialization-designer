package com.ditadesigner.xslt;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Immutable model of an XML document's element paths and attribute names.
 *
 * <p>Built once via {@link #build(String)} from XML text and cached for the lifetime
 * of the loaded document. Provides context-aware suggestion lists for
 * {@code select}, {@code match}, and {@code test} attributes in the XSLT editor.
 */
public final class XmlStructureModel {

    private final List<String> selectSuggestions;
    private final List<String> matchSuggestions;
    private final List<String> testSuggestions;

    private XmlStructureModel(List<String> select, List<String> match, List<String> test) {
        this.selectSuggestions = List.copyOf(select);
        this.matchSuggestions  = List.copyOf(match);
        this.testSuggestions   = List.copyOf(test);
    }

    /**
     * Parse {@code xmlText} with JAXP and build the structure model.
     * Throws if the XML is so malformed that JAXP cannot even start a traversal.
     */
    static XmlStructureModel build(String xmlText) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // Suppress external DTD fetch — we only care about structure
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);

        Document doc = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xmlText)));

        // Sets preserve insertion order and deduplicate automatically
        Set<String> absolutePaths = new LinkedHashSet<>();
        Set<String> elementNames  = new LinkedHashSet<>();
        Set<String> attrNames     = new LinkedHashSet<>();

        walkElement(doc.getDocumentElement(), "", absolutePaths, elementNames, attrNames);

        // ── select="" suggestions ────────────────────────────────────────────
        List<String> select = new ArrayList<>();
        select.addAll(absolutePaths);
        elementNames.forEach(n -> select.add("//" + n));
        attrNames.forEach(a -> select.add("@" + a));
        select.addAll(XPATH_FUNCTIONS);
        select.addAll(XPATH_AXES);

        // ── match="" suggestions ─────────────────────────────────────────────
        List<String> match = new ArrayList<>();
        match.addAll(elementNames);
        elementNames.forEach(n -> match.add("//" + n));
        match.addAll(MATCH_WILDCARDS);

        // ── test="" suggestions ──────────────────────────────────────────────
        List<String> test = new ArrayList<>();
        attrNames.forEach(a -> test.add("@" + a));
        test.addAll(XPATH_BOOLEAN_FUNCTIONS);
        test.addAll(select);   // boolean tests can include value paths

        return new XmlStructureModel(select, match, test);
    }

    private static void walkElement(Element el, String path,
            Set<String> absPaths, Set<String> names, Set<String> attrs) {
        String localName = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
        String absPath   = path + "/" + localName;
        absPaths.add(absPath);
        names.add(localName);

        NamedNodeMap attrMap = el.getAttributes();
        for (int i = 0; i < attrMap.getLength(); i++) {
            Attr a     = (Attr) attrMap.item(i);
            String name = a.getLocalName() != null ? a.getLocalName() : a.getName();
            if (!name.startsWith("xmlns")) attrs.add(name);
        }

        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                walkElement((Element) child, absPath, absPaths, names, attrs);
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Return suggestions for the given attribute context, filtered by the user's typed prefix.
     *
     * @param context attribute type (SELECT, MATCH, TEST)
     * @param prefix  text typed so far inside the attribute value (may be blank)
     * @return filtered list, capped at 30 entries
     */
    List<String> getSuggestions(AttributeContext context, String prefix) {
        List<String> source = switch (context) {
            case SELECT -> selectSuggestions;
            case MATCH  -> matchSuggestions;
            case TEST   -> testSuggestions;
            default     -> selectSuggestions;
        };
        if (prefix.isBlank()) {
            return source.size() > 20 ? source.subList(0, 20) : source;
        }
        String lp = prefix.toLowerCase();
        return source.stream()
                .filter(s -> s.toLowerCase().contains(lp))
                .limit(30)
                .toList();
    }

    // ── Attribute context ──────────────────────────────────────────────────────

    /** Which XSLT attribute the caret is currently inside. */
    enum AttributeContext { SELECT, MATCH, TEST, NONE }

    // ── Built-in suggestion banks ──────────────────────────────────────────────

    private static final List<String> XPATH_FUNCTIONS = List.of(
            "count()", "string()", "string-length()", "substring()", "contains()",
            "starts-with()", "ends-with()", "normalize-space()", "translate()",
            "concat()", "position()", "last()", "name()", "local-name()",
            "not()", "true()", "false()", "boolean()", "number()", "sum()",
            "tokenize()", "string-join()", "upper-case()", "lower-case()",
            "matches()", "replace()", "exists()", "empty()", "distinct-values()",
            "text()", "node()", ".", "..", "*", "@*"
    );

    private static final List<String> XPATH_BOOLEAN_FUNCTIONS = List.of(
            "not()", "boolean()", "true()", "false()", "exists()", "empty()",
            "count()", "starts-with()", "contains()", "ends-with()", "matches()"
    );

    private static final List<String> XPATH_AXES = List.of(
            "child::", "descendant::", "ancestor::", "following-sibling::",
            "preceding-sibling::", "parent::", "attribute::", "self::",
            "descendant-or-self::"
    );

    private static final List<String> MATCH_WILDCARDS = List.of(
            "*", "text()", "node()", "@*", "/"
    );
}
