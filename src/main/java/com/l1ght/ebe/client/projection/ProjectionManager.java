package com.l1ght.ebe.client.projection;

import com.l1ght.ebe.config.EBEClientConfig;
import com.l1ght.ebe.data.BuildingModel;
import com.l1ght.ebe.data.io.EBEFormatIO;
import com.l1ght.ebe.network.PlaceBlocksPayload;
import com.l1ght.ebe.projection.ProjectionData;
import com.l1ght.ebe.projection.compute.ComputedProjection;
import com.l1ght.ebe.projection.compute.ProjectionComputePlanner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectionManager {

    private static final Logger LOG = LoggerFactory.getLogger("EBE/Projection");
    private static final Path PERSIST_DIR = Path.of("config", "ebe", "client");
    private static final String PERSIST_STATE_FILE_NAME = "projection_state.properties";
    private static final String PERSIST_MODEL_FILE_NAME = "projection_snapshot.ebe";

    private static ProjectionData activeProjection;
    private static boolean projectionVisible = true;
    private static boolean projectionLoaded = false;
    private static BlockPos projectionOrigin = BlockPos.ZERO;
    private static int progressPlaced = 0;
    private static int progressTotal = 0;
    private static boolean progressAuthoritative = false;
    private static boolean persistentStateLoaded = false;
    private static boolean restoringPersistentState = false;
    private static long lastMissingPersistLogMs = 0L;
    private static final AtomicInteger PROJECTION_COMPUTE_SEQUENCE = new AtomicInteger();

    public static void setProjection(BuildingModel model) {
        setProjection(model, null);
    }

    public static void setProjection(BuildingModel model, ComputedProjection computed) {
        if (model == null) {
            activeProjection = null;
            projectionLoaded = false;
            deletePersistentState();
            return;
        }
        if (computed != null) {
            projectionOrigin = computed.getOrigin();
            activeProjection = new ProjectionData(model, projectionOrigin, computed);
        } else {
            activeProjection = new ProjectionData(model, projectionOrigin);
        }
        projectionLoaded = true;
        savePersistentState(true);
    }

    public static void selectProjection(BuildingModel model) {
        selectProjection(model, null);
    }

    public static void selectProjection(BuildingModel model, ComputedProjection computed) {
        if (model == null) return;
        if (computed != null) {
            projectionOrigin = computed.getOrigin();
            activeProjection = new ProjectionData(model, projectionOrigin, computed);
        } else {
            activeProjection = new ProjectionData(model, projectionOrigin);
        }
        projectionLoaded = false;
        savePersistentState(true);
    }

    public static void loadProjection() {
        if (activeProjection != null) {
            projectionLoaded = true;
            savePersistentState(false);
        }
    }

    public static void unloadProjection() {
        projectionLoaded = false;
        savePersistentState(false);
    }

    public static void removeProjection() {
        activeProjection = null;
        projectionLoaded = false;
        deletePersistentState();
    }

    public static boolean hasProjection() {
        return activeProjection != null;
    }

    public static ProjectionData getProjection() {
        return activeProjection;
    }

    public static boolean isProjectionVisible() {
        return projectionVisible && activeProjection != null && projectionLoaded;
    }

    public static boolean isProjectionLoaded() {
        return projectionLoaded;
    }

    public static void setProjectionVisible(boolean visible) {
        projectionVisible = visible;
        savePersistentState(false);
    }

    public static BlockPos getProjectionOrigin() {
        return projectionOrigin;
    }

    public static void setProjectionOrigin(BlockPos origin) {
        if (activeProjection != null) {
            recomputeProjectionAsync(origin, activeProjection.getRotation(), activeProjection.getMirror(),
                    activeProjection.getCenterPoint(), activeProjection.getRotation() != Rotation.NONE || activeProjection.getMirror() != Mirror.NONE);
        } else {
            projectionOrigin = origin;
            savePersistentState(false);
        }
    }

    public static void moveOrigin(int dx, int dy, int dz) {
        setProjectionOrigin(projectionOrigin.offset(dx, dy, dz));
    }

    public static void rotateCounterClockwise90() {
        if (activeProjection == null) return;
        Rotation target = switch (activeProjection.getRotation()) {
            case NONE -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.NONE;
        };
        recomputeProjectionAsync(projectionOrigin, target, activeProjection.getMirror(), activeProjection.getCenterPoint(), true);
    }

    public static void rotateClockwise90() {
        if (activeProjection == null) return;
        Rotation target = switch (activeProjection.getRotation()) {
            case NONE -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_90 -> Rotation.CLOCKWISE_180;
            case CLOCKWISE_180 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.NONE;
        };
        recomputeProjectionAsync(projectionOrigin, target, activeProjection.getMirror(), activeProjection.getCenterPoint(), true);
    }

    public static void rotate180() {
        if (activeProjection == null) return;
        Rotation target = activeProjection.getRotation() == Rotation.NONE ? Rotation.CLOCKWISE_180 :
                activeProjection.getRotation() == Rotation.CLOCKWISE_180 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        recomputeProjectionAsync(projectionOrigin, target, activeProjection.getMirror(), activeProjection.getCenterPoint(), true);
    }

    public static void resetTransform() {
        if (activeProjection == null) return;
        recomputeProjectionAsync(projectionOrigin, Rotation.NONE, Mirror.NONE, activeProjection.getCenterPoint(), true);
    }

    public static void toggleMirrorLeftRight() {
        if (activeProjection == null) return;
        Mirror target = activeProjection.getMirror() == Mirror.LEFT_RIGHT ? Mirror.NONE : Mirror.LEFT_RIGHT;
        recomputeProjectionAsync(projectionOrigin, activeProjection.getRotation(), target, activeProjection.getCenterPoint(), true);
    }

    public static void toggleMirrorFrontBack() {
        if (activeProjection == null) return;
        Mirror target = activeProjection.getMirror() == Mirror.FRONT_BACK ? Mirror.NONE : Mirror.FRONT_BACK;
        recomputeProjectionAsync(projectionOrigin, activeProjection.getRotation(), target, activeProjection.getCenterPoint(), true);
    }

    public static void setProjectionCenter(BlockPos center) {
        if (activeProjection == null) return;
        recomputeProjectionAsync(projectionOrigin, activeProjection.getRotation(), activeProjection.getMirror(), center, true);
    }

    private static void recomputeProjectionAsync(BlockPos origin, Rotation rotation, Mirror mirror, BlockPos center, boolean meshChanged) {
        ProjectionData projection = activeProjection;
        if (projection == null) return;
        int sequence = PROJECTION_COMPUTE_SEQUENCE.incrementAndGet();
        BuildingModel model = projection.getModel();
        String cacheKey = "projection:" + System.identityHashCode(model) + ":" + projection.getMeshVersion() + ":" + projection.getRenderVersion();
        ProjectionComputePlanner.computeAsync(cacheKey, model, origin, rotation, mirror, center, false)
                .whenComplete((computed, error) -> Minecraft.getInstance().execute(() -> {
                    if (sequence != PROJECTION_COMPUTE_SEQUENCE.get() || activeProjection != projection) {
                        return;
                    }
                    if (error != null) {
                        LOG.warn("Failed to recompute projection transform", error);
                        return;
                    }
                    projectionOrigin = origin;
                    projection.applyComputedTransform(computed, origin, rotation, mirror, center, meshChanged);
                    savePersistentState(false);
                }));
    }

    public static void loadPersistentStateIfNeeded() {
        if (persistentStateLoaded) {
            return;
        }
        if (activeProjection != null) {
            persistentStateLoaded = true;
            return;
        }
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }

        Path stateFile = persistStateFile();
        Path modelFile = persistModelFile();
        if (!Files.isRegularFile(stateFile) || !Files.isRegularFile(modelFile)) {
            logMissingPersistentState(stateFile, modelFile);
            return;
        }

        restoringPersistentState = true;
        try (InputStream in = Files.newInputStream(stateFile)) {
            Properties properties = new Properties();
            properties.load(in);

            BuildingModel model = EBEFormatIO.read(modelFile);
            projectionOrigin = new BlockPos(
                    parseInt(properties, "originX", 0),
                    parseInt(properties, "originY", 0),
                    parseInt(properties, "originZ", 0)
            );

            activeProjection = new ProjectionData(model, projectionOrigin);
            activeProjection.setCenterPoint(new BlockPos(
                    parseInt(properties, "centerX", activeProjection.getCenterPoint().getX()),
                    parseInt(properties, "centerY", activeProjection.getCenterPoint().getY()),
                    parseInt(properties, "centerZ", activeProjection.getCenterPoint().getZ())
            ));
            activeProjection.setRotation(parseEnum(Rotation.class, properties.getProperty("rotation"), Rotation.NONE));
            activeProjection.setMirror(parseEnum(Mirror.class, properties.getProperty("mirror"), Mirror.NONE));

            projectionVisible = Boolean.parseBoolean(properties.getProperty("visible", "true"));
            projectionLoaded = Boolean.parseBoolean(properties.getProperty("loaded", "true"));
            persistentStateLoaded = true;
            LOG.info("Restored persisted projection: {} blocks, loaded={}, visible={}, origin={}, stateFile={}, modelFile={}",
                    activeProjection.getBlockCount(),
                    projectionLoaded,
                    projectionVisible,
                    projectionOrigin,
                    stateFile.toAbsolutePath(),
                    modelFile.toAbsolutePath());
        } catch (Exception e) {
            activeProjection = null;
            projectionLoaded = false;
            persistentStateLoaded = false;
            LOG.warn("Failed to restore persisted projection", e);
        } finally {
            restoringPersistentState = false;
        }
    }

    private static void savePersistentState(boolean writeModelSnapshot) {
        if (restoringPersistentState || !persistentStateLoaded && activeProjection == null) {
            return;
        }

        try {
            Path persistDir = persistDir();
            Files.createDirectories(persistDir);
            if (activeProjection == null) {
                deletePersistentState();
                return;
            }

            Path modelFile = persistModelFile();
            if (writeModelSnapshot || !Files.isRegularFile(modelFile)) {
                EBEFormatIO.write(activeProjection.getModel(), modelFile, true);
            }

            Properties properties = new Properties();
            properties.setProperty("loaded", Boolean.toString(projectionLoaded));
            properties.setProperty("visible", Boolean.toString(projectionVisible));
            properties.setProperty("originX", Integer.toString(projectionOrigin.getX()));
            properties.setProperty("originY", Integer.toString(projectionOrigin.getY()));
            properties.setProperty("originZ", Integer.toString(projectionOrigin.getZ()));
            properties.setProperty("rotation", activeProjection.getRotation().name());
            properties.setProperty("mirror", activeProjection.getMirror().name());
            properties.setProperty("centerX", Integer.toString(activeProjection.getCenterPoint().getX()));
            properties.setProperty("centerY", Integer.toString(activeProjection.getCenterPoint().getY()));
            properties.setProperty("centerZ", Integer.toString(activeProjection.getCenterPoint().getZ()));

            try (OutputStream out = Files.newOutputStream(persistStateFile())) {
                properties.store(out, "EasyBlockEditor persisted projection state");
            }
            LOG.debug("Saved persisted projection: writeModelSnapshot={}, blocks={}, stateFile={}, modelFile={}",
                    writeModelSnapshot,
                    activeProjection.getBlockCount(),
                    persistStateFile().toAbsolutePath(),
                    modelFile.toAbsolutePath());
        } catch (Exception e) {
            LOG.warn("Failed to save persisted projection", e);
        }
    }

    private static void deletePersistentState() {
        try {
            Files.deleteIfExists(persistStateFile());
            Files.deleteIfExists(persistModelFile());
        } catch (Exception e) {
            LOG.warn("Failed to delete persisted projection", e);
        }
    }

    private static Path persistDir() {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.gameDirectory != null) {
            return mc.gameDirectory.toPath().resolve(PERSIST_DIR);
        }
        return PERSIST_DIR;
    }

    private static Path persistStateFile() {
        return persistDir().resolve(PERSIST_STATE_FILE_NAME);
    }

    private static Path persistModelFile() {
        return persistDir().resolve(PERSIST_MODEL_FILE_NAME);
    }

    private static void logMissingPersistentState(Path stateFile, Path modelFile) {
        long now = System.currentTimeMillis();
        if (now - lastMissingPersistLogMs < 10_000L) {
            return;
        }
        lastMissingPersistLogMs = now;
        LOG.debug("No persisted projection found yet: stateExists={}, modelExists={}, stateFile={}, modelFile={}",
                Files.isRegularFile(stateFile),
                Files.isRegularFile(modelFile),
                stateFile.toAbsolutePath(),
                modelFile.toAbsolutePath());
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static float getOpacity() {
        return EBEClientConfig.projectionOpacity.get().floatValue();
    }

    public static void setProgress(int placed, int total) {
        progressPlaced = placed;
        progressTotal = total;
        progressAuthoritative = true;
    }

    public static int getProgressPlaced() { return progressPlaced; }
    public static int getProgressTotal() { return progressTotal; }
    public static float getProgressPercent() {
        return progressTotal > 0 ? (float) progressPlaced / progressTotal : 0;
    }

    public static void placeAll() {
        if (activeProjection == null || !projectionLoaded) return;

        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        if (!player.isCreative()) return;

        var blocks = activeProjection.getBlocks();
        if (blocks.isEmpty()) return;

        List<PlaceBlocksPayload.Entry> entries = new ArrayList<>();
        for (var pb : blocks) {
            int stateId = Block.getId(pb.state());
            entries.add(new PlaceBlocksPayload.Entry(pb.pos(), stateId, pb.hasNbt() ? pb.nbt().toString() : ""));
        }

        setProgress(entries.size(), entries.size());
        PacketDistributor.sendToServer(new PlaceBlocksPayload(entries));
    }

    public static void calculateProgress() {
        if (activeProjection == null || !projectionLoaded) {
            progressPlaced = 0;
            progressTotal = 0;
            progressAuthoritative = false;
            return;
        }

        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) return;

        var blocks = activeProjection.getBlocks();
        if (progressAuthoritative && progressTotal == blocks.size() && progressPlaced == progressTotal) {
            return;
        }
        int placed = 0;
        int total = blocks.size();

        for (var pb : blocks) {
            var existing = level.getBlockState(pb.pos());
            if (!existing.isAir() && existing.equals(pb.state()) && blockEntityMatches(pb)) {
                placed++;
            }
        }

        progressPlaced = placed;
        progressTotal = total;
        progressAuthoritative = false;
    }

    private static boolean blockEntityMatches(ProjectionData.ProjectionBlock block) {
        if (!block.hasNbt()) return true;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        var be = mc.level.getBlockEntity(block.pos());
        if (be == null) return false;
        try {
            var existing = be.saveWithId(mc.level.registryAccess());
            cleanPlacementNbt(existing);
            var target = block.nbt().copy();
            cleanPlacementNbt(target);
            return existing.equals(target);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void cleanPlacementNbt(net.minecraft.nbt.CompoundTag tag) {
        tag.remove("x");
        tag.remove("y");
        tag.remove("z");
        tag.remove("id");
    }
}
