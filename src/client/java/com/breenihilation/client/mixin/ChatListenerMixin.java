package com.breenihilation.client.mixin;
import com.breenihilation.client.SilentFilmsClient;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.time.Instant;
import java.util.UUID;

// chat to big ass black title converter

@Mixin(ChatListener.class)
public class ChatListenerMixin {
	@Inject(method = "showMessageToPlayer", at = @At("RETURN"))
	private void silentfilms$captureRemoteChat(
			ChatType.Bound bound,
			PlayerChatMessage message,
			Component decorated,
			GameProfile profile,
			boolean onlyShowSecure,
			Instant timestamp,
			CallbackInfoReturnable<Boolean> callback
	) {
		if (!callback.getReturnValueZ() || profile == null || message == null) {
			return;
		}

		UUID senderId = profile.id();
		SilentFilmsClient.onRemoteChat(senderId, profile.name(), message.decoratedContent().getString());
	}
}
