package com.breenihilation.client;

// Verifies travel and sleep iris transition timing.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisTransitionTest {
	@Test
	void travelClosesWaitsForReadyAndReopensAfterRelease() {
		IrisTransition transition = new IrisTransition();
		transition.beginTravel(7, 12, true);

		for (int tick = 0; tick < 12; tick++) {
			transition.tick(false);
		}

		assertEquals(0.0f, transition.aperture(), 0.001f);
		assertEquals(7, transition.consumeTravelReadyTicket());
		assertEquals(-1, transition.consumeTravelReadyTicket());

		transition.releaseTravel(7);
		for (int tick = 0; tick < 12; tick++) {
			transition.tick(false);
		}

		assertFalse(transition.isActive());
		assertEquals(1.0f, transition.aperture(), 0.001f);
	}

	@Test
	void reversibleTravelReleaseCanResumeFromItsCurrentProgress() {
		IrisTransition transition = new IrisTransition();
		transition.beginTravel(8, 12, true, true);
		for (int tick = 0; tick < 6; tick++) {
			transition.tick(false);
		}
		float partiallyClosed = transition.aperture();

		transition.releaseTravel(8, false);
		for (int tick = 0; tick < 3; tick++) {
			transition.tick(false);
		}
		float partiallyReopened = transition.aperture();
		assertTrue(partiallyReopened > partiallyClosed);

		transition.beginTravel(8, 12, true, true);
		for (int tick = 0; tick < 3; tick++) {
			transition.tick(false);
		}
		assertTrue(transition.aperture() < partiallyReopened);

		transition.releaseTravel(8);
		for (int tick = 0; tick < 12; tick++) {
			transition.tick(false);
		}
		assertFalse(transition.isActive());
	}

	@Test
	void sleepClosureCanReverseAndResumeFromItsCurrentProgress() {
		IrisTransition transition = new IrisTransition();
		transition.setSleepState(1, true, true, false);
		for (int tick = 0; tick < 15; tick++) {
			transition.tick(false);
		}
		float partiallyClosed = transition.aperture();

		transition.setSleepState(1, true, false, false);
		for (int tick = 0; tick < 5; tick++) {
			transition.tick(false);
		}
		float partiallyReopened = transition.aperture();
		assertTrue(partiallyReopened > partiallyClosed);

		transition.setSleepState(1, true, true, false);
		for (int tick = 0; tick < 5; tick++) {
			transition.tick(false);
		}
		assertTrue(transition.aperture() < partiallyReopened);
	}

	@Test
	void sleepStaysClosedForSleepingPlayerUntilWakeAndTimeSkipFinish() {
		IrisTransition transition = new IrisTransition();
		transition.setSleepState(2, true, true, false);
		for (int tick = 0; tick < 30; tick++) {
			transition.tick(false);
		}
		assertEquals(0.0f, transition.aperture(), 0.001f);

		transition.setSleepState(2, true, false, true);
		for (int tick = 0; tick < 10; tick++) {
			transition.tick(true);
		}
		assertEquals(0.0f, transition.aperture(), 0.001f);

		for (int tick = 0; tick < 20; tick++) {
			transition.tick(false);
		}
		assertTrue(transition.aperture() > 0.0f);

		transition.setSleepState(2, false, false, true);
		for (int tick = 0; tick < 20; tick++) {
			transition.tick(false);
		}
		assertFalse(transition.isActive());
	}

	@Test
	void disablingReturnsAnUnsentTravelTicket() {
		IrisTransition transition = new IrisTransition();
		transition.beginTravel(3, 12, true);

		assertEquals(3, transition.disable());
		assertFalse(transition.isActive());
		assertEquals(1.0f, transition.aperture(), 0.001f);
	}

	@Test
	void fallbackTravelWaitsUntilBlackBeforeOpening() {
		IrisTransition transition = new IrisTransition();
		transition.beginFallbackTravel();
		transition.requestFallbackRelease();

		for (int tick = 0; tick < 12; tick++) {
			transition.tick(false);
		}
		assertEquals(0.0f, transition.aperture(), 0.001f);
		assertTrue(transition.isActive());

		for (int tick = 0; tick < 12; tick++) {
			transition.tick(false);
		}
		assertFalse(transition.isActive());
	}
}
