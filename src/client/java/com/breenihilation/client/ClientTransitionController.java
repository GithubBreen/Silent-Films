package com.breenihilation.client;
import com.breenihilation.TransitionPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class ClientTransitionController {
	private static final IrisTransition IRIS_TRANSITION = new IrisTransition();
	private static final MenuIrisIntro MENU_IRIS = new MenuIrisIntro();
	private static boolean serverProtocolActive;
	private static volatile boolean resourceReloadSuppressed;

	private ClientTransitionController() {
	}

	static void tickMenu(Minecraft client) {
		MENU_IRIS.tick(
				client.gui.screen() instanceof TitleScreen,
				client.gui.overlay() instanceof LoadingOverlay
		);
	}

	static int tickWorld(boolean transitionsEnabled, boolean sleeping) {
		if (!transitionsEnabled) {
			return IRIS_TRANSITION.disable();
		}
		IRIS_TRANSITION.tick(sleeping);
		return IRIS_TRANSITION.consumeTravelReadyTicket();
	}

	static void resetWorld() {
		serverProtocolActive = false;
		IRIS_TRANSITION.reset();
	}

	public static IrisTransition irisTransition() {
		return IRIS_TRANSITION;
	}

	static boolean suppressesIntertitles() {
		return IRIS_TRANSITION.suppressesIntertitles();
	}

	static boolean menuIrisActive() {
		return MENU_IRIS.menuMaskActive();
	}

	static float menuIrisAperture() {
		return MENU_IRIS.menuAperture();
	}

	public static boolean delayLoadingCompletionForIris(SilentFilmsConfig config) {
		return !resourceReloadSuppressed
				&& config != null
				&& config.filmModeEnabled
				&& config.irisTransitionsEnabled
				&& MENU_IRIS.delayLoadingCompletion();
	}

	static boolean loadingIrisActive() {
		return !resourceReloadSuppressed && MENU_IRIS.loadingMaskActive();
	}

	static float loadingIrisAperture() {
		return MENU_IRIS.loadingAperture();
	}

	public static void setResourceReloadSuppressed(boolean suppressed) {
		resourceReloadSuppressed = suppressed;
		if (suppressed) {
			MENU_IRIS.cancelLoadingHandoff();
		}
	}

	public static boolean resourceReloadSuppressed() {
		return resourceReloadSuppressed;
	}

	static void onTravelStart(TransitionPayloads.TravelStart payload) {
		serverProtocolActive = true;
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config == null || !config.irisTransitionsEnabled) {
			if (payload.awaitAcknowledgement()) {
				sendTravelReady(payload.ticketId());
			}
			return;
		}

		if (!payload.awaitAcknowledgement() && !cameraCanSeeTravelTarget(payload)) {
			return;
		}

		IRIS_TRANSITION.beginTravel(
				payload.ticketId(),
				payload.edgeTicks(),
				payload.awaitAcknowledgement(),
				payload.resumable()
		);
	}

	static void onSleepState(TransitionPayloads.SleepState payload) {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (config != null && config.irisTransitionsEnabled) {
			IRIS_TRANSITION.setSleepState(
					payload.sessionId(),
					payload.active(),
					payload.targetClosed(),
					payload.timeSkipped()
			);
		}
	}

	public static void onFallbackTravelPacket() {
		SilentFilmsConfig config = SilentFilmsClient.config();
		if (!serverProtocolActive && config != null && config.irisTransitionsEnabled) {
			IRIS_TRANSITION.beginFallbackTravel();
		}
	}

	public static void onFallbackTravelPacketApplied() {
		if (!serverProtocolActive) {
			IRIS_TRANSITION.requestFallbackRelease();
		}
	}

	private static void sendTravelReady(int ticketId) {
		if (ticketId < 0) {
			return;
		}
		try {
			if (ClientPlayNetworking.canSend(TransitionPayloads.TravelReady.TYPE)) {
				ClientPlayNetworking.send(new TransitionPayloads.TravelReady(ticketId));
			}
		} catch (IllegalStateException ignored) {
			// The connection can close while a transition is resetting.
		}
	}

	private static boolean cameraCanSeeTravelTarget(TransitionPayloads.TravelStart payload) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.gameRenderer == null) {
			return false;
		}

		Camera camera = client.gameRenderer.mainCamera();
		if (camera == null || !camera.isInitialized()) {
			return false;
		}

		if (payload.checkOrigin()) {
			Entity subject = client.level.getEntity(payload.subjectEntityId());
			if (subject != null && cameraCanSeeBox(client, camera, subject.getBoundingBox())) {
				return true;
			}
		}

		if (payload.checkDestination()) {
			AABB destination = new AABB(
					payload.destinationX() - 0.6,
					payload.destinationY(),
					payload.destinationZ() - 0.6,
					payload.destinationX() + 0.6,
					payload.destinationY() + 1.8,
					payload.destinationZ() + 0.6
			);
			return cameraCanSeeBox(client, camera, destination);
		}

		return false;
	}

	private static boolean cameraCanSeeBox(Minecraft client, Camera camera, AABB targetBox) {
		Frustum frustum = camera.getCullFrustum();
		if (frustum != null && !frustum.isVisible(targetBox)) {
			return false;
		}

		Vec3 cameraPosition = camera.position();
		Vec3 targetCenter = targetBox.getCenter();
		Vec3 toTarget = targetCenter.subtract(cameraPosition);
		if (toTarget.lengthSqr() < 0.0001) {
			return true;
		}

		Vec3 forward = new Vec3(
				camera.forwardVector().x(),
				camera.forwardVector().y(),
				camera.forwardVector().z()
		).normalize();
		if (forward.dot(toTarget.normalize()) < 0.05) {
			return false;
		}

		Vec3[] samplePoints = {
				targetCenter,
				new Vec3(targetCenter.x(), targetBox.maxY, targetCenter.z()),
				new Vec3(targetCenter.x(), targetBox.minY, targetCenter.z())
		};
		for (Vec3 target : samplePoints) {
			BlockHitResult hit = client.level.clip(new ClipContext(
					cameraPosition,
					target,
					ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE,
					camera.entity()
			));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}
}
