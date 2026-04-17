package com.ditadesigner;

import com.ditadesigner.xml.XmlCoreService;
import com.ditadesigner.xml.xpath.XPathEvaluationService;
import com.ditadesigner.xml.xpath.XPathEvaluationService.ExpressionCheckResult;
import com.ditadesigner.xml.xpath.XPathEvaluationService.XPathResult;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XPathEvaluationService: evaluate() and checkExpression().
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class XPathTest {

    private static XPathEvaluationService service;

    private static final String CATALOG_XML = """
            <catalog>
              <book id="1" lang="en"><title>Saxon Guide</title><author>Michael Kay</author></book>
              <book id="2" lang="fr"><title>XSLT Cookbook</title><author>Sal Mangano</author></book>
              <book id="3" lang="en"><title>XPath 2.0</title><author>Priscilla Walmsley</author></book>
            </catalog>
            """;

    @BeforeAll
    static void setup() {
        service = new XPathEvaluationService(new XmlCoreService());
    }

    // ── Scenario 1: Simple element selection ─────────────────────────

    @Test @Order(1)
    void selectAllTitles() {
        XPathResult r = service.evaluate(CATALOG_XML, "//title", null);
        assertTrue(r.success(), "evaluation must succeed");
        assertEquals(3, r.items().size(), "three titles expected");
        assertTrue(r.items().contains("Saxon Guide"));
        assertTrue(r.items().contains("XSLT Cookbook"));
    }

    @Test @Order(2)
    void selectRootElement() {
        XPathResult r = service.evaluate(CATALOG_XML, "/catalog", null);
        assertTrue(r.success());
        assertEquals(1, r.items().size());
    }

    // ── Scenario 2: Attribute selection ──────────────────────────────

    @Test @Order(3)
    void selectAttributes() {
        XPathResult r = service.evaluate(CATALOG_XML, "//book/@id", null);
        assertTrue(r.success());
        assertEquals(3, r.items().size());
        assertTrue(r.items().contains("1"));
        assertTrue(r.items().contains("2"));
    }

    @Test @Order(4)
    void selectSpecificAttribute() {
        XPathResult r = service.evaluate(CATALOG_XML, "//book[@id='1']/@lang", null);
        assertTrue(r.success());
        assertEquals(1, r.items().size());
        assertEquals("en", r.items().get(0));
    }

    // ── Scenario 3: Count function ────────────────────────────────────

    @Test @Order(5)
    void countFunction() {
        XPathResult r = service.evaluate(CATALOG_XML, "count(//book)", null);
        assertTrue(r.success());
        assertEquals(1, r.items().size());
        assertEquals("3", r.items().get(0));
    }

    @Test @Order(6)
    void countWithPredicate() {
        XPathResult r = service.evaluate(CATALOG_XML, "count(//book[@lang='en'])", null);
        assertTrue(r.success());
        assertEquals("2", r.items().get(0));
    }

    // ── Scenario 4: Multiple matches ─────────────────────────────────

    @Test @Order(7)
    void multipleMatchesReturnsAll() {
        XPathResult r = service.evaluate(CATALOG_XML, "//book", null);
        assertTrue(r.success());
        assertEquals(3, r.items().size());
    }

    // ── Scenario 5: No matches ────────────────────────────────────────

    @Test @Order(8)
    void noMatchReturnsEmptyList() {
        XPathResult r = service.evaluate(CATALOG_XML, "//nonexistent", null);
        assertTrue(r.success(), "valid expression with no matches must still succeed");
        assertTrue(r.items().isEmpty(), "items must be empty when nothing matches");
    }

    // ── Scenario 6: XPath syntax error ───────────────────────────────

    @Test @Order(9)
    void invalidXPathExpressionFails() {
        XPathResult r = service.evaluate(CATALOG_XML, "//[invalid", null);
        assertFalse(r.success(), "syntax error must cause failure");
        assertNotNull(r.errorMessage(), "error message must be non-null");
        assertFalse(r.errorMessage().isBlank());
    }

    @Test @Order(10)
    void badFunctionCallFails() {
        XPathResult r = service.evaluate(CATALOG_XML, "notafunction(//book)", null);
        assertFalse(r.success());
    }

    // ── Scenario 7: Malformed XML ─────────────────────────────────────

    @Test @Order(11)
    void malformedXmlReturnsFailure() {
        XPathResult r = service.evaluate("<root><unclosed>", "//root", null);
        assertFalse(r.success(), "malformed XML must fail");
        assertNotNull(r.errorMessage());
        assertTrue(r.errorMessage().toLowerCase().contains("well-formed")
                || r.errorMessage().toLowerCase().contains("xml"),
                "error must reference XML parsing");
    }

    @Test @Order(12)
    void emptyXmlReturnsFailure() {
        XPathResult r = service.evaluate("", "//root", null);
        assertFalse(r.success());
    }

    // ── Scenario 8: Namespace-aware query ────────────────────────────

    @Test @Order(13)
    void namespaceAwareQuery() {
        String nsXml = "<ns:root xmlns:ns=\"http://example.com\">" +
                       "<ns:item>value</ns:item></ns:root>";
        Map<String, String> ns = Map.of("ns", "http://example.com");
        XPathResult r = service.evaluate(nsXml, "//ns:item", ns);
        assertTrue(r.success(), "namespace-aware query must succeed");
        assertEquals(1, r.items().size());
        assertEquals("value", r.items().get(0));
    }

    @Test @Order(14)
    void namespaceQueryWithoutDeclarationFails() {
        String nsXml = "<ns:root xmlns:ns=\"http://example.com\"><ns:item/></ns:root>";
        // Intentionally NOT declaring ns prefix in the map
        XPathResult r = service.evaluate(nsXml, "//ns:item", null);
        assertFalse(r.success(), "undeclared namespace prefix must cause error");
    }

    // ── Scenario 9: Predicate filtering ──────────────────────────────

    @Test @Order(15)
    void predicateFilterByAttribute() {
        XPathResult r = service.evaluate(CATALOG_XML, "//book[@lang='fr']/title", null);
        assertTrue(r.success());
        assertEquals(1, r.items().size());
        assertEquals("XSLT Cookbook", r.items().get(0));
    }

    @Test @Order(16)
    void predicateFilterByPosition() {
        XPathResult r = service.evaluate(CATALOG_XML, "//book[1]/title", null);
        assertTrue(r.success());
        assertEquals(1, r.items().size());
        assertEquals("Saxon Guide", r.items().get(0));
    }

    // ── Scenario 10: checkExpression ─────────────────────────────────

    @Test @Order(17)
    void checkExpressionValidSyntax() {
        ExpressionCheckResult r = service.checkExpression("//title", null);
        assertTrue(r.valid(), "valid expression must pass syntax check");
        assertNotNull(r.message());
    }

    @Test @Order(18)
    void checkExpressionComplexValid() {
        ExpressionCheckResult r = service.checkExpression(
                "//book[@lang='en' and @id > 0]/title", null);
        assertTrue(r.valid());
    }

    @Test @Order(19)
    void checkExpressionInvalidSyntax() {
        ExpressionCheckResult r = service.checkExpression("///bad[[[", null);
        assertFalse(r.valid(), "invalid expression must fail syntax check");
        assertNotNull(r.message());
        assertFalse(r.message().isBlank());
    }

    @Test @Order(20)
    void checkExpressionEmptyString() {
        // Empty expression is invalid XPath
        ExpressionCheckResult r = service.checkExpression("", null);
        assertNotNull(r);
        // May be valid (empty sequence) or invalid — must not throw
    }

    // ── Scenario 11: XPath 2.0 features ──────────────────────────────

    @Test @Order(21)
    void xpath2StringFunctions() {
        XPathResult r = service.evaluate(CATALOG_XML,
                "//title[starts-with(., 'Saxon')]", null);
        assertTrue(r.success());
        assertEquals(1, r.items().size());
        assertEquals("Saxon Guide", r.items().get(0));
    }

    @Test @Order(22)
    void xpath2UpperCaseFunction() {
        XPathResult r = service.evaluate(CATALOG_XML,
                "upper-case(//book[1]/title)", null);
        assertTrue(r.success());
        assertEquals(1, r.items().size());
        assertEquals("SAXON GUIDE", r.items().get(0));
    }
}
