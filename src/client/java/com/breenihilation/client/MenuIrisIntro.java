package com.breenihilation.client;

// main menu iris out
final class MenuIrisIntro {
	private static final int CLOSING_TICKS = 24;
	private static final int OPENING_TICKS = 32;
	private int closingTick = -1;
	private int openingTick = -1;
	private boolean loadingHandoff;
	private boolean titleIntroPlayed;


	boolean delayLoadingCompletion() {
		if (!loadingHandoff) {
			loadingHandoff = true;
			closingTick = 0;
			openingTick = -1;
			return true;
		}
		if (closingTick < CLOSING_TICKS) {
			closingTick++;
			return true;
		}
		return false;
	}

	void cancelLoadingHandoff() {
		if (!titleIntroPlayed) {
			return;
		}
		loadingHandoff = false;
		closingTick = -1;
		openingTick = -1;
	}

	void tick(boolean titleScreenOpen, boolean loadingOverlayOpen) {
		boolean titleVisible = titleScreenOpen && !loadingOverlayOpen;
		if (loadingOverlayOpen) {
			return;
		}

		if (titleVisible && (loadingHandoff || !titleIntroPlayed)) {
			loadingHandoff = false;
			closingTick = -1;
			openingTick = 0;
			titleIntroPlayed = true;
		} else if (titleVisible && openingTick >= 0 && openingTick < OPENING_TICKS) {
			openingTick++;
		} else if (!titleVisible) {
			openingTick = -1;
		}
	}

	boolean loadingMaskActive() {
		return closingTick >= 0;
	}

	float loadingAperture() {
		if (closingTick < 0) {
			return 1.0f;
		}
		float progress = smooth(closingTick / (float) CLOSING_TICKS);
		return 1.0f - progress;
	}

	boolean menuMaskActive() {
		return loadingHandoff || openingTick >= 0 && openingTick < OPENING_TICKS;
	}

	float menuAperture() {
		if (loadingHandoff) {
			return 0.0f;
		}
		if (openingTick < 0) {
			return 1.0f;
		}
		return smooth(openingTick / (float) OPENING_TICKS);
	}

	private static float smooth(float progress) {
		float clamped = Math.clamp(progress, 0.0f, 1.0f);
		return clamped * clamped * (3.0f - 2.0f * clamped);
	}
}
