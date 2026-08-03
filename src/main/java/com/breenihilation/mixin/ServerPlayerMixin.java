package com.breenihilation.mixin;

// Starts transition tracking when a player sleeps or travels.
import com.breenihilation.ServerTransitionCoordinator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
	@Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
	private void silentfilms$queueTeleport(TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> callback) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (ServerTransitionCoordinator.interceptTeleport(player, transition)) {
			callback.setReturnValue(player);
		}
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void silentfilms$protectQueuedPlayer(
			ServerLevel level,
			DamageSource source,
			float amount,
			CallbackInfoReturnable<Boolean> callback
	) {
		if (ServerTransitionCoordinator.hasPending((ServerPlayer) (Object) this)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void silentfilms$holdQueuedPlayer(CallbackInfo callback) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (ServerTransitionCoordinator.hasPending(player)) {
			player.setDeltaMovement(0.0, 0.0, 0.0);
		}
	}
}
