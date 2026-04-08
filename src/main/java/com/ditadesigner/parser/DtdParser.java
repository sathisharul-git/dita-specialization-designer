package com.ditadesigner.parser;

import com.ditadesigner.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Lightweight DTD parser that extracts element and attribute information
 * from .dtd and .mod files for display in the OASIS library browser.
 * (Full DTD parsing is deferred to Xerces for validation.)
 */
public class DtdParser {

    private static final Pattern ELEMENT_PATTERN =
            Pattern.compile("<!ELEMENT\\s+(\\S+)\\s+(.+?)\\s*>", Pattern.DOTALL);
    private static final Pattern ATTLIST_PATTERN =
            Pattern.compile("<!ATTLIST\\s+(\\S+)\\s+(.+?)>", Pattern.DOTALL);
    private static final Pattern ATTR_LINE =
            Pattern.compile("(\\S+)\\s+(\\S+(?:\\([^)]*\\))?)\\s+(#REQUIRED|#IMPLIED|#FIXED\\s+\"[^\"]*\"|\"[^\"]*\")?");

    public List<ElementDef> parseFile(File dtdFile) throws IOException {
        String content = Files.readString(dtdFile.toPath(), StandardCharsets.UTF_8);
        return parse(content);
    }

    public List<ElementDef> parse(String dtdContent) {
        // Strip comments
        String clean = dtdContent.replaceAll("<!--.*?-->", " ");
        // Expand simple parameter entity references (best-effort)
        Map<String, String> entities = extractEntities(clean);
        for (Map.Entry<String, String> e : entities.entrySet()) {
            clean = clean.replace("%" + e.getKey() + ";", e.getValue());
        }

        List<ElementDef> elements = new ArrayList<>();
        Map<String, List<AttributeDef>> attlists = new HashMap<>();

        // Parse ATTLIST first
        Matcher am = ATTLIST_PATTERN.matcher(clean);
        while (am.find()) {
            String elemName = am.group(1);
            String attBody = am.group(2);
            List<AttributeDef> attrs = parseAttlistBody(attBody);
            attlists.put(elemName, attrs);
        }

        // Parse ELEMENT
        Matcher em = ELEMENT_PATTERN.matcher(clean);
        while (em.find()) {
            String name = em.group(1);
            String cm = em.group(2).trim();
            ElementDef elem = new ElementDef(name);
            elem.setContentModel(cm);
            List<AttributeDef> attrs = attlists.getOrDefault(name, Collections.emptyList());
            for (AttributeDef a : attrs) {
                elem.addAttribute(a);
            }
            elements.add(elem);
        }

        return elements;
    }

    private List<AttributeDef> parseAttlistBody(String body) {
        List<AttributeDef> result = new ArrayList<>();
        String[] lines = body.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("%") || line.startsWith("class")) continue;
            Matcher m = ATTR_LINE.matcher(line);
            if (m.find()) {
                String name = m.group(1);
                String type = m.group(2);
                String def = m.group(3);
                if (name.startsWith("%")) continue;
                AttributeDef attr = new AttributeDef(name, type);
                if ("#REQUIRED".equals(def)) {
                    attr.setRequired(true);
                } else if (def != null && def.startsWith("\"")) {
                    attr.setDefaultValue(def.replace("\"", ""));
                }
                if (type.startsWith("(")) {
                    // Enumeration
                    String inner = type.replaceAll("[()]", "");
                    Arrays.stream(inner.split("\\|"))
                            .map(String::trim)
                            .forEach(attr::addEnumValue);
                    attr.setType("NMTOKEN");
                }
                result.add(attr);
            }
        }
        return result;
    }

    private Map<String, String> extractEntities(String content) {
        Map<String, String> map = new HashMap<>();
        Pattern p = Pattern.compile("<!ENTITY\\s+%\\s+(\\S+)\\s+\"([^\"]*)\">", Pattern.DOTALL);
        Matcher m = p.matcher(content);
        while (m.find()) {
            map.put(m.group(1), m.group(2));
        }
        return map;
    }

    /**
     * Scans a directory for .dtd and .mod files and collects all element names.
     */
    public Set<String> collectElementNames(File dir) {
        Set<String> names = new LinkedHashSet<>();
        File[] files = dir.listFiles(f -> f.isFile() &&
                (f.getName().endsWith(".dtd") || f.getName().endsWith(".mod")));
        if (files == null) return names;
        for (File f : files) {
            try {
                List<ElementDef> elems = parseFile(f);
                for (ElementDef e : elems) {
                    names.add(e.getName());
                }
            } catch (IOException ignored) {
            }
        }
        return names;
    }
}
