package com.ditadesigner.xml.xpath;

import com.ditadesigner.util.LogService;
import com.ditadesigner.xml.XmlCoreService;

import javafx.stage.Stage;

/**
 * Entry point for the XPath Checker module.
 *
 * <p>Follows the same instantiation pattern as {@code XsltModule}: one lazy
 * singleton controller per owner window, activated from
 * {@code MainController.onOpenXPathChecker()}.
 *
 * <h3>Provides:</h3>
 * <ul>
 *   <li>Syntax-highlighted XML editor (via {@code XsltEditor} reuse)</li>
 *   <li>XPath 2.0/3.1 evaluation via Saxon HE ({@link XPathEvaluationService})</li>
 *   <li>XML well-formedness and XSD validation ({@link XmlCoreService})</li>
 *   <li>Namespace prefix declarations for qualified XPath expressions</li>
 * </ul>
 */
public final class XPathModule {

    private static final LogService log = LogService.getInstance();

    /** Shared XML core service — one Saxon Processor for the whole module. */
    private static final XmlCoreService XML_CORE = new XmlCoreService();

    private static XPathUIController controller;

    private XPathModule() { /* static utility */ }

    // ── Public API (called by MainController) ─────────────────────────────────

    /**
     * Open (or bring to front) the XPath Checker window.
     *
     * @param ownerStage the application's primary stage
     */
    public static void openChecker(Stage ownerStage) {
        ensureController(ownerStage).show();
        log.log("[XPathModule] XPath Checker opened.");
    }

    /**
     * Open the checker pre-loaded with the given XML text.
     *
     * @param ownerStage the application's primary stage
     * @param xmlText    XML content to pre-fill (may be {@code null})
     */
    public static void openChecker(Stage ownerStage, String xmlText) {
        ensureController(ownerStage).show(xmlText);
        log.log("[XPathModule] XPath Checker opened with XML content.");
    }

    /** Expose the shared {@link XmlCoreService} for use by other modules. */
    public static XmlCoreService getXmlCoreService() {
        return XML_CORE;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static XPathUIController ensureController(Stage ownerStage) {
        if (controller == null) {
            controller = new XPathUIController(ownerStage, XML_CORE);
        }
        return controller;
    }
}
