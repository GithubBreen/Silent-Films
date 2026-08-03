package com.breenihilation.client.mixin;

import com.breenihilation.client.ClientTransitionController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
	private boolean silentfilms$localEntityTeleport;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"))
	private void silentfilms$beginPlayerPositionTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo callback) {
		ClientTransitionController.onFallbackTravelPacket();
	}

	@Inject(method = "handleMovePlayer", at = @At("TAIL"))
	private void silentfilms$finishPlayerPositionTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo callback) {
		ClientTransitionController.onFallbackTravelPacketApplied();
	}

	@Inject(method = "handleRespawn", at = @At("HEAD"))
	private void silentfilms$beginRespawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
		ClientTransitionController.onFallbackTravelPacket();
	}

	@Inject(method = "handleRespawn", at = @At("TAIL"))
	private void silentfilms$finishRespawn(ClientboundRespawnPacket packet, CallbackInfo callback) {
		ClientTransitionController.onFallbackTravelPacketApplied();
	}

	@Inject(method = "handleTeleportEntity", at = @At("HEAD"))
	private void silentfilms$beginEntityTeleport(ClientboundTeleportEntityPacket packet, CallbackInfo callback) {
		Minecraft client = Minecraft.getInstance();
		silentfilms$localEntityTeleport = client.player != null && packet.id() == client.player.getId();
		if (silentfilms$localEntityTeleport) {
			ClientTransitionController.onFallbackTravelPacket();
		}
	}

	@Inject(method = "handleTeleportEntity", at = @At("TAIL"))
	private void silentfilms$finishEntityTeleport(ClientboundTeleportEntityPacket packet, CallbackInfo callback) {
		if (silentfilms$localEntityTeleport) {
			ClientTransitionController.onFallbackTravelPacketApplied();
			silentfilms$localEntityTeleport = false;
		}
	}

}
