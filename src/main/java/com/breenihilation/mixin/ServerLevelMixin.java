package com.breenihilation.mixin;

// Hooks server-level time and sleep events into Silent Films.
import com.breenihilation.ServerTransitionCoordinator;
import com.breenihilation.access.ServerLevelAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.Entity;
import com.breenihilation.IntertitleFreezeZones;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ServerLevelAccess {
	@Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
	private void silentfilms$freezeEntityInsideIntertitleZone(Entity entity, CallbackInfo callback) {
		if (IntertitleFreezeZones.shouldFreeze((ServerLevel) (Object) this, entity)) {
			callback.cancel();
		}
	}

	@Redirect(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Z"
			)
	)
	private boolean silentfilms$queueSleepTime(
			ServerClockManager manager,
			Holder<WorldClock> clock,
			ResourceKey<ClockTimeMarker> marker
	) {
		return ServerTransitionCoordinator.interceptSleepTime(
				(ServerLevel) (Object) this,
				manager,
				clock,
				marker
		);
	}

	@Inject(method = "wakeUpAllPlayers", at = @At("HEAD"), cancellable = true)
	private void silentfilms$deferWakeUp(CallbackInfo callback) {
		if (ServerTransitionCoordinator.shouldDeferWakeUp((ServerLevel) (Object) this)) {
			callback.cancel();
		}
	}

	@Override
	@Invoker("wakeUpAllPlayers")
	public abstract void silentfilms$invokeWakeUpAllPlayers();
}
