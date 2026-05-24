package com.l1ght.ebe.server.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.l1ght.ebe.EBEMod;
import com.l1ght.ebe.network.NetworkLimits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ServerFileLibraryManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = Path.of("config", "ebe", "server", "library");
    private static final Path FILES = ROOT.resolve("files");
    private static final Path INDEX = ROOT.resolve("index.json");
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static long lastModified = Long.MIN_VALUE;

    private ServerFileLibraryManager() {
    }

    public static synchronized void load() {
        try {
            Files.createDirectories(FILES);
            if (!Files.exists(INDEX)) {
                save();
                return;
            }
            var loaded = GSON.fromJson(Files.readString(INDEX, StandardCharsets.UTF_8), Index.class);
            ENTRIES.clear();
            if (loaded != null && loaded.entries != null) {
                for (Entry entry : loaded.entries) {
                    if (entry != null && entry.id != null && entry.fileName != null) ENTRIES.add(entry.sanitized());
                }
            }
            lastModified = Files.getLastModifiedTime(INDEX).toMillis();
            pruneMissingFiles();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load EBE server file library", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILES);
            var index = new Index();
            index.entries = new ArrayList<>(ENTRIES);
            Files.writeString(INDEX, GSON.toJson(index), StandardCharsets.UTF_8);
            lastModified = Files.getLastModifiedTime(INDEX).toMillis();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save EBE server file library", e);
        }
    }

    public static synchronized boolean pollHotReload() {
        try {
            if (!Files.exists(INDEX)) {
                save();
                return true;
            }
            long modified = Files.getLastModifiedTime(INDEX).toMillis();
            if (modified != lastModified) {
                load();
                return true;
            }
        } catch (Exception e) {
            EBEMod.LOGGER.warn("Failed to hot-reload EBE server file library", e);
        }
        return false;
    }

    public static synchronized List<Entry> list() {
        pruneMissingFiles();
        return List.copyOf(ENTRIES);
    }

    public static synchronized Entry importLocal(Path source, String owner) throws IOException {
        if (!Files.isRegularFile(source)) throw new IOException("Library source is not a file");
        long size = Files.size(source);
        if (size > NetworkLimits.MAX_LIBRARY_UPLOAD_BYTES) throw new IOException("Library file is too large");
        String ext = extension(source.getFileName().toString());
        if (!isSupported(ext)) throw new IOException("Unsupported library file format: " + ext);
        String id = UUID.randomUUID().toString();
        String safeName = sanitizeName(source.getFileName().toString());
        Path target = FILES.resolve(id + ext);
        Files.createDirectories(FILES);
        Files.copy(source, target);
        var entry = new Entry();
        entry.id = id;
        entry.name = safeName;
        entry.fileName = target.getFileName().toString();
        entry.owner = owner == null ? "" : owner;
        entry.format = ext.substring(1).toLowerCase(Locale.ROOT);
        entry.size = size;
        entry.checksum = sha256(target);
        entry.created = Instant.now().toEpochMilli();
        entry.modified = entry.created;
        ENTRIES.add(entry.sanitized());
        save();
        return entry;
    }

    public static synchronized boolean delete(String id) {
        Entry entry = find(id);
        if (entry == null) return false;
        ENTRIES.remove(entry);
        try {
            Files.deleteIfExists(resolveFile(entry));
        } catch (IOException e) {
            EBEMod.LOGGER.warn("Failed to delete server library file {}", entry.fileName, e);
        }
        save();
        return true;
    }

    public static synchronized boolean rename(String id, String name) {
        Entry entry = find(id);
        if (entry == null) return false;
        entry.name = sanitizeName(name);
        entry.modified = Instant.now().toEpochMilli();
        save();
        return true;
    }

    public static synchronized Path filePath(String id) {
        Entry entry = find(id);
        return entry == null ? null : resolveFile(entry);
    }

    public static synchronized String toJson() {
        var index = new Index();
        index.entries = new ArrayList<>(list());
        return GSON.toJson(index.entries);
    }

    private static Entry find(String id) {
        if (id == null || id.isBlank()) return null;
        for (Entry entry : ENTRIES) {
            if (id.equals(entry.id)) return entry;
        }
        return null;
    }

    private static Path resolveFile(Entry entry) {
        return FILES.resolve(entry.fileName).normalize();
    }

    private static void pruneMissingFiles() {
        boolean changed = ENTRIES.removeIf(entry -> !Files.isRegularFile(resolveFile(entry)));
        if (changed) save();
    }

    private static boolean isSupported(String ext) {
        return ext.equals(".ebe") || ext.equals(".litematic") || ext.equals(".schem")
                || ext.equals(".schematic") || ext.equals(".nbt");
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    private static String sanitizeName(String name) {
        String value = name == null || name.isBlank() ? "untitled.ebe" : name.trim();
        value = value.replace('\\', '_').replace('/', '_').replace(':', '_');
        return NetworkLimits.bounded(value, NetworkLimits.MAX_SHORT_TEXT_CHARS);
    }

    private static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IOException("Failed to hash library file", e);
        }
    }

    private static class Index {
        List<Entry> entries = new ArrayList<>();
    }

    public static class Entry {
        public String id;
        public String name;
        public String fileName;
        public String owner;
        public String format;
        public long size;
        public String checksum;
        public long created;
        public long modified;
        public List<String> tags = new ArrayList<>();

        Entry sanitized() {
            name = sanitizeName(name);
            fileName = sanitizeName(fileName);
            owner = owner == null ? "" : NetworkLimits.bounded(owner, NetworkLimits.MAX_SHORT_TEXT_CHARS);
            format = format == null ? "" : format.toLowerCase(Locale.ROOT);
            checksum = checksum == null ? "" : checksum;
            tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags.stream()
                    .filter(tag -> tag != null && !tag.isBlank())
                    .map(tag -> NetworkLimits.bounded(tag.trim(), 32))
                    .distinct()
                    .limit(16)
                    .toList());
            return this;
        }
    }
}
