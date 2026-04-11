package com.ditadesigner.xml;

import net.sf.saxon.s9api.XdmNode;

import java.util.List;

/**
 * Result of parsing an XML document via {@link XmlCoreService}.
 *
 * @param document the parsed Saxon {@link XdmNode}, or {@code null} if parsing failed
 * @param errors   list of error/warning strings (may be empty)
 */
public record XmlParseResult(XdmNode document, List<String> errors) {

    /** Returns {@code true} iff the document was parsed with no fatal or error messages. */
    public boolean isWellFormed() {
        return document != null
                && errors.stream().noneMatch(e -> e.startsWith("[ERROR") || e.startsWith("[FATAL"));
    }
}
