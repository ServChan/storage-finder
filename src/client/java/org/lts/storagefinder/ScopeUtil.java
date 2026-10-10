package org.lts.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class ScopeUtil {
    private ScopeUtil() {
    }

    public static String current(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "";
        }
        return server(minecraft) + "|" + minecraft.level.dimension().identifier() + WorldScopeTracker.seedSuffix();
    }

    public static String ender(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return "";
        }
        return server(minecraft) + "|ender:" + minecraft.player.getUUID();
    }

    private static String server(Minecraft minecraft) {
        ServerData data = minecraft.getCurrentServer();
        if (data != null) {
            return data.ip;
        }
        if (minecraft.getSingleplayerServer() != null) {
            return "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        }
        return "local";
    }
}
