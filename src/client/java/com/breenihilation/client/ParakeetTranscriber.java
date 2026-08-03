package com.breenihilation.client;

// parakeet shit v2
import com.breenihilation.SilentFilms;
import com.breenihilation.voice.VoiceCaptureBridge;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class ParakeetTranscriber implements AutoCloseable {
	private static final int MODEL_SAMPLE_RATE = 16_000;
	private static final int FEATURE_DIMENSION = 80;
	private static final int THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 4, 1, 4);

	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Silent Films Parakeet recognition");
		thread.setDaemon(true);
		return thread;
	});
	private OfflineRecognizer recognizer;
	private Path loadedDirectory;
	private volatile boolean ready;
	private volatile boolean loading;
	private volatile boolean transcribing;
	private volatile String lastError = "";

	synchronized void prewarm(Path modelDirectory) {
		Path normalized = modelDirectory.toAbsolutePath().normalize();
		if (loading) {
			return;
		}
		loading = true;
		worker.execute(() -> {
			// Check on the worker, after any previously queued unload. Checking
			// ready on the caller thread allowed unload -> prewarm to become
			// prewarm(no-op) -> unload, permanently stranding the status at LOADING.
			if (ready && normalized.equals(loadedDirectory)) {
				loading = false;
				return;
			}
			load(normalized);
		});
	}

	void transcribe(VoiceCaptureBridge.Utterance utterance, Consumer<TranscriptionResult> callback) {
		worker.execute(() -> transcribeOnWorker(utterance, callback));
	}

	void unload() {
		worker.execute(this::releaseRecognizer);
	}

	void unloadAndThen(Runnable callback) {
		worker.execute(() -> {
			releaseRecognizer();
			callback.run();
		});
	}

	boolean ready() {
		return ready;
	}

	boolean transcribing() {
		return transcribing;
	}

	String lastError() {
		return lastError;
	}

	private void load(Path directory) {
		long started = System.nanoTime();
		try {
			releaseRecognizer();
			OfflineTransducerModelConfig transducer = OfflineTransducerModelConfig.builder()
					.setEncoder(directory.resolve("encoder.int8.onnx").toString())
					.setDecoder(directory.resolve("decoder.int8.onnx").toString())
					.setJoiner(directory.resolve("joiner.int8.onnx").toString())
					.build();
			OfflineModelConfig model = OfflineModelConfig.builder()
					.setTransducer(transducer)
					.setTokens(directory.resolve("tokens.txt").toString())
					.setNumThreads(THREADS)
					.setProvider("cpu")
					.setModelType("nemo_transducer")
					.setDebug(false)
					.build();
			FeatureConfig features = FeatureConfig.builder()
					.setSampleRate(MODEL_SAMPLE_RATE)
					.setFeatureDim(FEATURE_DIMENSION)
					.setDither(0.0F)
					.build();
			OfflineRecognizerConfig config = OfflineRecognizerConfig.builder()
					.setFeatureConfig(features)
					.setOfflineModelConfig(model)
					.setDecodingMethod("greedy_search")
					.setMaxActivePaths(4)
					.build();
			recognizer = new OfflineRecognizer(config);
			loadedDirectory = directory;
			ready = true;
			lastError = "";
			SilentFilms.LOGGER.info("Parakeet model ready in {} ms using {} CPU threads", elapsedMillis(started), THREADS);
		} catch (Throwable throwable) {
			ready = false;
			lastError = message(throwable);
			SilentFilms.LOGGER.warn("Could not initialize Parakeet recognition: {}", lastError, throwable);
		} finally {
			loading = false;
		}
	}

	private void transcribeOnWorker(
			VoiceCaptureBridge.Utterance utterance,
		Consumer<TranscriptionResult> callback
	) {
		long started = System.nanoTime();
		String text = "";
		String error = "";
		boolean used = ready && recognizer != null;
		transcribing = true;
		OfflineStream stream = null;
		try {
			if (used) {
				stream = recognizer.createStream();
				stream.acceptWaveform(normalizeSamples(utterance.samples()), utterance.sampleRate());
				recognizer.decode(stream);
				text = clean(recognizer.getResult(stream).getText());
			}
		} catch (Throwable throwable) {
			error = message(throwable);
			lastError = error;
			SilentFilms.LOGGER.warn("Could not transcribe with Parakeet: {}", error, throwable);
		} finally {
			if (stream != null) {
				stream.release();
			}
			transcribing = false;
		}
		callback.accept(new TranscriptionResult(text, used, elapsedMillis(started), error, utterance));
	}

	private void releaseRecognizer() {
		if (recognizer != null) {
			recognizer.release();
			recognizer = null;
		}
		ready = false;
		loadedDirectory = null;
	}

	@Override
	public void close() {
		worker.execute(this::releaseRecognizer);
		worker.shutdown();
	}

	private static float[] normalizeSamples(short[] source) {
		float[] result = new float[source.length];
		for (int index = 0; index < source.length; index++) {
			result[index] = source[index] / 32768.0F;
		}
		return result;
	}

	private static String clean(String raw) {
		return raw == null ? "" : raw.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
	}

	private static long elapsedMillis(long startedNanos) {
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	private static String message(Throwable throwable) {
		return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
	}

	record TranscriptionResult(
			String text,
			boolean used,
			long inferenceMillis,
			String error,
			VoiceCaptureBridge.Utterance utterance
	) {
	}
}
