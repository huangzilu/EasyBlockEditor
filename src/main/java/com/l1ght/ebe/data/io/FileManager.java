package com.l1ght.ebe.data.io;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class FileManager {
    private final Path schematicDir;
    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".ebe", ".litematic", ".schem", ".nbt", ".schematic");

    public FileManager(String dir) {
        this.schematicDir = Path.of(dir);
    }

    public Path getSchematicDir() { return schematicDir; }

    public void ensureDir() throws IOException {
        Files.createDirectories(schematicDir);
    }

    public List<Path> listFiles() throws IOException {
        if (!Files.exists(schematicDir)) return List.of();
        try (Stream<Path> stream = Files.list(schematicDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(f -> SUPPORTED_EXTENSIONS.stream().anyMatch(ext -> f.getFileName().toString().endsWith(ext)))
                .sorted()
                .toList();
        }
    }

    public static String getFileExtension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    public static String getFileType(Path file) {
        String ext = getFileExtension(file).toLowerCase();
        return switch (ext) {
            case ".ebe" -> "ebe";
            case ".litematic" -> "litematic";
            case ".schem" -> "sponge";
            case ".nbt" -> "vanilla";
            case ".schematic" -> "schematica";
            default -> "unknown";
        };
    }

    public Path resolve(String filename) {
        return schematicDir.resolve(filename);
    }
}
