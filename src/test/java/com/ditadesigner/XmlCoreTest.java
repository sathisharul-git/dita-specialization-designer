package com.ditadesigner;

import com.ditadesigner.xml.XmlCoreService;
import com.ditadesigner.xml.XmlParseResult;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlCoreService: parse, validateAgainstXsd, prettyPrint, getProcessor.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class XmlCoreTest {

    private static XmlCoreService xmlCore;

    @TempDir
    static File tempDir;

    @BeforeAll
    static void setup() {
        xmlCore = new XmlCoreService();
    }

    // ── Scenario 1: Well-formed XML ───────────────────────────────────

    @Test @Order(1)
    void parseWellFormedXml() {
        XmlParseResult result = xmlCore.parse("<root><child/></root>");
        assertTrue(result.isWellFormed(), "simple XML must be well-formed");
        assertNotNull(result.document(), "document must not be null");
        assertTrue(result.errors().isEmpty(), "no errors expected");
    }

    @Test @Order(2)
    void parseXmlWithAttributes() {
        XmlParseResult result = xmlCore.parse("<root id=\"1\"><item lang=\"en\">text</item></root>");
        assertTrue(result.isWellFormed());
        assertTrue(result.errors().isEmpty());
    }

    @Test @Order(3)
    void parseXmlWithNamespace() {
        XmlParseResult result = xmlCore.parse(
                "<ns:root xmlns:ns=\"http://example.com\"><ns:item/></ns:root>");
        assertTrue(result.isWellFormed(), "namespace-qualified XML must parse cleanly");
    }

    // ── Scenario 2: Malformed XML ─────────────────────────────────────

    @Test @Order(4)
    void parseMalformedXmlUnclosedTag() {
        XmlParseResult result = xmlCore.parse("<root><child></root>");
        assertFalse(result.isWellFormed(), "unclosed tag must fail");
        assertFalse(result.errors().isEmpty(), "errors must be collected");
    }

    @Test @Order(5)
    void parseMalformedXmlNoRoot() {
        XmlParseResult result = xmlCore.parse("just plain text");
        assertFalse(result.isWellFormed());
    }

    @Test @Order(6)
    void parseMalformedXmlDuplicateAttribute() {
        XmlParseResult result = xmlCore.parse("<root id=\"1\" id=\"2\"/>");
        assertFalse(result.isWellFormed(), "duplicate attribute must fail");
    }

    // ── Scenario 3: Empty / null input ───────────────────────────────

    @Test @Order(7)
    void parseEmptyStringDoesNotThrow() {
        assertDoesNotThrow(() -> {
            XmlParseResult result = xmlCore.parse("");
            assertFalse(result.isWellFormed());
        });
    }

    @Test @Order(8)
    void parseNullProducesInvalidResult() {
        // Service does not guard null — callers must not pass null.
        // Verify it either throws cleanly or returns a not-well-formed result.
        try {
            XmlParseResult result = xmlCore.parse(null);
            assertFalse(result.isWellFormed(), "null input must not be considered well-formed");
        } catch (Exception e) {
            // Any exception is acceptable for null input
        }
    }

    // ── Scenario 4: Pretty-print ──────────────────────────────────────

    @Test @Order(9)
    void prettyPrintAddsIndentation() throws Exception {
        String compact = "<root><a><b/></a></root>";
        String pretty  = xmlCore.prettyPrint(compact);
        assertTrue(pretty.contains("\n"), "pretty-print must add newlines");
        assertTrue(pretty.contains("  ") || pretty.contains("\t"),
                "pretty-print must add indentation");
    }

    @Test @Order(10)
    void prettyPrintOutputIsWellFormed() throws Exception {
        String compact = "<catalog><book id=\"1\"><title>Test</title></book></catalog>";
        String pretty  = xmlCore.prettyPrint(compact);
        XmlParseResult re = xmlCore.parse(pretty);
        assertTrue(re.isWellFormed(), "pretty-printed output must still be well-formed");
    }

    @Test @Order(11)
    void prettyPrintPreservesContent() throws Exception {
        String input  = "<root><child>hello world</child></root>";
        String pretty = xmlCore.prettyPrint(input);
        assertTrue(pretty.contains("hello world"), "text content must survive pretty-print");
    }

    @Test @Order(12)
    void prettyPrintThrowsOnMalformedXml() {
        assertThrows(Exception.class, () -> xmlCore.prettyPrint("<root><unclosed>"));
    }

    // ── Scenario 5: XSD validation — valid document ──────────────────

    @Test @Order(13)
    void xsdValidationPassesForValidDocument() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="book">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="title" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """;
        File xsdFile = new File(tempDir, "book.xsd");
        Files.writeString(xsdFile.toPath(), xsd);

        String xml = "<book><title>Saxon in Action</title></book>";
        List<String> errors = xmlCore.validateAgainstXsd(xml, xsdFile);
        assertTrue(errors.isEmpty(), "valid document must pass XSD validation: " + errors);
    }

    // ── Scenario 6: XSD validation — schema violation ─────────────────

    @Test @Order(14)
    void xsdValidationFailsWhenRequiredChildMissing() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="book">
                    <xs:complexType>
                      <xs:sequence>
                        <xs:element name="title" type="xs:string"/>
                      </xs:sequence>
                    </xs:complexType>
                  </xs:element>
                </xs:schema>
                """;
        File xsdFile = new File(tempDir, "book2.xsd");
        Files.writeString(xsdFile.toPath(), xsd);

        String xml = "<book/>"; // missing required <title>
        List<String> errors = xmlCore.validateAgainstXsd(xml, xsdFile);
        assertFalse(errors.isEmpty(), "missing required child must produce validation error");
    }

    // ── Scenario 7: XSD validation — wrong element type ──────────────

    @Test @Order(15)
    void xsdValidationFailsForWrongRootElement() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="book" type="xs:string"/>
                </xs:schema>
                """;
        File xsdFile = new File(tempDir, "book3.xsd");
        Files.writeString(xsdFile.toPath(), xsd);

        String xml = "<notbook>wrong</notbook>";
        List<String> errors = xmlCore.validateAgainstXsd(xml, xsdFile);
        assertFalse(errors.isEmpty(), "wrong root element must fail validation");
    }

    // ── Scenario 8: getProcessor ──────────────────────────────────────

    @Test @Order(16)
    void getProcessorReturnsNonNull() {
        Processor p = xmlCore.getProcessor();
        assertNotNull(p, "Saxon Processor must not be null");
    }

    @Test @Order(17)
    void getProcessorReturnsSameInstance() {
        Processor p1 = xmlCore.getProcessor();
        Processor p2 = xmlCore.getProcessor();
        assertSame(p1, p2, "same Processor instance must be returned each time");
    }

    // ── Scenario 9: Large XML ─────────────────────────────────────────

    @Test @Order(18)
    void parseLargeXmlWithManyChildren() {
        StringBuilder sb = new StringBuilder("<catalog>");
        for (int i = 0; i < 500; i++) {
            sb.append("<item id=\"").append(i).append("\">text").append(i).append("</item>");
        }
        sb.append("</catalog>");
        XmlParseResult result = xmlCore.parse(sb.toString());
        assertTrue(result.isWellFormed(), "large XML with 500 children must parse cleanly");
    }
}
