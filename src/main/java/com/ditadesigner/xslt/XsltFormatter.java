package com.ditadesigner.xslt;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * F-251: Format an XSLT stylesheet using Saxon's pretty-print serializer.
 *
 * <p>Parses the source as XML then re-serializes with {@code indent=yes}.
 * All namespace prefixes, attributes, and element order are preserved.
 */
final class XsltFormatter {

    private static final Processor PROCESSOR = new Processor(false);

    /**
     * Re-indent {@code xslt} and return the formatted string.
     *
     * @throws Exception if the source is not well-formed XML
     */
    static String format(String xslt) throws Exception {
        DocumentBuilder builder = PROCESSOR.newDocumentBuilder();
        XdmNode doc = builder.build(new StreamSource(new StringReader(xslt)));

        Serializer ser = PROCESSOR.newSerializer();
        ser.setOutputProperty(Serializer.Property.METHOD,  "xml");
        ser.setOutputProperty(Serializer.Property.INDENT,  "yes");
        ser.setOutputProperty(Serializer.Property.ENCODING, "UTF-8");
        // Preserve the XML declaration so the file stays standalone
        ser.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "no");

        StringWriter sw = new StringWriter();
        ser.setOutputWriter(sw);
        PROCESSOR.writeXdmValue(doc, ser);
        return sw.toString();
    }

    private XsltFormatter() {}
}
