package com.breenihilation;

// Registers the common-side events and network payloads for Silent Films.
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SilentFilms implements ModInitializer {
	public static final String MOD_ID = "silentfilms";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TransitionPayloads.register();
		ServerPlayNetworking.registerGlobalReceiver(
				TransitionPayloads.TravelReady.TYPE,
				(payload, context) -> ServerTransitionCoordinator.acknowledgeTravel(context.player(), payload.ticketId())
		);
		ServerPlayNetworking.registerGlobalReceiver(
				TransitionPayloads.VoiceTranscriptRequest.TYPE,
				(payload, context) -> VoiceTranscriptRouter.receive(context.player(), payload)
		);
		ServerPlayNetworking.registerGlobalReceiver(
				TransitionPayloads.IntertitleFreezeState.TYPE,
				(payload, context) -> IntertitleFreezeZones.update(context.player(), payload.active())
		);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerTransitionCoordinator.tickTeleports(server);
			ServerTransitionCoordinator.tickTimeTransitions(server);
			IntertitleFreezeZones.tick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> IntertitleFreezeZones.clear());
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
