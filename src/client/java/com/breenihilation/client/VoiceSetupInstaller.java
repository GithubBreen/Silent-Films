package com.breenihilation.client;


import com.breenihilation.SilentFilms;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// parakeet downloader

public final class VoiceSetupInstaller {
	private static final ExecutorService INSTALL_THREAD = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Silent Films voice setup installer");
		thread.setDaemon(true);
		return thread;
	});
	private static final AtomicBoolean CANCELLED = new AtomicBoolean();
	private static volatile SetupSnapshot snapshot = new SetupSnapshot(
			Stage.IDLE, 0L, ParakeetModelFiles.totalBytes(), 0.0, "", ""
	);
	private static boolean sessionPromptShown;

	private VoiceSetupInstaller() {
	}

	public static SetupSnapshot snapshot() {
		return snapshot;
	}

	public static boolean isInstalled() {
		return ParakeetModelFiles.installed(Minecraft.getInstance().gameDirectory.toPath());
	}

	public static long selectedDownloadMiB() {
		return Math.max(1L, ParakeetModelFiles.totalBytes() / (1024L * 1024L));
	}

	public static long expectedDownloadBytes() {
		Path directory = ParakeetModelFiles.directory(Minecraft.getInstance().gameDirectory.toPath());
		return ParakeetModelFiles.FILES.stream()
				.filter(file -> !Files.isRegularFile(directory.resolve(file.filename()))
						|| file.bytes() != safeSize(directory.resolve(file.filename())))
				.mapToLong(ParakeetModelFiles.ModelFile::bytes)
				.sum();
	}

	public static boolean simpleVoiceChatInstalled() {
		return FabricLoader.getInstance().isModLoaded("voicechat");
	}

	public static void open(Screen parent) {
		sessionPromptShown = true;
		long expectedBytes = expectedDownloadBytes();
		if (!snapshot.stage().busy()) {
			snapshot = isInstalled()
					? new SetupSnapshot(Stage.COMPLETE, expectedBytes, expectedBytes, 0.0, "", "")
					: new SetupSnapshot(Stage.IDLE, 0L, expectedBytes, 0.0, "", "");
		}
		Minecraft.getInstance().gui.setScreen(new VoiceSetupScreen(parent));
	}

	public static void maybePrompt(Minecraft client) {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (sessionPromptShown || config == null || !config.voiceIntertitles
				|| config.voiceSetupPromptDismissed || isInstalled()
				|| client.player == null || client.gui.screen() != null) {
			return;
		}
		open(null);
	}

	public static synchronized void start() {
		if (snapshot.stage().busy()) {
			return;
		}
		if (isInstalled()) {
			long expectedBytes = expectedDownloadBytes();
			snapshot = new SetupSnapshot(Stage.COMPLETE, expectedBytes, expectedBytes, 0.0, "", "");
			return;
		}

		CANCELLED.set(false);
		snapshot = new SetupSnapshot(Stage.DOWNLOADING_MODEL, 0L, expectedDownloadBytes(), 0.0, "", "");
		INSTALL_THREAD.execute(VoiceSetupInstaller::installParakeet);
	}

	public static void cancel() {
		if (snapshot.stage().busy()) {
			CANCELLED.set(true);
		}
	}

	public static synchronized void resetForRetry() {
		if (!snapshot.stage().busy()) {
			snapshot = new SetupSnapshot(Stage.IDLE, 0L, expectedDownloadBytes(), 0.0, "", "");
		}
	}

	public static synchronized void deleteModel() {
		if (snapshot.stage().busy()) {
			return;
		}
		Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
		Path modelDirectory = ParakeetModelFiles.directory(gameDirectory);
		snapshot = new SetupSnapshot(Stage.DELETING, 0L, 0L, 0.0, "", "");
		VoiceIntertitleClient.unloadRecognizerAndThen(() -> {
			String error = "";
			try {
				deleteDirectory(modelDirectory);
				Path downloads = gameDirectory.resolve("silentfilms/downloads");
				for (ParakeetModelFiles.ModelFile modelFile : ParakeetModelFiles.FILES) {
					Files.deleteIfExists(downloads.resolve("parakeet-" + modelFile.filename() + ".part"));
				}
			} catch (IOException exception) {
				error = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			}
			String finalError = error;
			Minecraft.getInstance().execute(() -> {
				if (finalError.isBlank()) {
					VoiceIntertitleClient.recognizerConfigurationChanged();
					snapshot = new SetupSnapshot(Stage.IDLE, 0L, ParakeetModelFiles.totalBytes(), 0.0, "", "");
				} else {
					snapshot = new SetupSnapshot(Stage.FAILED, 0L, ParakeetModelFiles.totalBytes(), 0.0, "", finalError);
				}
			});
		});
	}

	private static void deleteDirectory(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException exception) {
					throw new DeleteFailure(exception);
				}
			});
		} catch (DeleteFailure failure) {
			throw failure.exception;
		}
	}

	private static final class DeleteFailure extends RuntimeException {
		private final IOException exception;

		private DeleteFailure(IOException exception) {
			this.exception = exception;
		}
	}

	private static void installParakeet() {
		Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath();
		Path directory = ParakeetModelFiles.directory(gameDirectory);
		Path downloads = gameDirectory.resolve("silentfilms/downloads");
		long installBytes = expectedDownloadBytes();
		long completed = 0L;
		try {
			Files.createDirectories(directory);
			Files.createDirectories(downloads);
			for (ParakeetModelFiles.ModelFile modelFile : ParakeetModelFiles.FILES) {
				Path installed = directory.resolve(modelFile.filename());
				if (matchesChecksum(installed, modelFile.sha256())) {
					continue;
				}
				Path part = downloads.resolve("parakeet-" + modelFile.filename() + ".part");
				download(modelFile.downloadUri(), part, modelFile.bytes(), completed, installBytes);
				snapshot = new SetupSnapshot(Stage.VERIFYING, completed + modelFile.bytes(), installBytes,
						snapshot.downloadBytesPerSecond(), modelFile.filename(), "");
				verify(part, modelFile.sha256(), "Parakeet " + modelFile.filename());
				moveReplacing(part, installed);
				completed += modelFile.bytes();
			}

			SilentFilmsConfig config = SilentFilmsClient.config();
			config.voiceSetupPromptDismissed = false;
			SilentFilmsClient.saveConfig();
			VoiceIntertitleClient.recognizerConfigurationChanged();
			snapshot = new SetupSnapshot(Stage.COMPLETE, installBytes, installBytes, 0.0, "", "");
		} catch (CancellationException exception) {
			snapshot = new SetupSnapshot(Stage.CANCELLED, snapshot.completedBytes(), snapshot.totalBytes(), 0.0, "", "");
		} catch (Exception exception) {
			String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
			SilentFilms.LOGGER.warn("Parakeet voice intertitle setup failed", exception);
			snapshot = new SetupSnapshot(Stage.FAILED, snapshot.completedBytes(), snapshot.totalBytes(), 0.0, "", message);
		} finally {
			for (ParakeetModelFiles.ModelFile modelFile : ParakeetModelFiles.FILES) {
				try {
					Files.deleteIfExists(downloads.resolve("parakeet-" + modelFile.filename() + ".part"));
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static void download(java.net.URI uri, Path target, long expectedBytes, long offset, long totalBytes)
			throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpRequest request = HttpRequest.newBuilder(uri).header("User-Agent", "SilentFilms/1.0").GET().build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("Download failed with HTTP " + response.statusCode());
		}

		Files.createDirectories(target.getParent());
		long downloaded = 0L;
		long startedNanos = System.nanoTime();
		try (InputStream input = response.body(); OutputStream output = Files.newOutputStream(target)) {
			byte[] buffer = new byte[64 * 1024];
			int count;
			while ((count = input.read(buffer)) >= 0) {
				checkCancelled();
				output.write(buffer, 0, count);
				downloaded += count;
				double elapsedSeconds = Math.max(0.001, (System.nanoTime() - startedNanos) / 1_000_000_000.0);
				double bytesPerSecond = downloaded / elapsedSeconds;
				snapshot = new SetupSnapshot(
						Stage.DOWNLOADING_MODEL, offset + downloaded, totalBytes, bytesPerSecond, "", ""
				);
			}
		}
		if (downloaded != expectedBytes) {
			throw new IOException("Downloaded file size was " + downloaded + " bytes; expected " + expectedBytes);
		}
	}

	private static void verify(Path path, String expectedSha256, String label) throws IOException {
		checkCancelled();
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream input = Files.newInputStream(path)) {
				byte[] buffer = new byte[64 * 1024];
				int count;
				while ((count = input.read(buffer)) >= 0) {
					checkCancelled();
					digest.update(buffer, 0, count);
				}
			}
			String actual = HexFormat.of().formatHex(digest.digest());
			if (!actual.equalsIgnoreCase(expectedSha256)) {
				throw new IOException("Checksum verification failed for " + label);
			}
		} catch (NoSuchAlgorithmException exception) {
			throw new IOException("SHA-256 is unavailable", exception);
		}
	}

	private static boolean matchesChecksum(Path path, String expectedSha256) {
		if (!Files.isRegularFile(path)) {
			return false;
		}
		try {
			verify(path, expectedSha256, "installed model");
			return true;
		} catch (IOException | CancellationException exception) {
			return false;
		}
	}

	private static long safeSize(Path path) {
		try {
			return Files.size(path);
		} catch (IOException exception) {
			return -1L;
		}
	}

	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void checkCancelled() {
		if (CANCELLED.get()) {
			throw new CancellationException();
		}
	}

	public enum Stage {
		IDLE,
		DOWNLOADING_MODEL,
		VERIFYING,
		DELETING,
		COMPLETE,
		CANCELLED,
		FAILED;

		public boolean busy() {
			return this == DOWNLOADING_MODEL || this == VERIFYING || this == DELETING;
		}
	}

	public record SetupSnapshot(
			Stage stage,
			long completedBytes,
			long totalBytes,
			double downloadBytesPerSecond,
			String detail,
			String error
	) {
		public float progress() {
			if (totalBytes <= 0L) {
				return 0.0F;
			}
			return Math.clamp(completedBytes / (float) totalBytes, 0.0F, 1.0F);
		}
	}
}
