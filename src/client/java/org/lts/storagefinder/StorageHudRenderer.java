package org.lts.storagefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class StorageHudRenderer {
    public static final int MARGIN = 6;
    private static final int NORMAL_PANEL_WIDTH = 238;
    private static final int COMPACT_PANEL_WIDTH = 190;
    private static final int PRICE_PANEL_WIDTH = 270;
    private static final int NORMAL_ROW_HEIGHT = 23;
    private static final int COMPACT_ROW_HEIGHT = 18;
    private static final int PRICE_ROW_HEIGHT = 33;
    private static final int MORE_ROW_HEIGHT = 14;
    private static final int FOOTER_HEIGHT = 14;
    private static final int TEXT_LEFT = 26;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int MUTED = 0xFFB5BEC9;
    private static final int READY = 0xFF8EE6B0;
    private static final int WORKING = 0xFFFFD970;
    private static final int PROBLEM = 0xFFFF9A8F;
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
        boolean leftAlt = InputConstants.isKeyDown(InputConstants.KEY_LALT);
        boolean bridgeAvailable = EconomyPriceBridge.isAvailable();
        boolean priceMode = leftAlt && bridgeAvailable;
        Component footer = null;
        if (bridgeAvailable) {
            footer = Component.translatable(priceMode
                    ? "storagefinder.hud.economy_context"
                    : "storagefinder.hud.economy_hint");
        } else if (leftAlt && EconomyPriceBridge.isEconomyInstalled()) {
            footer = Component.translatable("storagefinder.hud.economy_unavailable");
        }

        List<Row> rows = new ArrayList<>(queries.size());
        for (SearchSelection.Query query : queries) {
            rows.add(new Row(query.stack(), query.displayName(), query.color(),
                    stats.getOrDefault(query.color(), StorageLocator.QueryStats.EMPTY),
                    priceMode ? priceComponent(query) : null));
        }
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        drawPanel(minecraft.font, graphics, config, rows, priceMode, footer, screenWidth, screenHeight);
    }

    public static Bounds renderPreview(Minecraft minecraft, GuiGraphicsExtractor graphics,
                                       StorageFinderConfig config, int screenWidth, int screenHeight) {
        return drawPanel(minecraft.font, graphics, config, previewRows(), false, null,
                screenWidth, screenHeight);
    }

    public static Bounds previewBounds(StorageFinderConfig config, int screenWidth, int screenHeight) {
        int width = panelWidth(config, false);
        int height = panelHeight(config, previewRows().size(), 0, false, false);
        return place(config, width, height, screenWidth, screenHeight);
    }

    public static Bounds place(StorageFinderConfig config, int panelWidth, int panelHeight,
                               int screenWidth, int screenHeight) {
        boolean right = config.hudAnchor.endsWith("RIGHT");
        boolean bottom = config.hudAnchor.startsWith("BOTTOM");
        int x = right ? screenWidth - MARGIN - config.hudOffsetX - panelWidth : MARGIN + config.hudOffsetX;
        int y = bottom ? screenHeight - MARGIN - config.hudOffsetY - panelHeight : MARGIN + config.hudOffsetY;
        x = Math.max(0, Math.min(x, screenWidth - panelWidth));
        y = Math.max(0, Math.min(y, screenHeight - panelHeight));
        return new Bounds(x, y, panelWidth, panelHeight);
    }

    private static Bounds drawPanel(Font font, GuiGraphicsExtractor graphics, StorageFinderConfig config,
                                    List<Row> rows, boolean priceMode, Component footer,
                                    int screenWidth, int screenHeight) {
        int visible = Math.min(config.hudMaxVisible, rows.size());
        int hidden = rows.size() - visible;
        int panelWidth = panelWidth(config, priceMode);
        int rowHeight = rowHeight(config, priceMode);
        int panelHeight = panelHeight(config, visible, hidden, priceMode, footer != null);
        Bounds bounds = place(config, panelWidth, panelHeight, screenWidth, screenHeight);
        int x = bounds.x();
        int y = bounds.y();
        int background = background(config);

        for (int index = 0; index < visible; index++) {
            Row row = rows.get(index);
            int rowY = y + index * rowHeight;
            graphics.fill(x, rowY, x + panelWidth, rowY + rowHeight - 1, background);
            graphics.fill(x, rowY, x + 3, rowY + rowHeight - 1, row.color());
            if (config.hudCompact && !priceMode) {
                drawCompactRow(font, graphics, config, row, x, rowY, panelWidth);
            } else {
                drawFullRow(font, graphics, config, row, x, rowY, panelWidth, priceMode);
            }
        }

        int nextY = y + visible * rowHeight;
        if (hidden > 0) {
            graphics.fill(x, nextY, x + panelWidth, nextY + MORE_ROW_HEIGHT - 1, background);
            graphics.text(font, Component.translatable("storagefinder.hud.more", hidden),
                    x + 6, nextY + 3, MUTED, true);
            nextY += MORE_ROW_HEIGHT;
        }
        if (footer != null) {
            graphics.fill(x, nextY, x + panelWidth, nextY + FOOTER_HEIGHT - 1, background);
            graphics.text(font, trimToWidth(font, footer.getString(), panelWidth - 12),
                    x + 6, nextY + 3, MUTED, true);
        }
        return bounds;
    }

    private static void drawFullRow(Font font, GuiGraphicsExtractor graphics, StorageFinderConfig config,
                                    Row row, int x, int rowY, int panelWidth, boolean priceMode) {
        graphics.item(row.stack(), x + 6, rowY + (priceMode ? 8 : 3));
        String distance = distanceText(config, row.stats());
        int distanceWidth = distance == null ? 0 : font.width(distance) + 6;
        int textWidth = panelWidth - TEXT_LEFT - 6;
        String name = trimToWidth(font, row.name().getString(), textWidth - distanceWidth);
        graphics.text(font, name, x + TEXT_LEFT, rowY + 3, TEXT, true);
        if (distance != null) {
            graphics.text(font, distance, x + panelWidth - 6 - font.width(distance), rowY + 3, MUTED, true);
        }
        Status status = status(config, row.stats());
        graphics.text(font, trimToWidth(font, status.text().getString(), textWidth),
                x + TEXT_LEFT, rowY + 13, status.color(), true);
        if (priceMode && row.price() != null) {
            graphics.text(font, trimToWidth(font, row.price().getString(), textWidth),
                    x + TEXT_LEFT, rowY + 23, PRICE, true);
        }
    }

    private static void drawCompactRow(Font font, GuiGraphicsExtractor graphics, StorageFinderConfig config,
                                       Row row, int x, int rowY, int panelWidth) {
        graphics.item(row.stack(), x + 6, rowY + 1);
        Status status = compactStatus(config, row.stats());
        int textWidth = panelWidth - TEXT_LEFT - 6;
        String statusText = trimToWidth(font, status.text().getString(), textWidth * 2 / 5);
        int statusWidth = font.width(statusText);
        String name = trimToWidth(font, row.name().getString(), textWidth - statusWidth - 6);
        graphics.text(font, name, x + TEXT_LEFT, rowY + 5, TEXT, true);
        graphics.text(font, statusText, x + panelWidth - 6 - statusWidth, rowY + 5, status.color(), true);
    }

    private static int panelWidth(StorageFinderConfig config, boolean priceMode) {
        return priceMode ? PRICE_PANEL_WIDTH : config.hudCompact ? COMPACT_PANEL_WIDTH : NORMAL_PANEL_WIDTH;
    }

    private static int rowHeight(StorageFinderConfig config, boolean priceMode) {
        return priceMode ? PRICE_ROW_HEIGHT : config.hudCompact ? COMPACT_ROW_HEIGHT : NORMAL_ROW_HEIGHT;
    }

    private static int panelHeight(StorageFinderConfig config, int visible, int hidden,
                                   boolean priceMode, boolean footer) {
        return visible * rowHeight(config, priceMode) + (hidden > 0 ? MORE_ROW_HEIGHT : 0)
                + (footer ? FOOTER_HEIGHT : 0);
    }

    private static List<Row> previewRows() {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack logs = new ItemStack(Items.OAK_LOG);
        return List.of(
                new Row(diamond, diamond.getHoverName(), 0xFF40C4FF,
                        new StorageLocator.QueryStats(2, 2, false, 64, 2, 0, 0, 0, 18.0, 24.0, null), null),
                new Row(logs, logs.getHoverName(), 0xFFFFD740,
                        new StorageLocator.QueryStats(1, 0, true, 12, 0, 1, 0, 0, 41.0, Double.NaN, null), null));
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

    private static String distanceText(StorageFinderConfig config, StorageLocator.QueryStats stats) {
        if (!config.hudShowDistance || stats.matches() == 0) {
            return null;
        }
        double distance = Double.isFinite(stats.routeDistance()) ? stats.routeDistance() : stats.nearestDistance();
        if (!Double.isFinite(distance)) {
            return null;
        }
        return Component.translatable("storagefinder.hud.distance", Math.round(distance)).getString();
    }

    private static Status status(StorageFinderConfig config, StorageLocator.QueryStats stats) {
        if (stats.matches() == 0) {
            return new Status(Component.translatable("storagefinder.hud.none"), MUTED);
        }
        Component amount = stats.itemCount() > 0
                ? Component.translatable("storagefinder.hud.amount", stats.itemCount(), stats.matches())
                : Component.translatable("storagefinder.hud.matches", stats.matches());
        Component text;
        int color;
        if (!config.routeEnabled) {
            text = amount;
            color = MUTED;
        } else if (stats.routes() > 0) {
            text = Component.translatable("storagefinder.hud.ready", amount, stats.routes());
            color = READY;
        } else if (stats.searching()) {
            text = Component.translatable("storagefinder.hud.calculating", amount);
            color = WORKING;
        } else {
            text = stats.failureReason() == null
                    ? Component.translatable("storagefinder.hud.unreachable", amount)
                    : Component.translatable("storagefinder.hud.unreachable_reason", amount,
                    Component.translatable("storagefinder.route_failure."
                            + stats.failureReason().name().toLowerCase(Locale.ROOT)));
            color = PROBLEM;
        }
        int old = stats.aging() + stats.stale();
        if (config.hudShowFreshness && (old > 0 || stats.frameOnly() > 0)) {
            text = text.copy().append(Component.translatable(
                    "storagefinder.hud.data_quality", old, stats.frameOnly()));
        }
        return new Status(text, color);
    }

    private static Status compactStatus(StorageFinderConfig config, StorageLocator.QueryStats stats) {
        if (stats.matches() == 0) {
            return new Status(Component.translatable("storagefinder.hud.none"), MUTED);
        }
        String distance = distanceText(config, stats);
        if (config.routeEnabled && stats.routes() == 0) {
            return stats.searching()
                    ? new Status(Component.translatable("storagefinder.hud.short.routing"), WORKING)
                    : new Status(Component.translatable("storagefinder.hud.short.no_route"), PROBLEM);
        }
        if (distance != null) {
            return new Status(Component.literal(distance), config.routeEnabled ? READY : MUTED);
        }
        return new Status(Component.translatable("storagefinder.hud.matches", stats.matches()),
                config.routeEnabled ? READY : MUTED);
    }

    private static int background(StorageFinderConfig config) {
        int alpha = Math.round(config.hudOpacityPercent * 255.0F / 100.0F);
        return alpha << 24 | 0x0012161D;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.2f", value);
    }

    public static String trimToWidth(Font font, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    private record Row(ItemStack stack, Component name, int color,
                       StorageLocator.QueryStats stats, Component price) {
    }

    private record Status(Component text, int color) {
    }
}
