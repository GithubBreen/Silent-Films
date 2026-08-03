package com.breenihilation.client;

// Lists the available bundled, custom, vanilla, and disabled music modes.
public enum SoundtrackMode {
	ON,
	CUSTOM,
	VANILLA,
	OFF;

	public static SoundtrackMode fromConfig(String value) {
		if (value == null) {
			return ON;
		}

		try {
			return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return ON;
		}
	}
}
