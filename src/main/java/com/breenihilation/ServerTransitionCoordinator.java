package com.breenihilation;

// Coordinates server-side travel, sleep, and time-change transition state.
import com.breenihilation.access.ServerLevelAccess;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerTransitionCoordinator {
	private static final int TRAVEL_EDGE_TICKS = 12;
	private static final int TIME_EDGE_TICKS = 30;
	private static final int TRAVEL_TIMEOUT_TICKS = 40;
	private static final int TIME_TIMEOUT_TICKS = 80;
	private static final int SLEEP_REVERSE_GRACE_TICKS = 60;
	private static final double OBSERVER_RADIUS = 32.0;
	private static final long TICKS_PER_DAY = 24_000L;

	private static final Map<UUID, PendingTeleport> PENDING_TELEPORTS = new HashMap<>();
	private static final Map<Integer, PendingTimeTransition> PENDING_TIME_TRANSITIONS = new HashMap<>();
	private static final Set<UUID> EXECUTING_TELEPORTS = new HashSet<>();
	private static final Set<Integer> EXECUTING_TIME_TRANSITIONS = new HashSet<>();
	private static final Set<ServerClockManager> EXECUTING_TIME_MANAGERS = new HashSet<>();

	private static int nextTicketId = 1;

	private ServerTransitionCoordinator() {
	}

	public static boolean interceptTeleport(ServerPlayer player, TeleportTransition transition) {
		if (isExecuting(player)) {
			return false;
		}
		if (hasPending(player)) {
			return true;
		}
		if (transition.missingRespawnBlock() || !ServerPlayNetworking.canSend(player, TransitionPayloads.TravelStart.TYPE)) {
			return false;
		}

		queueTeleport(player, transition);
		return true;
	}

	private static void queueTeleport(ServerPlayer player, TeleportTransition transition) {
		int ticketId = nextTicket();
		Vec3 origin = player.position();
		Vec3 destination = transition.position();
		List<TravelRecipient> recipients = collectRecipients(player, transition);
		PendingTeleport pending = new PendingTeleport(
				ticketId,
				player,
				transition,
				recipients,
				player.isInvulnerable()
		);
		PENDING_TELEPORTS.put(player.getUUID(), pending);
		player.setInvulnerable(true);

		for (TravelRecipient recipient : recipients) {
			if (!ServerPlayNetworking.canSend(recipient.player(), TransitionPayloads.TravelStart.TYPE)) {
				continue;
			}
			ServerPlayNetworking.send(
					recipient.player(),
					new TransitionPayloads.TravelStart(
							ticketId,
							TRAVEL_EDGE_TICKS,
							recipient.player() == player,
							false,
							player.getId(),
							origin.x(),
							origin.y(),
							origin.z(),
							destination.x(),
							destination.y(),
							destination.z(),
							recipient.checkOrigin(),
							recipient.checkDestination()
					)
			);
		}
	}

	private static List<TravelRecipient> collectRecipients(ServerPlayer subject, TeleportTransition transition) {
		ServerLevel originLevel = subject.level();
		Vec3 origin = subject.position();
		ServerLevel destinationLevel = transition.newLevel();
		Vec3 destination = transition.position();
		Map<UUID, TravelRecipient> recipients = new LinkedHashMap<>();
		recipients.put(subject.getUUID(), new TravelRecipient(subject, true, true));

		for (ServerPlayer candidate : subject.level().getServer().getPlayerList().getPlayers()) {
			if (candidate == subject || !ServerPlayNetworking.canSend(candidate, TransitionPayloads.TravelStart.TYPE)) {
				continue;
			}

			boolean seesDeparture = candidate.level() == originLevel
					&& candidate.distanceToSqr(origin) <= OBSERVER_RADIUS * OBSERVER_RADIUS;
			boolean seesArrival = candidate.level() == destinationLevel
					&& candidate.distanceToSqr(destination) <= OBSERVER_RADIUS * OBSERVER_RADIUS;
			if (seesDeparture || seesArrival) {
				recipients.put(candidate.getUUID(), new TravelRecipient(candidate, seesDeparture, seesArrival));
			}
		}

		return new ArrayList<>(recipients.values());
	}

	public static void acknowledgeTravel(ServerPlayer player, int ticketId) {
		PendingTeleport teleport = PENDING_TELEPORTS.get(player.getUUID());
		if (teleport != null && teleport.ticketId == ticketId && teleport.player == player) {
			teleport.ready = true;
			return;
		}

		for (PendingTimeTransition timeTransition : PENDING_TIME_TRANSITIONS.values()) {
			if (timeTransition.ticketId == ticketId) {
				timeTransition.awaitingAcknowledgements.remove(player.getUUID());
				return;
			}
		}
	}

	public static boolean hasPending(ServerPlayer player) {
		return PENDING_TELEPORTS.containsKey(player.getUUID());
	}

	public static boolean isExecuting(ServerPlayer player) {
		return EXECUTING_TELEPORTS.contains(player.getUUID());
	}

	public static void tickTeleports(MinecraftServer server) {
		for (PendingTeleport pending : List.copyOf(PENDING_TELEPORTS.values())) {
			ServerPlayer player = pending.player;
			if (player.hasDisconnected() || player.isRemoved()) {
				PENDING_TELEPORTS.remove(player.getUUID());
				player.setInvulnerable(pending.wasInvulnerable);
				continue;
			}

			if (pending.ready || server.getTickCount() - pending.startedAt >= TRAVEL_TIMEOUT_TICKS) {
				executeTeleport(pending);
			}
		}
	}

	private static void executeTeleport(PendingTeleport pending) {
		PENDING_TELEPORTS.remove(pending.player.getUUID());
		EXECUTING_TELEPORTS.add(pending.player.getUUID());
		try {
			pending.player.teleport(pending.transition);
		} finally {
			EXECUTING_TELEPORTS.remove(pending.player.getUUID());
			pending.player.setInvulnerable(pending.wasInvulnerable);
			for (TravelRecipient recipient : pending.recipients) {
				if (ServerPlayNetworking.canSend(recipient.player(), TransitionPayloads.TravelRelease.TYPE)) {
					ServerPlayNetworking.send(recipient.player(), new TransitionPayloads.TravelRelease(pending.ticketId));
				}
			}
		}
	}

	/**
	 * Intercepts /time set. Returning true means the caller must not apply the
	 * clock change yet because the change has been put behind the iris.
	 */
	public static boolean interceptSetTotalTicks(
			ServerClockManager manager,
			Holder<WorldClock> clock,
			long targetTicks,
			MinecraftServer server
	) {
		if (EXECUTING_TIME_MANAGERS.contains(manager)) {
			return false;
		}

		PendingTimeTransition existing = findClockTransition(manager, clock);
		if (existing != null) {
			if (!existing.sleepTransition) {
				existing.operation = TimeOperation.SET_TOTAL_TICKS;
				existing.value = targetTicks;
			}
			return true;
		}

		long currentTicks = manager.getTotalTicks(clock);
		if (dayIndex(currentTicks) == dayIndex(targetTicks)) {
			return false;
		}

		List<ServerPlayer> recipients = supportedRecipients(server);
		if (recipients == null) {
			return false;
		}

		queueTimeTransition(
				server,
				manager,
				clock,
				TimeOperation.SET_TOTAL_TICKS,
				targetTicks,
				null,
				null,
				recipients
		);
		return true;
	}

	/** Intercepts /time add using the same day-boundary rule as /time set. */
	public static boolean interceptAddTicks(
			ServerClockManager manager,
			Holder<WorldClock> clock,
			int amount,
			MinecraftServer server
	) {
		if (EXECUTING_TIME_MANAGERS.contains(manager)) {
			return false;
		}

		PendingTimeTransition existing = findClockTransition(manager, clock);
		if (existing != null) {
			if (!existing.sleepTransition) {
				existing.operation = existing.operation == TimeOperation.SET_TOTAL_TICKS
						? TimeOperation.SET_TOTAL_TICKS
						: TimeOperation.ADD_TICKS;
				existing.value += amount;
			}
			return true;
		}

		long currentTicks = manager.getTotalTicks(clock);
		long targetTicks = currentTicks + amount;
		if (dayIndex(currentTicks) == dayIndex(targetTicks)) {
			return false;
		}

		List<ServerPlayer> recipients = supportedRecipients(server);
		if (recipients == null) {
			return false;
		}

		queueTimeTransition(
				server,
				manager,
				clock,
				TimeOperation.ADD_TICKS,
				amount,
				null,
				null,
				recipients
		);
		return true;
	}

	/**
	 * This redirect is reached by ServerLevel only after Minecraft's own sleep
	 * threshold and deep-sleep checks have passed.
	 */
	public static boolean interceptSleepTime(
			ServerLevel level,
			ServerClockManager manager,
			Holder<WorldClock> clock,
			ResourceKey<ClockTimeMarker> marker
	) {
		if (!ClockTimeMarkers.WAKE_UP_FROM_SLEEP.equals(marker)
				|| !level.getGameRules().get(GameRules.ADVANCE_TIME)
				|| EXECUTING_TIME_MANAGERS.contains(manager)) {
			return manager.moveToTimeMarker(clock, marker);
		}

		PendingTimeTransition existing = findClockTransition(manager, clock);
		if (existing != null) {
			if (!existing.sleepTransition) {
				existing.sleepTransition = true;
				existing.operation = TimeOperation.MOVE_TO_MARKER;
				existing.value = 0L;
				existing.marker = marker;
				existing.sleepLevel = level;
				existing.releaseSent = false;
				existing.sleepGraceTicks = 0;
			}
			return false;
		}

		List<ServerPlayer> recipients = supportedRecipients(level.getServer());
		if (recipients == null) {
			return manager.moveToTimeMarker(clock, marker);
		}

		queueTimeTransition(
				level.getServer(),
				manager,
				clock,
				TimeOperation.MOVE_TO_MARKER,
				0L,
				marker,
				level,
				recipients
		);
		return false;
	}

	/** Keeps the vanilla wake-up call deferred while a sleep iris is pending. */
	public static boolean shouldDeferWakeUp(ServerLevel level) {
		PendingTimeTransition pending = findSleepTransition(level);
		return pending != null && !EXECUTING_TIME_TRANSITIONS.contains(pending.ticketId);
	}

	public static void tickTimeTransitions(MinecraftServer server) {
		for (PendingTimeTransition pending : List.copyOf(PENDING_TIME_TRANSITIONS.values())) {
			if (!PENDING_TIME_TRANSITIONS.containsKey(pending.ticketId)) {
				continue;
			}

			pruneRecipients(server, pending);
			if (pending.sleepTransition) {
				boolean sleepThresholdMet = pending.sleepLevel != null && isSleepThresholdMet(pending.sleepLevel);
				if (!sleepThresholdMet) {
					if (!pending.releaseSent) {
						pending.releaseSent = true;
						pending.sleepGraceTicks = 0;
						pending.awaitingAcknowledgements.clear();
						sendTravelRelease(pending, false);
					} else if (++pending.sleepGraceTicks >= SLEEP_REVERSE_GRACE_TICKS) {
						sendTravelRelease(pending, true);
						PENDING_TIME_TRANSITIONS.remove(pending.ticketId);
					}
					continue;
				}

				if (pending.releaseSent) {
					pending.releaseSent = false;
					pending.sleepGraceTicks = 0;
					pending.startedAt = server.getTickCount();
					startTimeTransition(pending);
				}
			}

			if (!pending.releaseSent
					&& (pending.awaitingAcknowledgements.isEmpty()
					|| server.getTickCount() - pending.startedAt >= TIME_TIMEOUT_TICKS)) {
				executeTimeTransition(pending);
			}
		}
	}

	private static void queueTimeTransition(
			MinecraftServer server,
			ServerClockManager manager,
			Holder<WorldClock> clock,
			TimeOperation operation,
			long value,
			ResourceKey<ClockTimeMarker> marker,
			ServerLevel sleepLevel,
			List<ServerPlayer> recipients
	) {
		PendingTimeTransition pending = new PendingTimeTransition(
				nextTicket(),
				server,
				manager,
				clock,
				operation,
				value,
				marker,
				sleepLevel,
				recipients,
				server.getTickCount()
		);
		PENDING_TIME_TRANSITIONS.put(pending.ticketId, pending);
		startTimeTransition(pending);
	}

	private static void startTimeTransition(PendingTimeTransition pending) {
		pending.awaitingAcknowledgements.clear();
		for (Iterator<ServerPlayer> iterator = pending.recipients.iterator(); iterator.hasNext();) {
			ServerPlayer player = iterator.next();
			if (player.hasDisconnected()
					|| player.isRemoved()
					|| !ServerPlayNetworking.canSend(player, TransitionPayloads.TravelStart.TYPE)) {
				iterator.remove();
				continue;
			}

			pending.awaitingAcknowledgements.add(player.getUUID());
			ServerPlayNetworking.send(
					player,
					new TransitionPayloads.TravelStart(
							pending.ticketId,
							TIME_EDGE_TICKS,
							true,
							pending.sleepTransition,
							-1,
							0.0,
							0.0,
							0.0,
							0.0,
							0.0,
							0.0,
							false,
							false
					)
			);
		}
	}

	private static void sendTravelRelease(PendingTimeTransition pending, boolean finalRelease) {
		for (ServerPlayer player : pending.recipients) {
			if (ServerPlayNetworking.canSend(player, TransitionPayloads.TravelRelease.TYPE)) {
				ServerPlayNetworking.send(
						player,
						new TransitionPayloads.TravelRelease(pending.ticketId, finalRelease)
				);
			}
		}
	}

	private static void executeTimeTransition(PendingTimeTransition pending) {
		if (!PENDING_TIME_TRANSITIONS.containsKey(pending.ticketId)) {
			return;
		}

		EXECUTING_TIME_TRANSITIONS.add(pending.ticketId);
		EXECUTING_TIME_MANAGERS.add(pending.manager);
		try {
			switch (pending.operation) {
				case SET_TOTAL_TICKS -> pending.manager.setTotalTicks(pending.clock, pending.value);
				case ADD_TICKS -> pending.manager.addTicks(pending.clock, (int) pending.value);
				case MOVE_TO_MARKER -> pending.manager.moveToTimeMarker(pending.clock, pending.marker);
			}

			if (pending.sleepLevel != null) {
				((ServerLevelAccess) (Object) pending.sleepLevel).silentfilms$invokeWakeUpAllPlayers();
			}
		} finally {
			EXECUTING_TIME_MANAGERS.remove(pending.manager);
			EXECUTING_TIME_TRANSITIONS.remove(pending.ticketId);
			PENDING_TIME_TRANSITIONS.remove(pending.ticketId);
			sendTravelRelease(pending, true);
		}
	}

	private static void pruneRecipients(MinecraftServer server, PendingTimeTransition pending) {
		List<ServerPlayer> connectedPlayers = server.getPlayerList().getPlayers();
		for (Iterator<ServerPlayer> iterator = pending.recipients.iterator(); iterator.hasNext();) {
			ServerPlayer player = iterator.next();
			if (player.hasDisconnected()
					|| player.isRemoved()
					|| !connectedPlayers.contains(player)
					|| !ServerPlayNetworking.canSend(player, TransitionPayloads.TravelStart.TYPE)) {
				pending.awaitingAcknowledgements.remove(player.getUUID());
				iterator.remove();
			}
		}
	}

	private static List<ServerPlayer> supportedRecipients(MinecraftServer server) {
		List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
		if (players.isEmpty()) {
			return null;
		}
		for (ServerPlayer player : players) {
			if (!ServerPlayNetworking.canSend(player, TransitionPayloads.TravelStart.TYPE)
					|| !ServerPlayNetworking.canSend(player, TransitionPayloads.TravelRelease.TYPE)) {
				return null;
			}
		}
		return players;
	}

	private static PendingTimeTransition findClockTransition(
			ServerClockManager manager,
			Holder<WorldClock> clock
	) {
		for (PendingTimeTransition pending : PENDING_TIME_TRANSITIONS.values()) {
			if (pending.manager == manager && pending.clock.equals(clock)) {
				return pending;
			}
		}
		return null;
	}

	private static PendingTimeTransition findSleepTransition(ServerLevel level) {
		for (PendingTimeTransition pending : PENDING_TIME_TRANSITIONS.values()) {
			if (pending.sleepLevel == level) {
				return pending;
			}
		}
		return null;
	}

	private static boolean isSleepThresholdMet(ServerLevel level) {
		int activePlayers = 0;
		int sleepingPlayers = 0;
		int deepSleepingPlayers = 0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}

			activePlayers++;
			if (player.isSleeping()) {
				sleepingPlayers++;
				if (player.isSleepingLongEnough()) {
					deepSleepingPlayers++;
				}
			}
		}

		int percentage = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		int sleepersNeeded = Math.max(1, (int) Math.ceil(activePlayers * percentage / 100.0D));
		return sleepingPlayers >= sleepersNeeded && deepSleepingPlayers >= sleepersNeeded;
	}

	private static long dayIndex(long ticks) {
		return Math.floorDiv(ticks, TICKS_PER_DAY);
	}

	private static int nextTicket() {
		int ticketId = nextTicketId++;
		if (nextTicketId <= 0) {
			nextTicketId = 1;
		}
		return ticketId;
	}

	private enum TimeOperation {
		SET_TOTAL_TICKS,
		ADD_TICKS,
		MOVE_TO_MARKER
	}

	private static final class PendingTeleport {
		private final int ticketId;
		private final ServerPlayer player;
		private final TeleportTransition transition;
		private final List<TravelRecipient> recipients;
		private final boolean wasInvulnerable;
		private final long startedAt;
		private boolean ready;

		private PendingTeleport(
				int ticketId,
				ServerPlayer player,
				TeleportTransition transition,
				List<TravelRecipient> recipients,
				boolean wasInvulnerable
		) {
			this.ticketId = ticketId;
			this.player = player;
			this.transition = transition;
			this.recipients = recipients;
			this.wasInvulnerable = wasInvulnerable;
			this.startedAt = player.level().getServer().getTickCount();
		}
	}

	private static final class PendingTimeTransition {
		private final int ticketId;
		private final MinecraftServer server;
		private final ServerClockManager manager;
		private final Holder<WorldClock> clock;
		private final List<ServerPlayer> recipients;
		private final Set<UUID> awaitingAcknowledgements = new HashSet<>();
		private TimeOperation operation;
		private long value;
		private ResourceKey<ClockTimeMarker> marker;
		private ServerLevel sleepLevel;
		private boolean sleepTransition;
		private boolean releaseSent;
		private int sleepGraceTicks;
		private long startedAt;

		private PendingTimeTransition(
				int ticketId,
				MinecraftServer server,
				ServerClockManager manager,
				Holder<WorldClock> clock,
				TimeOperation operation,
				long value,
				ResourceKey<ClockTimeMarker> marker,
				ServerLevel sleepLevel,
				List<ServerPlayer> recipients,
				long startedAt
		) {
			this.ticketId = ticketId;
			this.server = server;
			this.manager = manager;
			this.clock = clock;
			this.operation = operation;
			this.value = value;
			this.marker = marker;
			this.sleepLevel = sleepLevel;
			this.sleepTransition = sleepLevel != null;
			this.recipients = new ArrayList<>(recipients);
			this.startedAt = startedAt;
		}
	}

	private record TravelRecipient(ServerPlayer player, boolean checkOrigin, boolean checkDestination) {
	}
}
