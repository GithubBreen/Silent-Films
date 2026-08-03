package com.breenihilation.voice;

// Bridges Simple Voice Chat events into the voice transcription pipeline.
import com.breenihilation.SilentFilms;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

public final class SilentFilmsVoicechatPlugin implements VoicechatPlugin {
	@Override
	public String getPluginId() {
		return SilentFilms.MOD_ID;
	}

	@Override
	public void registerEvents(EventRegistration registration) {
		registration.registerEvent(ClientSoundEvent.class, this::onClientSound);
	}

	private void onClientSound(ClientSoundEvent event) {
		event.setRawAudio(VoiceCaptureBridge.capture(event.getRawAudio(), event.isWhispering()));
	}
}
