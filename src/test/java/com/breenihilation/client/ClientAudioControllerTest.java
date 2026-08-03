package com.breenihilation.client;

// Verifies gameplay sound mute state detection.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientAudioControllerTest {
	@Test
	void treatsOnlyNearZeroGameplayVolumesAsMuted() {
		assertTrue(ClientAudioController.isGameplaySourceMuted(0.0));
		assertTrue(ClientAudioController.isGameplaySourceMuted(0.001));
		assertFalse(ClientAudioController.isGameplaySourceMuted(0.002));
		assertFalse(ClientAudioController.isGameplaySourceMuted(1.0));
	}
}
