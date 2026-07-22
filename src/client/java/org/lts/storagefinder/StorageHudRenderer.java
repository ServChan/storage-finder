package org.lts.storagefinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

public final class StorageHudRenderer {
    private static final int MAX_VISIBLE = 6;
    private static final int PANEL_WIDTH = 238;
    private static final int ROW_HEIGHT = 21;
    private static final int MARGIN = 6;
    private static final int BACKGROUND = 0xB812161D;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int STATUS = 0xFFB5BEC9;

    private StorageHudRenderer() {
    }

    public static void render(Minecraft minecraft, GuiGraphicsExtractor graphics) {
        StorageFinderConfig config = StorageFinderConfig.current();
        if (minecraft == null || minecraft.player == null || minecraft.level == null
                || MinecraftUiAccess.isHudHidden(minecraft) || MinecraftScreenAccess.getScreen(minecraft) != null || !config.enabled) {
            return;
        }

        List<SearchSelection.Query> queries = StorageFinderClient.activeQueries();
        if (queries.isEmpty()) {
            return;
        }
        Map<Integer, StorageLocator.QueryStats> stats = StorageFinderClient.queryStats();
        int visible = Math.min(MAX_VISIBLE, queries.size());
        int extraRows = queries.size() > visible ? 1 : 0;
        int panelHeight = visible * ROW_HEIGHT + extraRows * 14;
        boolean right = config.hudAnchor.endsWith("RIGHT");
        boolean bottom = config.hudAnchor.startsWith("BOTTOM");
        int x = right
                ? minecraft.getWindow().getGuiScaledWidth() - MARGIN - config.hudOffsetX - PANEL_WIDTH
                : MARGIN + config.hudOffsetX;
        int y = bottom
                ? minecraft.getWindow().getGuiScaledHeight() - MARGIN - config.hudOffsetY - panelHeight
                : MARGIN + config.hudOffsetY;
        x = Math.max(0, Math.min(x, minecraft.getWindow().getGuiScaledWidth() - PANEL_WIDTH));
        y = Math.max(0, Math.min(y, minecraft.getWindow().getGuiScaledHeight() - panelHeight));

        for (int index = 0; index < visible; index++) {
            SearchSelection.Query query = queries.get(index);
            int rowY = y + index * ROW_HEIGHT;
            StorageLocator.QueryStats queryStats = stats.getOrDefault(
                    query.color(), new StorageLocator.QueryStats(0, 0, false, 0));
            Component status = statusComponent(config, queryStats);
            int statusWidth = minecraft.font.width(status);
            int nameWidth = Math.max(30, PANEL_WIDTH - 34 - statusWidth);
            String name = trimToWidth(minecraft, query.displayName().getString(), nameWidth);

            graphics.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW_HEIGHT - 1, BACKGROUND);
            graphics.fill(x, rowY, x + 3, rowY + ROW_HEIGHT - 1, query.color());
            graphics.item(query.stack(), x + 6, rowY + 2);
            graphics.text(minecraft.font, Component.literal(name), x + 27, rowY + 6, TEXT, true);
            graphics.text(minecraft.font, status, x + PANEL_WIDTH - statusWidth - 5,
                    rowY + 6, STATUS, true);
        }

        if (queries.size() > visible) {
            int moreY = y + visible * ROW_HEIGHT;
            graphics.fill(x, moreY, x + PANEL_WIDTH, moreY + 13, BACKGROUND);
            graphics.text(minecraft.font, Component.translatable(
                    "storagefinder.hud.more", queries.size() - visible), x + 6, moreY + 2, STATUS, true);
        }
    }

    private static Component statusComponent(StorageFinderConfig config, StorageLocator.QueryStats stats) {
        if (stats.matches() == 0) {
            return Component.translatable("storagefinder.hud.none");
        }
        Component amount = stats.itemCount() > 0
                ? Component.translatable("storagefinder.hud.amount", stats.itemCount(), stats.matches())
                : Component.translatable("storagefinder.hud.matches", stats.matches());
        if (!config.routeEnabled) {
            return amount;
        }
        if (stats.searching()) {
            return Component.translatable("storagefinder.hud.calculating", amount);
        }
        if (stats.routes() == 0) {
            return Component.translatable("storagefinder.hud.unreachable", amount);
        }
        return Component.translatable("storagefinder.hud.ready", amount, stats.routes());
    }

    private static String trimToWidth(Minecraft minecraft, String text, int maxWidth) {
        if (minecraft.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && minecraft.font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
