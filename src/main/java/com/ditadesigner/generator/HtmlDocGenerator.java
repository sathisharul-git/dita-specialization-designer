package com.ditadesigner.generator;

import com.ditadesigner.model.*;
import com.ditadesigner.transformer.ModelTransformer;
import com.ditadesigner.util.FileUtil;

import java.io.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Generates a self-contained HTML documentation page from a DitaModel.
 * Implements F-142.
 */
public class HtmlDocGenerator {

    private static final String NL = System.lineSeparator();
    private final ModelTransformer transformer = new ModelTransformer();

    public File generate(DitaModel model, File outputDir) throws IOException {
        FileUtil.ensureDir(outputDir);
        File htmlFile = new File(outputDir, sanitize(model.getName()) + "_specialization.html");
        FileUtil.writeString(htmlFile, buildHtml(model));
        return htmlFile;
    }

    private String buildHtml(DitaModel model) {
        StringBuilder sb = new StringBuilder();
        String title = esc(model.getName()) + " Specialization Documentation";
        String year  = String.valueOf(LocalDate.now().getYear());

        sb.append("<!DOCTYPE html>").append(NL);
        sb.append("<html lang=\"en\">").append(NL);
        sb.append("<head>").append(NL);
        sb.append("  <meta charset=\"UTF-8\">").append(NL);
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">").append(NL);
        sb.append("  <title>").append(title).append("</title>").append(NL);
        sb.append("  <style>").append(NL).append(CSS).append("  </style>").append(NL);
        sb.append("</head>").append(NL);
        sb.append("<body>").append(NL);

        // ── Header ────────────────────────────────────────────────────
        sb.append("  <header>").append(NL);
        sb.append("    <h1>").append(title).append("</h1>").append(NL);
        sb.append("    <div class=\"meta\">").append(NL);
        sb.append("      <span>Version: <b>").append(esc(nvl(model.getVersion(), "1.0"))).append("</b></span>").append(NL);
        if (model.getTargetNamespace() != null && !model.getTargetNamespace().isBlank()) {
            sb.append("      <span>Namespace: <code>").append(esc(model.getTargetNamespace())).append("</code></span>").append(NL);
        }
        if (model.getCopyrightOwner() != null && !model.getCopyrightOwner().isBlank()) {
            sb.append("      <span>© ").append(year).append(" ").append(esc(model.getCopyrightOwner())).append("</span>").append(NL);
        }
        sb.append("      <span>Generated: ").append(LocalDate.now()).append("</span>").append(NL);
        sb.append("    </div>").append(NL);
        if (model.getDescription() != null && !model.getDescription().isBlank()) {
            sb.append("    <p class=\"desc\">").append(esc(model.getDescription())).append("</p>").append(NL);
        }
        sb.append("  </header>").append(NL).append(NL);

        // ── Table of Contents ─────────────────────────────────────────
        sb.append("  <nav>").append(NL);
        sb.append("    <h2>Contents</h2>").append(NL);
        sb.append("    <ul>").append(NL);
        for (TopicType tt : model.getTopicTypes()) {
            sb.append("      <li><a href=\"#tt-").append(tt.getId()).append("\">")
              .append(esc(tt.getName())).append("</a></li>").append(NL);
        }
        for (DomainDef dd : model.getDomains()) {
            sb.append("      <li><a href=\"#dd-").append(dd.getId()).append("\">")
              .append(esc(dd.getName())).append(" (domain)</a></li>").append(NL);
        }
        sb.append("    </ul>").append(NL);
        sb.append("  </nav>").append(NL).append(NL);

        // ── Topic Types ───────────────────────────────────────────────
        sb.append("  <main>").append(NL);
        for (TopicType tt : model.getTopicTypes()) {
            sb.append(buildTopicTypeSection(tt, model));
        }

        // ── Domains ───────────────────────────────────────────────────
        for (DomainDef dd : model.getDomains()) {
            sb.append(buildDomainSection(dd));
        }

        // ── Relationships summary ─────────────────────────────────────
        if (!model.getRelationships().isEmpty()) {
            sb.append("  <section class=\"relationships\">").append(NL);
            sb.append("    <h2>Relationships</h2>").append(NL);
            sb.append("    <table>").append(NL);
            sb.append("      <tr><th>Type</th><th>Source</th><th>Target</th></tr>").append(NL);
            for (Relationship rel : model.getRelationships()) {
                String srcName = resolveNodeName(rel.getSourceId(), model);
                String tgtName = resolveNodeName(rel.getTargetId(), model);
                sb.append("      <tr><td>").append(esc(rel.getType().getLabel())).append("</td>")
                  .append("<td>").append(esc(srcName)).append("</td>")
                  .append("<td>").append(esc(tgtName)).append("</td></tr>").append(NL);
            }
            sb.append("    </table>").append(NL);
            sb.append("  </section>").append(NL);
        }

        sb.append("  </main>").append(NL).append(NL);
        sb.append("  <footer><p>Generated by <b>DITA Specialization Designer</b></p></footer>").append(NL);
        sb.append("</body>").append(NL);
        sb.append("</html>").append(NL);
        return sb.toString();
    }

    private String buildTopicTypeSection(TopicType tt, DitaModel model) {
        StringBuilder sb = new StringBuilder();
        String classAttr = transformer.buildClassAttribute(tt);

        sb.append("  <section class=\"topic-type\" id=\"tt-").append(tt.getId()).append("\">").append(NL);
        sb.append("    <h2>").append(esc(tt.getName())).append("</h2>").append(NL);

        sb.append("    <table class=\"info\">").append(NL);
        sb.append("      <tr><th>Base Type</th><td><code>").append(esc(nvl(tt.getBaseType(), "—"))).append("</code></td></tr>").append(NL);
        if (tt.getNamespace() != null && !tt.getNamespace().isBlank())
            sb.append("      <tr><th>Namespace</th><td><code>").append(esc(tt.getNamespace())).append("</code></td></tr>").append(NL);
        if (tt.getPublicId() != null && !tt.getPublicId().isBlank())
            sb.append("      <tr><th>Public ID</th><td><code>").append(esc(tt.getPublicId())).append("</code></td></tr>").append(NL);
        if (tt.getSystemId() != null && !tt.getSystemId().isBlank())
            sb.append("      <tr><th>System ID</th><td><code>").append(esc(tt.getSystemId())).append("</code></td></tr>").append(NL);
        sb.append("      <tr><th>class attr</th><td><code>").append(esc(classAttr)).append("</code></td></tr>").append(NL);
        sb.append("    </table>").append(NL);

        if (tt.getDescription() != null && !tt.getDescription().isBlank())
            sb.append("    <p>").append(esc(tt.getDescription())).append("</p>").append(NL);

        // Elements
        if (!tt.getElements().isEmpty()) {
            sb.append("    <h3>Elements</h3>").append(NL);
            sb.append("    <table>").append(NL);
            sb.append("      <tr><th>Name</th><th>Content Model</th><th>Cardinality</th><th>Description</th></tr>").append(NL);
            for (ElementDef elem : tt.getElements()) {
                sb.append("      <tr>")
                  .append("<td><code>").append(esc(elem.getName())).append("</code></td>")
                  .append("<td><code>").append(esc(nvl(elem.getContentModel(), "(#PCDATA)*"))).append("</code></td>")
                  .append("<td>").append(esc(nvl(elem.getCardinality(), "?"))).append("</td>")
                  .append("<td>").append(esc(nvl(elem.getDescription(), ""))).append("</td>")
                  .append("</tr>").append(NL);
            }
            sb.append("    </table>").append(NL);
        }

        // Attributes
        if (!tt.getAttributes().isEmpty()) {
            sb.append("    <h3>Attributes</h3>").append(NL);
            sb.append("    <table>").append(NL);
            sb.append("      <tr><th>Name</th><th>Type</th><th>Required</th><th>Default</th></tr>").append(NL);
            for (AttributeDef attr : tt.getAttributes()) {
                sb.append("      <tr>")
                  .append("<td><code>").append(esc(attr.getName())).append("</code></td>")
                  .append("<td>").append(esc(attr.getType())).append("</td>")
                  .append("<td>").append(attr.isRequired() ? "✔" : "").append("</td>")
                  .append("<td><code>").append(esc(nvl(attr.getDefaultValue(), ""))).append("</code></td>")
                  .append("</tr>").append(NL);
            }
            sb.append("    </table>").append(NL);
        }

        sb.append("  </section>").append(NL).append(NL);
        return sb.toString();
    }

    private String buildDomainSection(DomainDef dd) {
        StringBuilder sb = new StringBuilder();
        sb.append("  <section class=\"domain\" id=\"dd-").append(dd.getId()).append("\">").append(NL);
        sb.append("    <h2>").append(esc(dd.getName())).append(" <span class=\"badge\">domain</span></h2>").append(NL);
        if (dd.getDescription() != null && !dd.getDescription().isBlank())
            sb.append("    <p>").append(esc(dd.getDescription())).append("</p>").append(NL);
        if (dd.getPublicId() != null && !dd.getPublicId().isBlank())
            sb.append("    <p><b>Public ID:</b> <code>").append(esc(dd.getPublicId())).append("</code></p>").append(NL);
        if (!dd.getElements().isEmpty()) {
            sb.append("    <h3>Elements</h3><ul>").append(NL);
            for (ElementDef e : dd.getElements())
                sb.append("      <li><code>").append(esc(e.getName())).append("</code></li>").append(NL);
            sb.append("    </ul>").append(NL);
        }
        sb.append("  </section>").append(NL).append(NL);
        return sb.toString();
    }

    private String resolveNodeName(String id, DitaModel model) {
        TopicType tt = model.findTopicTypeById(id);
        if (tt != null) return tt.getName();
        DomainDef dd = model.findDomainById(id);
        if (dd != null) return dd.getName();
        return id;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String nvl(String s, String def) { return (s != null && !s.isBlank()) ? s : def; }
    private static String sanitize(String s) { return s == null ? "output" : s.replaceAll("[^a-zA-Z0-9_-]", "_"); }

    // ── Embedded CSS ──────────────────────────────────────────────────

    private static final String CSS =
        "    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; background: #f7f9fc; color: #222; }\n" +
        "    header { background: linear-gradient(135deg, #1565C0, #0d47a1); color: white; padding: 24px 32px; }\n" +
        "    header h1 { margin: 0 0 8px; font-size: 26px; }\n" +
        "    header .meta { display: flex; gap: 20px; flex-wrap: wrap; font-size: 13px; opacity: 0.85; margin-bottom: 8px; }\n" +
        "    header .desc { margin: 8px 0 0; opacity: 0.9; font-size: 14px; }\n" +
        "    nav { background: #fff; border-bottom: 1px solid #dde3ea; padding: 12px 32px; }\n" +
        "    nav h2 { font-size: 14px; margin: 0 0 6px; color: #555; text-transform: uppercase; letter-spacing: 1px; }\n" +
        "    nav ul { display: flex; gap: 12px; flex-wrap: wrap; list-style: none; margin: 0; padding: 0; }\n" +
        "    nav a { color: #1565C0; text-decoration: none; font-size: 13px; padding: 3px 8px;" +
        "            border-radius: 4px; background: #e8f0fe; }\n" +
        "    nav a:hover { background: #c5d9fb; }\n" +
        "    main { max-width: 1100px; margin: 0 auto; padding: 24px 32px; }\n" +
        "    section { background: #fff; border: 1px solid #dde3ea; border-radius: 8px; padding: 20px 24px;" +
        "              margin-bottom: 24px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }\n" +
        "    section.topic-type h2 { color: #1565C0; border-bottom: 2px solid #e8f0fe; padding-bottom: 8px; }\n" +
        "    section.domain h2 { color: #7b3ad5; border-bottom: 2px solid #f0e8fe; padding-bottom: 8px; }\n" +
        "    section.relationships h2 { color: #37474f; }\n" +
        "    h3 { font-size: 14px; color: #555; margin: 16px 0 8px; text-transform: uppercase; letter-spacing: 0.5px; }\n" +
        "    table { width: 100%; border-collapse: collapse; font-size: 13px; margin-bottom: 8px; }\n" +
        "    table.info { width: auto; min-width: 400px; }\n" +
        "    th { background: #f0f4fa; text-align: left; padding: 6px 10px; border: 1px solid #dde3ea; font-weight: 600; }\n" +
        "    td { padding: 5px 10px; border: 1px solid #eee; vertical-align: top; }\n" +
        "    tr:hover td { background: #f9fbff; }\n" +
        "    code { background: #f0f4fa; padding: 1px 5px; border-radius: 3px; font-size: 12px;" +
        "           font-family: 'Consolas', 'Courier New', monospace; color: #c0392b; }\n" +
        "    .badge { background: #7b3ad5; color: white; font-size: 10px; padding: 2px 8px;" +
        "             border-radius: 10px; vertical-align: middle; font-weight: normal; }\n" +
        "    footer { text-align: center; padding: 20px; color: #aaa; font-size: 12px; }\n";
}
