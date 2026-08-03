package com.breenihilation.client;

// mod settings
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SilentFilmsScreen extends Screen {
	private static final int PANEL_HEIGHT = 340;
	private final Screen parent;
	private MenuTab tab = MenuTab.FILM;

	public SilentFilmsScreen(Screen parent) {
		super(Component.translatable("screen.silentfilms.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		SilentFilmsConfig config = SilentFilmsClient.config();
		int panelX = panelX();
		int panelY = panelY();
		int contentLeft = contentLeft();
		int columnWidth = columnWidth();
		int secondColumn = contentLeft + columnWidth + 16;
		int tabY = panelY + 50;
		int tabGap = 8;
		int tabWidth = (panelWidth() - 64 - tabGap * 2) / 3;
		int thirdColumn = contentLeft + (tabWidth + tabGap) * 2;

		addRenderableWidget(new FilmButton(
				contentLeft,
				tabY,
				tabWidth,
				28,
				Component.translatable("screen.silentfilms.tab.film"),
				tab == MenuTab.FILM,
				() -> {
					tab = MenuTab.FILM;
					rebuildWidgets();
				}
		));
		addRenderableWidget(new FilmButton(
				contentLeft + tabWidth + tabGap,
				tabY,
				tabWidth,
				28,
				Component.translatable("screen.silentfilms.tab.intertitles"),
				tab == MenuTab.INTERTITLES,
				() -> {
					tab = MenuTab.INTERTITLES;
					rebuildWidgets();
				}
		));
		addRenderableWidget(new FilmButton(
				thirdColumn,
				tabY,
				tabWidth,
				28,
				Component.translatable("screen.silentfilms.tab.sound"),
				tab == MenuTab.SOUND,
				() -> {
					tab = MenuTab.SOUND;
					rebuildWidgets();
				}
		));

		int firstRow = panelY + 112;
		int rowSpacing = 30;
		if (tab == MenuTab.FILM) {
			addToggle(config.filmModeEnabled, "screen.silentfilms.film_mode", contentLeft, firstRow,
					value -> config.filmModeEnabled = value);
			addToggle(config.monochromeEnabled, "screen.silentfilms.monochrome", contentLeft, firstRow + rowSpacing,
					value -> config.monochromeEnabled = value);
			addToggle(config.grainEnabled, "screen.silentfilms.grain", contentLeft, firstRow + rowSpacing * 2,
					value -> config.grainEnabled = value);
			addToggle(config.vignetteEnabled, "screen.silentfilms.vignette", secondColumn, firstRow,
					value -> config.vignetteEnabled = value);
			addToggle(config.letterboxEnabled, "screen.silentfilms.letterbox", secondColumn, firstRow + rowSpacing,
					value -> config.letterboxEnabled = value);
			addToggle(config.flickerEnabled, "screen.silentfilms.flicker", secondColumn, firstRow + rowSpacing * 2,
					value -> config.flickerEnabled = value);
			addToggle(config.irisTransitionsEnabled, "screen.silentfilms.iris_transitions", secondColumn,
					firstRow + rowSpacing * 3, value -> config.irisTransitionsEnabled = value);
		} else if (tab == MenuTab.INTERTITLES) {
			addToggle(config.intertitlesEnabled, "screen.silentfilms.intertitles", contentLeft, firstRow,
					value -> config.intertitlesEnabled = value);
			addToggle(config.chatIntertitles, "screen.silentfilms.chat_intertitles", contentLeft,
					firstRow + rowSpacing, value -> config.chatIntertitles = value);
			addToggle(config.voiceIntertitles, "screen.silentfilms.voice_intertitles", contentLeft,
					firstRow + rowSpacing * 2, value -> {
						config.voiceIntertitles = value;
						if (value && !VoiceSetupInstaller.isInstalled()) {
							VoiceSetupInstaller.open(this);
						}
					});
			addToggle(config.suppressVoiceAudio, "screen.silentfilms.suppress_voice", contentLeft,
					firstRow + rowSpacing * 3, value -> config.suppressVoiceAudio = value);
			addCycle(config.intertitleDurationTicks, "screen.silentfilms.duration", new Integer[]{40, 80, 120, 160},
					secondColumn, firstRow, value -> config.intertitleDurationTicks = value,
					value -> Component.literal((value / 20) + "s"));
			addCycle(config.intertitleTextScale, "screen.silentfilms.text_scale", new Integer[]{0, 1, 2},
					secondColumn, firstRow + rowSpacing, value -> config.intertitleTextScale = value,
					value -> Component.translatable("screen.silentfilms.scale." + value));
			addRenderableWidget(new FilmButton(secondColumn, firstRow + rowSpacing * 2, columnWidth, 24,
					Component.translatable("screen.silentfilms.voice_setup.button"), false,
					() -> VoiceSetupInstaller.open(this)));
		} else {
			int soundRowSpacing = 34;
			addToggle(!ClientAudioController.gameplaySoundsMuted(), "screen.silentfilms.sound_effects", contentLeft, firstRow,
					value -> ClientAudioController.setGameplaySoundsMuted(!value));
			addVolumeSlider(secondColumn, firstRow, SoundSource.MUSIC, "screen.silentfilms.music_volume");
			addCycle(config.soundtrackMode(), "screen.silentfilms.soundtrack_mode",
					SoundtrackMode.values(), contentLeft, firstRow + soundRowSpacing,
					ClientAudioController::setSoundtrackMode,
					value -> Component.translatable("screen.silentfilms.soundtrack_mode." + value.name().toLowerCase(java.util.Locale.ROOT)));
			addRenderableWidget(new FilmButton(
					secondColumn,
					firstRow + soundRowSpacing,
					columnWidth,
					24,
					Component.translatable("screen.silentfilms.open_soundtrack_folder"),
					false,
					() -> CustomSoundtrackManager.openFolder(minecraft)
			));
			addRenderableWidget(new FilmButton(
					contentLeft,
					firstRow + soundRowSpacing * 2,
					columnWidth,
					24,
					Component.translatable("screen.silentfilms.reload_soundtracks"),
					false,
					ClientAudioController::reloadCustomSoundtrack
			));
		}

		int footerY = panelY + PANEL_HEIGHT - 48;
		addRenderableWidget(new FilmButton(
				contentLeft,
				footerY,
				columnWidth,
				24,
				Component.translatable("screen.silentfilms.reset"),
				false,
				() -> {
					config.reset();
					SilentFilmsClient.saveConfig();
					rebuildWidgets();
				}
		));
		addRenderableWidget(new FilmButton(
				secondColumn,
				footerY,
				columnWidth,
				24,
				Component.translatable("screen.silentfilms.close"),
				false,
				this::onClose
		));
	}

	private void addToggle(
			boolean initial,
			String translationKey,
			int x,
			int y,
			Consumer<Boolean> setter
	) {
		addCycle(initial, translationKey, new Boolean[]{true, false}, x, y, setter,
				value -> Component.translatable(value ? "screen.silentfilms.on" : "screen.silentfilms.off"));
	}

	private void addToggle(
			boolean initial,
			String translationKey,
			int x,
			int y,
			Consumer<Boolean> setter,
			Function<Boolean, Component> valueStringifier
	) {
		addCycle(initial, translationKey, new Boolean[]{true, false}, x, y, setter, valueStringifier);
	}

	private <T> void addCycle(
			T initial,
			String translationKey,
			T[] values,
			int x,
			int y,
			Consumer<T> setter,
			Function<T, Component> valueStringifier
	) {
		addRenderableWidget(new FilmCycleButton<>(
				x,
				y,
				columnWidth(),
				30,
				Component.translatable(translationKey),
				initial,
				values,
				valueStringifier,
				value -> {
					setter.accept(value);
					SilentFilmsClient.saveConfig();
				}
		));
	}

	@Override
	protected void repositionElements() {
		rebuildWidgets();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (this.parent != null) {
			super.extractBackground(graphics, mouseX, mouseY, partialTick);
		} else {
			graphics.fillGradient(0, 0, width, height, 0xB0100F0D, 0xD0000000);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int panelX = panelX();
		int panelY = panelY();
		int panelWidth = panelWidth();
		int left = contentLeft();
		int right = panelX + panelWidth - 32;

		graphics.fill(panelX + 5, panelY + 7, panelX + panelWidth + 5, panelY + PANEL_HEIGHT + 7, 0x78000000);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + PANEL_HEIGHT, 0xF0151412);
		graphics.outline(panelX, panelY, panelWidth, PANEL_HEIGHT, 0xFFC7B48D);
		graphics.outline(panelX + 6, panelY + 6, panelWidth - 12, PANEL_HEIGHT - 12, 0xFF685941);
		graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 4, 0xFFC7B48D);

		graphics.centeredText(font, title, width / 2, panelY + 15, 0xFFE8DCC0);
		graphics.centeredText(
				font,
				Component.translatable("screen.silentfilms.subtitle"),
				width / 2,
				panelY + 31,
				0xFF8E7B5C
		);
		graphics.fill(left, panelY + 87, right, panelY + 88, 0xFF6D5C42);

		int sectionY = panelY + 94;
		if (tab == MenuTab.FILM) {
			extractSectionHeader(graphics, Component.translatable("screen.silentfilms.section.projector"), left, sectionY,
					right - left);
		} else if (tab == MenuTab.INTERTITLES) {
			extractSectionHeader(graphics, Component.translatable("screen.silentfilms.section.intertitles"), left, sectionY,
					right - left);
			graphics.centeredText(
					font,
					Component.translatable(
							"screen.silentfilms.voice_status",
							Component.translatable("screen.silentfilms.voice_status."
									+ VoiceIntertitleClient.status().name().toLowerCase(java.util.Locale.ROOT))
					),
					width / 2,
					panelY + 248,
					0xFF8E7B5C
			);
			graphics.centeredText(
					font,
					Component.literal(VoiceIntertitleClient.diagnosticsSummary()),
					width / 2,
					panelY + 262,
					0xFF6F614C
			);
			graphics.centeredText(
					font,
					Component.translatable("screen.silentfilms.voice_setup_hint"),
					width / 2,
					panelY + 276,
					0xFF6F614C
			);
		} else {
			extractSectionHeader(graphics, Component.translatable("screen.silentfilms.section.sound"), left, sectionY,
					right - left);
			graphics.centeredText(
					font,
					Component.translatable("screen.silentfilms.soundtrack_mode_hint."
							+ SilentFilmsClient.config().soundtrackMode().name().toLowerCase(java.util.Locale.ROOT)),
					width / 2,
					panelY + 214,
					0xFF8E7B5C
				);
			if (SilentFilmsClient.config().soundtrackMode() == SoundtrackMode.CUSTOM) {
				graphics.centeredText(
						font,
						Component.translatable("screen.silentfilms.custom_track_count", CustomSoundtrackManager.trackCount()),
						width / 2,
						panelY + 200,
						0xFF6F614C
				);
				graphics.centeredText(
						font,
						Component.translatable("screen.silentfilms.custom_soundtrack_hint"),
						width / 2,
						panelY + 228,
						0xFF6F614C
				);
			}
		}

		graphics.centeredText(
				font,
				Component.translatable("screen.silentfilms.key_hint", OPEN_MENU_KEY_LABEL()),
				width / 2,
				panelY + PANEL_HEIGHT - 14,
				0xFF8E7B5C
		);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void extractSectionHeader(GuiGraphicsExtractor graphics, Component label, int x, int y, int width) {
		graphics.text(font, label, x, y, 0xFFE0CFA9);
		int labelWidth = font.width(label);
		graphics.fill(x + labelWidth + 12, y + 4, x + width, y + 5, 0xFF4F4332);
	}

	private String OPEN_MENU_KEY_LABEL() {
		return SilentFilmsClient.OPEN_MENU_KEY.getTranslatedKeyMessage().getString();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	@Override
	public void onClose() {
		SilentFilmsClient.saveConfig();
		minecraft.gui.setScreen(parent);
	}

	private int panelWidth() {
		return Math.max(360, Math.min(720, width - 32));
	}

	private int panelX() {
		return (width - panelWidth()) / 2;
	}

	private int panelY() {
		return (height - PANEL_HEIGHT) / 2;
	}

	private int contentLeft() {
		return panelX() + 32;
	}

	private int columnWidth() {
		return (panelWidth() - 64 - 16) / 2;
	}

	private void addVolumeSlider(int x, int y, SoundSource source, String labelKey) {
			addRenderableWidget(new FilmSlider(
				x,
				y,
				columnWidth(),
				34,
				Component.translatable(labelKey),
				Minecraft.getInstance().options.getSoundSourceVolume(source),
				value -> setSoundVolume(source, value)
		));
	}

	private static void setSoundVolume(SoundSource source, double volume) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.options.getSoundSourceOptionInstance(source).set(Math.clamp(volume, 0.0, 1.0));
		minecraft.options.save();
	}

	private enum MenuTab {
		FILM,
		INTERTITLES,
		SOUND
	}

	private static class FilmButton extends AbstractButton {
		private final boolean selected;
		private final Runnable action;

		private FilmButton(int x, int y, int width, int height, Component message, boolean selected, Runnable action) {
			super(x, y, width, height, message);
			this.selected = selected;
			this.action = action;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			action.run();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			int right = x + getWidth();
			int bottom = y + getHeight();
			boolean highlighted = selected || isHoveredOrFocused();
			graphics.fill(x + 2, y + 3, right + 2, bottom + 3, 0x70000000);
			graphics.fill(x, y, right, bottom, selected ? 0xFFD0B889 : highlighted ? 0xB33F372B : 0xB322211E);
			graphics.outline(x, y, getWidth(), getHeight(), highlighted ? 0xFFE0CFA9 : 0xFF766348);
			graphics.fill(x, y, right, y + 2, selected ? 0xFFF2E5C7 : 0xFF8F7956);
			graphics.centeredText(
					Minecraft.getInstance().font,
					getMessage(),
					x + getWidth() / 2,
					y + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2,
					selected ? 0xFF261F17 : 0xFFE8DCC0
			);
		}

		@Override
		public void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	private static final class FilmCycleButton<T> extends AbstractButton {
		private final Component label;
		private final List<T> values;
		private final Function<T, Component> valueStringifier;
		private final Consumer<T> onValueChange;
		private int index;
		private T value;

		private FilmCycleButton(
				int x,
				int y,
				int width,
				int height,
				Component label,
				T initial,
				T[] values,
				Function<T, Component> valueStringifier,
				Consumer<T> onValueChange
		) {
			super(x, y, width, height, label);
			this.label = label;
			this.values = Arrays.asList(values);
			this.valueStringifier = valueStringifier;
			this.onValueChange = onValueChange;
			this.value = initial;
			this.index = Math.max(0, this.values.indexOf(initial));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (values.isEmpty()) {
				return;
			}
			int direction = input.hasShiftDown() ? -1 : 1;
			index = Math.floorMod(index + direction, values.size());
			value = values.get(index);
			onValueChange.accept(value);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			int right = x + getWidth();
			int bottom = y + getHeight();
			boolean highlighted = isHoveredOrFocused();
			graphics.fill(x + 2, y + 3, right + 2, bottom + 3, 0x60000000);
			graphics.fill(x, y, right, bottom, highlighted ? 0xB33B332A : 0xB3201F1C);
			graphics.outline(x, y, getWidth(), getHeight(), highlighted ? 0xFFD0B889 : 0xFF5E503D);
			graphics.fill(x, y, x + 2, bottom, highlighted ? 0xFFE0CFA9 : 0xFF806B4C);

			Minecraft minecraft = Minecraft.getInstance();
			int textY = y + (getHeight() - minecraft.font.lineHeight) / 2;
			Component valueText = valueStringifier.apply(value);
			graphics.text(minecraft.font, label, x + 12, textY, 0xFFE2D3B5);
			graphics.text(minecraft.font, valueText, right - 12 - minecraft.font.width(valueText), textY,
					highlighted ? 0xFFF1DFC0 : 0xFFC5B591);
		}

		@Override
		public void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	private static final class FilmSlider extends AbstractSliderButton {
		private final Component label;
		private final Consumer<Double> onValueChange;

		private FilmSlider(
				int x,
				int y,
				int width,
				int height,
				Component label,
				double initialValue,
				Consumer<Double> onValueChange
		) {
			super(x, y, width, height, label, Math.clamp(initialValue, 0.0, 1.0));
			this.label = label;
			this.onValueChange = onValueChange;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(
					"screen.silentfilms.music_volume_value",
					Math.round(value * 100.0)
			));
		}

		@Override
		protected void applyValue() {
			onValueChange.accept(value);
		}

		@Override
		public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int x = getX();
			int y = getY();
			int right = x + getWidth();
			int bottom = y + getHeight();
			boolean highlighted = isHoveredOrFocused();
			int trackLeft = x + 10;
			int trackRight = right - 10;
			int trackY = y + 22;
			int filledRight = trackLeft + (int) Math.round((trackRight - trackLeft) * value);
			int handleX = Math.clamp(filledRight, trackLeft, trackRight);

			graphics.fill(x + 2, y + 3, right + 2, bottom + 3, 0x60000000);
			graphics.fill(x, y, right, bottom, highlighted ? 0xB33B332A : 0xB3201F1C);
			graphics.outline(x, y, getWidth(), getHeight(), highlighted ? 0xFFD0C09E : 0xFF5E503D);
			graphics.text(Minecraft.getInstance().font, label, x + 12, y + 4, 0xFFE2D3B5);
			Component valueText = Component.literal(Math.round(value * 100.0) + "%");
			graphics.text(Minecraft.getInstance().font, valueText,
					right - 12 - Minecraft.getInstance().font.width(valueText), y + 4,
					highlighted ? 0xFFF1DFC0 : 0xFFC5B591);
			graphics.fill(trackLeft, trackY, trackRight, trackY + 4, 0xFF4B4032);
			graphics.fill(trackLeft, trackY, filledRight, trackY + 4, 0xFFC7B48D);
			graphics.fill(handleX - 3, trackY - 3, handleX + 4, trackY + 7,
					highlighted ? 0xFFF2E5C7 : 0xFFE0CFA9);
		}
	}
}
