package com.breenihilation.voice;

// Verifies transcript confidence filtering.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TranscriptConfidenceTest {
	@Test
	void acceptsConfidentSpeech() {
		assertFalse(TranscriptConfidence.shouldReject(-0.42, 0.08));
	}

	@Test
	void rejectsExtremelyUnlikelyText() {
		assertTrue(TranscriptConfidence.shouldReject(-1.50, 0.12));
	}

	@Test
	void rejectsLikelySilenceWhenTextIsAlsoWeak() {
		assertTrue(TranscriptConfidence.shouldReject(-0.95, 0.88));
	}

	@Test
	void keepsMissingConfidenceMetadata() {
		assertFalse(TranscriptConfidence.shouldReject(Double.NaN, Double.NaN));
	}

	@Test
	void rejectsImplausiblyLongTextForOneWordSpeech() {
		assertTrue(TranscriptConfidence.shouldReject(
				"This invented sentence contains far too many words for such a tiny sound clip",
				-0.20,
				0.01,
				360
		));
	}

	@Test
	void acceptsConciseShortSpeech() {
		assertFalse(TranscriptConfidence.shouldReject("Creeper!", -0.25, 0.01, 360));
	}

	@Test
	void rejectsPathologicalRepetition() {
		assertTrue(TranscriptConfidence.shouldReject(
				"hello hello hello hello hello hello hello hello hello hello hello hello",
				-0.10,
				0.01,
				4_000
		));
	}

	@Test
	void acceptsNormalMultilingualText() {
		assertFalse(TranscriptConfidence.shouldReject("Осторожно, крипер сзади!", -0.30, 0.01, 1_500));
	}
}
