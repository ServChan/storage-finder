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

        String server;
        ServerData data = minecraft.getCurrentServer();
        if (data != null) {
            server = data.ip;
        } else if (minecraft.getSingleplayerServer() != null) {
            server = "singleplayer:" + minecraft.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            server = "local";
        }
        return server + "|" + minecraft.level.dimension().identifier() + WorldScopeTracker.seedSuffix();
    }
}
