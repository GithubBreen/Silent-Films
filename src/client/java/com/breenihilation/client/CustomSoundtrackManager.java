package com.breenihilation.client;


import com.breenihilation.SilentFilms;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

final class CustomSoundtrackManager {
	private static final String PACK_ID = "silentfilms-custom-ost";
	private static final String CUSTOM_NAMESPACE = "silentfilms_custom";
	private static final List<String> STATES = List.of(
			"main_menu",
			"peaceful",
			"village",
			"travel",
			"caves",
			"danger",
			"comedy",
			"night"
	);
	private static final Map<String, List<Identifier>> EMPTY_TRACKS = Map.of();
	private static volatile Map<String, List<Identifier>> tracks = EMPTY_TRACKS;
	private static volatile boolean reloadInProgress;

	private CustomSoundtrackManager() {
	}

	static void initialize(Minecraft client) {
		ensureDirectories(client);
		if (SilentFilmsClient.config().soundtrackMode() == SoundtrackMode.CUSTOM) {
			reload(client);
		}
	}

	static void reload(Minecraft client) {
		ensureDirectories(client);
		if (reloadInProgress) {
			return;
		}

		reloadInProgress = true;
		CompletableFuture
				.supplyAsync(() -> preparePack(client), Util.ioPool())
				.thenAcceptAsync(prepared -> applyPack(client, prepared), client)
				.exceptionally(exception -> {
					SilentFilms.LOGGER.warn("Could not prepare the custom Silent Films soundtrack.", exception);
					client.execute(() -> reloadInProgress = false);
					return null;
				});
	}

	static List<Identifier> tracksFor(String state) {
		return tracks.getOrDefault(state, List.of());
	}

	static int trackCount() {
		return tracks.values().stream().mapToInt(List::size).sum();
	}

	static Path directory(Minecraft client) {
		return client.gameDirectory.toPath().resolve("silentfilms").resolve("ost");
	}

	static void openFolder(Minecraft client) {
		Path folder = ensureDirectories(client);
		Util.getPlatform().openPath(folder);
	}

	private static Path ensureDirectories(Minecraft client) {
		Path root = directory(client);
		try {
			Files.createDirectories(root);
			for (String state : STATES) {
				Files.createDirectories(root.resolve(state));
			}
			Path readme = root.resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, "Place .ogg files in the folder matching the scene.\n\n"
						+ "main_menu, peaceful, village, travel, caves, danger, comedy, night\n"
						+ "Then press Reload Soundtracks in the Silent Films menu.\n");
			}
		} catch (IOException exception) {
			SilentFilms.LOGGER.warn("Could not create the custom soundtrack folders.", exception);
		}
		return root;
	}

	private static PreparedPack preparePack(Minecraft client) {
		Path sourceRoot = directory(client);
		Path generatedRoot = client.getResourcePackDirectory().resolve(PACK_ID);
		Map<String, List<TrackFile>> discovered = new LinkedHashMap<>();

		for (String state : STATES) {
			List<TrackFile> stateTracks = new ArrayList<>();
			Path stateDirectory = sourceRoot.resolve(state);
			try (Stream<Path> files = Files.list(stateDirectory)) {
				Set<String> usedNames = new LinkedHashSet<>();
				files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
						.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
						.sorted()
						.forEach(path -> {
							String name = uniqueName(sanitizeName(path.getFileName().toString()), usedNames);
							if (name != null) {
								stateTracks.add(new TrackFile(path, name));
							}
						});
			} catch (IOException exception) {
				SilentFilms.LOGGER.warn("Could not scan custom soundtrack folder {}.", stateDirectory, exception);
			}
			discovered.put(state, stateTracks);
		}

		int count = discovered.values().stream().mapToInt(List::size).sum();
		try {
			deleteDirectory(generatedRoot);
			if (count == 0) {
				return new PreparedPack(Map.of(), false);
			}

			Files.createDirectories(generatedRoot);
			Files.writeString(generatedRoot.resolve("pack.mcmeta"),
					"{\n  \"pack\": {\n    \"pack_format\": 64,\n"
							+ "    \"description\": \"Silent Films Custom Soundtrack\"\n  }\n}\n");

			Path soundRoot = generatedRoot.resolve("assets").resolve(CUSTOM_NAMESPACE).resolve("sounds").resolve("music").resolve("custom");
			Files.createDirectories(soundRoot);
			StringBuilder soundsJson = new StringBuilder("{\n");
			Map<String, List<Identifier>> discoveredIds = new LinkedHashMap<>();
			boolean first = true;
			for (Map.Entry<String, List<TrackFile>> entry : discovered.entrySet()) {
				String state = entry.getKey();
				Path stateSoundRoot = soundRoot.resolve(state);
				Files.createDirectories(stateSoundRoot);
				List<Identifier> stateIds = new ArrayList<>();
				for (TrackFile track : entry.getValue()) {
					Path destination = stateSoundRoot.resolve(track.resourceName + ".ogg");
					Files.copy(track.source, destination, StandardCopyOption.REPLACE_EXISTING);
					Identifier eventId = Identifier.fromNamespaceAndPath(CUSTOM_NAMESPACE,
							"custom_music." + state + "." + track.resourceName);
					String soundPath = CUSTOM_NAMESPACE + ":music/custom/" + state + "/" + track.resourceName;
					if (!first) {
						soundsJson.append(",\n");
					}
					first = false;
					soundsJson.append("  \"").append(eventId.getPath()).append("\": {\"sounds\": [{\"name\": \"")
							.append(soundPath).append("\", \"stream\": true}]}" );
					stateIds.add(eventId);
				}
				discoveredIds.put(state, List.copyOf(stateIds));
			}
			soundsJson.append("\n}\n");
			Files.writeString(generatedRoot.resolve("assets").resolve(CUSTOM_NAMESPACE).resolve("sounds.json"), soundsJson);
			return new PreparedPack(Map.copyOf(discoveredIds), true);
		} catch (IOException exception) {
			throw new RuntimeException(exception);
		}
	}

	private static void applyPack(Minecraft client, PreparedPack prepared) {
		try {
			PackRepository repository = client.getResourcePackRepository();
			repository.reload();
			if (prepared.hasTracks) {
				if (!repository.addPack(PACK_ID)) {
					throw new IllegalStateException("Generated custom soundtrack pack was not discovered");
				}
			} else {
				repository.removePack(PACK_ID);
			}
			ClientTransitionController.setResourceReloadSuppressed(true);
			client.reloadResourcePacks().whenComplete((ignored, exception) -> client.execute(() -> {
				ClientTransitionController.setResourceReloadSuppressed(false);
				if (exception != null) {
					SilentFilms.LOGGER.warn("Could not reload the custom Silent Films soundtrack.", exception);
				} else {
					tracks = prepared.tracks;
					ClientAudioController.refreshSoundtrack();
				}
				reloadInProgress = false;
			}));
		} catch (RuntimeException exception) {
			ClientTransitionController.setResourceReloadSuppressed(false);
			reloadInProgress = false;
			SilentFilms.LOGGER.warn("Could not activate the custom Silent Films soundtrack.", exception);
		}
	}

	private static String sanitizeName(String filename) {
		String base = filename.substring(0, filename.length() - 4).toLowerCase(Locale.ROOT);
		String sanitized = base.replaceAll("[^a-z0-9._-]+", "_");
		return sanitized.isBlank() ? null : sanitized;
	}

	private static String uniqueName(String base, Set<String> usedNames) {
		if (base == null) {
			return null;
		}
		String candidate = base;
		int suffix = 2;
		while (!usedNames.add(candidate)) {
			candidate = base + "-" + suffix++;
		}
		return candidate;
	}

	private static void deleteDirectory(Path directory) throws IOException {
		if (!Files.exists(directory)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(directory)) {
			for (Path path : paths.sorted(Comparator.comparingInt(Path::getNameCount).reversed()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}

	private record TrackFile(Path source, String resourceName) {
	}

	private record PreparedPack(Map<String, List<Identifier>> tracks, boolean hasTracks) {
	}
}
