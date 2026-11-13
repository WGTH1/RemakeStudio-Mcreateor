/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.integration.generator;

import net.mcreator.generator.Generator;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.plugin.modapis.ModAPIImplementation;
import net.mcreator.plugin.modapis.ModAPIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkForge1201GeneratorTest {

	private static final String GENERATOR_ID = "forge-1.20.1";

	@Test void aaaParticlesUsesLegacyForgeModDevDependencySyntax() {
		assertNotNull(Generator.GENERATOR_CACHE.get(GENERATOR_ID));

		ModAPIImplementation api = ModAPIManager.getModAPIForNameAndGenerator("aaa_particles", GENERATOR_ID);
		assertNotNull(api);
		assertEquals("[2.1.0,)", api.versionRange());
		assertTrue(api.gradle().contains(
				"modImplementation \"maven.modrinth:En8uHTOK:svild8SE\""));
		assertFalse(api.gradle().contains("fg.deobf"));
	}
}
