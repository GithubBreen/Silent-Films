package com.breenihilation.client;

// Verifies responsive intertitle sizing calculations.
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilmRendererTest {
	@Test
	void scalesTextWithTheCardViewport() {
		assertEquals(1.0F, FilmRenderer.responsiveIntertitleScale(480, 270), 0.0001F);
		assertEquals(2.0F, FilmRenderer.responsiveIntertitleScale(960, 540), 0.0001F);
		assertEquals(1.0F, FilmRenderer.responsiveIntertitleScale(480, 540), 0.0001F);
	}

	@Test
	void keepsTextInsideTheDecorativeCornerSafeArea() {
		FilmRenderer.IntertitleSafeArea area = FilmRenderer.intertitleSafeArea(1920, 1080);

		assertEquals(300, area.left());
		assertEquals(1620, area.right());
		assertEquals(147, area.top());
		assertEquals(906, area.bottom());
	}
}
