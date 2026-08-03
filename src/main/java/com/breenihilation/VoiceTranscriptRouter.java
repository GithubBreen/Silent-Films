package com.breenihilation;

// Delivers voice transcripts to players within the intended hearing range.
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VoiceTranscriptRouter {
	private static final int MIN_SEND_INTERVAL_TICKS = 10;
	private static final Map<UUID, Integer> LAST_SEND_TICK = new HashMap<>();

	private VoiceTranscriptRouter() {
	}

	public static void receive(ServerPlayer sender, TransitionPayloads.VoiceTranscriptRequest payload) {
		String text = sanitize(payload.text());
		if (text.isBlank()) {
			return;
		}

		int now = sender.level().getServer().getTickCount();
		int previous = LAST_SEND_TICK.getOrDefault(sender.getUUID(), Integer.MIN_VALUE / 2);
		if (now - previous < MIN_SEND_INTERVAL_TICKS) {
			return;
		}
		LAST_SEND_TICK.put(sender.getUUID(), now);

		int range = rangeBlocks(payload.whispering());
		double rangeSquared = DialogueRanges.squared(range);
		TransitionPayloads.VoiceTranscriptCard card = new TransitionPayloads.VoiceTranscriptCard(
				sender.getUUID(),
				sender.getGameProfile().name(),
				text
		);
		for (ServerPlayer recipient : sender.level().players()) {
			if (recipient.distanceToSqr(sender) <= rangeSquared
					&& ServerPlayNetworking.canSend(recipient, TransitionPayloads.VoiceTranscriptCard.TYPE)) {
				ServerPlayNetworking.send(recipient, card);
			}
		}
	}

	static int rangeBlocks(boolean whispering) {
		return whispering ? DialogueRanges.WHISPER_BLOCKS : DialogueRanges.VOICE_BLOCKS;
	}

	static String sanitize(String value) {
		if (value == null) {
			return "";
		}
		return value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
				.replaceAll("\\s+", " ")
				.trim();
	}
}
