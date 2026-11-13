/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.modgui;

import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateUIPreviewScaleTest {

	@Test void convertsPhysicalResolutionToMinecraftGuiScale() {
		assertEquals(new Dimension(427, 240),
				CreateUIGUI.logicalPreviewResolution(new Dimension(854, 480), 2));
		assertEquals(new Dimension(427, 240),
				CreateUIGUI.logicalPreviewResolution(new Dimension(1280, 720), 3));
		assertEquals(new Dimension(640, 360),
				CreateUIGUI.logicalPreviewResolution(new Dimension(1280, 720), 2));
	}
}
