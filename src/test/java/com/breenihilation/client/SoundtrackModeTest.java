package com.breenihilation.client;

// Verifies soundtrack mode ordering and parsing.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundtrackModeTest {
	@Test
	void selectorUsesTheIntendedOrder() {
		assertEquals(SoundtrackMode.ON, SoundtrackMode.values()[0]);
		assertEquals(SoundtrackMode.CUSTOM, SoundtrackMode.values()[1]);
		assertEquals(SoundtrackMode.VANILLA, SoundtrackMode.values()[2]);
		assertEquals(SoundtrackMode.OFF, SoundtrackMode.values()[3]);
	}

	@Test
	void invalidConfigFallsBackToBundledSoundtrack() {
		assertEquals(SoundtrackMode.ON, SoundtrackMode.fromConfig(null));
		assertEquals(SoundtrackMode.ON, SoundtrackMode.fromConfig("not-a-mode"));
		assertEquals(SoundtrackMode.CUSTOM, SoundtrackMode.fromConfig("custom"));
	}
}
