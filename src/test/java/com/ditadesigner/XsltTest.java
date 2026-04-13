package com.ditadesigner;

import com.ditadesigner.xslt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the xslt/ package:
 *   XsltExecutionService, XsltValidationService, XslKnowledgeBase, XslVariableScanner.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class XsltTest {

    private static XsltExecutionService executor;
    private static XsltValidationService validator;

    @TempDir
    static File tempDir;

    // ── Sample XML and XSLT strings ───────────────────────────────────

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <catalog>
              <book id="1"><title>Saxon Guide</title></book>
              <book id="2"><title>XSLT Cookbook</title></book>
            </catalog>
            """;

    private static final String IDENTITY_XSLT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml" indent="yes"/>
              <xsl:template match="@*|node()">
                <xsl:copy>
                  <xsl:apply-templates select="@*|node()"/>
                </xsl:copy>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String VALUE_XSLT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml" indent="yes"/>
              <xsl:template match="/">
                <result>
                  <xsl:for-each select="//book">
                    <item><xsl:value-of select="title"/></item>
                  </xsl:for-each>
                </result>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @BeforeAll
    static void setup() {
        executor  = new XsltExecutionService();
        validator = new XsltValidationService();
    }

    // ── Scenario 1: Identity transform ───────────────────────────────

    @Test @Order(1)
    void identityTransformPreservesStructure() {
        XsltExecutionService.TransformResult r =
                executor.transformText(SAMPLE_XML, IDENTITY_XSLT, null);
        assertTrue(r.isSuccess(), "identity transform must succeed: " + r.errorMessage());
        assertNotNull(r.output());
        assertTrue(r.output().contains("<title>Saxon Guide</title>"),
                "output must contain original content");
    }

    // ── Scenario 2: Value extraction ─────────────────────────────────

    @Test @Order(2)
    void valueExtractionTransform() {
        XsltExecutionService.TransformResult r =
                executor.transformText(SAMPLE_XML, VALUE_XSLT, null);
        assertTrue(r.isSuccess(), "value extraction must succeed: " + r.errorMessage());
        assertTrue(r.output().contains("Saxon Guide"));
        assertTrue(r.output().contains("XSLT Cookbook"));
    }

    // ── Scenario 3: Parameter passing ────────────────────────────────

    @Test @Order(3)
    void parameterPassedToStylesheet() {
        String paramXslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="xml"/>
                  <xsl:param name="greeting" select="'default'"/>
                  <xsl:template match="/">
                    <out><xsl:value-of select="$greeting"/></out>
                  </xsl:template>
                </xsl:stylesheet>
                """;
        XsltExecutionService.TransformResult r =
                executor.transformText(SAMPLE_XML, paramXslt, Map.of("greeting", "Hello"));
        assertTrue(r.isSuccess(), "param transform must succeed: " + r.errorMessage());
        assertTrue(r.output().contains("Hello"), "output must contain the passed parameter value");
    }

    // ── Scenario 4: Transform failure — bad XSLT ─────────────────────

    @Test @Order(4)
    void badXsltProducesFailureResult() {
        String badXslt = """
                <?xml version="1.0"?>
                <xsl:stylesheet version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <xsl:value-of select="$undeclaredVar"/>
                  </xsl:template>
                </xsl:stylesheet>
                """;
        XsltExecutionService.TransformResult r =
                executor.transformText(SAMPLE_XML, badXslt, null);
        assertFalse(r.isSuccess(), "bad XSLT must fail");
        assertNotNull(r.errorMessage());
    }

    // ── Scenario 5: xsl:message capture ──────────────────────────────

    @Test @Order(5)
    void xslMessageIsCaptured() {
        String msgXslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="xml"/>
                  <xsl:template match="/">
                    <xsl:message>debug-output-captured</xsl:message>
                    <out/>
                  </xsl:template>
                </xsl:stylesheet>
                """;
        XsltExecutionService.TransformResult r =
                executor.transformText(SAMPLE_XML, msgXslt, null);
        assertTrue(r.isSuccess(), "xsl:message must not fail the transform");
        assertTrue(r.messages().contains("debug-output-captured"),
                "message content must be captured");
    }

    // ── Scenario 6: Validation — valid stylesheet ─────────────────────

    @Test @Order(6)
    void validStylesheetProducesNoErrors() {
        List<XsltValidationError> errors = validator.validateText(IDENTITY_XSLT);
        assertTrue(errors.stream().noneMatch(
                e -> e.getSeverity() == XsltValidationError.Severity.ERROR),
                "valid stylesheet must produce no ERROR severity entries");
    }

    // ── Scenario 7: Validation — invalid stylesheet ───────────────────

    @Test @Order(7)
    void invalidStylesheetProducesErrors() {
        String invalid = """
                <?xml version="1.0"?>
                <xsl:stylesheet version="2.0"
                    xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <xsl:value-of select="$noSuchVar"/>
                  </xsl:template>
                </xsl:stylesheet>
                """;
        List<XsltValidationError> errors = validator.validateText(invalid);
        assertTrue(errors.stream().anyMatch(
                e -> e.getSeverity() == XsltValidationError.Severity.ERROR),
                "undeclared variable must produce at least one ERROR");
    }

    // ── Scenario 8: Validation — empty input ─────────────────────────

    @Test @Order(8)
    void validateEmptyStringDoesNotThrow() {
        assertDoesNotThrow(() -> {
            List<XsltValidationError> errors = validator.validateText("");
            assertNotNull(errors);
        });
    }

    // ── Scenario 9: XslKnowledgeBase element count ────────────────────

    @Test @Order(9)
    void knowledgeBaseHasSufficientElements() {
        assertTrue(XslKnowledgeBase.ALL_ELEMENTS.size() >= 25,
                "knowledge base must contain at least 25 XSL elements");
    }

    // ── Scenario 10: XslKnowledgeBase lookup ─────────────────────────

    @Test @Order(10)
    void lookupKnownElement() {
        Optional<XslKnowledgeBase.XslElement> el = XslKnowledgeBase.element("value-of");
        assertTrue(el.isPresent(), "value-of must be in the knowledge base");
        assertNotNull(el.get().description());
        assertNotNull(el.get().snippet());
        assertFalse(el.get().attributes().isEmpty(), "value-of must have attributes");
    }

    @Test @Order(11)
    void lookupUnknownElementReturnsEmpty() {
        assertTrue(XslKnowledgeBase.element("nonexistent").isEmpty());
    }

    // ── Scenario 11: Snippet cursor marker ───────────────────────────

    @Test @Order(12)
    void mostSnippetsContainCursorMarker() {
        // Structural/complete snippets (xsl:copy, xsl:choose, xsl:otherwise) may have
        // no fill-in position; at least 80% of snippets must contain the cursor marker.
        long total    = XslKnowledgeBase.ALL_ELEMENTS.size();
        long withMark = XslKnowledgeBase.ALL_ELEMENTS.stream()
                .filter(el -> el.snippet().contains("|"))
                .count();
        assertTrue(withMark >= total * 0.8,
                withMark + "/" + total + " snippets have cursor marker — expected ≥80%");
    }

    // ── Scenario 12: elementNames completeness ───────────────────────

    @Test @Order(13)
    void elementNamesContainsCommonInstructions() {
        List<String> names = XslKnowledgeBase.elementNames();
        assertTrue(names.contains("template"));
        assertTrue(names.contains("for-each"));
        assertTrue(names.contains("if"));
        assertTrue(names.contains("choose"));
        assertTrue(names.contains("value-of"));
        assertTrue(names.contains("variable"));
        assertTrue(names.contains("param"));
    }

    // ── Scenario 13: XslVariableScanner — variable detection ──────────

    @Test @Order(14)
    void scannerDetectsVariables() {
        String xslt = "<xsl:variable name=\"myVar\" select=\".\"/>";
        XslVariableScanner.ScanResult scan = XslVariableScanner.scan(xslt);
        assertTrue(scan.allVarNames().contains("myVar"), "myVar must be detected");
    }

    @Test @Order(15)
    void scannerDetectsParameters() {
        String xslt = "<xsl:param name=\"inputDoc\" select=\"''\"/>";
        XslVariableScanner.ScanResult scan = XslVariableScanner.scan(xslt);
        assertTrue(scan.allVarNames().contains("inputDoc"), "inputDoc param must be detected");
    }

    @Test @Order(16)
    void scannerDetectsNamedTemplates() {
        String xslt = "<xsl:template name=\"formatDate\"></xsl:template>";
        XslVariableScanner.ScanResult scan = XslVariableScanner.scan(xslt);
        assertTrue(scan.namedTemplates().contains("formatDate"),
                "named template 'formatDate' must be detected");
    }

    // ── Scenario 14: Deduplication ────────────────────────────────────

    @Test @Order(17)
    void scannerDeduplicatesVariables() {
        String xslt = "<xsl:variable name=\"x\"/><xsl:variable name=\"x\"/>";
        XslVariableScanner.ScanResult scan = XslVariableScanner.scan(xslt);
        assertEquals(1, scan.allVarNames().stream().filter("x"::equals).count(),
                "duplicate variable name must appear only once");
    }

    // ── Scenario 15: Var prefix detection ─────────────────────────────

    @Test @Order(18)
    void detectVarPrefixAtEndOfWord() {
        String text = "select=\"$myV";
        String prefix = XslVariableScanner.detectVarPrefix(text, text.length());
        assertEquals("myV", prefix, "prefix after $ must be detected");
    }

    @Test @Order(19)
    void detectVarPrefixNoDollarReturnsNull() {
        String text = "select=\"myV";
        assertNull(XslVariableScanner.detectVarPrefix(text, text.length()),
                "no $ means no prefix — must return null");
    }

    // ── Scenario 16: Filtered suggestions ────────────────────────────

    @Test @Order(20)
    void varSuggestionsFilteredByPrefix() {
        String xslt = "<xsl:variable name=\"myVar\"/>" +
                      "<xsl:param name=\"myParam\"/>" +
                      "<xsl:variable name=\"otherVar\"/>";
        XslVariableScanner.ScanResult scan = XslVariableScanner.scan(xslt);
        List<String> suggestions = XslVariableScanner.varSuggestions(scan, "my");
        assertTrue(suggestions.contains("$myVar"),   "$myVar must match prefix 'my'");
        assertTrue(suggestions.contains("$myParam"), "$myParam must match prefix 'my'");
        assertFalse(suggestions.contains("$otherVar"), "$otherVar must not match prefix 'my'");
    }

    @Test @Order(21)
    void varSuggestionsBlankPrefixReturnsAll() {
        String xslt = "<xsl:variable name=\"a\"/><xsl:variable name=\"b\"/>";
        XslVariableScanner.ScanResult scan = XslVariableScanner.scan(xslt);
        List<String> all = XslVariableScanner.varSuggestions(scan, "");
        assertTrue(all.contains("$a") && all.contains("$b"),
                "blank prefix must return all variables");
    }

    // ── Scenario 17: Edge cases ───────────────────────────────────────

    @Test @Order(22)
    void scanNullReturnsEmpty() {
        assertSame(XslVariableScanner.ScanResult.EMPTY, XslVariableScanner.scan(null));
    }

    @Test @Order(23)
    void scanBlankReturnsEmpty() {
        assertSame(XslVariableScanner.ScanResult.EMPTY, XslVariableScanner.scan("  "));
    }

    @Test @Order(24)
    void templateNameSuggestionsFilteredByPrefix() {
        String xslt = "<xsl:template name=\"formatDate\"/>" +
                      "<xsl:template name=\"formatTime\"/>" +
                      "<xsl:template name=\"renderTable\"/>";
        XslVariableScanner.ScanResult scan = XslVariableScanner.scan(xslt);
        List<String> suggs = XslVariableScanner.templateNameSuggestions(scan, "format");
        assertTrue(suggs.contains("formatDate"));
        assertTrue(suggs.contains("formatTime"));
        assertFalse(suggs.contains("renderTable"));
    }

    // ── Scenario 18: Save transform output to file ────────────────────

    @Test @Order(25)
    void saveToFileWritesContent() throws Exception {
        File outFile = new File(tempDir, "output.xml");
        executor.saveToFile("<root/>", outFile);
        assertTrue(outFile.exists(), "output file must be created");
        String content = Files.readString(outFile.toPath());
        assertTrue(content.contains("<root/>") || content.contains("<root />"));
    }
}
