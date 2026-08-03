package com.breenihilation.mixin;

// Coordinates world-clock changes with the shared iris transition.
import com.breenihilation.ServerTransitionCoordinator;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerClockManager.class)
public class ServerClockManagerMixin {
	@Inject(method = "setTotalTicks", at = @At("HEAD"), cancellable = true)
	private void silentfilms$queueSetTotalTicks(
			Holder<WorldClock> clock,
			long ticks,
			CallbackInfo callback
	) {
		if (ServerTransitionCoordinator.interceptSetTotalTicks(
				(ServerClockManager) (Object) this,
				clock,
				ticks,
				((ServerClockManagerAccess) (Object) this).silentfilms$getServer()
		)) {
			callback.cancel();
		}
	}

	@Inject(method = "addTicks", at = @At("HEAD"), cancellable = true)
	private void silentfilms$queueAddTicks(
			Holder<WorldClock> clock,
			int ticks,
			CallbackInfo callback
	) {
		if (ServerTransitionCoordinator.interceptAddTicks(
				(ServerClockManager) (Object) this,
				clock,
				ticks,
				((ServerClockManagerAccess) (Object) this).silentfilms$getServer()
		)) {
			callback.cancel();
		}
	}
}
