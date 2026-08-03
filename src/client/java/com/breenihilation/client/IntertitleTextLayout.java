package com.breenihilation.client;


import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

// make text scale in the card properly I hope

final class IntertitleTextLayout {
	static final int GLYPH_HEIGHT = 33;
	static final int LINE_STEP = 38;
	static final int HEADING_GAP = 14;
	static final float BYLINE_SCALE_RATIO = 0.78f;
	static final float BYLINE_GAP = 12.0f;
	private static final int MAX_WRAP_CANDIDATES = 256;

	private IntertitleTextLayout() {
	}

	static TextLayoutResult fit(
			Font font,
			Component body,
			Component heading,
			Component byline,
			boolean chatCard,
			int safeWidth,
			int safeHeight,
			float maximumScale
	) {
		String text = body.getString();
		int characterCount = Math.max(1, text.codePointCount(0, text.length()));
		int unwrappedWidth = Math.max(1, font.width(body));
		int candidates = Math.clamp((characterCount + 3) / 4, 1, MAX_WRAP_CANDIDATES);
		TextLayoutResult best = null;
		double bestScore = Double.NEGATIVE_INFINITY;

		for (int targetLines = 1; targetLines <= candidates; targetLines++) {
			int wrappingWidth = Math.max(1,
					(int) Math.ceil(unwrappedWidth / (double) targetLines * 1.02));
			List<FormattedCharSequence> lines = font.split(body, wrappingWidth);
			TextLayoutResult candidate = measure(
					font, lines, heading, byline, chatCard, safeWidth, safeHeight, maximumScale
			);
			double score = occupancyScore(candidate, font, safeWidth, safeHeight, maximumScale);
			if (score > bestScore) {
				bestScore = score;
				best = candidate;
			}
		}

		if (best != null) {
			return best;
		}
		return measure(
				font, font.split(body, Math.max(1, safeWidth)), heading, byline,
				chatCard, safeWidth, safeHeight, maximumScale
		);
	}

	private static TextLayoutResult measure(
			Font font,
			List<FormattedCharSequence> lines,
			Component heading,
			Component byline,
			boolean chatCard,
			int safeWidth,
			int safeHeight,
			float maximumScale
	) {
		int widestLine = lines.stream().mapToInt(font::width).max().orElse(1);
		float bodyUnits = Math.max(GLYPH_HEIGHT,
				(lines.size() - 1) * LINE_STEP + GLYPH_HEIGHT);
		float headingUnits = chatCard ? 0.0f : GLYPH_HEIGHT + HEADING_GAP;
		float footerUnits = chatCard ? BYLINE_GAP + GLYPH_HEIGHT * BYLINE_SCALE_RATIO : 0.0f;
		float totalUnits = headingUnits + bodyUnits + footerUnits;

		float scale = Math.max(0.000001f, maximumScale);
		scale = Math.min(scale, safeWidth / (float) Math.max(1, widestLine));
		scale = Math.min(scale, safeHeight / Math.max(0.000001f, totalUnits));
		if (!chatCard) {
			scale = Math.min(scale, safeWidth / (float) Math.max(1, font.width(heading)));
		} else {
			scale = Math.min(scale,
					safeWidth / (float) Math.max(1, font.width(byline)) / BYLINE_SCALE_RATIO);
		}

		float bodyHeight = bodyUnits * scale;
		float authorScale = chatCard ? scale * BYLINE_SCALE_RATIO : 0.0f;
		float authorGap = chatCard ? BYLINE_GAP * scale : 0.0f;
		float totalHeight = totalUnits * scale;
		return new TextLayoutResult(lines, scale, authorScale, bodyHeight, authorGap, totalHeight, widestLine);
	}

	private static double occupancyScore(
		TextLayoutResult result,
			Font font,
			int safeWidth,
			int safeHeight,
			float maximumScale
	) {
		float widthUse = Math.clamp(
				result.widestLine() * result.bodyScale() / Math.max(1.0f, safeWidth), 0.0f, 1.0f
		);
		float heightUse = Math.clamp(
				result.totalHeight() / Math.max(1.0f, safeHeight), 0.0f, 1.0f
		);
		double balancedUse = Math.min(widthUse, heightUse);
		double areaUse = widthUse * heightUse;
		double scaleUse = Math.min(1.0, result.bodyScale() / Math.max(0.000001f, maximumScale));
		return balancedUse * 8.0 + areaUse * 2.0 + scaleUse * 0.01;
	}

	record TextLayoutResult(
			List<FormattedCharSequence> lines,
			float bodyScale,
			float authorScale,
			float bodyHeight,
			float authorGap,
			float totalHeight,
			int widestLine
	) {
	}
}
