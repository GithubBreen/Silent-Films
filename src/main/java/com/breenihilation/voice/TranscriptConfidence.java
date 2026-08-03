package com.breenihilation.voice;

// Filters transcripts that are likely to be recognition errors.
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class TranscriptConfidence {
	private static final double VERY_LOW_AVERAGE_LOG_PROBABILITY = -1.35;
	private static final double LOW_AVERAGE_LOG_PROBABILITY = -0.80;
	private static final double HIGH_NO_SPEECH_PROBABILITY = 0.75;

	private TranscriptConfidence() {
	}

	public static boolean shouldReject(double averageLogProbability, double maximumNoSpeechProbability) {
		if (!Double.isFinite(averageLogProbability)) {
			return false;
		}
		return averageLogProbability < VERY_LOW_AVERAGE_LOG_PROBABILITY
				|| (maximumNoSpeechProbability >= HIGH_NO_SPEECH_PROBABILITY
				&& averageLogProbability < LOW_AVERAGE_LOG_PROBABILITY);
	}

	public static boolean shouldReject(
			String text,
			double averageLogProbability,
			double maximumNoSpeechProbability,
			long activeSpeechMillis
	) {
		if (shouldReject(averageLogProbability, maximumNoSpeechProbability)) {
			return true;
		}
		if (text == null || text.isBlank()) {
			return false;
		}

		String[] words = text.trim().split("\\s+");
		int plausibleWords = activeSpeechMillis <= 1_200L
				? Math.max(4, (int) Math.ceil(activeSpeechMillis / 180.0) + 3)
				: Math.max(12, (int) Math.ceil(activeSpeechMillis / 150.0) + 5);
		if (activeSpeechMillis > 0L && words.length > plausibleWords) {
			return true;
		}
		if (hasPathologicalRepetition(words) || hasMostlyGarbledCharacters(text)) {
			return true;
		}
		return false;
	}

	private static boolean hasPathologicalRepetition(String[] words) {
		if (words.length < 8) {
			return false;
		}
		Set<String> unique = new HashSet<>();
		String previous = "";
		int repeatedRun = 0;
		for (String word : words) {
			String normalized = word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
			if (normalized.isBlank()) {
				continue;
			}
			unique.add(normalized);
			if (normalized.equals(previous)) {
				repeatedRun++;
				if (repeatedRun >= 4) {
					return true;
				}
			} else {
				previous = normalized;
				repeatedRun = 1;
			}
		}
		return words.length >= 12 && unique.size() * 4 < words.length;
	}

	private static boolean hasMostlyGarbledCharacters(String text) {
		int visible = 0;
		int lettersOrDigits = 0;
		for (int offset = 0; offset < text.length();) {
			int codePoint = text.codePointAt(offset);
			offset += Character.charCount(codePoint);
			if (!Character.isWhitespace(codePoint)) {
				visible++;
				if (Character.isLetterOrDigit(codePoint)) {
					lettersOrDigits++;
				}
			}
		}
		return visible >= 20 && lettersOrDigits * 2 < visible;
	}
}
