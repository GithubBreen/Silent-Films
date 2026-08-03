package com.breenihilation.client;

// Stores client preferences and persists them to the config file.
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SilentFilmsConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean filmModeEnabled = true;
	public boolean monochromeEnabled = true;
	public boolean grainEnabled = true;
	public boolean vignetteEnabled = true;
	public boolean letterboxEnabled = false;
	public boolean flickerEnabled = true;
	public boolean irisTransitionsEnabled = true;

	public boolean intertitlesEnabled = true;
	public boolean chatIntertitles = true;
	public boolean voiceIntertitles = false;
	public boolean suppressVoiceAudio = true;
	public boolean voiceSetupPromptDismissed = false;
	public int intertitleDurationTicks = 80;
	public int intertitleTextScale = 1;
	public boolean masterVolumeDefaultApplied = false;
	public boolean soundCategoriesDefaultApplied = false;
	public String soundtrackMode = SoundtrackMode.ON.name();
	@Deprecated
	public boolean dynamicSoundtrackEnabled = true;

	private transient Path path;

	public static SilentFilmsConfig load(Path path, Logger logger) {
		SilentFilmsConfig result = defaults();
		result.path = path;

		if (!Files.exists(path)) {
			result.save(logger);
			return result;
		}

		try {
			String json = Files.readString(path);
			SilentFilmsConfig loaded = GSON.fromJson(json, SilentFilmsConfig.class);
			if (loaded != null) {
				// Older profiles only had a boolean. Preserve their meaning while
				// moving them to the four-state selector.
				if (!JsonParser.parseString(json).getAsJsonObject().has("soundtrackMode")) {
					loaded.setSoundtrackMode(loaded.dynamicSoundtrackEnabled ? SoundtrackMode.ON : SoundtrackMode.VANILLA);
				}
				loaded.path = path;
				return loaded;
			}
		} catch (Exception exception) {
			logger.warn("Could not read Silent Films client settings; using defaults.", exception);
		}

		return result;
	}

	public static SilentFilmsConfig defaults() {
		return new SilentFilmsConfig();
	}

	public void reset() {
		SilentFilmsConfig defaults = defaults();
		filmModeEnabled = defaults.filmModeEnabled;
		monochromeEnabled = defaults.monochromeEnabled;
		grainEnabled = defaults.grainEnabled;
		vignetteEnabled = defaults.vignetteEnabled;
		letterboxEnabled = defaults.letterboxEnabled;
		flickerEnabled = defaults.flickerEnabled;
		irisTransitionsEnabled = defaults.irisTransitionsEnabled;
		intertitlesEnabled = defaults.intertitlesEnabled;
		chatIntertitles = defaults.chatIntertitles;
		voiceIntertitles = defaults.voiceIntertitles;
		suppressVoiceAudio = defaults.suppressVoiceAudio;
		voiceSetupPromptDismissed = defaults.voiceSetupPromptDismissed;
		intertitleDurationTicks = defaults.intertitleDurationTicks;
		intertitleTextScale = defaults.intertitleTextScale;
		soundtrackMode = defaults.soundtrackMode;
		dynamicSoundtrackEnabled = defaults.dynamicSoundtrackEnabled;
	}

	public SoundtrackMode soundtrackMode() {
		return SoundtrackMode.fromConfig(soundtrackMode);
	}

	public void setSoundtrackMode(SoundtrackMode mode) {
		SoundtrackMode selected = mode == null ? SoundtrackMode.ON : mode;
		soundtrackMode = selected.name();
		// Keep this field synchronized for profiles or tooling that still reads
		// the pre-selector setting.
		dynamicSoundtrackEnabled = selected == SoundtrackMode.ON;
	}

	public void save(Logger logger) {
		if (path == null) {
			return;
		}

		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException exception) {
			logger.warn("Could not save Silent Films client settings.", exception);
		}
	}
}
