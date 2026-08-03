package com.breenihilation.client;

// Verifies the title-screen iris handoff.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuIrisIntroTest {
	@Test
	void loadingScreenClosesThenHoldsBlackUntilTitleIsVisible() {
		MenuIrisIntro intro = new MenuIrisIntro();

		assertTrue(intro.delayLoadingCompletion());
		assertEquals(1.0f, intro.loadingAperture(), 0.0001f);
		for (int tick = 0; tick < 24; tick++) {
			intro.delayLoadingCompletion();
		}
		assertEquals(0.0f, intro.loadingAperture(), 0.0001f);
		assertFalse(intro.delayLoadingCompletion());

		intro.tick(true, true);
		assertTrue(intro.menuMaskActive());
		assertEquals(0.0f, intro.menuAperture(), 0.0001f);

		intro.tick(true, false);
		assertTrue(intro.menuMaskActive());
		assertEquals(0.0f, intro.menuAperture(), 0.0001f);
	}

	@Test
	void titleOpensOnceAndReturningFromASubmenuDoesNotRestartIt() {
		MenuIrisIntro intro = new MenuIrisIntro();
		intro.tick(true, false);
		assertEquals(0.0f, intro.menuAperture(), 0.0001f);

		for (int tick = 0; tick < 32; tick++) {
			intro.tick(true, false);
		}
		assertFalse(intro.menuMaskActive());
		assertEquals(1.0f, intro.menuAperture(), 0.0001f);

		intro.tick(false, false);
		intro.tick(true, false);
		assertFalse(intro.menuMaskActive());
		assertEquals(1.0f, intro.menuAperture(), 0.0001f);
	}

	@Test
	void ordinaryResourceReloadDoesNotLeaveTheMenuBlack() {
		MenuIrisIntro intro = new MenuIrisIntro();
		intro.tick(true, false);
		for (int tick = 0; tick < 32; tick++) {
			intro.tick(true, false);
		}

		assertFalse(intro.menuMaskActive());
		assertTrue(intro.delayLoadingCompletion());
		intro.cancelLoadingHandoff();
		intro.tick(false, true);
		intro.tick(false, false);

		assertFalse(intro.menuMaskActive());
		assertEquals(1.0f, intro.menuAperture(), 0.0001f);
	}
}
