package org.lts.storagefinder.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import org.lts.storagefinder.StorageFinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void storagefinder$saveBeforeRemoval(CallbackInfo ci) {
        if (((AbstractContainerScreen<?>) (Object) this).getMenu() instanceof ChestMenu chestMenu) {
            StorageFinderClient.onContainerScreenRemoved(chestMenu);
        }
    }
}
