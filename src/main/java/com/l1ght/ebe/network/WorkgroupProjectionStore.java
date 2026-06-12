package com.l1ght.ebe.network;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkgroupProjectionStore {
    public record StoredBlock(BlockPos pos, int stateId, String nbt) {
        public StoredBlock {
            pos = pos == null ? BlockPos.ZERO : pos.immutable();
            nbt = nbt == null ? "" : nbt;
        }
    }

    public record StoredProjection(UUID projectionId, String fileName, BlockPos origin, boolean visible,
                                   List<StoredBlock> blocks) {
        public StoredProjection {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    private static final Map<UUID, Map<UUID, StoredProjection>> GROUPS = new HashMap<>();

    private WorkgroupProjectionStore() {
    }

    public static synchronized void store(UUID groupId, UUID projectionId, String fileName, BlockPos origin,
                                          boolean visible, List<StoredBlock> blocks) {
        if (groupId == null || projectionId == null) return;
        GROUPS.computeIfAbsent(groupId, ignored -> new HashMap<>())
                .put(projectionId, new StoredProjection(projectionId, fileName, origin, visible, new ArrayList<>(blocks)));
    }

    public static synchronized StoredProjection get(UUID groupId, UUID projectionId) {
        Map<UUID, StoredProjection> group = GROUPS.get(groupId);
        return group == null ? null : group.get(projectionId);
    }

    public static synchronized void remove(UUID groupId, UUID projectionId) {
        Map<UUID, StoredProjection> group = GROUPS.get(groupId);
        if (group != null) {
            group.remove(projectionId);
            if (group.isEmpty()) GROUPS.remove(groupId);
        }
    }

    public static synchronized void removeGroup(UUID groupId) {
        GROUPS.remove(groupId);
    }
}
