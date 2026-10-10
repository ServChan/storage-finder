package org.lts.storagefinder.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class ClientContainerPreviewTooltip implements ClientTooltipComponent {
    private static final int SLOT = 18;
    private static final int PADDING = 4;
    private static final int ACCENT_HEIGHT = 3;
    private static final int TOP_GAP = 3;
    private static final int FOOTER_HEIGHT = 11;
    private static final int PANEL_SHADOW = 0xFF555555;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    private static final int SLOT_SHADOW = 0xFF373737;
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int FOOTER_TEXT = 0xFFAAAAAA;
    private static final int MORE_TEXT = 0xFFFFD970;

    private final ContainerPreviewTooltip preview;

    public ClientContainerPreviewTooltip(ContainerPreviewTooltip preview) {
        this.preview = preview;
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(panelWidth(), font.width(footer()));
    }

    @Override
    public int getHeight(Font font) {
        return TOP_GAP + panelHeight() + FOOTER_HEIGHT;
    }

    @Override
    public void extractImage(Font font, int x, int y, int tooltipWidth, int tooltipHeight,
                             GuiGraphicsExtractor graphics) {
        int panelX = x;
        int panelY = y + TOP_GAP;
        int width = panelWidth();
        int height = panelHeight();
        graphics.fill(panelX, panelY, panelX + width, panelY + height, PANEL_SHADOW);
        graphics.fill(panelX + 1, panelY + 1, panelX + width - 1, panelY + height - 1, PANEL);
        graphics.fill(panelX + 1, panelY + 1, panelX + width - 1, panelY + 2, PANEL_HIGHLIGHT);
        graphics.fill(panelX + 1, panelY + 1, panelX + 2, panelY + height - 1, PANEL_HIGHLIGHT);
        graphics.fill(panelX + 1, panelY + 1, panelX + width - 1, panelY + 1 + ACCENT_HEIGHT, preview.accent());

        int gridX = panelX + PADDING;
        int gridY = panelY + PADDING + ACCENT_HEIGHT;
        for (int index = 0; index < preview.slots().size(); index++) {
            int slotX = gridX + index % preview.columns() * SLOT;
            int slotY = gridY + index / preview.columns() * SLOT;
            drawSlot(graphics, slotX, slotY);
            ItemStack stack = preview.slots().get(index);
            if (!stack.isEmpty()) {
                graphics.item(stack, slotX + 1, slotY + 1);
                graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
            }
        }

        int footerY = panelY + height + 2;
        graphics.text(font, footer(), x, footerY, preview.hiddenSlots() > 0 ? MORE_TEXT : FOOTER_TEXT, true);
    }

    private Component footer() {
        if (preview.hiddenSlots() > 0) {
            return Component.translatable("storagefinder.preview.summary_more", preview.usedSlots(),
                    preview.totalSlots(), preview.itemCount(), preview.hiddenSlots());
        }
        return Component.translatable("storagefinder.preview.summary", preview.usedSlots(),
                preview.totalSlots(), preview.itemCount());
    }

    private int panelWidth() {
        return preview.columns() * SLOT + PADDING * 2;
    }

    private int panelHeight() {
        return preview.rows() * SLOT + PADDING * 2 + ACCENT_HEIGHT;
    }

    private static void drawSlot(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT, y + SLOT, SLOT_HIGHLIGHT);
        graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_FILL);
    }
}
