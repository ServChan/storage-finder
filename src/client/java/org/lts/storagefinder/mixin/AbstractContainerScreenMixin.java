package org.lts.storagefinder.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.lts.storagefinder.StorageFinderClient;
import org.lts.storagefinder.tooltip.ContainerPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void storagefinder$saveBeforeRemoval(CallbackInfo ci) {
        StorageFinderClient.onContainerScreenRemoved(
                ((AbstractContainerScreen<?>) (Object) this).getMenu());
    }

    @WrapOperation(method = "extractTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getTooltipImage()Ljava/util/Optional;"))
    private Optional<TooltipComponent> storagefinder$previewContainer(
            ItemStack stack, Operation<Optional<TooltipComponent>> original) {
        Optional<TooltipComponent> preview = ContainerPreview.forStack(stack);
        return preview.isPresent() ? preview : original.call(stack);
    }
}
