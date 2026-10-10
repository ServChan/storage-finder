package org.lts.storagefinder.macro.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MenuType;
import org.lts.storagefinder.macro.AutoPeekClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMacroMixin {
    @WrapOperation(method = "handleOpenScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"))
    private void storagefinder_macro$openWithoutScreen(MenuType<?> type, Minecraft minecraft, int containerId,
                                                       Component title, Operation<Void> original) {
        if (!AutoPeekClient.interceptMenu(type, minecraft, containerId)) {
            original.call(type, minecraft, containerId, title);
        }
    }

    @WrapOperation(method = "handleContainerClose", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"))
    private void storagefinder_macro$closeWithoutScreen(LocalPlayer player, Operation<Void> original,
                                                        ClientboundContainerClosePacket packet) {
        if (!AutoPeekClient.interceptServerClose(Minecraft.getInstance(), packet.getContainerId())) {
            original.call(player);
        }
    }

    @WrapOperation(method = "handleSoundEvent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"))
    private void storagefinder_macro$muteAutoPeek(ClientLevel level, Entity except, double x, double y, double z,
                                                  Holder<SoundEvent> sound, SoundSource source, float volume,
                                                  float pitch, long seed, Operation<Void> original) {
        if (!AutoPeekClient.shouldMute(sound, x, y, z)) {
            original.call(level, except, x, y, z, sound, source, volume, pitch, seed);
        }
    }
}
