package com.breenihilation.client;
import com.breenihilation.SilentFilms;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import java.util.Locale;
import java.util.Random;

// shader overlay and stuff

public final class FilmRenderer {
	private static final Identifier MONOCHROME_POST_EFFECT = SilentFilms.id("monochrome");
	private static final Identifier INTERTITLE_FRAME = SilentFilms.id("textures/gui/intertitle_frame.png");
	private static final FontDescription INTERTITLE_FONT = new FontDescription.Resource(SilentFilms.id("intertitle"));
	private static final Random GRAIN_RANDOM = new Random();
	private static final float REFERENCE_CARD_WIDTH = 480.0f;
	private static final float REFERENCE_CARD_HEIGHT = 270.0f;
	private static final long GRAIN_SEED_MULTIPLIER = 31L;
	private static final long GRAIN_SEED_OFFSET = 17L;
	private static final long IRIS_WOBBLE_X_MULTIPLIER = 97L;
	private static final long IRIS_WOBBLE_Y_MULTIPLIER = 31L;
	private static final long INTERTITLE_SHAKE_X_MULTIPLIER = 71L;
	private static final long INTERTITLE_SHAKE_Y_MULTIPLIER = 97L;
	private static final long INTERTITLE_SHAKE_X_OFFSET = 19L;
	private static final long INTERTITLE_SHAKE_Y_OFFSET = 23L;
	private static final int INTERTITLE_DAMAGE_SEED_OFFSET = 7_919;
	private static final long MENU_IRIS_TICK_OFFSET = 104_729L;
	private static final long LOADING_IRIS_TICK_OFFSET = 53_111L;

	private FilmRenderer() {
	}

	public static void updatePostEffect(Minecraft client) {
		if (client.gameRenderer == null || SilentFilmsClient.config() == null) {
			return;
		}

		GameRenderer renderer = client.gameRenderer;

		if (MONOCHROME_POST_EFFECT.equals(renderer.currentPostEffect())) {
			renderer.clearPostEffect();
		}
	}

	public static void applyFinalPostEffect(
			Minecraft client,
			RenderTarget mainRenderTarget,
			GraphicsResourceAllocator resourceAllocator
	) {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null || !config.filmModeEnabled || !config.monochromeEnabled) {
			return;
		}
		if (client.gui.overlay() instanceof LoadingOverlay) {
			return;
		}
		try {
			PostChain chain = client.getShaderManager().getPostChain(
					MONOCHROME_POST_EFFECT, LevelTargetBundle.MAIN_TARGETS
			);
			if (chain != null) {
				chain.process(mainRenderTarget, resourceAllocator);
			}
		} catch (RuntimeException exception) {
			SilentFilms.LOGGER.warn("Could not apply final-frame Silent Films monochrome effect.", exception);
		}
	}

	public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null || client.gui.screen() != null) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		extractFilmEffects(graphics, config, width, height, SilentFilmsClient.clientTicks());

		IntertitleQueue.IntertitleEntry active = SilentFilmsClient.intertitles().active();
		if (config.intertitlesEnabled && active != null) {
			if (ClientTransitionController.suppressesIntertitles()) {
				active = null;
			}
		}
		if (config.intertitlesEnabled && active != null) {
			extractIntertitle(graphics, client, active, width, height, config.intertitleTextScale);
			if (config.filmModeEnabled && config.grainEnabled) {
				extractFilmDamage(graphics, width, height,
						(int) SilentFilmsClient.clientTicks() + INTERTITLE_DAMAGE_SEED_OFFSET);
			}
		}

		if (config.irisTransitionsEnabled && ClientTransitionController.irisTransition().isActive()) {
			extractIrisTransition(graphics, width, height, ClientTransitionController.irisTransition().aperture(),
				SilentFilmsClient.clientTicks());
		}
	}

	/**
	 * Screen content is extracted after the HUD. Keep the film treatment visible
	 * while chat, menus, and the in-bed chat screen are open, with the iris last
	 * so a transition can still cover the interface completely.
	 */
	public static void extractScreenOverlay(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		extractFilmEffects(graphics, config, width, height, SilentFilmsClient.clientTicks());

		IntertitleQueue.IntertitleEntry active = SilentFilmsClient.intertitles().active();
		if (config.intertitlesEnabled && active != null) {
			if (ClientTransitionController.suppressesIntertitles()) {
				active = null;
			}
		}
		if (config.intertitlesEnabled && active != null) {
			extractIntertitle(graphics, client, active, width, height, config.intertitleTextScale);
		}

		if (config.irisTransitionsEnabled && ClientTransitionController.irisTransition().isActive()) {
			extractIrisTransition(graphics, width, height, ClientTransitionController.irisTransition().aperture(),
				SilentFilmsClient.clientTicks());
		}
		if (config.irisTransitionsEnabled && ClientTransitionController.menuIrisActive()) {
				extractIrisTransition(graphics, width, height, ClientTransitionController.menuIrisAperture(),
					SilentFilmsClient.clientTicks() + MENU_IRIS_TICK_OFFSET);
		}
	}

	public static void extractLoadingIris(GuiGraphicsExtractor graphics) {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null || ClientTransitionController.resourceReloadSuppressed()
				|| !config.filmModeEnabled || !config.irisTransitionsEnabled
				|| !ClientTransitionController.loadingIrisActive()) {
			return;
		}
		extractIrisTransition(
				graphics,
				graphics.guiWidth(),
				graphics.guiHeight(),
				ClientTransitionController.loadingIrisAperture(),
				SilentFilmsClient.clientTicks() + LOADING_IRIS_TICK_OFFSET
		);
	}

	private static void extractFilmEffects(
			GuiGraphicsExtractor graphics,
			SilentFilmsConfig config,
			int width,
			int height,
			long tick
	) {
		if (!config.filmModeEnabled) {
			return;
		}

		if (config.letterboxEnabled) {
			int barHeight = Math.max(12, height / 16);
			graphics.fill(0, 0, width, barHeight, 0xEE000000);
			graphics.fill(0, height - barHeight, width, height, 0xEE000000);
		}
		if (config.vignetteEnabled) {
			extractVignette(graphics, width, height, tick);
		}
		if (config.flickerEnabled) {
			float flicker = (float) (0.015 + Math.abs(Math.sin(tick * 0.31)) * 0.025);
			graphics.fill(0, 0, width, height, ((int) (flicker * 255) << 24));
		}
		if (config.grainEnabled) {
			extractFilmDamage(graphics, width, height, (int) tick);
		}
	}

	private static void extractIrisTransition(
			GuiGraphicsExtractor graphics,
			int width,
			int height,
			float aperture,
			long tick
	) {
		if (aperture >= 0.999f) {
			return;
		}

		int centerX = width / 2 + Math.round((float) Math.sin(tick * 0.12) * 0.8f);
		int centerY = height / 2 + Math.round((float) Math.cos(tick * 0.095 + 0.7) * 0.65f);
		float maximumRadius = (float) Math.hypot(width / 2.0, height / 2.0) + 2.0f;
		float radius = maximumRadius * aperture;
		if (radius <= 1.0f) {
			graphics.fill(0, 0, width, height, 0xFF000000);
			return;
		}

		int stripHeight = Math.max(1, height / 180);
		float roughness = Math.min(1.0f, (1.0f - aperture) * 4.0f);
		for (int y = 0; y < height; y += stripHeight) {
			int nextY = Math.min(height, y + stripHeight);
			float sampleY = (y + nextY) * 0.5f;
			float distanceY = sampleY - centerY;
			float edgeWobble = (pseudoRandom(tick * IRIS_WOBBLE_X_MULTIPLIER + y * IRIS_WOBBLE_Y_MULTIPLIER) - 0.5f)
					* 3.0f * roughness;
			float rowRadius = radius + edgeWobble;
			float halfWidth = distanceY * distanceY < rowRadius * rowRadius
					? (float) Math.sqrt(rowRadius * rowRadius - distanceY * distanceY)
					: 0.0f;
			if (halfWidth <= 0.5f) {
				graphics.fill(0, y, width, nextY, 0xFF000000);
				continue;
			}
			int left = Math.max(0, Math.min(width, Math.round(centerX - halfWidth)));
			int right = Math.max(0, Math.min(width, Math.round(centerX + halfWidth)));

			if (left <= 1 || right >= width - 1) {
				if (left > 0) {
					graphics.fill(0, y, left, nextY, 0xFF000000);
				}
				if (right < width) {
					graphics.fill(right, y, width, nextY, 0xFF000000);
				}
				continue;
			}

			graphics.fill(0, y, left - 2, nextY, 0xFF000000);
			graphics.fill(left - 2, y, left - 1, nextY, 0x66000000);
			graphics.fill(left - 1, y, left, nextY, 0xB8000000);
			graphics.fill(right, y, right + 1, nextY, 0xB8000000);
			graphics.fill(right + 1, y, right + 2, nextY, 0x66000000);
			graphics.fill(right + 2, y, width, nextY, 0xFF000000);
		}
	}

	private static void extractVignette(GuiGraphicsExtractor graphics, int width, int height, long tick) {
		float breathing = (float) ((Math.sin(tick * 0.035) + Math.sin(tick * 0.019 + 1.3)) * 0.5);
		float drift = (float) Math.sin(tick * 0.011 + 0.8);
		int verticalEdge = Math.max(28, Math.min(width, height) / 9 + Math.round(breathing * 4.0f));
		int horizontalEdge = Math.max(36, width / 9 + Math.round(drift * 7.0f));
		int topAlpha = dynamicAlpha(0x4D, breathing * 10.0f + drift * 4.0f);
		int bottomAlpha = dynamicAlpha(0x52, -breathing * 8.0f);
		graphics.fillGradient(0, 0, width, verticalEdge, topAlpha << 24, 0x00000000);
		graphics.fillGradient(0, height - verticalEdge, width, height, 0x00000000, bottomAlpha << 24);

		extractVignetteSide(graphics, width, height, horizontalEdge, dynamicAlpha(0x4A, drift * 12.0f), true);
		extractVignetteSide(graphics, width, height, horizontalEdge, dynamicAlpha(0x4A, -drift * 10.0f), false);
	}

	private static void extractVignetteSide(
			GuiGraphicsExtractor graphics,
			int width,
			int height,
			int edge,
			int baseAlpha,
			boolean left
	) {
		int bands = 24;
		for (int band = 0; band < bands; band++) {
			float start = band / (float) bands;
			float end = (band + 1) / (float) bands;
			int alpha = (int) (baseAlpha * Math.pow(1.0f - start, 1.65f));
			int startX = (int) (edge * start);
			int endX = Math.max(startX + 1, (int) (edge * end));
			int x1 = left ? startX : width - endX;
			int x2 = left ? endX : width - startX;
			graphics.fill(x1, 0, x2, height, alpha << 24);
		}
	}

	private static int dynamicAlpha(int base, float offset) {
		return Math.max(0, Math.min(255, Math.round(base + offset)));
	}

	private static void extractFilmDamage(GuiGraphicsExtractor graphics, int width, int height, int tick) {
		GRAIN_RANDOM.setSeed(tick * GRAIN_SEED_MULTIPLIER + GRAIN_SEED_OFFSET);

		for (int i = 0; i < 76; i++) {
			int x = GRAIN_RANDOM.nextInt(Math.max(1, width));
			int y = GRAIN_RANDOM.nextInt(Math.max(1, height));
			int size = 1 + GRAIN_RANDOM.nextInt(3);
			int alpha = 18 + GRAIN_RANDOM.nextInt(32);
			int color = (alpha << 24) | (GRAIN_RANDOM.nextBoolean() ? 0xFFFFFF : 0x000000);
			graphics.fill(x, y, Math.min(width, x + size), Math.min(height, y + size), color);
		}

		for (int scratch = 0; scratch < 5; scratch++) {
			int x = GRAIN_RANDOM.nextInt(Math.max(1, width));
			int y = GRAIN_RANDOM.nextInt(Math.max(1, height / 3));
			int remaining = height - y;
			while (remaining > 0) {
				int segment = Math.min(remaining, 18 + GRAIN_RANDOM.nextInt(90));
				int alpha = 20 + GRAIN_RANDOM.nextInt(35);
				int color = (alpha << 24) | (GRAIN_RANDOM.nextBoolean() ? 0xFFFFFF : 0x000000);
				graphics.fill(x, y, Math.min(width, x + 1), Math.min(height, y + segment), color);
				y += segment + GRAIN_RANDOM.nextInt(38);
				remaining = height - y;
			}
		}
	}

	private static void extractIntertitle(
			GuiGraphicsExtractor graphics,
			Minecraft client,
			IntertitleQueue.IntertitleEntry active,
			int width,
			int height,
			int textScaleMode
	) {
		IntertitleQueue queue = SilentFilmsClient.intertitles();
		float progress = queue.activeProgress();
		float fade = Math.min(1.0f, Math.min(progress / 0.12f, (1.0f - progress) / 0.12f));
		fade = Math.max(0.0f, fade);
		boolean endingCard = isFinalCard(active);

		if (endingCard) {
			extractEndingBackdrop(graphics, width, height, fade);
		} else {
			graphics.fill(0, 0, width, height, withAlpha(0xF0000000, fade));
		}

		graphics.pose().pushMatrix();
		graphics.pose().pushMatrix();
		graphics.pose().scale(width / 256.0f, height / 144.0f);
		graphics.blit(RenderPipelines.GUI_TEXTURED, INTERTITLE_FRAME,
				0, 0, 0.0f, 0.0f, 256, 144, 256, 144);
		graphics.pose().popMatrix();

		if (endingCard) {
			extractFinText(graphics, client, width, height, fade, textScaleMode);
			graphics.pose().popMatrix();
			return;
		}

		float preferredScale = switch (textScaleMode) {
			case 0 -> 0.8f;
			case 2 -> 1.2f;
			default -> 1.0f;
		};
		preferredScale *= responsiveIntertitleScale(width, height);
		boolean chatCard = active.senderId() != null;
		IntertitleSafeArea safeArea = intertitleSafeArea(width, height);
		int safeLeft = safeArea.left();
		int safeRight = safeArea.right();
		int safeTop = safeArea.top();
		int safeBottom = safeArea.bottom();
		int safeWidth = Math.max(100, safeRight - safeLeft);
		int safeHeight = Math.max(80, safeBottom - safeTop);
		Component body = withIntertitleFont("\u201c" + active.body() + "\u201d");
		Component heading = chatCard ? null : withIntertitleFont(active.heading().toUpperCase(Locale.ROOT));
		Component byline = chatCard ? withIntertitleFont("\u2014 " + active.heading()) : null;
		IntertitleTextLayout.TextLayoutResult layout = IntertitleTextLayout.fit(
				client.font, body, heading, byline, chatCard, safeWidth, safeHeight, preferredScale
		);
		int headingColor = withAlpha(0xB8E6E6DE, fade);
		float[] textShake = intertitleTextShake(SilentFilmsClient.clientTicks());

		float contentTop = safeTop + (safeHeight - layout.totalHeight()) / 2.0f;
		float bodyTop = contentTop;
		if (!chatCard) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2.0f + textShake[0], contentTop + textShake[1]);
			graphics.pose().scale(layout.bodyScale(), layout.bodyScale());
			int headingX = -client.font.width(heading) / 2;
			graphics.text(client.font, heading, headingX + 1, 1, withAlpha(0x66000000, fade));
			graphics.text(client.font, heading, headingX, 0, headingColor);
			graphics.pose().popMatrix();
			bodyTop += (IntertitleTextLayout.GLYPH_HEIGHT + IntertitleTextLayout.HEADING_GAP)
					* layout.bodyScale();
		}

		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0f + textShake[0], bodyTop + textShake[1]);
		graphics.pose().scale(layout.bodyScale(), layout.bodyScale());
		for (int index = 0; index < layout.lines().size(); index++) {
			net.minecraft.util.FormattedCharSequence line = layout.lines().get(index);
			int lineX = -client.font.width(line) / 2;
			int lineY = index * IntertitleTextLayout.LINE_STEP;
			graphics.text(client.font, line, lineX + 1, lineY + 1, withAlpha(0x66000000, fade));
			graphics.text(client.font, line, lineX, lineY, withAlpha(0xF2F2F0E8, fade));
		}
		graphics.pose().popMatrix();

		if (chatCard) {
			float authorX = safeRight - client.font.width(byline) * layout.authorScale();
			float authorY = bodyTop + layout.bodyHeight() + layout.authorGap();
			graphics.pose().pushMatrix();
			graphics.pose().translate(authorX + textShake[0], authorY + textShake[1]);
			graphics.pose().scale(layout.authorScale(), layout.authorScale());
			graphics.text(client.font, byline, 1, 1, withAlpha(0x66000000, fade));
			graphics.text(client.font, byline, 0, 0, headingColor);
			graphics.pose().popMatrix();
		}
		graphics.pose().popMatrix();
	}

	static float responsiveIntertitleScale(int viewportWidth, int viewportHeight) {
		return Math.max(0.25f, Math.min(
				viewportWidth / REFERENCE_CARD_WIDTH,
				viewportHeight / REFERENCE_CARD_HEIGHT
		));
	}

	static IntertitleSafeArea intertitleSafeArea(int viewportWidth, int viewportHeight) {
		int opticalGuard = Math.max(2, Math.round(responsiveIntertitleScale(viewportWidth, viewportHeight) * 3.0f));
		int horizontalInset = Math.max(24, Math.round(viewportWidth * 0.15f)) + opticalGuard;
		int topInset = Math.max(18, Math.round(viewportHeight * 0.125f)) + opticalGuard;
		int bottomInset = Math.max(18, Math.round(viewportHeight * 0.15f)) + opticalGuard;
		return new IntertitleSafeArea(
				horizontalInset,
				viewportWidth - horizontalInset,
				topInset,
				viewportHeight - bottomInset
		);
	}

	record IntertitleSafeArea(int left, int right, int top, int bottom) {
	}

	private static float[] intertitleTextShake(long tick) {
		float x = (pseudoRandom(tick * INTERTITLE_SHAKE_X_MULTIPLIER + INTERTITLE_SHAKE_X_OFFSET) - 0.5f) * 1.2f;
		float y = (pseudoRandom(tick * INTERTITLE_SHAKE_Y_MULTIPLIER + INTERTITLE_SHAKE_Y_OFFSET) - 0.5f) * 0.8f;
		return new float[]{x, y};
	}

	private static float pseudoRandom(long seed) {
		long value = seed ^ (seed >>> 33);
		value *= 0xff51afd7ed558ccdl;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53l;
		value ^= value >>> 33;
		return (value & 0xFFFFFFL) / 16777215.0f;
	}

	private static void extractEndingBackdrop(GuiGraphicsExtractor graphics, int width, int height, float fade) {
		graphics.fillGradient(
			0,
			0,
			width,
			height,
			withAlpha(0xFF4A4A4A, fade),
			withAlpha(0xFF1B1B1B, fade)
		);
		int layers = 12;
		for (int layer = 0; layer < layers; layer++) {
			float progress = layer / (float) (layers - 1);
			int insetX = (int) (width * 0.42f * progress);
			int insetY = (int) (height * 0.40f * progress);
			int alpha = 5 + (int) (progress * 10.0f);
			graphics.fill(
				insetX,
				insetY,
				width - insetX,
				height - insetY,
				withAlpha(0xFFFFFFFF, fade * alpha / 255.0f)
			);
		}
	}

	private static void extractFinText(
			GuiGraphicsExtractor graphics,
			Minecraft client,
			int width,
			int height,
			float fade,
			int textScaleMode
	) {
		float scale = switch (textScaleMode) {
			case 0 -> 0.8f;
			case 2 -> 1.2f;
			default -> 1.0f;
		};
		Component fin = withIntertitleFont("Fin");
		int textWidth = client.font.width(fin);
		int textY = -client.font.lineHeight / 2;
		float[] textShake = intertitleTextShake(SilentFilmsClient.clientTicks());
		graphics.pose().pushMatrix();
		graphics.pose().translate(width / 2.0f + textShake[0], height / 2.0f + textShake[1]);
		graphics.pose().scale(scale, scale);
		graphics.text(client.font, fin, -textWidth / 2 + 1, textY + 1, withAlpha(0x66000000, fade));
		graphics.text(client.font, fin, -textWidth / 2, textY, withAlpha(0xF2F2F0E8, fade));
		graphics.pose().popMatrix();
	}

	private static boolean isFinalCard(IntertitleQueue.IntertitleEntry active) {
		return active.senderId() == null
				&& (active.heading().equalsIgnoreCase("FIN") || active.heading().equalsIgnoreCase("THE END"));
	}

	private static int withAlpha(int color, float opacity) {
		int alpha = (int) (((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, opacity)));
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private static Component withIntertitleFont(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(INTERTITLE_FONT));
	}
}
