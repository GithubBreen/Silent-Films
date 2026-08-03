package com.breenihilation.mixin;

// Prevents movement packets from bypassing an active travel transition.
import com.breenihilation.ServerTransitionCoordinator;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void silentfilms$freezeQueuedPlayer(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
		if (ServerTransitionCoordinator.hasPending(player)) {
			callback.cancel();
		}
	}
}
