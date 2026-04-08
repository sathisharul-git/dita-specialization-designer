package com.ditadesigner.util;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.util.zip.*;

public final class FileUtil {

    private FileUtil() {}

    public static void writeString(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    public static String readString(File file) throws IOException {
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    public static void ensureDir(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static void zipDirectory(File sourceDir, File targetZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetZip))) {
            Path sourcePath = sourceDir.toPath();
            Files.walk(sourcePath)
                    .filter(p -> !Files.isDirectory(p))
                    .forEach(p -> {
                        ZipEntry entry = new ZipEntry(sourcePath.relativize(p).toString().replace('\\', '/'));
                        try {
                            zos.putNextEntry(entry);
                            Files.copy(p, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    public static void copyDirectory(File src, File dest) throws IOException {
        FileUtils.copyDirectory(src, dest);
    }

    public static void copyFile(File src, File dest) throws IOException {
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static String extension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}
