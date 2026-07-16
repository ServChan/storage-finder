package org.lts.storagefinder.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.lts.storagefinder.WorldScopeTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void storagefinder$captureLoginWorld(ClientboundLoginPacket packet, CallbackInfo ci) {
        WorldScopeTracker.setSeed(packet.commonPlayerSpawnInfo().seed());
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void storagefinder$captureRespawnWorld(ClientboundRespawnPacket packet, CallbackInfo ci) {
        WorldScopeTracker.setSeed(packet.commonPlayerSpawnInfo().seed());
    }
}
