package com.breenihilation.client.mixin;
import com.breenihilation.client.FilmRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(Screen.class)
public class ScreenMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
	private void silentfilms$extractFilmOverlay(
			GuiGraphicsExtractor graphics,
			int mouseX,
			int mouseY,
			float delta,
			CallbackInfo callback
	) {
		FilmRenderer.extractScreenOverlay(graphics);
	}
}
