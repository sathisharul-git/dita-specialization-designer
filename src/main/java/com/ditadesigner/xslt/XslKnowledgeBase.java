package com.ditadesigner.xslt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Static knowledge base for XSLT 2.0 elements and their attributes.
 *
 * <p>Used by the completion service to suggest element names, required/optional
 * attributes, and ready-to-insert snippet templates.
 *
 * <h3>Attribute types (for context-aware popup routing)</h3>
 * <ul>
 *   <li>{@code XPATH}  — value is an XPath expression (select/match/test/use/group-by)</li>
 *   <li>{@code QNAME}  — value is a QName (name/mode/as)</li>
 *   <li>{@code STRING} — free text</li>
 *   <li>{@code ENUM}   — enumerated values (method/order/case-order)</li>
 * </ul>
 */
public final class XslKnowledgeBase {

    // ── Attribute metadata ────────────────────────────────────────────────────

    public enum AttrType { XPATH, QNAME, STRING, ENUM }

    public record XslAttribute(
            String name,
            AttrType type,
            boolean required,
            String description,
            List<String> enumValues   // non-empty only for ENUM
    ) {
        public static XslAttribute xpath(String name, boolean required, String desc) {
            return new XslAttribute(name, AttrType.XPATH, required, desc, List.of());
        }
        public static XslAttribute qname(String name, boolean required, String desc) {
            return new XslAttribute(name, AttrType.QNAME, required, desc, List.of());
        }
        public static XslAttribute str(String name, boolean required, String desc) {
            return new XslAttribute(name, AttrType.STRING, required, desc, List.of());
        }
        public static XslAttribute en(String name, boolean required, String desc, String... vals) {
            return new XslAttribute(name, AttrType.ENUM, required, desc, List.of(vals));
        }
    }

    // ── Element metadata ──────────────────────────────────────────────────────

    /**
     * @param localName      e.g. {@code "template"}
     * @param description    one-line description for the popup tooltip
     * @param attributes     ordered list — required attributes first
     * @param snippet        text inserted into the editor; {@code |} marks the cursor position
     */
    public record XslElement(
            String localName,
            String description,
            List<XslAttribute> attributes,
            String snippet
    ) {}

    // ── Registry ──────────────────────────────────────────────────────────────

    public static final List<XslElement> ALL_ELEMENTS = List.of(

        new XslElement("template",
            "Define a template rule or named template",
            List.of(
                XslAttribute.xpath("match", false, "Pattern that this template matches"),
                XslAttribute.qname("name",  false, "Named template identifier"),
                XslAttribute.qname("mode",  false, "Template mode"),
                XslAttribute.str ("priority", false, "Numeric conflict resolution priority"),
                XslAttribute.qname("as",    false, "Required return type")
            ),
            "<xsl:template match=\"|\">\n  \n</xsl:template>"),

        new XslElement("apply-templates",
            "Apply matching templates to selected nodes",
            List.of(
                XslAttribute.xpath("select", false, "Node set to process (default: child nodes)"),
                XslAttribute.qname("mode",   false, "Template mode to use")
            ),
            "<xsl:apply-templates select=\"|\"/>"),

        new XslElement("call-template",
            "Call a named template",
            List.of(
                XslAttribute.qname("name", true, "Name of the template to call")
            ),
            "<xsl:call-template name=\"|\"/>"),

        new XslElement("value-of",
            "Output the string value of an XPath expression",
            List.of(
                XslAttribute.xpath("select",    true,  "XPath expression to evaluate"),
                XslAttribute.en  ("separator",  false, "Separator between values", " ", ", ", "")
            ),
            "<xsl:value-of select=\"|\"/>"),

        new XslElement("variable",
            "Declare a local or global variable",
            List.of(
                XslAttribute.qname("name",   true,  "Variable name (referenced as $name)"),
                XslAttribute.xpath("select", false, "XPath value (or use content)"),
                XslAttribute.qname("as",     false, "Sequence type annotation")
            ),
            "<xsl:variable name=\"|\" select=\"\"/>"),

        new XslElement("param",
            "Declare a template parameter",
            List.of(
                XslAttribute.qname("name",     true,  "Parameter name (referenced as $name)"),
                XslAttribute.xpath("select",   false, "Default value as XPath"),
                XslAttribute.qname("as",       false, "Sequence type annotation"),
                XslAttribute.en  ("required",  false, "Whether the param is required", "yes", "no"),
                XslAttribute.en  ("tunnel",    false, "Tunnel parameter", "yes", "no")
            ),
            "<xsl:param name=\"|\" select=\"\"/>"),

        new XslElement("with-param",
            "Pass a parameter value to a called template",
            List.of(
                XslAttribute.qname("name",  true,  "Parameter name"),
                XslAttribute.xpath("select",false,  "Value expression"),
                XslAttribute.en  ("tunnel", false,  "Tunnel through", "yes", "no")
            ),
            "<xsl:with-param name=\"|\" select=\"\"/>"),

        new XslElement("for-each",
            "Iterate over a node set",
            List.of(
                XslAttribute.xpath("select", true, "XPath expression selecting nodes to iterate")
            ),
            "<xsl:for-each select=\"|\">\n  \n</xsl:for-each>"),

        new XslElement("for-each-group",
            "Iterate over grouped nodes",
            List.of(
                XslAttribute.xpath("select",          true,  "Node set to group"),
                XslAttribute.xpath("group-by",        false, "Grouping key expression"),
                XslAttribute.xpath("group-adjacent",  false, "Group by adjacent key"),
                XslAttribute.xpath("group-starting-with", false, "Group starting with pattern"),
                XslAttribute.xpath("group-ending-with",   false, "Group ending with pattern")
            ),
            "<xsl:for-each-group select=\"|\" group-by=\"\">\n  \n</xsl:for-each-group>"),

        new XslElement("if",
            "Conditional output — single branch",
            List.of(
                XslAttribute.xpath("test", true, "Boolean condition")
            ),
            "<xsl:if test=\"|\">\n  \n</xsl:if>"),

        new XslElement("choose",
            "Multi-branch conditional (when/otherwise)",
            List.of(),
            "<xsl:choose>\n  <xsl:when test=\"|\">\n    \n  </xsl:when>\n  <xsl:otherwise>\n    \n  </xsl:otherwise>\n</xsl:choose>"),

        new XslElement("when",
            "Conditional branch inside xsl:choose",
            List.of(
                XslAttribute.xpath("test", true, "Boolean condition for this branch")
            ),
            "<xsl:when test=\"|\">\n  \n</xsl:when>"),

        new XslElement("otherwise",
            "Default branch inside xsl:choose",
            List.of(),
            "<xsl:otherwise>\n  |\n</xsl:otherwise>"),

        new XslElement("element",
            "Create an element node in the result tree",
            List.of(
                XslAttribute.xpath("name",      true,  "Element name (may be an AVT)"),
                XslAttribute.xpath("namespace", false, "Namespace URI"),
                XslAttribute.qname("as",        false, "Validation type")
            ),
            "<xsl:element name=\"|\">\n  \n</xsl:element>"),

        new XslElement("attribute",
            "Create an attribute node",
            List.of(
                XslAttribute.xpath("name",      true,  "Attribute name (may be an AVT)"),
                XslAttribute.xpath("select",    false, "Value expression"),
                XslAttribute.xpath("namespace", false, "Namespace URI")
            ),
            "<xsl:attribute name=\"|\" select=\"\"/>"),

        new XslElement("copy",
            "Shallow copy of the context node",
            List.of(
                XslAttribute.en("copy-namespaces", false, "Copy namespace nodes", "yes", "no")
            ),
            "<xsl:copy>\n  <xsl:apply-templates/>\n</xsl:copy>"),

        new XslElement("copy-of",
            "Deep copy of a node set or value",
            List.of(
                XslAttribute.xpath("select",          true,  "Nodes or value to copy"),
                XslAttribute.en  ("copy-namespaces", false,  "Copy namespace nodes", "yes", "no")
            ),
            "<xsl:copy-of select=\"|\"/>"),

        new XslElement("text",
            "Output a literal text node",
            List.of(
                XslAttribute.en("disable-output-escaping", false,
                        "Disable entity escaping in output", "yes", "no")
            ),
            "<xsl:text>|</xsl:text>"),

        new XslElement("comment",
            "Create a comment node in the result",
            List.of(),
            "<xsl:comment>|</xsl:comment>"),

        new XslElement("processing-instruction",
            "Create a processing instruction node",
            List.of(
                XslAttribute.xpath("name", true, "PI target name")
            ),
            "<xsl:processing-instruction name=\"|\">|</xsl:processing-instruction>"),

        new XslElement("message",
            "Emit a diagnostic message",
            List.of(
                XslAttribute.xpath("select",    false, "Message content as XPath"),
                XslAttribute.en  ("terminate",  false, "Stop processing after message", "yes", "no")
            ),
            "<xsl:message>|</xsl:message>"),

        new XslElement("sort",
            "Define sort key for xsl:apply-templates or xsl:for-each",
            List.of(
                XslAttribute.xpath("select",     false, "Sort key expression"),
                XslAttribute.en  ("order",       false, "Sort direction", "ascending", "descending"),
                XslAttribute.en  ("data-type",   false, "Data type for comparison", "text", "number"),
                XslAttribute.en  ("case-order",  false, "Case ordering", "upper-first", "lower-first"),
                XslAttribute.str ("lang",        false, "Language for collation"),
                XslAttribute.str ("collation",   false, "Collation URI")
            ),
            "<xsl:sort select=\"|\" order=\"ascending\"/>"),

        new XslElement("number",
            "Format and output a number",
            List.of(
                XslAttribute.xpath("value",   false, "Numeric value expression"),
                XslAttribute.en  ("level",    false, "Numbering level", "single", "multiple", "any"),
                XslAttribute.xpath("count",   false, "Pattern for counted nodes"),
                XslAttribute.str ("format",   false, "Number format picture")
            ),
            "<xsl:number value=\"|\" format=\"1\"/>"),

        new XslElement("output",
            "Declare serialization properties",
            List.of(
                XslAttribute.en  ("method",   false, "Output method", "xml", "html", "text", "xhtml"),
                XslAttribute.str ("encoding", false, "Output encoding"),
                XslAttribute.en  ("indent",   false, "Indent output", "yes", "no"),
                XslAttribute.en  ("omit-xml-declaration", false, "Omit XML declaration", "yes", "no")
            ),
            "<xsl:output method=\"|\" encoding=\"UTF-8\" indent=\"yes\"/>"),

        new XslElement("import",
            "Import another stylesheet",
            List.of(
                XslAttribute.str("href", true, "URI of the stylesheet to import")
            ),
            "<xsl:import href=\"|\"/>"),

        new XslElement("include",
            "Include another stylesheet",
            List.of(
                XslAttribute.str("href", true, "URI of the stylesheet to include")
            ),
            "<xsl:include href=\"|\"/>"),

        new XslElement("key",
            "Declare a named key for xsl:key() lookups",
            List.of(
                XslAttribute.qname("name",  true,  "Key name"),
                XslAttribute.xpath("match", true,  "Pattern for indexed nodes"),
                XslAttribute.xpath("use",   true,  "Key value expression")
            ),
            "<xsl:key name=\"|\" match=\"\" use=\"\"/>"),

        new XslElement("function",
            "Declare a stylesheet function",
            List.of(
                XslAttribute.qname("name",     true,  "Qualified function name (prefix:local)"),
                XslAttribute.qname("as",       false, "Return type"),
                XslAttribute.en  ("override",  false, "Override built-in function", "yes", "no")
            ),
            "<xsl:function name=\"|\">\n  \n</xsl:function>"),

        new XslElement("sequence",
            "Construct a sequence of items",
            List.of(
                XslAttribute.xpath("select", false, "XPath expression for sequence content")
            ),
            "<xsl:sequence select=\"|\"/>"),

        new XslElement("analyze-string",
            "Regex analysis of a string",
            List.of(
                XslAttribute.xpath("select", true, "String to analyze"),
                XslAttribute.str ("regex",   true, "Regular expression"),
                XslAttribute.str ("flags",   false,"Regex flags")
            ),
            "<xsl:analyze-string select=\"|\" regex=\"\">\n  <xsl:matching-substring/>\n  <xsl:non-matching-substring/>\n</xsl:analyze-string>"),

        new XslElement("result-document",
            "Write output to a secondary result document",
            List.of(
                XslAttribute.xpath("href",     false, "Output URI"),
                XslAttribute.en  ("method",    false, "Serialization method", "xml", "html", "text"),
                XslAttribute.en  ("indent",    false, "Indent output", "yes", "no"),
                XslAttribute.str ("encoding",  false, "Output encoding")
            ),
            "<xsl:result-document href=\"|\">\n  \n</xsl:result-document>"),

        new XslElement("stylesheet",
            "Root element of a stylesheet",
            List.of(
                XslAttribute.str ("version",        true,  "XSLT version (2.0 or 3.0)"),
                XslAttribute.str ("xmlns:xsl",      true,  "XSLT namespace declaration"),
                XslAttribute.str ("xpath-default-namespace", false, "Default namespace for XPath")
            ),
            "<xsl:stylesheet version=\"2.0\"\n  xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">\n  |\n</xsl:stylesheet>")
    );

    // ── Fast lookup ───────────────────────────────────────────────────────────

    private static final Map<String, XslElement> BY_NAME =
            ALL_ELEMENTS.stream().collect(
                    Collectors.toMap(XslElement::localName, Function.identity()));

    public static Optional<XslElement> element(String localName) {
        return Optional.ofNullable(BY_NAME.get(localName));
    }

    /** All element local names, for auto-completion after {@code <xsl:}. */
    public static List<String> elementNames() {
        return ALL_ELEMENTS.stream().map(XslElement::localName).toList();
    }

    private XslKnowledgeBase() {}
}
