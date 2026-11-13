/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.integration;

import net.mcreator.element.ModElementType;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.CustomElement;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.workspace.RemarkWorkspaceDependencyManager;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkAAAParticleDependencyLifecycleTest {

	private static final String GENERATOR_ID = "neoforge-1.21.1-21.1.219";

	@Test void removesDependencyOnlyAfterLastAAAParticleIsDeleted(@TempDir File tempRoot) {
		Workspace workspace = createWorkspace(tempRoot);
		try {
			workspace.getWorkspaceSettings().setMCreatorDependencies(
					new HashSet<>(Set.of(RemarkWorkspaceDependencyManager.AAA_PARTICLES, "unrelated_api")));
			ModElement first = addAAAParticle(workspace, "FirstAAA");
			ModElement second = addAAAParticle(workspace, "SecondAAA");

			workspace.removeModElement(first);
			assertTrue(hasDependency(workspace, RemarkWorkspaceDependencyManager.AAA_PARTICLES));

			workspace.removeModElement(second);
			assertFalse(hasDependency(workspace, RemarkWorkspaceDependencyManager.AAA_PARTICLES));
			assertTrue(hasDependency(workspace, "unrelated_api"));
		} finally {
			workspace.close();
		}
	}

	@Test void unrelatedDeletionDoesNotRemoveManuallyEnabledDependency(@TempDir File tempRoot) {
		Workspace workspace = createWorkspace(tempRoot);
		try {
			workspace.getWorkspaceSettings()
					.setMCreatorDependencies(Set.of(RemarkWorkspaceDependencyManager.AAA_PARTICLES));
			ModElement customElement = new ModElement(workspace, "CustomCode", ModElementType.CODE);
			workspace.addModElement(customElement);
			workspace.getModElementManager().storeModElement(new CustomElement(customElement));

			workspace.removeModElement(customElement);

			assertTrue(hasDependency(workspace, RemarkWorkspaceDependencyManager.AAA_PARTICLES));
		} finally {
			workspace.close();
		}
	}

	private static Workspace createWorkspace(File tempRoot) {
		GeneratorConfiguration generator = Generator.GENERATOR_CACHE.get(GENERATOR_ID);
		assertNotNull(generator);
		return TestWorkspaceDataProvider.createTestWorkspace(tempRoot, generator, false, false, null);
	}

	private static ModElement addAAAParticle(Workspace workspace, String name) {
		ModElement modElement = new ModElement(workspace, name, ModElementType.AAAPARTICLE);
		AAAParticle particle = new AAAParticle(modElement);
		particle.effectPath = modElement.getRegistryName();
		particle.scaleX = 1;
		particle.scaleY = 1;
		particle.scaleZ = 1;
		particle.speed = 1;
		workspace.addModElement(modElement);
		workspace.getModElementManager().storeModElement(particle);
		return modElement;
	}

	private static boolean hasDependency(Workspace workspace, String dependency) {
		return workspace.getWorkspaceSettings().getMCreatorDependenciesRaw().contains(dependency);
	}
}
