package com.l1ght.ebe.client;

import com.l1ght.ebe.EBEMod;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
public final class SplashState {
    private static final Path FLAG_FILE = Path.of("config", "ebe", "client", "splash.flag");

    private static boolean seenThisSession = false;
    private static Boolean seenEverCache = null;

    private SplashState() {}

    private static Path flagPath() {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.gameDirectory != null) {
            return mc.gameDirectory.toPath().resolve(FLAG_FILE);
        }
        return FLAG_FILE;
    }

    public static boolean seenEver() {
        if (seenEverCache == null) {
            seenEverCache = Files.exists(flagPath());
        }
        return seenEverCache;
    }

    public static boolean seenThisSession() {
        return seenThisSession;
    }

    public static boolean shouldPlay(String mode) {
        if (mode == null) mode = "first_ever";
        return switch (mode) {
            case "off" -> false;
            case "always" -> true;
            case "per_session" -> !seenThisSession;
            default -> !seenEver();
        };
    }

    public static void markPlayed() {
        seenThisSession = true;
        if (seenEverCache == null || !seenEverCache) {
            try {
                Path p = flagPath();
                Files.createDirectories(p.getParent());
                Files.writeString(p, "1", StandardCharsets.UTF_8);
            } catch (Exception e) {
                EBEMod.LOGGER.warn("Failed to write EBE splash flag", e);
            }
            seenEverCache = Boolean.TRUE;
        }
    }
}
