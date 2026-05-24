package com.l1ght.ebe.server.placement;

public final class ServerPlacementBudget {
    private static final long SOFT_BUDGET_NANOS = 2_500_000L;
    private static final long HARD_BUDGET_NANOS = 8_000_000L;
    private static final int SCALE_MIN = 125;
    private static final int SCALE_MAX = 1000;
    private static int scale = SCALE_MAX;
    private static int calmTicks = 0;

    private ServerPlacementBudget() {
    }

    public static synchronized int scaledInt(int configured, int min) {
        int safe = Math.max(min, configured);
        return Math.max(min, (safe * scale) / SCALE_MAX);
    }

    public static synchronized void recordWork(long nanos) {
        if (nanos >= HARD_BUDGET_NANOS) {
            scale = Math.max(SCALE_MIN, (scale * 3) / 4);
            calmTicks = 0;
            return;
        }
        if (nanos >= SOFT_BUDGET_NANOS) {
            scale = Math.max(SCALE_MIN, (scale * 9) / 10);
            calmTicks = 0;
            return;
        }
        if (scale < SCALE_MAX && ++calmTicks >= 40) {
            scale = Math.min(SCALE_MAX, scale + 50);
            calmTicks = 0;
        }
    }

    public static synchronized int scalePermille() {
        return scale;
    }
}
