package com.breenihilation;

// Defines the packets shared by the client and server transition systems.
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public final class TransitionPayloads {
	private static final int MAX_INTERTITLE_TEXT_LENGTH = 32_767;
	private TransitionPayloads() {
	}

	public record VoiceTranscriptRequest(String text, boolean whispering) implements CustomPacketPayload {
		public static final Type<VoiceTranscriptRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("silentfilms", "voice_transcript"));
		public static final StreamCodec<RegistryFriendlyByteBuf, VoiceTranscriptRequest> CODEC = StreamCodec.of(
				(buf, payload) -> {
					buf.writeUtf(payload.text, MAX_INTERTITLE_TEXT_LENGTH);
					buf.writeBoolean(payload.whispering);
				},
				buf -> new VoiceTranscriptRequest(buf.readUtf(MAX_INTERTITLE_TEXT_LENGTH), buf.readBoolean())
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record VoiceTranscriptCard(UUID senderId, String sender, String text) implements CustomPacketPayload {
		public static final Type<VoiceTranscriptCard> TYPE = new Type<>(Identifier.fromNamespaceAndPath("silentfilms", "voice_intertitle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, VoiceTranscriptCard> CODEC = StreamCodec.of(
				(buf, payload) -> {
					buf.writeUUID(payload.senderId);
					buf.writeUtf(payload.sender, 64);
					buf.writeUtf(payload.text, MAX_INTERTITLE_TEXT_LENGTH);
				},
				buf -> new VoiceTranscriptCard(buf.readUUID(), buf.readUtf(64), buf.readUtf(MAX_INTERTITLE_TEXT_LENGTH))
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record IntertitleFreezeState(boolean active) implements CustomPacketPayload {
		public static final Type<IntertitleFreezeState> TYPE = new Type<>(
				Identifier.fromNamespaceAndPath("silentfilms", "intertitle_freeze_state")
		);
		public static final StreamCodec<RegistryFriendlyByteBuf, IntertitleFreezeState> CODEC = StreamCodec.of(
				(buf, payload) -> buf.writeBoolean(payload.active),
				buf -> new IntertitleFreezeState(buf.readBoolean())
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final class TravelStart implements CustomPacketPayload {
		public static final Type<TravelStart> TYPE = new Type<>(Identifier.fromNamespaceAndPath("silentfilms", "travel_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TravelStart> CODEC = StreamCodec.of(
				(buf, payload) -> {
					buf.writeVarInt(payload.ticketId);
					buf.writeVarInt(payload.edgeTicks);
					buf.writeBoolean(payload.awaitAcknowledgement);
					buf.writeBoolean(payload.resumable);
					buf.writeVarInt(payload.subjectEntityId);
					buf.writeDouble(payload.originX);
					buf.writeDouble(payload.originY);
					buf.writeDouble(payload.originZ);
					buf.writeDouble(payload.destinationX);
					buf.writeDouble(payload.destinationY);
					buf.writeDouble(payload.destinationZ);
					buf.writeBoolean(payload.checkOrigin);
					buf.writeBoolean(payload.checkDestination);
				},
				buf -> new TravelStart(
						buf.readVarInt(),
						buf.readVarInt(),
						buf.readBoolean(),
						buf.readBoolean(),
						buf.readVarInt(),
						buf.readDouble(),
						buf.readDouble(),
						buf.readDouble(),
						buf.readDouble(),
						buf.readDouble(),
						buf.readDouble(),
						buf.readBoolean(),
						buf.readBoolean()
				)
		);

		private final int ticketId;
		private final int edgeTicks;
		private final boolean awaitAcknowledgement;
		private final boolean resumable;
		private final int subjectEntityId;
		private final double originX;
		private final double originY;
		private final double originZ;
		private final double destinationX;
		private final double destinationY;
		private final double destinationZ;
		private final boolean checkOrigin;
		private final boolean checkDestination;

		public TravelStart(
				int ticketId,
				int edgeTicks,
				boolean awaitAcknowledgement,
				boolean resumable,
				int subjectEntityId,
				double originX,
				double originY,
				double originZ,
				double destinationX,
				double destinationY,
				double destinationZ,
				boolean checkOrigin,
				boolean checkDestination
		) {
			this.ticketId = ticketId;
			this.edgeTicks = edgeTicks;
			this.awaitAcknowledgement = awaitAcknowledgement;
			this.resumable = resumable;
			this.subjectEntityId = subjectEntityId;
			this.originX = originX;
			this.originY = originY;
			this.originZ = originZ;
			this.destinationX = destinationX;
			this.destinationY = destinationY;
			this.destinationZ = destinationZ;
			this.checkOrigin = checkOrigin;
			this.checkDestination = checkDestination;
		}

		public int ticketId() {
			return ticketId;
		}

		public int edgeTicks() {
			return edgeTicks;
		}

		public boolean awaitAcknowledgement() {
			return awaitAcknowledgement;
		}

		public boolean resumable() {
			return resumable;
		}

		public int subjectEntityId() {
			return subjectEntityId;
		}

		public double originX() {
			return originX;
		}

		public double originY() {
			return originY;
		}

		public double originZ() {
			return originZ;
		}

		public double destinationX() {
			return destinationX;
		}

		public double destinationY() {
			return destinationY;
		}

		public double destinationZ() {
			return destinationZ;
		}

		public boolean checkOrigin() {
			return checkOrigin;
		}

		public boolean checkDestination() {
			return checkDestination;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final class TravelReady implements CustomPacketPayload {
		public static final Type<TravelReady> TYPE = new Type<>(Identifier.fromNamespaceAndPath("silentfilms", "travel_ready"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TravelReady> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				TravelReady::ticketId,
				TravelReady::new
		);

		private final int ticketId;

		public TravelReady(int ticketId) {
			this.ticketId = ticketId;
		}

		public int ticketId() {
			return ticketId;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final class TravelRelease implements CustomPacketPayload {
		public static final Type<TravelRelease> TYPE = new Type<>(Identifier.fromNamespaceAndPath("silentfilms", "travel_release"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TravelRelease> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				TravelRelease::ticketId,
				ByteBufCodecs.BOOL,
				TravelRelease::finalRelease,
				TravelRelease::new
		);

		private final int ticketId;
		private final boolean finalRelease;

		public TravelRelease(int ticketId) {
			this(ticketId, true);
		}

		public TravelRelease(int ticketId, boolean finalRelease) {
			this.ticketId = ticketId;
			this.finalRelease = finalRelease;
		}

		public int ticketId() {
			return ticketId;
		}

		public boolean finalRelease() {
			return finalRelease;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static final class SleepState implements CustomPacketPayload {
		public static final Type<SleepState> TYPE = new Type<>(Identifier.fromNamespaceAndPath("silentfilms", "sleep_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SleepState> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				SleepState::sessionId,
				ByteBufCodecs.BOOL,
				SleepState::active,
				ByteBufCodecs.BOOL,
				SleepState::targetClosed,
				ByteBufCodecs.BOOL,
				SleepState::timeSkipped,
				SleepState::new
		);

		private final int sessionId;
		private final boolean active;
		private final boolean targetClosed;
		private final boolean timeSkipped;

		public SleepState(int sessionId, boolean active, boolean targetClosed, boolean timeSkipped) {
			this.sessionId = sessionId;
			this.active = active;
			this.targetClosed = targetClosed;
			this.timeSkipped = timeSkipped;
		}

		public int sessionId() {
			return sessionId;
		}

		public boolean active() {
			return active;
		}

		public boolean targetClosed() {
			return targetClosed;
		}

		public boolean timeSkipped() {
			return timeSkipped;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(VoiceTranscriptRequest.TYPE, VoiceTranscriptRequest.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VoiceTranscriptCard.TYPE, VoiceTranscriptCard.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(IntertitleFreezeState.TYPE, IntertitleFreezeState.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TravelStart.TYPE, TravelStart.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TravelReady.TYPE, TravelReady.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TravelRelease.TYPE, TravelRelease.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SleepState.TYPE, SleepState.CODEC);
	}
}
