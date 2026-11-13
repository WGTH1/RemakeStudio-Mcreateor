/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.integration;

import com.google.gson.JsonObject;
import net.mcreator.element.ModElementType;
import net.mcreator.element.converter.v2026_2.RemarkElementSchemaConverter;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.CreateUI;
import net.mcreator.element.types.Fashion;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.io.FileIO;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkElementSchemaConverterTest {

	@Test void upgradesLegacyRemarkElementsAndRestoresSafeDefaults() throws Exception {
		File workspaceFolder = Files.createTempDirectory("remark_schema_converter").toFile();
		Workspace workspace = null;
		try {
			GeneratorConfiguration generator = GeneratorConfiguration.getRecommendedGeneratorForBaseLanguage(
					Generator.GENERATOR_CACHE.values(), GeneratorFlavor.BaseLanguage.JAVA);
			assertNotNull(generator);
			workspace = TestWorkspaceDataProvider.createTestWorkspace(workspaceFolder, generator, false, false, null);
			RemarkElementSchemaConverter converter = new RemarkElementSchemaConverter();

			AAAParticle particle = new AAAParticle(new ModElement(workspace, "LegacyAAA", ModElementType.AAAPARTICLE));
			particle.remarkSchemaVersion = 0;
			particle.spawnMode = "removed_mode";
			particle.sourceRoot = null;
			particle.scaleX = 0;
			particle.scaleY = 0;
			particle.scaleZ = 0;
			particle.speed = 0;
			converter.convert(workspace, particle, legacyJson());
			assertEquals(AAAParticle.CURRENT_REMARK_SCHEMA, particle.remarkSchemaVersion);
			assertEquals("world", particle.spawnMode);
			assertEquals("", particle.sourceRoot);
			assertEquals(1, particle.scaleX);
			assertEquals(1, particle.scaleY);
			assertEquals(1, particle.scaleZ);
			assertEquals(1, particle.speed);

			Fashion fashion = new Fashion(new ModElement(workspace, "LegacyFashion", ModElementType.FASHION));
			fashion.remarkSchemaVersion = 0;
			fashion.simpleItemWearable = false;
			fashion.singleItemSet = false;
			fashion.maxDamage = 50;
			fashion.helmetItemDisplay = null;
			fashion.bodyItemDisplay = null;
			fashion.leggingsItemDisplay = null;
			fashion.bootsItemDisplay = null;
			converter.convert(workspace, fashion, legacyJson());
			assertEquals(Fashion.CURRENT_REMARK_SCHEMA, fashion.remarkSchemaVersion);
			assertTrue(fashion.simpleItemWearable);
			assertTrue(fashion.singleItemSet);
			assertEquals(0, fashion.maxDamage);
			assertNotNull(fashion.getItemDisplaySettingsFor("helmet"));
			assertNotNull(fashion.getItemDisplaySettingsFor("body"));
			assertNotNull(fashion.getItemDisplaySettingsFor("leggings"));
			assertNotNull(fashion.getItemDisplaySettingsFor("boots"));
			assertFalse(fashion.getItemDisplaySettingsFor("helmet").isEnabled());

			CreateUI createUI = new CreateUI(new ModElement(workspace, "LegacyUI", ModElementType.CREATEUI));
			createUI.remarkSchemaVersion = 0;
			createUI.components = null;
			createUI.backgroundImages = null;
			createUI.buttonActions = null;
			createUI.responsiveMode = "removed_mode";
			createUI.backgroundDurationTicks = 0;
			converter.convert(workspace, createUI, legacyJson());
			assertEquals(CreateUI.CURRENT_REMARK_SCHEMA, createUI.remarkSchemaVersion);
			assertNotNull(createUI.components);
			assertNotNull(createUI.backgroundImages);
			assertNotNull(createUI.buttonActions);
			assertEquals(CreateUI.RESPONSIVE_FIT_DOWN, createUI.responsiveMode);
			assertEquals(1, createUI.backgroundDurationTicks);
		} finally {
			if (workspace != null)
				workspace.close();
			FileIO.deleteDir(workspaceFolder);
		}
	}

	private static JsonObject legacyJson() {
		JsonObject root = new JsonObject();
		root.add("definition", new JsonObject());
		return root;
	}
}
