package com.breenihilation.client;

// simple voice chat 
import com.breenihilation.SilentFilms;
import com.breenihilation.TransitionPayloads;
import com.breenihilation.voice.SpeechActivityAnalyzer;
import com.breenihilation.voice.TranscriptConfidence;
import com.breenihilation.voice.VoiceCaptureBridge;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;

public final class VoiceIntertitleClient {
	private static final ParakeetTranscriber PARAKEET = new ParakeetTranscriber();
	private static final AtomicBoolean PREWARM_STARTED = new AtomicBoolean();
	private static volatile String lastError = "";
	private static volatile String lastDiagnostics = "";

	private VoiceIntertitleClient() {
	}

	public static void initialize() {
		VoiceCaptureBridge.configure(
				VoiceIntertitleClient::captureReady,
				() -> SilentFilmsClient.config() != null && SilentFilmsClient.config().suppressVoiceAudio,
				ignored -> { },
				() -> { },
				VoiceIntertitleClient::accept
		);
	}

	public static void tick() {
		VoiceCaptureBridge.tick();
		prewarmIfAvailable();
	}

	public static void reset() {
		VoiceCaptureBridge.reset();
	}

	public static void recognizerConfigurationChanged() {
		VoiceCaptureBridge.reset();
		PARAKEET.unload();
		PREWARM_STARTED.set(false);
		lastError = "";
		lastDiagnostics = "";
	}

	public static void unloadRecognizerAndThen(Runnable callback) {
		VoiceCaptureBridge.reset();
		PARAKEET.unloadAndThen(callback);
	}

	public static void shutdown() {
		VoiceCaptureBridge.reset();
		PARAKEET.close();
	}

	public static VoiceStatus status() {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null || !config.voiceIntertitles) {
			return VoiceStatus.OFF;
		}
		if (!FabricLoader.getInstance().isModLoaded("voicechat")) {
			return VoiceStatus.MISSING_VOICE_CHAT;
		}
		if (!ParakeetModelFiles.installed(Minecraft.getInstance().gameDirectory.toPath())) {
			return VoiceStatus.MISSING_MODEL;
		}
		if (!lastError.isBlank() || !PARAKEET.lastError().isBlank()) {
			return VoiceStatus.ERROR;
		}
		if (!PARAKEET.ready()) {
			return VoiceStatus.LOADING;
		}
		return PARAKEET.transcribing() ? VoiceStatus.TRANSCRIBING : VoiceStatus.READY;
	}

	public static String lastError() {
		return !lastError.isBlank() ? lastError : PARAKEET.lastError();
	}

	public static String diagnosticsSummary() {
		return lastDiagnostics;
	}

	private static boolean captureReady() {
		SilentFilmsConfig config = SilentFilmsClient.config();
		return config != null
				&& config.voiceIntertitles
				&& FabricLoader.getInstance().isModLoaded("voicechat")
				&& ParakeetModelFiles.installed(Minecraft.getInstance().gameDirectory.toPath())
				&& PARAKEET.ready();
	}

	private static void prewarmIfAvailable() {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null || !config.voiceIntertitles
				|| !FabricLoader.getInstance().isModLoaded("voicechat")
				|| !ParakeetModelFiles.installed(Minecraft.getInstance().gameDirectory.toPath())
				|| !PREWARM_STARTED.compareAndSet(false, true)) {
			return;
		}
		PARAKEET.prewarm(ParakeetModelFiles.directory(Minecraft.getInstance().gameDirectory.toPath()));
	}

	private static void accept(VoiceCaptureBridge.Utterance utterance) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.level == null || client.player == null || !captureReady()) {
				return;
			}
			PARAKEET.transcribe(utterance, result -> client.execute(() -> handleResult(result)));
		});
	}

	private static void handleResult(ParakeetTranscriber.TranscriptionResult result) {
		SpeechActivityAnalyzer.SpeechAnalysis speech = SpeechActivityAnalyzer.analyze(
				result.utterance().samples(), result.utterance().sampleRate()
		);
		boolean rejected = !result.text().isBlank() && TranscriptConfidence.shouldReject(
				result.text(), Double.NaN, Double.NaN, speech.activeMillis()
		);
		if (result.used() && result.error().isBlank() && !result.text().isBlank() && !rejected) {
			lastError = "";
			lastDiagnostics = "PARAKEET / " + result.inferenceMillis() + " ms / AUTO";
			SilentFilms.LOGGER.info(
					"Voice STT: backend=PARAKEET accepted=true audio={}ms finalize={}ms inference={}ms language=AUTO",
					result.utterance().audioDurationMillis(), result.utterance().finalizationDelayMillis(),
					result.inferenceMillis()
			);
			sendTranscript(result.text(), result.utterance().whispering());
			return;
		}

		if (!result.error().isBlank()) {
			lastError = "Parakeet: " + result.error();
			lastDiagnostics = "PARAKEET ERROR / " + result.inferenceMillis() + " ms";
		} else {
			lastError = "";
			lastDiagnostics = "PARAKEET / NO TRANSCRIPT / " + result.inferenceMillis() + " ms";
		}
		SilentFilms.LOGGER.info(
				"Parakeet produced no usable transcript (used={}, rejected={}, error={})",
				result.used(), rejected, result.error().isBlank() ? "none" : result.error()
		);
	}

	private static void sendTranscript(String text, boolean whispering) {
		try {
			if (ClientPlayNetworking.canSend(TransitionPayloads.VoiceTranscriptRequest.TYPE)) {
				ClientPlayNetworking.send(new TransitionPayloads.VoiceTranscriptRequest(text, whispering));
			}
		} catch (IllegalStateException ignored) {
			// The player disconnected while recognition was running.
		}
	}

	public enum VoiceStatus {
		OFF,
		MISSING_VOICE_CHAT,
		MISSING_MODEL,
		LOADING,
		READY,
		TRANSCRIBING,
		ERROR
	}
}
