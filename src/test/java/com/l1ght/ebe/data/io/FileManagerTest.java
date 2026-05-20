package com.l1ght.ebe.data.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FileManagerTest {

    @Test
    void testListFiles(@TempDir Path dir) throws IOException {
        var fm = new FileManager(dir.toString());
        Files.writeString(dir.resolve("test.ebe"), "{}");
        Files.writeString(dir.resolve("castle.litematic"), "data");
        Files.writeString(dir.resolve("readme.txt"), "ignore me");
        Files.writeString(dir.resolve("house.schem"), "data");

        var files = fm.listFiles();
        assertEquals(3, files.size());
    }

    @Test
    void testEmptyDir(@TempDir Path dir) throws IOException {
        var fm = new FileManager(dir.toString());
        assertEquals(0, fm.listFiles().size());
    }

    @Test
    void testNonexistentDir(@TempDir Path dir) throws IOException {
        var fm = new FileManager(dir.resolve("nonexistent").toString());
        assertEquals(0, fm.listFiles().size());
    }

    @Test
    void testGetFileType() {
        assertEquals("ebe", FileManager.getFileType(Path.of("test.ebe")));
        assertEquals("litematic", FileManager.getFileType(Path.of("castle.litematic")));
        assertEquals("sponge", FileManager.getFileType(Path.of("house.schem")));
        assertEquals("vanilla", FileManager.getFileType(Path.of("struct.nbt")));
        assertEquals("schematica", FileManager.getFileType(Path.of("old.schematic")));
        assertEquals("unknown", FileManager.getFileType(Path.of("readme.txt")));
    }

    @Test
    void testEnsureDir(@TempDir Path dir) throws IOException {
        var sub = dir.resolve("sub" ).resolve("dir");
        var fm = new FileManager(sub.toString());
        fm.ensureDir();
        assertTrue(Files.isDirectory(sub));
    }

    @Test
    void testResolve() {
        var fm = new FileManager("config/ebe/client/schematics");
        assertEquals(Path.of("config/ebe/client/schematics/test.ebe"), fm.resolve("test.ebe"));
    }
}
