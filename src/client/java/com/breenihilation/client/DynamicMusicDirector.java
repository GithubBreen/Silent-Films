package com.breenihilation.client;


import com.breenihilation.SilentFilms;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// dynamic music

final class DynamicMusicDirector {
	private static final int SAMPLE_INTERVAL = 10;
	private static final int CROSSFADE_TICKS = 80;
	private static final int NORMAL_MOOD_HOLD_TICKS = 100;
	private static final int DANGER_EXIT_HOLD_TICKS = 80;
	private static final int BETWEEN_TRACK_TICKS = 50;
	private static final int IMMEDIATE_FADE_TICKS = 30;
	private static final int RESET_FADE_TICKS = 40;
	private static final double MOOD_SAMPLE_RADIUS = 28.0;
	private static final float PLAYBACK_VOLUME = 0.82f;
	private static final int HISTORY_SIZE = 4;

	private static final Map<Mood, List<Identifier>> BUNDLED_TRACKS = createTracks();
	private final Random random = new Random();
	private final ArrayDeque<Identifier> history = new ArrayDeque<>();
	private FadingMusicInstance current;
	private Identifier currentTrack;
	private Mood currentMood;
	private Mood pendingMood;
	private int pendingTicks;
	private int currentAge;
	private int nextTrackDelay;
	private int sampleCountdown;
	private SoundtrackMode previousMode;
	private SoundtrackMode activeMode = SoundtrackMode.ON;

	void tick(Minecraft minecraft, SoundtrackMode mode) {
		activeMode = mode;
		if (mode != previousMode) {
			previousMode = mode;
			fadeOutAndReset();
			sampleCountdown = 0;
		}

		if (mode == SoundtrackMode.VANILLA) {
			return;
		}
		if (mode == SoundtrackMode.OFF) {
			minecraft.getMusicManager().stopPlaying();
			fadeOutAndReset();
			return;
		}

		if (--sampleCountdown <= 0) {
			sampleCountdown = SAMPLE_INTERVAL;
			minecraft.getMusicManager().stopPlaying();
			observeMood(minecraft, detectMood(minecraft));
		}

		if (current != null) {
			currentAge++;
			if (currentAge > 20 && !minecraft.getSoundManager().isActive(current)) {
				current = null;
				currentTrack = null;
				currentAge = 0;
				nextTrackDelay = BETWEEN_TRACK_TICKS;
			}
		}

		if (current == null && currentMood != null && nextTrackDelay-- <= 0) {
			startTrack(minecraft, currentMood, false);
		}
	}

	void shutdown() {
		fadeOutAndReset();
	}

	void refresh() {
		fadeOutAndReset();
		sampleCountdown = 0;
	}

	private void observeMood(Minecraft minecraft, Mood observed) {
		if (observed == null) {
			fadeOutAndReset();
			return;
		}
		if (currentMood == null) {
			currentMood = observed;
			pendingMood = null;
			startTrack(minecraft, observed, false);
			return;
		}
		if (observed == currentMood) {
			pendingMood = null;
			pendingTicks = 0;
			return;
		}

		if (observed != pendingMood) {
			pendingMood = observed;
			pendingTicks = SAMPLE_INTERVAL;
		} else {
			pendingTicks += SAMPLE_INTERVAL;
		}

		int hold = observed == Mood.DANGER || observed == Mood.MAIN_MENU
				? SAMPLE_INTERVAL
				: currentMood == Mood.DANGER ? DANGER_EXIT_HOLD_TICKS : NORMAL_MOOD_HOLD_TICKS;
		if (pendingTicks >= hold) {
			currentMood = observed;
			pendingMood = null;
			pendingTicks = 0;
			startTrack(minecraft, observed, true);
		}
	}

	private void startTrack(Minecraft minecraft, Mood mood, boolean crossfade) {
		Identifier next = chooseTrack(mood);
		if (next == null) {
			return;
		}
		if (current != null) {
			current.fadeTo(0.0f, crossfade ? CROSSFADE_TICKS : IMMEDIATE_FADE_TICKS, true);
		}

		FadingMusicInstance incoming = new FadingMusicInstance(next, 0.0f);
		incoming.fadeTo(PLAYBACK_VOLUME, CROSSFADE_TICKS, false);
		minecraft.getSoundManager().play(incoming);
		current = incoming;
		currentTrack = next;
		currentAge = 0;
		nextTrackDelay = 0;
		remember(next);
	}

	private Identifier chooseTrack(Mood mood) {
		List<Identifier> custom = activeMode == SoundtrackMode.CUSTOM
				? CustomSoundtrackManager.tracksFor(mood.folder())
				: List.of();
		List<Identifier> pool = new ArrayList<>(custom.isEmpty()
				? BUNDLED_TRACKS.getOrDefault(mood, List.of())
				: custom);
		if (mood == Mood.PEACEFUL && random.nextInt(3) == 0) {
			List<Identifier> comedy = activeMode == SoundtrackMode.CUSTOM
					? CustomSoundtrackManager.tracksFor(Mood.COMEDY.folder())
					: List.of();
			pool = new ArrayList<>(comedy.isEmpty() ? BUNDLED_TRACKS.get(Mood.COMEDY) : comedy);
		}
		pool.removeIf(track -> track.equals(currentTrack) || history.contains(track));
		if (pool.isEmpty()) {
			pool = new ArrayList<>(custom.isEmpty()
					? BUNDLED_TRACKS.getOrDefault(mood, List.of())
					: custom);
			pool.remove(currentTrack);
		}
		return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
	}

	private void remember(Identifier track) {
		history.remove(track);
		history.addLast(track);
		while (history.size() > HISTORY_SIZE) {
			history.removeFirst();
		}
	}

	private void fadeOutAndReset() {
		if (current != null) {
			current.fadeTo(0.0f, RESET_FADE_TICKS, true);
		}
		current = null;
		currentTrack = null;
		currentMood = null;
		pendingMood = null;
		pendingTicks = 0;
		currentAge = 0;
		nextTrackDelay = 0;
	}
 // fucking mood lmfao
	private static Mood detectMood(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return minecraft.gui.screen() != null && minecraft.gui.overlay() == null ? Mood.MAIN_MENU : null;
		}

		var player = minecraft.player;
		var level = minecraft.level;
		List<Entity> nearby = level.getEntities(
				player,
				player.getBoundingBox().inflate(MOOD_SAMPLE_RADIUS),
				entity -> entity.isAlive() && entity != player
		);
		boolean immediateThreat = player.hurtTime > 0 || player.getHealth() <= 8.0f || nearby.stream().anyMatch(entity -> {
			double distanceSquared = entity.distanceToSqr(player);
			return entity instanceof Projectile && distanceSquared <= 64.0
					|| entity instanceof Enemy && distanceSquared <= 324.0
					&& (distanceSquared <= 100.0 || player.hasLineOfSight(entity));
		});
		if (immediateThreat) {
			return Mood.DANGER;
		}

		boolean cave = player.getY() < level.getSeaLevel() - 4
				&& level.getEffectiveSkyBrightness(player.blockPosition()) < 7;
		if (cave) {
			return Mood.CAVES;
		}
		if (player.isPassenger() || player.getDeltaMovement().horizontalDistanceSqr() > 0.035) {
			return Mood.TRAVEL;
		}
		if (nearby.stream().anyMatch(entity -> entity instanceof Villager && entity.distanceToSqr(player) <= 784.0)) {
			return Mood.VILLAGE;
		}

		long dayTime = Math.floorMod(level.getOverworldClockTime(), 24_000L);
		if (dayTime >= 13_000L && dayTime < 23_000L) {
			return Mood.NIGHT;
		}
		return Mood.PEACEFUL;
	}

	private static Map<Mood, List<Identifier>> createTracks() {
		Map<Mood, List<Identifier>> tracks = new EnumMap<>(Mood.class);
		tracks.put(Mood.MAIN_MENU, ids("main_menu", "friendly_day", "olde_timey"));
		tracks.put(Mood.PEACEFUL, ids("peaceful_overworld", "fig_leaf_times_two", "look_busy", "plucky_daisy",
				"wagon_wheel", "work_is_work_fx"));
		tracks.put(Mood.VILLAGE, ids("villages_social", "barroom_ballet", "breaktime", "five_card_shuffle",
				"lively_lumpsucker", "royal_banana"));
		tracks.put(Mood.TRAVEL, ids("travel", "gold_rush", "hand_trolley", "iron_horse", "keystone_deluge"));
		tracks.put(Mood.CAVES, ids("caves_darkness", "bad_ideas_distressed", "dark_hallway_distressed",
				"mister_exposition"));
		tracks.put(Mood.DANGER, ids("danger_combat", "amazing_plan_distressed", "iron_horse_distressed",
				"the_bandit", "trouble", "villainous_treachery", "villainous_treachery_distressed"));
		tracks.put(Mood.COMEDY, ids("comedy", "amazing_plan", "comic_plodding", "fun_in_a_bottle",
				"hammock_fight", "hyperfun", "mr_mealeys_mediocre_machine"));
		tracks.put(Mood.NIGHT, ids("night", "batty_mcfaddin_slower", "fig_leaf_rag_distressed",
				"merry_go_distressed", "merry_go_slower_distressed", "waltz_of_treachery_fx"));
		return Map.copyOf(tracks);
	}

	private static List<Identifier> ids(String mood, String... names) {
		List<Identifier> result = new ArrayList<>(names.length);
		for (String name : names) {
			result.add(SilentFilms.id("music." + mood + "." + name));
		}
		return List.copyOf(result);
	}

	private enum Mood {
		MAIN_MENU("main_menu"),
		PEACEFUL("peaceful"),
		VILLAGE("village"),
		TRAVEL("travel"),
		CAVES("caves"),
		DANGER("danger"),
		COMEDY("comedy"),
		NIGHT("night");

		private final String folder;

		Mood(String folder) {
			this.folder = folder;
		}

		private String folder() {
			return folder;
		}
	}
}
