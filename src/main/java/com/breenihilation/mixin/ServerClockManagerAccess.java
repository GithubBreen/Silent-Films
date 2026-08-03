package com.breenihilation.mixin;

// Exposes the server clock manager to the transition mixin.
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerClockManager.class)
public interface ServerClockManagerAccess {
	@Accessor("server")
	MinecraftServer silentfilms$getServer();
}
