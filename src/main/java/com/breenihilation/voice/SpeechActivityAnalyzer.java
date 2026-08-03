package com.breenihilation.voice;

// Detects speech boundaries in raw microphone samples.
import java.util.Arrays;

public final class SpeechActivityAnalyzer {
	private static final int WINDOW_MILLIS = 20;
	private static final int MAX_BRIDGED_SILENCE_MILLIS = 100;
	private static final int TRIM_PADDING_MILLIS = 120;
	private static final double MINIMUM_ACTIVE_RMS = 160.0;

	private SpeechActivityAnalyzer() {
	}

	public static SpeechAnalysis analyze(short[] samples, int sampleRate) {
		if (samples == null || samples.length == 0 || sampleRate <= 0) {
			return new SpeechAnalysis(new short[0], 0L, 0L);
		}

		int windowSamples = Math.max(1, sampleRate * WINDOW_MILLIS / 1_000);
		int windowCount = (samples.length + windowSamples - 1) / windowSamples;
		double[] levels = new double[windowCount];
		for (int window = 0; window < windowCount; window++) {
			int start = window * windowSamples;
			int end = Math.min(samples.length, start + windowSamples);
			double sum = 0.0;
			for (int index = start; index < end; index++) {
				double value = samples[index];
				sum += value * value;
			}
			levels[window] = Math.sqrt(sum / Math.max(1, end - start));
		}

		double[] sorted = levels.clone();
		Arrays.sort(sorted);
		int noiseWindows = Math.max(1, sorted.length / 3);
		double noiseFloor = median(sorted, noiseWindows);
		double peak = sorted[sorted.length - 1];
		double threshold = Math.max(MINIMUM_ACTIVE_RMS, Math.max(noiseFloor * 3.0, peak * 0.07));

		boolean[] active = new boolean[windowCount];
		for (int window = 0; window < windowCount; window++) {
			active[window] = levels[window] >= threshold;
		}
		bridgeShortGaps(active, MAX_BRIDGED_SILENCE_MILLIS / WINDOW_MILLIS);

		int first = -1;
		int last = -1;
		int activeWindows = 0;
		for (int window = 0; window < active.length; window++) {
			if (active[window]) {
				if (first < 0) {
					first = window;
				}
				last = window;
				activeWindows++;
			}
		}
		if (first < 0) {
			return new SpeechAnalysis(samples.clone(), 0L, 0L);
		}

		int paddingSamples = sampleRate * TRIM_PADDING_MILLIS / 1_000;
		int trimStart = Math.max(0, first * windowSamples - paddingSamples);
		int trimEnd = Math.min(samples.length, (last + 1) * windowSamples + paddingSamples);
		long activeMillis = (long) activeWindows * WINDOW_MILLIS;
		long spanMillis = Math.round((trimEnd - trimStart) * 1_000.0 / sampleRate);
		return new SpeechAnalysis(Arrays.copyOfRange(samples, trimStart, trimEnd), activeMillis, spanMillis);
	}

	private static void bridgeShortGaps(boolean[] active, int maximumGapWindows) {
		int index = 0;
		while (index < active.length) {
			if (active[index]) {
				index++;
				continue;
			}
			int start = index;
			while (index < active.length && !active[index]) {
				index++;
			}
			if (start > 0 && index < active.length && index - start <= maximumGapWindows) {
				Arrays.fill(active, start, index, true);
			}
		}
	}

	private static double median(double[] sorted, int length) {
		int middle = length / 2;
		return length % 2 == 0
				? (sorted[middle - 1] + sorted[middle]) / 2.0
				: sorted[middle];
	}

	public record SpeechAnalysis(short[] trimmedSamples, long activeMillis, long trimmedMillis) {
	}
}
