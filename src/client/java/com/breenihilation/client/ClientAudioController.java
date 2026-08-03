package com.breenihilation.client;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;



final class ClientAudioController {
	private static final double MUTED_VOLUME = 0.0;
	private static final double ENABLED_VOLUME = 1.0;
	private static final float VOLUME_EPSILON = 0.001f;
	private static final SoundSource[] GAMEPLAY_SOURCES = {
			SoundSource.RECORDS,
			SoundSource.WEATHER,
			SoundSource.BLOCKS,
			SoundSource.HOSTILE,
			SoundSource.NEUTRAL,
			SoundSource.PLAYERS,
			SoundSource.AMBIENT,
			SoundSource.VOICE
	};

	private static final DynamicMusicDirector MUSIC_DIRECTOR = new DynamicMusicDirector();

	private ClientAudioController() {
	}

	static void initialize(Minecraft client) {
		applyInitialDefaults(client);
		CustomSoundtrackManager.initialize(client);
	}

	static void shutdown() {
		MUSIC_DIRECTOR.shutdown();
	}

	static void tick(Minecraft client, SilentFilmsConfig config) {
		MUSIC_DIRECTOR.tick(client, config == null ? SoundtrackMode.VANILLA : config.soundtrackMode());
	}

	static boolean gameplaySoundsMuted() {
		Minecraft client = Minecraft.getInstance();
		for (SoundSource source : GAMEPLAY_SOURCES) {
			if (!isGameplaySourceMuted(client.options.getSoundSourceVolume(source))) {
				return false;
			}
		}
		return true;
	}

	static boolean isGameplaySourceMuted(double volume) {
		return volume <= VOLUME_EPSILON;
	}

	static void setGameplaySoundsMuted(boolean muted) {
		Minecraft client = Minecraft.getInstance();
		for (SoundSource source : GAMEPLAY_SOURCES) {
			client.options.getSoundSourceOptionInstance(source).set(muted ? MUTED_VOLUME : ENABLED_VOLUME);
		}
		client.options.save();
	}

	static void setSoundtrackMode(SoundtrackMode mode) {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null) {
			return;
		}
		config.setSoundtrackMode(mode);
		SilentFilmsClient.saveConfig();
		if (config.soundtrackMode() == SoundtrackMode.CUSTOM) {
			CustomSoundtrackManager.reload(Minecraft.getInstance());
		} else {
			MUSIC_DIRECTOR.refresh();
		}
	}

	static void reloadCustomSoundtrack() {
		CustomSoundtrackManager.reload(Minecraft.getInstance());
	}

	static void refreshSoundtrack() {
		MUSIC_DIRECTOR.refresh();
	}

	private static void applyInitialDefaults(Minecraft client) {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config.soundCategoriesDefaultApplied) {
			return;
		}

		if (config.masterVolumeDefaultApplied
				&& client.options.getSoundSourceVolume(SoundSource.MASTER) <= VOLUME_EPSILON) {
			client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(ENABLED_VOLUME);
		}
		setGameplaySoundsMuted(true);
		config.masterVolumeDefaultApplied = true;
		config.soundCategoriesDefaultApplied = true;
		SilentFilmsClient.saveConfig();
	}
}
