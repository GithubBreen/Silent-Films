package com.breenihilation;

// Freezes nearby entities while an intertitle is being displayed.
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class IntertitleFreezeZones {
	public static final double RADIUS = 48.0;
	private static final double RADIUS_SQUARED = RADIUS * RADIUS;
	private static final int HEARTBEAT_TIMEOUT_TICKS = 60;
	private static final Map<UUID, FreezeSignal> ACTIVE = new HashMap<>();

	private IntertitleFreezeZones() {
	}

	public static void update(ServerPlayer player, boolean active) {
		if (!active) {
			ACTIVE.remove(player.getUUID());
			return;
		}
		ACTIVE.put(player.getUUID(), new FreezeSignal(player.level().getServer().getTickCount()));
	}

	public static boolean shouldFreeze(ServerLevel level, Entity entity) {
		if (entity instanceof Player || entity.hasPassenger(passenger -> passenger instanceof Player)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		int now = server.getTickCount();
		for (Map.Entry<UUID, FreezeSignal> entry : ACTIVE.entrySet()) {
			if (now - entry.getValue().lastHeartbeatTick() > HEARTBEAT_TIMEOUT_TICKS) {
				continue;
			}
			ServerPlayer recipient = server.getPlayerList().getPlayer(entry.getKey());
			if (recipient != null && recipient.level() == level
					&& withinRadius(recipient.getX(), recipient.getY(), recipient.getZ(),
					entity.getX(), entity.getY(), entity.getZ())) {
				return true;
			}
		}
		return false;
	}

	public static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, FreezeSignal>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, FreezeSignal> entry = iterator.next();
			if (now - entry.getValue().lastHeartbeatTick() > HEARTBEAT_TIMEOUT_TICKS
					|| server.getPlayerList().getPlayer(entry.getKey()) == null) {
				iterator.remove();
			}
		}
	}

	public static void clear() {
		ACTIVE.clear();
	}

	static boolean withinRadius(
			double centerX, double centerY, double centerZ,
			double entityX, double entityY, double entityZ
	) {
		double x = entityX - centerX;
		double y = entityY - centerY;
		double z = entityZ - centerZ;
		return x * x + y * y + z * z <= RADIUS_SQUARED;
	}

	private record FreezeSignal(int lastHeartbeatTick) {
	}
}
