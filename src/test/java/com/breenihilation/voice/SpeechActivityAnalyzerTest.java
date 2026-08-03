package com.breenihilation.voice;

// Verifies speech activity segmentation.
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpeechActivityAnalyzerTest {
	private static final int SAMPLE_RATE = 48_000;

	@Test
	void trimsLeadingAndTrailingSilence() {
		short[] audio = new short[SAMPLE_RATE * 2];
		Arrays.fill(audio, SAMPLE_RATE * 4 / 5, SAMPLE_RATE * 6 / 5, (short) 2_000);

		SpeechActivityAnalyzer.SpeechAnalysis analysis = SpeechActivityAnalyzer.analyze(audio, SAMPLE_RATE);

		assertEquals(400L, analysis.activeMillis());
		assertTrue(analysis.trimmedMillis() >= 600L);
		assertTrue(analysis.trimmedMillis() <= 680L);
		assertTrue(analysis.trimmedSamples().length < audio.length / 2);
	}

	@Test
	void bridgesBriefGapsInsideSpeech() {
		short[] audio = new short[SAMPLE_RATE];
		Arrays.fill(audio, SAMPLE_RATE / 5, SAMPLE_RATE * 2 / 5, (short) 2_000);
		Arrays.fill(audio, SAMPLE_RATE * 12 / 25, SAMPLE_RATE * 17 / 25, (short) 2_000);

		SpeechActivityAnalyzer.SpeechAnalysis analysis = SpeechActivityAnalyzer.analyze(audio, SAMPLE_RATE);

		assertTrue(analysis.activeMillis() >= 480L);
	}
}
