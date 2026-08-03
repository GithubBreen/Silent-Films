package com.breenihilation.client.mixin;


import com.breenihilation.client.FilmRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// draw overlay over hud or whatever i forgor

@Mixin(Hud.class)
public class HudMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void silentfilms$extractFilmHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo callback) {
		FilmRenderer.extractHud(graphics, deltaTracker);
	}
}
