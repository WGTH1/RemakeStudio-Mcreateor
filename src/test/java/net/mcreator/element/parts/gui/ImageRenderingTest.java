/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.element.parts.gui;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageRenderingTest {

	@Test void uiImagesRemainCrispWhenTheCanvasUsesBilinearInterpolation() {
		BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
		source.setRGB(0, 0, Color.RED.getRGB());
		source.setRGB(1, 0, Color.GREEN.getRGB());
		source.setRGB(0, 1, Color.BLUE.getRGB());
		source.setRGB(1, 1, Color.WHITE.getRGB());

		BufferedImage target = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		Image.drawCrisp(graphics, source, 0, 0, target.getWidth(), target.getHeight(), null);
		graphics.dispose();

		Set<Integer> sourceColors = Set.of(Color.RED.getRGB(), Color.GREEN.getRGB(), Color.BLUE.getRGB(),
				Color.WHITE.getRGB());
		for (int y = 0; y < target.getHeight(); y++) {
			for (int x = 0; x < target.getWidth(); x++)
				assertTrue(sourceColors.contains(target.getRGB(x, y)), "Found a blended pixel at " + x + ", " + y);
		}
	}

	@Test void largeUiArtworkUsesQualityDownsampling() {
		BufferedImage source = new BufferedImage(90, 90, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++)
				source.setRGB(x, y, (x + y) % 2 == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
		}

		BufferedImage target = new BufferedImage(9, 9, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		Image.drawCrisp(graphics, source, 0, 0, target.getWidth(), target.getHeight(), null);
		graphics.dispose();

		int center = target.getRGB(target.getWidth() / 2, target.getHeight() / 2);
		assertNotEquals(Color.BLACK.getRGB(), center);
		assertNotEquals(Color.WHITE.getRGB(), center);
	}
}
