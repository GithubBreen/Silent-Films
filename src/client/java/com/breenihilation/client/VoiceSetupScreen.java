package com.breenihilation.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import java.util.Locale;


// setups screen for parkaeet

public final class VoiceSetupScreen extends Screen {
	private static final int PANEL_WIDTH = 520;
	private static final int PANEL_HEIGHT = 260;
	private final Screen parent;
	private VoiceSetupInstaller.Stage lastStage;

	public VoiceSetupScreen(Screen parent) {
		super(Component.translatable("screen.silentfilms.voice_setup.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		VoiceSetupInstaller.SetupSnapshot snapshot = VoiceSetupInstaller.snapshot();
		lastStage = snapshot.stage();
		int center = width / 2;
		int buttonsY = panelY() + PANEL_HEIGHT - 50;

		if (snapshot.stage() == VoiceSetupInstaller.Stage.IDLE) {
			addRenderableWidget(new SetupButton(center - 198, buttonsY, 126, 26,
					Component.translatable(
							"screen.silentfilms.voice_setup.install",
							Math.max(1L, VoiceSetupInstaller.expectedDownloadBytes() / (1024L * 1024L))
					), () -> {
				VoiceSetupInstaller.start();
				rebuildWidgets();
			}));
			addRenderableWidget(new SetupButton(center - 63, buttonsY, 126, 26,
					Component.translatable("screen.silentfilms.voice_setup.not_now"), this::decline));
			addRenderableWidget(new SetupButton(center + 72, buttonsY, 126, 26,
					Component.translatable("screen.silentfilms.voice_setup.never"), this::dismiss));
		} else if (snapshot.stage() == VoiceSetupInstaller.Stage.DELETING) {
			addRenderableWidget(new SetupButton(center - 80, buttonsY, 160, 26,
					Component.translatable("screen.silentfilms.voice_setup.close"), this::returnToParent));
		} else if (snapshot.stage().busy()) {
			addRenderableWidget(new SetupButton(center - 80, buttonsY, 160, 26,
					Component.translatable("screen.silentfilms.voice_setup.cancel"), VoiceSetupInstaller::cancel));
		} else if (snapshot.stage() == VoiceSetupInstaller.Stage.COMPLETE) {
			addRenderableWidget(new SetupButton(center - 168, buttonsY, 160, 26,
					Component.translatable("screen.silentfilms.voice_setup.delete_model"), () -> {
				VoiceSetupInstaller.deleteModel();
				rebuildWidgets();
			}));
			addRenderableWidget(new SetupButton(center + 8, buttonsY, 160, 26,
					Component.translatable("screen.silentfilms.voice_setup.done"), this::returnToParent));
		} else {
			addRenderableWidget(new SetupButton(center - 168, buttonsY, 160, 26,
					Component.translatable("screen.silentfilms.voice_setup.retry"), () -> {
					VoiceSetupInstaller.resetForRetry();
					VoiceSetupInstaller.start();
					rebuildWidgets();
			}));
			addRenderableWidget(new SetupButton(
					center + 8,
					buttonsY,
					160,
					26,
					Component.translatable("screen.silentfilms.voice_setup.close"),
					this::returnToParent
			));
		}
	}

	@Override
	public void tick() {
		VoiceSetupInstaller.Stage stage = VoiceSetupInstaller.snapshot().stage();
		if (stage != lastStage) {
			rebuildWidgets();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fillGradient(0, 0, width, height, 0xD0100F0D, 0xE8000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		VoiceSetupInstaller.SetupSnapshot snapshot = VoiceSetupInstaller.snapshot();
		int x = panelX();
		int y = panelY();
		int center = width / 2;

		graphics.fill(x + 5, y + 7, x + PANEL_WIDTH + 5, y + PANEL_HEIGHT + 7, 0x78000000);
		graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFA151412);
		graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFFC7B48D);
		graphics.outline(x + 6, y + 6, PANEL_WIDTH - 12, PANEL_HEIGHT - 12, 0xFF685941);
		graphics.centeredText(font, title, center, y + 20, 0xFFE8DCC0);
		graphics.centeredText(font, stageText(snapshot.stage()), center, y + 52, 0xFFD7C49D);

		if (snapshot.stage() == VoiceSetupInstaller.Stage.IDLE) {
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.prompt.1"), center, y + 82, 0xFFE2D3B5);
			graphics.centeredText(font, Component.translatable(
					"screen.silentfilms.voice_setup.prompt.2",
					Component.translatable("screen.silentfilms.voice_model.parakeet"),
					VoiceSetupInstaller.selectedDownloadMiB()
			), center, y + 98, 0xFFC1AF8C);
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.prompt.3"),
					center, y + 114, 0xFFC1AF8C);
		} else if (snapshot.stage() == VoiceSetupInstaller.Stage.DELETING) {
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.deleting"), center, y + 96, 0xFFD7C49D);
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.background_download"), center, y + 114, 0xFF8E7B5C);
		} else if (snapshot.stage().busy()) {
			drawProgress(graphics, snapshot, x + 44, y + 96, PANEL_WIDTH - 88);
			graphics.centeredText(font,
					Component.translatable("screen.silentfilms.voice_setup.background_download"),
					center, y + 142, 0xFF8E7B5C);
		} else if (snapshot.stage() == VoiceSetupInstaller.Stage.COMPLETE) {
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.complete"), center, y + 96, 0xFFD7C49D);
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.parakeet_ready"),
					center, y + 114, 0xFF8E7B5C);
		} else {
			String error = snapshot.error() == null ? "" : snapshot.error();
			if (error.length() > 72) {
				error = error.substring(0, 69) + "...";
			}
			graphics.centeredText(font, Component.literal(error), center, y + 96, 0xFFD59A88);
		}

		if (!VoiceSetupInstaller.simpleVoiceChatInstalled()) {
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.svc_missing"), center, y + 166, 0xFFD59A88);
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.svc_restart"), center, y + 181, 0xFF8E7B5C);
		} else {
			graphics.centeredText(font, Component.translatable("screen.silentfilms.voice_setup.svc_ready"), center, y + 174, 0xFF8E7B5C);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	private void drawProgress(GuiGraphicsExtractor graphics, VoiceSetupInstaller.SetupSnapshot snapshot, int x, int y, int width) {
		int filled = Math.round((width - 4) * snapshot.progress());
		graphics.fill(x, y, x + width, y + 18, 0xFF29241D);
		graphics.outline(x, y, width, 18, 0xFF806B4C);
		graphics.fill(x + 2, y + 2, x + 2 + filled, y + 16, 0xFFC7B48D);
		long completedMiB = snapshot.completedBytes() / (1024L * 1024L);
		long totalMiB = snapshot.totalBytes() / (1024L * 1024L);
		String progressText = completedMiB + " / " + totalMiB + " MiB";
		if (snapshot.downloadBytesPerSecond() > 0.0) {
			progressText += "  •  " + formatDownloadSpeed(snapshot.downloadBytesPerSecond());
		}
		graphics.centeredText(font, Component.literal(progressText), width / 2 + x, y + 24, 0xFFC1AF8C);
	}

	private static String formatDownloadSpeed(double bytesPerSecond) {
		double mebibytesPerSecond = bytesPerSecond / (1024.0 * 1024.0);
		if (mebibytesPerSecond >= 1.0) {
			return String.format(Locale.ROOT, "%.1f MiB/s", mebibytesPerSecond);
		}
		return String.format(Locale.ROOT, "%.0f KiB/s", bytesPerSecond / 1024.0);
	}

	private Component stageText(VoiceSetupInstaller.Stage stage) {
		return Component.translatable("screen.silentfilms.voice_setup.stage." + stage.name().toLowerCase(Locale.ROOT));
	}

	private void decline() {
		SilentFilmsClient.config().voiceIntertitles = false;
		SilentFilmsClient.saveConfig();
		returnToParent();
	}

	private void dismiss() {
		SilentFilmsClient.config().voiceIntertitles = false;
		SilentFilmsClient.config().voiceSetupPromptDismissed = true;
		SilentFilmsClient.saveConfig();
		returnToParent();
	}

	private void returnToParent() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void onClose() {
		returnToParent();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private int panelX() {
		return (width - PANEL_WIDTH) / 2;
	}

	private int panelY() {
		return (height - PANEL_HEIGHT) / 2;
	}

	private static final class SetupButton extends AbstractButton {
		private final Runnable action;

		private SetupButton(int x, int y, int width, int height, Component message, Runnable action) {
			super(x, y, width, height, message);
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
			boolean highlighted = isHoveredOrFocused();
			graphics.fill(x + 2, y + 3, right + 2, bottom + 3, 0x70000000);
			graphics.fill(x, y, right, bottom, highlighted ? 0xB33F372B : 0xB322211E);
			graphics.outline(x, y, getWidth(), getHeight(), highlighted ? 0xFFE0CFA9 : 0xFF766348);
			graphics.centeredText(Minecraft.getInstance().font, getMessage(), x + getWidth() / 2,
					y + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2, 0xFFE8DCC0);
		}

		@Override
		public void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
