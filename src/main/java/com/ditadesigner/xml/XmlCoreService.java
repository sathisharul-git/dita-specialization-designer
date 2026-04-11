package com.ditadesigner.xml;

import com.ditadesigner.util.LogService;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.xml.sax.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import java.io.*;
import java.util.*;

/**
 * Core XML services: parsing, well-formedness checking, and schema validation.
 *
 * <p>Shares a single Saxon {@link Processor} instance so it can hand the parsed
 * {@link XdmNode} directly to {@code XPathEvaluationService} without re-parsing.
 * Thread-safe for read operations; do not call {@link #parse} concurrently on the
 * same instance from multiple threads.
 */
public class XmlCoreService {

    private static final LogService log = LogService.getInstance();

    private final Processor processor = new Processor(false);

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Parse an XML string using Saxon, collecting any parse errors.
     *
     * @param xmlText raw XML content
     * @return {@link XmlParseResult} — always non-null; check {@link XmlParseResult#isWellFormed()}
     */
    public XmlParseResult parse(String xmlText) {
        List<String> errors = new ArrayList<>();
        XdmNode doc = null;

        try {
            doc = processor.newDocumentBuilder()
                            .build(new StreamSource(new StringReader(xmlText)));
        } catch (SaxonApiException ex) {
            errors.add("[ERROR] " + ex.getMessage());
        }

        return new XmlParseResult(doc, errors);
    }

    /**
     * Validate an XML string against a W3C XSD file using JAXP (Xerces).
     *
     * @param xmlText raw XML content
     * @param xsdFile the XSD schema file to validate against
     * @return list of validation messages; empty = valid
     */
    public List<String> validateAgainstXsd(String xmlText, File xsdFile) {
        List<String> errors = new ArrayList<>();
        try {
            SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
            Schema schema = factory.newSchema(xsdFile);
            Validator validator = schema.newValidator();
            validator.setErrorHandler(collectingErrorHandler(errors));
            validator.validate(new StreamSource(new StringReader(xmlText)));
        } catch (Exception ex) {
            if (errors.isEmpty()) {
                errors.add("[ERROR] " + ex.getMessage());
            }
        }
        return errors;
    }

    /**
     * Check an XML string for DTD validity (the XML must reference an inline DOCTYPE).
     *
     * @param xmlText raw XML content
     * @return list of validation messages; empty = valid
     */
    public List<String> validateAgainstDtd(String xmlText) {
        List<String> errors = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(true);
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(collectingErrorHandler(errors));
            builder.parse(new InputSource(new StringReader(xmlText)));
        } catch (Exception ex) {
            if (errors.isEmpty()) {
                errors.add("[ERROR] " + ex.getMessage());
            }
        }
        return errors;
    }

    /**
     * Pretty-print XML with 2-space indentation.
     *
     * @param xmlText raw XML content
     * @return formatted XML
     * @throws Exception when the XML is invalid or transformation fails
     */
    public String prettyPrint(String xmlText) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        org.w3c.dom.Document document = factory.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xmlText)));

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        try {
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (Exception ignored) {
            // Some JAXP providers may not support this feature; continue safely.
        }

        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    /** Expose the shared Saxon {@link Processor} for use by XPath/XSLT services. */
    public Processor getProcessor() {
        return processor;
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private ErrorHandler collectingErrorHandler(List<String> target) {
        return new ErrorHandler() {
            public void warning(SAXParseException e) {
                target.add("[WARN] L" + e.getLineNumber() + ": " + e.getMessage());
            }
            public void error(SAXParseException e) {
                target.add("[ERROR] L" + e.getLineNumber() + ": " + e.getMessage());
            }
            public void fatalError(SAXParseException e) {
                target.add("[FATAL] L" + e.getLineNumber() + ": " + e.getMessage());
            }
        };
    }
}
