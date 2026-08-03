package com.breenihilation.client;


import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

// music fade in out

final class FadingMusicInstance extends AbstractTickableSoundInstance {
	private float targetVolume;
	private float volumeStep;
	private boolean stopWhenSilent;

	FadingMusicInstance(Identifier identifier, float initialVolume) {
		super(SoundEvent.createVariableRangeEvent(identifier), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
		volume = initialVolume;
		pitch = 1.0f;
		looping = false;
		relative = true;
		attenuation = SoundInstance.Attenuation.NONE;
	}

	void fadeTo(float target, int ticks, boolean stopAtEnd) {
		targetVolume = Math.clamp(target, 0.0f, 1.0f);
		volumeStep = Math.abs(targetVolume - volume) / Math.max(1, ticks);
		stopWhenSilent = stopAtEnd;
	}

	@Override
	public void tick() {
		if (volume < targetVolume) {
			volume = Math.min(targetVolume, volume + volumeStep);
		} else if (volume > targetVolume) {
			volume = Math.max(targetVolume, volume - volumeStep);
		}
		if (stopWhenSilent && volume <= 0.0001f) {
			stop();
		}
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
