package com.l1ght.ebe.editor.history;

import com.l1ght.ebe.data.BuildingModel;

public class HistoryEntry {
    private final int id;
    private final HistoryActionType actionType;
    private final Object[][] snapshots;
    private final BuildingModel.LayerState beforeLayerState;
    private final BuildingModel.LayerState afterLayerState;
    private final int primaryX;
    private final int primaryY;
    private final int primaryZ;
    private final Object primaryBlock;
    private final int affectedCount;
    private final long timestamp;

    public HistoryEntry(int id, HistoryActionType actionType, Object[][] snapshots,
                        int primaryX, int primaryY, int primaryZ,
                        Object primaryBlock, int affectedCount) {
        this(id, actionType, snapshots, primaryX, primaryY, primaryZ, primaryBlock, affectedCount, System.currentTimeMillis());
    }

    public HistoryEntry(int id, HistoryActionType actionType, Object[][] snapshots,
                        int primaryX, int primaryY, int primaryZ,
                        Object primaryBlock, int affectedCount, long timestamp) {
        this(id, actionType, snapshots, null, null, primaryX, primaryY, primaryZ, primaryBlock, affectedCount, timestamp);
    }

    public HistoryEntry(int id, HistoryActionType actionType,
                        BuildingModel.LayerState beforeLayerState,
                        BuildingModel.LayerState afterLayerState,
                        Object primaryBlock, int affectedCount) {
        this(id, actionType, new Object[0][], beforeLayerState, afterLayerState,
                0, 0, 0, primaryBlock, affectedCount, System.currentTimeMillis());
    }

    public HistoryEntry(int id, HistoryActionType actionType, Object[][] snapshots,
                        BuildingModel.LayerState beforeLayerState,
                        BuildingModel.LayerState afterLayerState,
                        int primaryX, int primaryY, int primaryZ,
                        Object primaryBlock, int affectedCount, long timestamp) {
        this.id = id;
        this.actionType = actionType;
        this.snapshots = snapshots != null ? snapshots : new Object[0][];
        this.beforeLayerState = beforeLayerState;
        this.afterLayerState = afterLayerState;
        this.primaryX = primaryX;
        this.primaryY = primaryY;
        this.primaryZ = primaryZ;
        this.primaryBlock = primaryBlock;
        this.affectedCount = affectedCount;
        this.timestamp = timestamp;
    }

    public int getId() { return id; }
    public HistoryActionType getActionType() { return actionType; }
    public Object[][] getSnapshots() { return snapshots; }
    public boolean isLayerChange() { return beforeLayerState != null && afterLayerState != null; }
    public BuildingModel.LayerState getBeforeLayerState() { return beforeLayerState; }
    public BuildingModel.LayerState getAfterLayerState() { return afterLayerState; }
    public int getPrimaryX() { return primaryX; }
    public int getPrimaryY() { return primaryY; }
    public int getPrimaryZ() { return primaryZ; }
    public Object getPrimaryBlock() { return primaryBlock; }
    public int getAffectedCount() { return affectedCount; }
    public long getTimestamp() { return timestamp; }
}
