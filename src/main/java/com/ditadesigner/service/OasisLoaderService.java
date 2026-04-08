package com.ditadesigner.service;

import com.ditadesigner.model.*;
import com.ditadesigner.parser.*;
import com.ditadesigner.util.LogService;

import java.io.File;
import java.util.*;

/**
 * Loads DITA library artefacts (DTD/XSD) from a user-selected directory.
 * Provides the list of available base types for specialization.
 */
public class OasisLoaderService {

    private static final List<String> BUILT_IN_BASE_TYPES = List.of(
            "topic", "concept", "task", "reference", "map",
            "bookmap", "glossentry", "glossgroup", "learningBase",
            "learningContent", "learningPlan"
    );

    private final DtdParser dtdParser = new DtdParser();
    private final XsdParser xsdParser = new XsdParser();
    private final LogService log = LogService.getInstance();

    private File libraryDir;
    private final List<String> availableBaseTypes = new ArrayList<>(BUILT_IN_BASE_TYPES);
    private final Map<String, List<ElementDef>> baseElementCache = new LinkedHashMap<>();

    /**
     * Loads DITA libraries from the given directory.
     * Returns the number of files processed.
     */
    public int loadFromDirectory(File dir) {
        if (!dir.isDirectory()) {
            log.log("OASIS Loader: '" + dir + "' is not a directory.");
            return 0;
        }
        this.libraryDir = dir;
        availableBaseTypes.clear();
        availableBaseTypes.addAll(BUILT_IN_BASE_TYPES);
        baseElementCache.clear();

        int count = 0;
        File[] dtdFiles = dir.listFiles(f -> f.isFile() &&
                (f.getName().endsWith(".dtd") || f.getName().endsWith(".mod")));
        if (dtdFiles != null) {
            for (File f : dtdFiles) {
                try {
                    List<ElementDef> elems = dtdParser.parseFile(f);
                    String stem = stem(f);
                    if (!availableBaseTypes.contains(stem)) availableBaseTypes.add(stem);
                    baseElementCache.put(stem, elems);
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

        log.logSuccess("Loaded " + count + " library files from " + dir.getPath());
        return count;
    }

    public List<String> getAvailableBaseTypes() {
        return Collections.unmodifiableList(availableBaseTypes);
    }

    public List<ElementDef> getBaseElements(String baseType) {
        return baseElementCache.getOrDefault(baseType, Collections.emptyList());
    }

    public File getLibraryDir() {
        return libraryDir;
    }

    public boolean isLibraryLoaded() {
        return libraryDir != null;
    }

    private String stem(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }
}
