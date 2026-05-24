package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.data.BuildingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionLoadProfileM6Test {

    @TempDir
    Path tempDir;

    @Test
    void classifiesM6FileSizeThresholdsWithoutMinecraftRuntime() throws Exception {
        BuildingModel model = new BuildingModel();
        model.addRegion(16, 16, 16);

        assertEquals(ProjectionLoadProfile.Risk.NORMAL,
                ProjectionLoadProfile.fromModel(sizedFile("normal.litematic", 200 * 1024), model).risk());
        assertEquals(ProjectionLoadProfile.Risk.LARGE,
                ProjectionLoadProfile.fromModel(sizedFile("large.litematic", 512 * 1024), model).risk());
        assertEquals(ProjectionLoadProfile.Risk.HUGE,
                ProjectionLoadProfile.fromModel(sizedFile("huge.litematic", 3 * 1024 * 1024), model).risk());
    }

    @Test
    void classifiesHugeProjectionByVolumeEvenWhenFileIsSmall() throws Exception {
        BuildingModel model = new BuildingModel();
        model.addRegion("huge-volume", 0, 0, 0, 256, 96, 256);

        ProjectionLoadProfile profile = ProjectionLoadProfile.fromModel(
                sizedFile("small-header.litematic", 64 * 1024),
                model
        );

        assertEquals(ProjectionLoadProfile.Risk.HUGE, profile.risk());
        assertTrue(profile.shouldPreferProgressiveViewport());
    }

    private Path sizedFile(String name, int bytes) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, new byte[bytes]);
        return file;
    }
}
