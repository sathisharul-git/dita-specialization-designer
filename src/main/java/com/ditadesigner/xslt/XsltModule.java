package com.ditadesigner.xslt;

import com.ditadesigner.util.LogService;
import javafx.stage.Stage;

import java.io.File;

/**
 * Entry point for the XSLT Development Environment module.
 *
 * <p>Follows the same instantiation pattern used by other services in the
 * application (direct construction, singleton per owner window).
 * The module is activated from {@code MainController.onOpenXsltWorkbench()}.
 *
 * <h3>Provides:</h3>
 * <ul>
 *   <li>Syntax-highlighted XSLT/XML editor ({@link XsltEditor} via RichTextFX)</li>
 *   <li>Saxon HE 2.0/3.0 transformation engine ({@link XsltExecutionService})</li>
 *   <li>XSLT compile-time validation ({@link XsltValidationService})</li>
 *   <li>Built-in DITA→HTML stylesheet template</li>
 * </ul>
 */
public final class XsltModule {

    private static final LogService log = LogService.getInstance();

    // One controller per owner stage (lazy-initialised)
    private static XsltUIController controller;

    private XsltModule() { /* static utility */ }

    // ── Public API (called by MainController) ─────────────────────────────────

    /**
     * Open (or bring to front) the XSLT Workbench window.
     *
     * @param ownerStage the application's primary stage
     */
    public static void openWorkbench(Stage ownerStage) {
        ensureController(ownerStage).show();
        log.log("[XsltModule] Workbench opened.");
    }

    /**
     * Open the workbench pre-loaded with specific files.
     * Useful for the "DITA → HTML" shortcut from the DITA menu.
     *
     * @param ownerStage the application's primary stage
     * @param xmlFile    XML/DITA source to load (may be {@code null})
     * @param xsltFile   XSLT stylesheet to load (may be {@code null})
     */
    public static void openWorkbench(Stage ownerStage, File xmlFile, File xsltFile) {
        ensureController(ownerStage).show(xmlFile, xsltFile);
        log.log("[XsltModule] Workbench opened with files: "
                + (xmlFile  != null ? xmlFile.getName()  : "—") + " / "
                + (xsltFile != null ? xsltFile.getName() : "—"));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static XsltUIController ensureController(Stage ownerStage) {
        if (controller == null) {
            controller = new XsltUIController(ownerStage);
        }
        return controller;
    }
}
