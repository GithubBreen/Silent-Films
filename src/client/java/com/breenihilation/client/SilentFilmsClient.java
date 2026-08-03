package com.breenihilation.client;

import com.breenihilation.DialogueRanges;
import com.breenihilation.SilentFilms;
import com.breenihilation.TransitionPayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.UUID;

public final class SilentFilmsClient implements ClientModInitializer {
	private static final long INTERTITLE_FREEZE_HEARTBEAT_TICKS = 20L;
	private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
			SilentFilms.id("general")
	);

	public static final KeyMapping OPEN_MENU_KEY = new KeyMapping(
			"key.silentfilms.open_menu",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K,
			KEY_CATEGORY
	);

	private static SilentFilmsConfig config;
	private static IntertitleQueue intertitles;
	private static long clientTicks;
	private static boolean intertitleFreezeReported;
	private static long lastIntertitleFreezeHeartbeat = Long.MIN_VALUE;

	@Override
	public void onInitializeClient() {
		Minecraft client = Minecraft.getInstance();
		Path configPath = client.gameDirectory.toPath().resolve("config").resolve("silentfilms-client.json");
		config = SilentFilmsConfig.load(configPath, SilentFilms.LOGGER);
		intertitles = new IntertitleQueue();
		VoiceIntertitleClient.initialize();
		ClientLifecycleEvents.CLIENT_STARTED.register(clientInstance -> {
			ClientAudioController.initialize(clientInstance);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(clientInstance -> {
			VoiceIntertitleClient.shutdown();
			ClientAudioController.shutdown();
		});

		KeyMappingHelper.registerKeyMapping(OPEN_MENU_KEY);
		ClientPlayNetworking.registerGlobalReceiver(
				TransitionPayloads.TravelStart.TYPE,
				(payload, context) -> context.client().execute(() -> ClientTransitionController.onTravelStart(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				TransitionPayloads.TravelRelease.TYPE,
				(payload, context) -> context.client().execute(() -> ClientTransitionController.irisTransition().releaseTravel(
						payload.ticketId(),
						payload.finalRelease()
				))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				TransitionPayloads.SleepState.TYPE,
				(payload, context) -> context.client().execute(() -> ClientTransitionController.onSleepState(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				TransitionPayloads.VoiceTranscriptCard.TYPE,
				(payload, context) -> context.client().execute(() -> onVoiceTranscript(payload))
		);
		ClientTickEvents.END_CLIENT_TICK.register(SilentFilmsClient::onClientTick);
	}

	private static void onClientTick(Minecraft client) {
		clientTicks++;
		ClientTransitionController.tickMenu(client);
		VoiceIntertitleClient.tick();
		ClientAudioController.tick(client, config);
		VoiceSetupInstaller.maybePrompt(client);
		if (OPEN_MENU_KEY.consumeClick()) {
			Screen current = client.gui.screen();
			if (current instanceof SilentFilmsScreen) {
				client.gui.setScreen(null);
			} else if (current == null && client.player != null) {
				client.gui.setScreen(new SilentFilmsScreen(null));
			}
		}

		if (client.level == null || client.player == null) {
			intertitles.clear();
			ClientTransitionController.resetWorld();
			VoiceIntertitleClient.reset();
			intertitleFreezeReported = false;
			lastIntertitleFreezeHeartbeat = Long.MIN_VALUE;
			FilmRenderer.updatePostEffect(client);
			return;
		}

		sendTravelReady(ClientTransitionController.tickWorld(config.irisTransitionsEnabled, client.player.isSleeping()));

		if (!ClientTransitionController.suppressesIntertitles()) {
			intertitles.tick(config.intertitleDurationTicks);
		}
		updateIntertitleFreezeState();
		FilmRenderer.updatePostEffect(client);
	}

	private static void updateIntertitleFreezeState() {
		boolean active = config.intertitlesEnabled
				&& intertitles.active() != null
				&& !ClientTransitionController.suppressesIntertitles();
		boolean heartbeatDue = active
				&& clientTicks - lastIntertitleFreezeHeartbeat >= INTERTITLE_FREEZE_HEARTBEAT_TICKS;
		if (active == intertitleFreezeReported && !heartbeatDue) {
			return;
		}
		if (!ClientPlayNetworking.canSend(TransitionPayloads.IntertitleFreezeState.TYPE)) {
			return;
		}
		ClientPlayNetworking.send(new TransitionPayloads.IntertitleFreezeState(active));
		intertitleFreezeReported = active;
		lastIntertitleFreezeHeartbeat = clientTicks;
	}

	public static SilentFilmsConfig config() {
		return config;
	}

	public static IntertitleQueue intertitles() {
		return intertitles;
	}

	public static long clientTicks() {
		return clientTicks;
	}

	private static void sendTravelReady(int ticketId) {
		if (ticketId < 0) {
			return;
		}
		try {
			if (ClientPlayNetworking.canSend(TransitionPayloads.TravelReady.TYPE)) {
				ClientPlayNetworking.send(new TransitionPayloads.TravelReady(ticketId));
			}
		} catch (IllegalStateException ignored) {
			// The client may be between play connections while a transition resets.
		}
	}

	public static void saveConfig() {
		if (config != null) {
			config.save(SilentFilms.LOGGER);
		}
	}

	public static void onRemoteChat(UUID senderId, String sender, String message) {
		if (config == null || !config.intertitlesEnabled || !config.chatIntertitles) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null || senderId == null) {
			return;
		}

		Player senderPlayer = null;
		for (Player candidate : client.level.players()) {
			if (senderId.equals(candidate.getUUID())) {
				senderPlayer = candidate;
				break;
			}
		}

		if (senderPlayer == null
				|| senderPlayer.distanceToSqr(client.player)
				> DialogueRanges.squared(DialogueRanges.CHAT_BLOCKS)) {
			return;
		}

		intertitles.enqueueChat(senderId, sender, message);
	}

	private static void onVoiceTranscript(TransitionPayloads.VoiceTranscriptCard payload) {
		if (config == null || !config.intertitlesEnabled || !config.voiceIntertitles) {
			return;
		}
		intertitles.enqueueChat(payload.senderId(), payload.sender(), payload.text());
	}

}
