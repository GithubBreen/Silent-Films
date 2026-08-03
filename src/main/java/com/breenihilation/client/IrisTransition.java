package com.breenihilation.client;

// Tracks the aperture state for travel and sleep transitions.
public final class IrisTransition {
	private static final float TRAVEL_OPEN_SPEED = 1.0f / 12.0f;
	private static final float SLEEP_CLOSE_SPEED = 1.0f / 30.0f;
	private static final float SLEEP_OPEN_SPEED = 1.0f / 20.0f;

	private TransitionMode mode = TransitionMode.IDLE;
	private float progress;
	private int travelTicket = -1;
	private int travelEdgeTicks = 12;
	private boolean travelAwaitingAcknowledgement;
	private boolean travelAcknowledgementSent;
	private boolean travelReleased;
	private boolean travelFinalRelease;
	private boolean travelResumable;
	private boolean fallbackTravel;
	private boolean fallbackReleaseRequested;

	private int sleepSessionId = -1;
	private boolean sleepActive;
	private boolean sleepTargetClosed;
	private boolean sleepTimeSkipped;

	public void beginTravel(int ticketId, int edgeTicks, boolean awaitAcknowledgement) {
		beginTravel(ticketId, edgeTicks, awaitAcknowledgement, false);
	}

	public void beginTravel(int ticketId, int edgeTicks, boolean awaitAcknowledgement, boolean resumable) {
		if (mode == TransitionMode.TRAVEL && travelTicket == ticketId && ticketId != -1) {
			travelEdgeTicks = Math.max(1, edgeTicks);
			travelAwaitingAcknowledgement = awaitAcknowledgement;
			travelAcknowledgementSent = false;
			travelReleased = false;
			travelFinalRelease = false;
			travelResumable = resumable;
			fallbackTravel = false;
			fallbackReleaseRequested = false;
			return;
		}

		mode = TransitionMode.TRAVEL;
		progress = 0.0f;
		travelTicket = ticketId;
		travelEdgeTicks = Math.max(1, edgeTicks);
		travelAwaitingAcknowledgement = awaitAcknowledgement;
		travelAcknowledgementSent = false;
		travelReleased = false;
		travelFinalRelease = false;
		travelResumable = resumable;
		fallbackTravel = false;
		fallbackReleaseRequested = false;
	}

	public void beginFallbackTravel() {
		beginTravel(-1, 12, false);
		fallbackTravel = true;
	}

	public void requestFallbackRelease() {
		if (mode == TransitionMode.TRAVEL && fallbackTravel) {
			fallbackReleaseRequested = true;
		}
	}

	public void releaseTravel(int ticketId) {
		releaseTravel(ticketId, true);
	}

	public void releaseTravel(int ticketId, boolean finalRelease) {
		if (mode != TransitionMode.TRAVEL || (travelTicket != -1 && travelTicket != ticketId)) {
			return;
		}

		travelReleased = true;
		travelFinalRelease |= finalRelease;
	}

	public int consumeTravelReadyTicket() {
		if (mode != TransitionMode.TRAVEL
				|| !travelAwaitingAcknowledgement
				|| travelAcknowledgementSent
				|| progress < 0.999f) {
			return -1;
		}

		travelAcknowledgementSent = true;
		return travelTicket;
	}

	public void setSleepState(int sessionId, boolean active, boolean targetClosed, boolean timeSkipped) {
		if (sessionId < sleepSessionId) {
			return;
		}
		if (sessionId != sleepSessionId) {
			sleepSessionId = sessionId;
			if (mode == TransitionMode.IDLE) {
				progress = 0.0f;
			}
		}

		sleepActive = active;
		sleepTargetClosed = targetClosed;
		sleepTimeSkipped = timeSkipped;
		if (mode == TransitionMode.IDLE && (active || progress > 0.0f)) {
			mode = TransitionMode.SLEEP;
		}
	}

	public void tick(boolean localPlayerSleeping) {
		switch (mode) {
			case IDLE -> {
				return;
			}
			case TRAVEL -> tickTravel();
			case SLEEP -> tickSleep(localPlayerSleeping);
		}
	}

	public boolean isActive() {
		return mode != TransitionMode.IDLE;
	}

	public boolean suppressesIntertitles() {
		return isActive();
	}

	/** Returns 0 when fully black and 1 when fully open. */
	public float aperture() {
		return 1.0f - smooth(clamp(progress));
	}

	/** Disables the visual and returns a pending travel ticket that needs acknowledgement. */
	public int disable() {
		int ticket = travelAwaitingAcknowledgement && !travelAcknowledgementSent ? travelTicket : -1;
		reset();
		return ticket;
	}

	public void reset() {
		mode = TransitionMode.IDLE;
		progress = 0.0f;
		travelTicket = -1;
		travelEdgeTicks = 12;
		travelAwaitingAcknowledgement = false;
		travelAcknowledgementSent = false;
		travelReleased = false;
		travelFinalRelease = false;
		travelResumable = false;
		fallbackTravel = false;
		fallbackReleaseRequested = false;
		sleepSessionId = -1;
		sleepActive = false;
		sleepTargetClosed = false;
		sleepTimeSkipped = false;
	}

	private void tickTravel() {
		if (!travelReleased) {
			progress = moveTowards(progress, 1.0f, 1.0f / travelEdgeTicks);
			if (fallbackTravel && fallbackReleaseRequested && progress >= 0.999f) {
				travelReleased = true;
			}
			return;
		}

		progress = moveTowards(progress, 0.0f, TRAVEL_OPEN_SPEED);
		if (progress <= 0.0f) {
			if (!travelFinalRelease && travelResumable) {
				return;
			}
			if (sleepActive || sleepTargetClosed) {
				mode = TransitionMode.SLEEP;
			} else {
				reset();
			}
		}
	}

	private void tickSleep(boolean localPlayerSleeping) {
		boolean targetClosed = sleepTargetClosed || (sleepTimeSkipped && localPlayerSleeping);
		float speed = targetClosed ? SLEEP_CLOSE_SPEED : SLEEP_OPEN_SPEED;
		progress = moveTowards(progress, targetClosed ? 1.0f : 0.0f, speed);

		if (!sleepActive && !targetClosed && progress <= 0.0f) {
			reset();
		}
	}

	private static float moveTowards(float current, float target, float amount) {
		if (Math.abs(target - current) <= amount) {
			return target;
		}
		return current + Math.copySign(amount, target - current);
	}

	private static float smooth(float value) {
		return value * value * (3.0f - 2.0f * value);
	}

	private static float clamp(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	private enum TransitionMode {
		IDLE,
		TRAVEL,
		SLEEP
	}
}
