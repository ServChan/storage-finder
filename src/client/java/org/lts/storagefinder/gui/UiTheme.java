package org.lts.storagefinder.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.lts.storagefinder.StorageHudRenderer;

import java.util.List;

final class UiTheme {
    static final int BACKGROUND = 0xF00D141F;
    static final int OVERLAY = 0xD00D141F;
    static final int HEADER = 0xFF182638;
    static final int HEADER_RULE = 0xFF2E435E;
    static final int HEADER_HEIGHT = 34;
    static final int CONTENT_TOP = 42;
    static final int TEXT = 0xFFFFFFFF;
    static final int BODY = 0xFFF2F5F8;
    static final int MUTED = 0xFF9AA4B2;
    static final int ROW = 0xEE1B2430;
    static final int ROW_ALT = 0xEE202B38;
    static final int ROW_HOVER = 0xEE2A3A4D;
    static final int ROW_SELECTED = 0xEE355A7A;
    static final int ROW_ACTIVE = 0xEE24382F;
    static final int ROW_BORDER = 0xFF394554;
    static final int ACCENT = 0xFF70C7FF;
    static final int FOCUS = 0xFFB3E1FF;
    static final int SUCCESS = 0xFF69F0AE;
    static final int WARNING = 0xFFFFD740;
    static final int DANGER = 0xFFFF8A80;
    static final int INFO = 0xFF80D8FF;
    static final int ON = 0xFF55FF55;
    static final int OFF = 0xFFFF6B6B;
    static final int SCROLL_TRACK = 0x66FFFFFF;
    static final int SCROLL_THUMB = 0xFFB5BEC9;
    static final int BUTTON_HEIGHT = 20;
    static final int GAP = 8;

    private UiTheme() {
    }

    static void drawFrame(GuiGraphicsExtractor graphics, Font font, int width, int height,
                          Component title, Component subtitle) {
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, HEADER_HEIGHT - 1, HEADER);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, HEADER_RULE);
        graphics.centeredText(font, title, width / 2, subtitle == null ? 13 : 7, TEXT);
        if (subtitle != null) {
            graphics.centeredText(font, trim(font, subtitle.getString(), width - 16), width / 2, 20, MUTED);
        }
    }

    static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key, Component.translatable(
                value ? "storagefinder.config.on" : "storagefinder.config.off")
                .withStyle(style -> style.withColor(value ? ON : OFF)));
    }

    static Button toggle(int x, int y, int width, String key, BooleanGetter getter, BooleanSetter setter) {
        return Button.builder(toggleLabel(key, getter.get()), button -> {
            setter.set(!getter.get());
            button.setMessage(toggleLabel(key, getter.get()));
        }).bounds(x, y, width, BUTTON_HEIGHT).tooltip(Tooltip.create(
                Component.translatable(key + ".tooltip"))).build();
    }

    static List<Button> stepper(int x, int y, int width, Component label, Runnable decrease,
                                Runnable reset, Runnable increase, Runnable afterChange) {
        int small = 40;
        Button minus = Button.builder(Component.literal("−"), button -> {
            decrease.run();
            afterChange.run();
        }).bounds(x, y, small, BUTTON_HEIGHT).build();
        Button value = Button.builder(label, button -> {
            reset.run();
            afterChange.run();
        }).bounds(x + small + GAP, y, width - small * 2 - GAP * 2, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("storagefinder.config.reset_value"))).build();
        Button plus = Button.builder(Component.literal("+"), button -> {
            increase.run();
            afterChange.run();
        }).bounds(x + width - small, y, small, BUTTON_HEIGHT).build();
        return List.of(minus, value, plus);
    }

    static String trim(Font font, String text, int maxWidth) {
        return StorageHudRenderer.trimToWidth(font, text, maxWidth);
    }

    static void drawScrollbar(GuiGraphicsExtractor graphics, int x, int top, int height,
                              int total, int visible, int offset) {
        if (total <= visible || height <= 0) {
            return;
        }
        graphics.fill(x, top, x + 2, top + height, SCROLL_TRACK);
        int thumbHeight = Math.max(8, height * visible / total);
        int maxOffset = Math.max(1, total - visible);
        int thumbTop = top + (height - thumbHeight) * Math.min(offset, maxOffset) / maxOffset;
        graphics.fill(x, thumbTop, x + 2, thumbTop + thumbHeight, SCROLL_THUMB);
    }

    @FunctionalInterface
    interface BooleanGetter {
        boolean get();
    }

    @FunctionalInterface
    interface BooleanSetter {
        void set(boolean value);
    }
}
