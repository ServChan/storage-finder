package org.lts.storagefinder.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.lts.storagefinder.StorageFinderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void storagefinder$captureStorageInteraction(LocalPlayer player, InteractionHand hand,
                                                          BlockHitResult hitResult,
                                                          CallbackInfoReturnable<?> cir) {
        StorageFinderClient.onStorageInteracted(hitResult.getBlockPos());
    }
}
