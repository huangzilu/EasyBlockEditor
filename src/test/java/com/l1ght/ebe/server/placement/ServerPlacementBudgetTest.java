package com.l1ght.ebe.server.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerPlacementBudgetTest {

    @Test
    void scalesDownAfterHardBudgetAndRecoversAfterCalmTicks() {
        int initial = ServerPlacementBudget.scalePermille();
        assertTrue(initial > 0);

        ServerPlacementBudget.recordWork(10_000_000L);
        int reduced = ServerPlacementBudget.scalePermille();
        assertTrue(reduced < initial);
        assertTrue(ServerPlacementBudget.scaledInt(64, 1) >= 1);

        for (int i = 0; i < 80; i++) {
            ServerPlacementBudget.recordWork(100_000L);
        }

        assertTrue(ServerPlacementBudget.scalePermille() > reduced);
    }
}
