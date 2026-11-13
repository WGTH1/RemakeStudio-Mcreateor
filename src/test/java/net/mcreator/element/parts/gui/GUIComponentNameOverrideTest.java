/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.element.parts.gui;

import com.google.gson.Gson;
import net.mcreator.integration.IntegrationTestSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ExtendWith(IntegrationTestSetup.class) class GUIComponentNameOverrideTest {

	private static final Gson GSON = new Gson();

	@Test void keepsDerivedNameWhenNoOverrideExists() {
		Image image = new Image(10, 20, "remark_logo.png", false, null);
		assertEquals("image_remark_logo", image.getName());
	}

	@Test void roundTripsNameOverrideThroughWorkspaceJson() {
		Image image = new Image(10, 20, "remark_logo.png", false, null);
		image.remarkNameOverride = "image_remark_logo_2";

		String json = GSON.toJson(image, GUIComponent.class);
		GUIComponent restored = GSON.fromJson(json, GUIComponent.class);

		assertInstanceOf(Image.class, restored);
		assertEquals("image_remark_logo_2", restored.getName());
	}
}
