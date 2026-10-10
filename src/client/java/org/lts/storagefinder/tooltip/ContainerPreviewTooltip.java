package org.lts.storagefinder.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ContainerPreviewTooltip(List<ItemStack> slots, int columns, int accent, int usedSlots,
                                      int totalSlots, long itemCount, int hiddenSlots) implements TooltipComponent {
    public int rows() {
        return (slots.size() + columns - 1) / columns;
    }
}
