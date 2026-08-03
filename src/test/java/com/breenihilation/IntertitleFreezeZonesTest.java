package com.breenihilation;

// Verifies the entity freeze radius calculations.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntertitleFreezeZonesTest {
	@Test
	void usesAThreeDimensionalFortyEightBlockRadius() {
		assertTrue(IntertitleFreezeZones.withinRadius(0, 0, 0, 48, 0, 0));
		assertTrue(IntertitleFreezeZones.withinRadius(10, 20, 30, 10, 20, 30));
		assertFalse(IntertitleFreezeZones.withinRadius(0, 0, 0, 48.01, 0, 0));
		assertFalse(IntertitleFreezeZones.withinRadius(0, 0, 0, 34, 34, 0));
	}
}
