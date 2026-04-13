package com.ditadesigner;

import com.ditadesigner.model.ElementDef;
import com.ditadesigner.model.TopicType;
import com.ditadesigner.parser.DtdParser;
import com.ditadesigner.parser.XsdParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the parser/ package: XsdParser and DtdParser.
 * All fixtures are written to a @TempDir — no external files required.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParserTest {

    private static XsdParser xsdParser;
    private static DtdParser dtdParser;

    @TempDir
    static File tempDir;

    @BeforeAll
    static void setup() {
        xsdParser = new XsdParser();
        dtdParser = new DtdParser();
    }

    // ── XSD helpers ────────────────────────────────────────────────────

    private File writeXsd(String filename, String content) throws IOException {
        File f = new File(tempDir, filename);
        Files.writeString(f.toPath(), content);
        return f;
    }

    // ── Scenario 1: Minimal specialization XSD ────────────────────────

    @Test @Order(1)
    void xsdParserExtractsTopicType() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="myTopic.class">
                    <xs:complexContent>
                      <xs:extension base="topic.class"/>
                    </xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """;
        List<TopicType> results = xsdParser.parseFile(writeXsd("minimal.xsd", xsd));
        assertFalse(results.isEmpty(), "parser must return at least one TopicType");
        TopicType tt = results.get(0);
        assertEquals("myTopic", tt.getName(), ".class suffix must be stripped from name");
        assertEquals("topic",   tt.getBaseType(), ".class suffix must be stripped from base type");
    }

    // ── Scenario 2: Child elements extracted ─────────────────────────

    @Test @Order(2)
    void xsdParserExtractsChildElements() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="phxTask.class">
                    <xs:complexContent>
                      <xs:extension base="task.class">
                        <xs:sequence>
                          <xs:element ref="phxSteps" minOccurs="1" maxOccurs="1"/>
                          <xs:element ref="phxResult" minOccurs="0" maxOccurs="unbounded"/>
                        </xs:sequence>
                      </xs:extension>
                    </xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """;
        List<TopicType> results = xsdParser.parseFile(writeXsd("elements.xsd", xsd));
        assertEquals(1, results.size());
        TopicType tt = results.get(0);
        assertEquals(2, tt.getElements().size(), "two child elements must be extracted");
        assertNotNull(tt.findElementByName("phxSteps"),  "phxSteps must be found");
        assertNotNull(tt.findElementByName("phxResult"), "phxResult must be found");
    }

    // ── Scenario 3: Cardinality mapping ──────────────────────────────

    @Test @Order(3)
    void xsdParserMapsCardinalityCorrectly() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="cardTest.class">
                    <xs:complexContent>
                      <xs:extension base="topic.class">
                        <xs:sequence>
                          <xs:element ref="required"   minOccurs="1" maxOccurs="1"/>
                          <xs:element ref="optional"   minOccurs="0" maxOccurs="1"/>
                          <xs:element ref="oneOrMore"  minOccurs="1" maxOccurs="unbounded"/>
                          <xs:element ref="zeroOrMore" minOccurs="0" maxOccurs="unbounded"/>
                        </xs:sequence>
                      </xs:extension>
                    </xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """;
        List<TopicType> results = xsdParser.parseFile(writeXsd("cardinality.xsd", xsd));
        TopicType tt = results.get(0);

        assertEquals("1", tt.findElementByName("required").getCardinality(),   "1:1 → '1'");
        assertEquals("?", tt.findElementByName("optional").getCardinality(),   "0:1 → '?'");
        assertEquals("+", tt.findElementByName("oneOrMore").getCardinality(),  "1:N → '+'");
        assertEquals("*", tt.findElementByName("zeroOrMore").getCardinality(), "0:N → '*'");
    }

    // ── Scenario 4: Attributes extracted ─────────────────────────────

    @Test @Order(4)
    void xsdParserExtractsAttributes() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="myTopic.class">
                    <xs:complexContent>
                      <xs:extension base="topic.class">
                        <xs:attribute name="id"     type="xs:ID"    use="required"/>
                        <xs:attribute name="status" type="xs:string" use="optional"/>
                      </xs:extension>
                    </xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """;
        List<TopicType> results = xsdParser.parseFile(writeXsd("attrs.xsd", xsd));
        TopicType tt = results.get(0);
        // 'class' attribute is filtered out by the parser; id and status must be present
        assertTrue(tt.getAttributes().stream().anyMatch(a -> "id".equals(a.getName())),
                "id attribute must be extracted");
        assertTrue(tt.getAttributes().stream().anyMatch(a -> "status".equals(a.getName())),
                "status attribute must be extracted");
        assertTrue(tt.getAttributes().stream()
                .filter(a -> "id".equals(a.getName()))
                .findFirst().orElseThrow().isRequired(),
                "id must be marked required");
    }

    // ── Scenario 5: Multiple complex types ───────────────────────────

    @Test @Order(5)
    void xsdParserHandlesMultipleComplexTypes() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="typeA.class">
                    <xs:complexContent>
                      <xs:extension base="topic.class"/>
                    </xs:complexContent>
                  </xs:complexType>
                  <xs:complexType name="typeB.class">
                    <xs:complexContent>
                      <xs:extension base="concept.class"/>
                    </xs:complexContent>
                  </xs:complexType>
                </xs:schema>
                """;
        List<TopicType> results = xsdParser.parseFile(writeXsd("multi.xsd", xsd));
        assertEquals(2, results.size(), "both complex types must be parsed");
        assertTrue(results.stream().anyMatch(t -> "typeA".equals(t.getName())));
        assertTrue(results.stream().anyMatch(t -> "typeB".equals(t.getName())));
    }

    // ── Scenario 6: Complex type without extension not included ────────

    @Test @Order(6)
    void xsdParserSkipsTypesWithoutExtension() throws Exception {
        String xsd = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="standalone.class">
                    <xs:sequence>
                      <xs:element name="child" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """;
        // Parser should either include it with blank base or skip it — must not throw
        assertDoesNotThrow(() -> xsdParser.parseFile(writeXsd("noext.xsd", xsd)));
    }

    // ── Scenario 7: DTD — ELEMENT declaration ────────────────────────

    @Test @Order(7)
    void dtdParserExtractsElementDeclaration() throws Exception {
        String dtd = """
                <!ELEMENT myTopic (title, body)>
                <!ATTLIST myTopic
                  id ID #REQUIRED
                  class CDATA #FIXED "- topic/topic"
                >
                """;
        File dtdFile = new File(tempDir, "simple.dtd");
        Files.writeString(dtdFile.toPath(), dtd);

        List<ElementDef> results = dtdParser.parseFile(dtdFile);
        assertFalse(results.isEmpty(), "DTD parser must extract at least one ElementDef");
        ElementDef el = results.get(0);
        assertEquals("myTopic", el.getName());
    }

    // ── Scenario 8: DTD — ATTLIST declaration ────────────────────────

    @Test @Order(8)
    void dtdParserExtractsAttributeList() throws Exception {
        String dtd = """
                <!ELEMENT myTopic (title)>
                <!ATTLIST myTopic
                  id     ID    #REQUIRED
                  status CDATA #IMPLIED
                >
                """;
        File dtdFile = new File(tempDir, "attlist.dtd");
        Files.writeString(dtdFile.toPath(), dtd);

        List<ElementDef> results = dtdParser.parseFile(dtdFile);
        assertFalse(results.isEmpty());
        ElementDef el = results.get(0);
        assertTrue(el.getAttributes().stream().anyMatch(a -> "id".equals(a.getName())),
                "id attribute must be parsed from ATTLIST");
    }

    // ── Scenario 9: File not found ────────────────────────────────────

    @Test @Order(9)
    void xsdParserThrowsOnMissingFile() {
        File missing = new File(tempDir, "ghost.xsd");
        assertThrows(Exception.class, () -> xsdParser.parseFile(missing),
                "parsing a non-existent file must throw");
    }
}
