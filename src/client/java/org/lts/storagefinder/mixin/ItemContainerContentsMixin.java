package org.lts.storagefinder.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.lts.storagefinder.tooltip.ContainerPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin {
    @Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
    private void storagefinder$replaceListWithPreview(Item.TooltipContext context, Consumer<Component> output,
                                                      TooltipFlag flag, DataComponentGetter components,
                                                      CallbackInfo ci) {
        if (ContainerPreview.shiftDown() && ContainerPreview.enabled()
                && ContainerPreview.hasItems((ItemContainerContents) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "addToTooltip", at = @At("TAIL"))
    private void storagefinder$addPreviewHint(Item.TooltipContext context, Consumer<Component> output,
                                              TooltipFlag flag, DataComponentGetter components, CallbackInfo ci) {
        if (!ContainerPreview.shiftDown() && ContainerPreview.enabled()
                && ContainerPreview.hasItems((ItemContainerContents) (Object) this)) {
            output.accept(Component.translatable("storagefinder.preview.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
