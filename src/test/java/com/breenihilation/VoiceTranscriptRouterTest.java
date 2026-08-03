package com.breenihilation;

// Verifies transcript routing and sanitization.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VoiceTranscriptRouterTest {
	@Test
	void sanitizesWithoutTruncating() {
		String transcript = "voice ".repeat(1000).trim();
		String sanitized = VoiceTranscriptRouter.sanitize(transcript);

		assertEquals(transcript, sanitized);
		assertFalse(sanitized.endsWith("..."));
	}

	@Test
	void keepsNormalAndWhisperRangesDistinct() {
		assertEquals(DialogueRanges.VOICE_BLOCKS, VoiceTranscriptRouter.rangeBlocks(false));
		assertEquals(DialogueRanges.WHISPER_BLOCKS, VoiceTranscriptRouter.rangeBlocks(true));
		assertEquals(1_600.0, DialogueRanges.squared(DialogueRanges.CHAT_BLOCKS));
	}
}
