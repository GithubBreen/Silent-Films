package com.breenihilation.client;

// Verifies the bundled Parakeet model manifest.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParakeetModelFilesTest {
	@Test
	void modelDownloadSizeMatchesItsFiles() {
		assertTrue(ParakeetModelFiles.totalBytes() > 650_000_000L);
		assertTrue(ParakeetModelFiles.FILES.stream().allMatch(file -> file.bytes() > 0L));
	}
}
