package com.breenihilation;

// Defines the hearing ranges used for chat and voice intertitles.
public final class DialogueRanges {
	public static final int CHAT_BLOCKS = 40;
	public static final int VOICE_BLOCKS = 40;
	public static final int WHISPER_BLOCKS = 16;

	private DialogueRanges() {
	}

	public static double squared(int blocks) {
		return (double) blocks * blocks;
	}
}
