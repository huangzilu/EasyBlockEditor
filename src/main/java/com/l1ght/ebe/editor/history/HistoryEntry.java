package com.l1ght.ebe.editor.history;

public class HistoryEntry {
    private final int id;
    private final HistoryActionType actionType;
    private final Object[][] snapshots;
    private final int primaryX;
    private final int primaryY;
    private final int primaryZ;
    private final Object primaryBlock;
    private final int affectedCount;

    public HistoryEntry(int id, HistoryActionType actionType, Object[][] snapshots,
                        int primaryX, int primaryY, int primaryZ,
                        Object primaryBlock, int affectedCount) {
        this.id = id;
        this.actionType = actionType;
        this.snapshots = snapshots;
        this.primaryX = primaryX;
        this.primaryY = primaryY;
        this.primaryZ = primaryZ;
        this.primaryBlock = primaryBlock;
        this.affectedCount = affectedCount;
    }

    public int getId() { return id; }
    public HistoryActionType getActionType() { return actionType; }
    public Object[][] getSnapshots() { return snapshots; }
    public int getPrimaryX() { return primaryX; }
    public int getPrimaryY() { return primaryY; }
    public int getPrimaryZ() { return primaryZ; }
    public Object getPrimaryBlock() { return primaryBlock; }
    public int getAffectedCount() { return affectedCount; }
}
