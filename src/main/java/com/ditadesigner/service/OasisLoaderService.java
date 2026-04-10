package com.ditadesigner.service;

import com.ditadesigner.model.*;
import com.ditadesigner.parser.*;
import com.ditadesigner.util.LogService;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads DITA library artefacts (DTD/XSD) and provides element browsing.
 * Auto-loads bundled DITA 1.3 grammars from the classpath on first use.
 */
public class OasisLoaderService {

    // ── Built-in base types ────────────────────────────────────────────

    private static final List<String> BUILT_IN_BASE_TYPES = List.of(
            "topic", "concept", "task", "reference", "map",
            "bookmap", "glossentry", "glossgroup", "learningBase",
            "learningContent", "learningPlan"
    );

    // ── Classpath paths to key DITA 1.3 .mod files ────────────────────

    private static final Map<String, String> BUNDLED_MOD_PATHS = new LinkedHashMap<>();
    static {
        String base = "/dita-v1.3-os-part3-all-inclusive-grammars/all-inclusive-grammars/dtd/";
        BUNDLED_MOD_PATHS.put("topic",      base + "base/dtd/topic.mod");
        BUNDLED_MOD_PATHS.put("map",        base + "base/dtd/map.mod");
        BUNDLED_MOD_PATHS.put("concept",    base + "technicalContent/dtd/concept.mod");
        BUNDLED_MOD_PATHS.put("task",       base + "technicalContent/dtd/task.mod");
        BUNDLED_MOD_PATHS.put("reference",  base + "technicalContent/dtd/reference.mod");
        BUNDLED_MOD_PATHS.put("bookmap",    base + "bookmap/dtd/bookmap.mod");
        BUNDLED_MOD_PATHS.put("commonElements", base + "base/dtd/commonElements.mod");
        BUNDLED_MOD_PATHS.put("metaDecl",   base + "base/dtd/metaDecl.mod");
        BUNDLED_MOD_PATHS.put("tblDecl",    base + "base/dtd/tblDecl.mod");
    }

    private final DtdParser dtdParser = new DtdParser();
    private final XsdParser xsdParser = new XsdParser();
    private final LogService log = LogService.getInstance();

    private File libraryDir;
    private final List<String> availableBaseTypes = new ArrayList<>(BUILT_IN_BASE_TYPES);
    private final Map<String, List<ElementDef>> baseElementCache = new LinkedHashMap<>();
    private boolean bundledLibraryLoaded = false;

    // ── Auto-load bundled DITA 1.3 grammars ───────────────────────────

    /**
     * Loads the bundled DITA 1.3 grammar .mod files from the classpath.
     * Called automatically on construction.
     */
    public void loadBundledLibrary() {
        if (bundledLibraryLoaded) return;
        int loaded = 0;
        for (Map.Entry<String, String> entry : BUNDLED_MOD_PATHS.entrySet()) {
            String typeName = entry.getKey();
            String path = entry.getValue();
            try {
                URL url = getClass().getResource(path);
                if (url == null) continue;

                // Copy to a temp file so DtdParser (File-based) can read it
                File tmpFile = File.createTempFile("dita_" + typeName + "_", ".mod");
                tmpFile.deleteOnExit();
                try (InputStream in = url.openStream();
                     OutputStream out = new FileOutputStream(tmpFile)) {
                    in.transferTo(out);
                }

                List<ElementDef> elems = dtdParser.parseFile(tmpFile);
                if (!elems.isEmpty()) {
                    baseElementCache.merge(typeName, elems, (existing, newElems) -> {
                        List<ElementDef> merged = new ArrayList<>(existing);
                        Set<String> existingNames = existing.stream()
                                .map(ElementDef::getName).collect(Collectors.toSet());
                        newElems.stream()
                                .filter(e -> !existingNames.contains(e.getName()))
                                .forEach(merged::add);
                        return merged;
                    });
                    loaded++;
                }
            } catch (Exception e) {
                // Silently skip – bundled lib is best-effort
            }
        }
        bundledLibraryLoaded = true;
        if (loaded > 0) {
            log.log("Bundled DITA 1.3 library loaded: " + loaded + " grammar module(s).");
        }
    }

    // ── Load from user-selected directory ─────────────────────────────

    public int loadFromDirectory(File dir) {
        if (!dir.isDirectory()) {
            log.log("OASIS Loader: '" + dir + "' is not a directory.");
            return 0;
        }
        this.libraryDir = dir;
        availableBaseTypes.clear();
        availableBaseTypes.addAll(BUILT_IN_BASE_TYPES);
        // Keep bundled elements, add user-loaded ones
        Map<String, List<ElementDef>> userCache = new LinkedHashMap<>();

        int count = 0;
        File[] dtdFiles = dir.listFiles(f -> f.isFile() &&
                (f.getName().endsWith(".dtd") || f.getName().endsWith(".mod")));
        if (dtdFiles != null) {
            for (File f : dtdFiles) {
                try {
                    List<ElementDef> elems = dtdParser.parseFile(f);
                    String stem = stem(f);
                    if (!availableBaseTypes.contains(stem)) availableBaseTypes.add(stem);
                    userCache.merge(stem, elems, (a, b) -> { a.addAll(b); return a; });
                    log.log("Loaded DTD: " + f.getName() + " (" + elems.size() + " elements)");
                    count++;
                } catch (Exception e) {
                    log.logError("Failed to parse " + f.getName(), e);
                }
            }
        }

        File[] xsdFiles = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".xsd"));
        if (xsdFiles != null) {
            for (File f : xsdFiles) {
                try {
                    List<TopicType> types = xsdParser.parseFile(f);
                    for (TopicType t : types) {
                        if (!availableBaseTypes.contains(t.getName())) {
                            availableBaseTypes.add(t.getName());
                        }
                    }
                    log.log("Loaded XSD: " + f.getName() + " (" + types.size() + " types)");
                    count++;
                } catch (Exception e) {
                    log.logError("Failed to parse " + f.getName(), e);
                }
            }
        }

        // Merge user-loaded into cache (user overrides bundled for same type)
        userCache.forEach((k, v) -> baseElementCache.put(k, v));

        log.logSuccess("Loaded " + count + " library files from " + dir.getPath());
        return count;
    }

    // ── Element browsing ──────────────────────────────────────────────

    /**
     * Returns elements available in a given base type (from bundled + user library).
     * Also includes elements from parent base types (topic is always included).
     */
    public List<ElementDef> getBaseElements(String baseType) {
        loadBundledLibrary(); // ensure bundled is loaded
        List<ElementDef> result = new ArrayList<>();

        // Always add topic elements first (base of all topics)
        if (!"topic".equals(baseType) && baseElementCache.containsKey("topic")) {
            result.addAll(baseElementCache.get("topic"));
        }
        if (baseElementCache.containsKey("commonElements")) {
            result.addAll(baseElementCache.get("commonElements"));
        }
        if (baseElementCache.containsKey(baseType)) {
            List<ElementDef> typeElems = baseElementCache.get(baseType);
            // Add type-specific elements, avoiding duplicates
            Set<String> existing = result.stream().map(ElementDef::getName).collect(Collectors.toSet());
            typeElems.stream().filter(e -> !existing.contains(e.getName())).forEach(result::add);
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Returns elements from the base type, filtered by a search term.
     */
    public List<ElementDef> searchBaseElements(String baseType, String searchTerm) {
        List<ElementDef> all = getBaseElements(baseType);
        if (searchTerm == null || searchTerm.isBlank()) return all;
        String lower = searchTerm.toLowerCase();
        return all.stream()
                .filter(e -> e.getName() != null && e.getName().toLowerCase().contains(lower))
                .collect(Collectors.toList());
    }

    /**
     * Returns all element names known across all loaded types.
     */
    public Set<String> getAllKnownElementNames() {
        loadBundledLibrary();
        Set<String> names = new LinkedHashSet<>();
        baseElementCache.values().forEach(list -> list.stream()
                .map(ElementDef::getName).filter(Objects::nonNull).forEach(names::add));
        return Collections.unmodifiableSet(names);
    }

    public List<String> getAvailableBaseTypes() {
        return Collections.unmodifiableList(availableBaseTypes);
    }

    public File getLibraryDir() { return libraryDir; }
    public boolean isLibraryLoaded() { return libraryDir != null || bundledLibraryLoaded; }

    private String stem(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }
}
