package org.lts.storagefinder.tooltip;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.lts.storagefinder.MinecraftScreenAccess;
import org.lts.storagefinder.StorageFinderConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ContainerPreview {
    public static final int COLUMNS = 9;
    private static final int MIN_SLOTS = 27;
    private static final int MAX_ROWS = 6;
    private static final int PLAIN_SHULKER_ACCENT = 0xFF8E6E9E;
    private static final int NEUTRAL_ACCENT = 0xFF8B8B8B;

    private ContainerPreview() {
    }

    public static boolean enabled() {
        return StorageFinderConfig.current().shulkerPreview
                && MinecraftScreenAccess.getScreen(Minecraft.getInstance()) instanceof AbstractContainerScreen<?>;
    }

    public static boolean shiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    public static boolean active() {
        return enabled() && shiftDown();
    }

    public static boolean hasItems(ItemContainerContents contents) {
        return contents != null && contents.nonEmptyItems().iterator().hasNext();
    }

    public static Optional<TooltipComponent> forStack(ItemStack stack) {
        if (stack.isEmpty() || !active()) {
            return Optional.empty();
        }
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (!hasItems(contents)) {
            return Optional.empty();
        }
        List<ItemStack> all = contents.itemCopies().toList();
        int totalSlots = Math.max(MIN_SLOTS, roundUp(all.size()));
        int shownSlots = Math.min(totalSlots, COLUMNS * MAX_ROWS);
        List<ItemStack> slots = new ArrayList<>(shownSlots);
        int used = 0;
        long itemCount = 0L;
        int hidden = 0;
        for (int index = 0; index < totalSlots; index++) {
            ItemStack item = index < all.size() ? all.get(index) : ItemStack.EMPTY;
            if (!item.isEmpty()) {
                used++;
                itemCount += item.getCount();
                if (index >= shownSlots) {
                    hidden++;
                }
            }
            if (index < shownSlots) {
                slots.add(item);
            }
        }
        return Optional.of(new ContainerPreviewTooltip(List.copyOf(slots), COLUMNS, accent(stack), used,
                totalSlots, itemCount, hidden));
    }

    private static int roundUp(int slots) {
        return (slots + COLUMNS - 1) / COLUMNS * COLUMNS;
    }

    private static int accent(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock shulker) {
            DyeColor color = shulker.getColor();
            return color == null ? PLAIN_SHULKER_ACCENT : 0xFF000000 | color.getTextureDiffuseColor();
        }
        return NEUTRAL_ACCENT;
    }
}
