package org.lts.storagefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StorageHudRenderer {
    private static final int MAX_VISIBLE = 6;
    private static final int NORMAL_PANEL_WIDTH = 238;
    private static final int PRICE_PANEL_WIDTH = 270;
    private static final int NORMAL_ROW_HEIGHT = 21;
    private static final int PRICE_ROW_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 14;
    private static final int MARGIN = 6;
    private static final int BACKGROUND = 0xB812161D;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int STATUS = 0xFFB5BEC9;
    private static final int PRICE = 0xFF7FE2A7;

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
        boolean leftAlt = InputConstants.isKeyDown(minecraft.getWindow(), InputConstants.KEY_LALT);
        boolean bridgeAvailable = EconomyPriceBridge.isAvailable();
        boolean priceMode = leftAlt && bridgeAvailable;
        boolean footerVisible = bridgeAvailable
                || leftAlt && EconomyPriceBridge.isEconomyInstalled();
        int panelWidth = priceMode ? PRICE_PANEL_WIDTH : NORMAL_PANEL_WIDTH;
        int rowHeight = priceMode ? PRICE_ROW_HEIGHT : NORMAL_ROW_HEIGHT;
        int visible = Math.min(MAX_VISIBLE, queries.size());
        int extraRows = queries.size() > visible ? 1 : 0;
        int panelHeight = visible * rowHeight + extraRows * 14
                + (footerVisible ? FOOTER_HEIGHT : 0);
        boolean right = config.hudAnchor.endsWith("RIGHT");
        boolean bottom = config.hudAnchor.startsWith("BOTTOM");
        int x = right
                ? minecraft.getWindow().getGuiScaledWidth() - MARGIN - config.hudOffsetX - panelWidth
                : MARGIN + config.hudOffsetX;
        int y = bottom
                ? minecraft.getWindow().getGuiScaledHeight() - MARGIN - config.hudOffsetY - panelHeight
                : MARGIN + config.hudOffsetY;
        x = Math.max(0, Math.min(x, minecraft.getWindow().getGuiScaledWidth() - panelWidth));
        y = Math.max(0, Math.min(y, minecraft.getWindow().getGuiScaledHeight() - panelHeight));

        for (int index = 0; index < visible; index++) {
            SearchSelection.Query query = queries.get(index);
            int rowY = y + index * rowHeight;
            StorageLocator.QueryStats queryStats = stats.getOrDefault(
                    query.color(), new StorageLocator.QueryStats(0, 0, false, 0));
            Component status = statusComponent(config, queryStats);
            int statusWidth = minecraft.font.width(status);
            int nameWidth = Math.max(30, panelWidth - 34 - statusWidth);
            String name = trimToWidth(minecraft, query.displayName().getString(), nameWidth);

            graphics.fill(x, rowY, x + panelWidth, rowY + rowHeight - 1, BACKGROUND);
            graphics.fill(x, rowY, x + 3, rowY + rowHeight - 1, query.color());
            graphics.item(query.stack(), x + 6, rowY + 2);
            int firstLineY = priceMode ? rowY + 4 : rowY + 6;
            graphics.text(minecraft.font, Component.literal(name), x + 27, firstLineY, TEXT, true);
            graphics.text(minecraft.font, status, x + panelWidth - statusWidth - 5,
                    firstLineY, STATUS, true);
            if (priceMode) {
                Component price = priceComponent(query);
                String priceText = trimToWidth(minecraft, price.getString(), panelWidth - 34);
                graphics.text(minecraft.font, Component.literal(priceText),
                        x + 27, rowY + 18, PRICE, true);
            }
        }

        int nextY = y + visible * rowHeight;
        if (queries.size() > visible) {
            int moreY = nextY;
            graphics.fill(x, moreY, x + panelWidth, moreY + 13, BACKGROUND);
            graphics.text(minecraft.font, Component.translatable(
                    "storagefinder.hud.more", queries.size() - visible), x + 6, moreY + 2, STATUS, true);
            nextY += 14;
        }
        if (footerVisible) {
            Component footer = bridgeAvailable
                    ? Component.translatable(priceMode
                    ? "storagefinder.hud.economy_context"
                    : "storagefinder.hud.economy_hint")
                    : Component.translatable("storagefinder.hud.economy_unavailable");
            graphics.fill(x, nextY, x + panelWidth, nextY + FOOTER_HEIGHT - 1, BACKGROUND);
            String footerText = trimToWidth(minecraft, footer.getString(), panelWidth - 12);
            graphics.text(minecraft.font, Component.literal(footerText),
                    x + 6, nextY + 2, STATUS, true);
        }
    }

    private static Component priceComponent(SearchSelection.Query query) {
        Optional<EconomyPriceBridge.PriceSummary> summary =
                EconomyPriceBridge.lookup(SearchSelection.itemIds(query.stack()));
        if (summary.isEmpty()) {
            return Component.translatable("storagefinder.hud.economy_no_data");
        }
        EconomyPriceBridge.PriceSummary price = summary.get();
        return Component.translatable("storagefinder.hud.economy_price",
                decimal(price.minimumUnitPrice()), decimal(price.averageUnitPrice()),
                price.currency(), price.offers());
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

    private static String decimal(double value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.2f", value);
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
