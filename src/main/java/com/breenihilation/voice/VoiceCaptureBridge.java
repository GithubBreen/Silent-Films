package com.breenihilation.voice;

// Collects voice-chat audio into bounded speech utterances.
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class VoiceCaptureBridge {
	private static final int INPUT_SAMPLE_RATE = 48_000;
	private static final int MINIMUM_SAMPLES = INPUT_SAMPLE_RATE / 4;
	private static final int MAXIMUM_SAMPLES = INPUT_SAMPLE_RATE * 15;
	private static final long END_SILENCE_NANOS = 250_000_000L;
	private static final double MINIMUM_RMS = 120.0;

	private static BooleanSupplier enabled = () -> false;
	private static BooleanSupplier suppressAudio = () -> true;
	private static Consumer<AudioFrame> frameListener = ignored -> { };
	private static Runnable discardedListener = () -> { };
	private static Consumer<Utterance> listener = ignored -> { };
	private static short[] samples = new short[INPUT_SAMPLE_RATE * 2];
	private static int sampleCount;
	private static long captureStartedNanos;
	private static long lastFrameNanos;
	private static boolean whispering;

	private VoiceCaptureBridge() {
	}

	public static synchronized void configure(
			BooleanSupplier enabledSupplier,
			BooleanSupplier suppressAudioSupplier,
		Consumer<AudioFrame> liveFrameListener,
			Runnable discardedUtteranceListener,
			Consumer<Utterance> utteranceListener
	) {
		enabled = enabledSupplier;
		suppressAudio = suppressAudioSupplier;
		frameListener = liveFrameListener;
		discardedListener = discardedUtteranceListener;
		listener = utteranceListener;
	}

	public static synchronized short[] capture(short[] frame, boolean frameWhispering) {
		if (!enabled.getAsBoolean() || frame == null || frame.length == 0) {
			return frame;
		}

		int accepted = Math.min(frame.length, MAXIMUM_SAMPLES - sampleCount);
		if (accepted > 0) {
			if (sampleCount == 0) {
				captureStartedNanos = System.nanoTime();
			}
			ensureCapacity(sampleCount + accepted);
			System.arraycopy(frame, 0, samples, sampleCount, accepted);
			sampleCount += accepted;
			whispering |= frameWhispering;
			lastFrameNanos = System.nanoTime();
			frameListener.accept(new AudioFrame(Arrays.copyOf(frame, accepted), INPUT_SAMPLE_RATE, frameWhispering));
		}

		if (sampleCount >= MAXIMUM_SAMPLES) {
			finish();
		}
		return suppressAudio.getAsBoolean() ? new short[frame.length] : frame;
	}

	public static synchronized void tick() {
		if (sampleCount == 0) {
			return;
		}
		if (!enabled.getAsBoolean()) {
			reset();
			return;
		}
		if (System.nanoTime() - lastFrameNanos >= END_SILENCE_NANOS) {
			finish();
		}
	}

	public static synchronized void reset() {
		sampleCount = 0;
		captureStartedNanos = 0L;
		lastFrameNanos = 0L;
		whispering = false;
	}

	private static void finish() {
		short[] utterance = Arrays.copyOf(samples, sampleCount);
		boolean wasWhispering = whispering;
		long startedNanos = captureStartedNanos;
		long finalAudioNanos = lastFrameNanos;
		long finalizedNanos = System.nanoTime();
		reset();
		if (utterance.length >= MINIMUM_SAMPLES && rms(utterance) >= MINIMUM_RMS) {
			listener.accept(new Utterance(
					utterance,
					INPUT_SAMPLE_RATE,
					wasWhispering,
					startedNanos,
					finalAudioNanos,
					finalizedNanos
			));
		} else {
			discardedListener.run();
		}
	}

	private static double rms(short[] audio) {
		double sum = 0.0;
		for (short sample : audio) {
			double value = sample;
			sum += value * value;
		}
		return Math.sqrt(sum / audio.length);
	}

	private static void ensureCapacity(int required) {
		if (required <= samples.length) {
			return;
		}
		samples = Arrays.copyOf(samples, Math.min(MAXIMUM_SAMPLES, Math.max(required, samples.length * 2)));
	}

	public record Utterance(
			short[] samples,
			int sampleRate,
			boolean whispering,
			long captureStartedNanos,
			long finalAudioNanos,
			long finalizedNanos
	) {
		public long audioDurationMillis() {
			return Math.round(samples.length * 1000.0 / sampleRate);
		}

		public long finalizationDelayMillis() {
			return Math.max(0L, (finalizedNanos - finalAudioNanos) / 1_000_000L);
		}
	}

	public record AudioFrame(short[] samples, int sampleRate, boolean whispering) {
	}
}
