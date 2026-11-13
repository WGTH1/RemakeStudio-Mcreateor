/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.integration.generator;

import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.CreateUI;
import net.mcreator.element.types.Fashion;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.ui.remark.RemarkWorkspaceSandbox;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkPinnedNeoForgeGeneratorTest {

	private static final String GENERATOR_ID = "neoforge-1.21.1-21.1.219";

	@Test void pinnedGeneratorAndRemarkFeaturesAreAvailable() {
		GeneratorConfiguration generator = Generator.GENERATOR_CACHE.get(GENERATOR_ID);
		assertNotNull(generator);
		assertEquals("1.21.1", generator.getGeneratorMinecraftVersion());
		assertEquals("21.1.219", generator.getGeneratorBuildFileVersion());

		assertNotNull(generator.getDefinitionsProvider().getModElementDefinition(ModElementType.AAAPARTICLE));
		assertNotNull(generator.getDefinitionsProvider().getModElementDefinition(ModElementType.FASHION));
		assertNotNull(generator.getDefinitionsProvider().getModElementDefinition(ModElementType.CREATEUI));

		assertNotNull(ModAPIManager.getModAPIForNameAndGenerator("aaa_particles", GENERATOR_ID));
	}

	@Test @EnabledIfEnvironmentVariable(named = "REMARK_FULL_GENERATOR_BUILD", matches = "true")
	void buildsPinnedGeneratorWithRemarkElements(@TempDir File tempRoot) throws Exception {
		GeneratorConfiguration generator = Generator.GENERATOR_CACHE.get(GENERATOR_ID);
		assertNotNull(generator);
		Workspace workspace = TestWorkspaceDataProvider.createTestWorkspace(new File(tempRoot, "source"), generator,
				false, false, null);
		try {
			workspace.getWorkspaceSettings().setMCreatorDependencies(Set.of("aaa_particles"));
			addAAAParticle(workspace);
			addFashion(workspace);
			addCreateUI(workspace);
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();

			try (RemarkWorkspaceSandbox sandbox = RemarkWorkspaceSandbox.create(workspace, generator)) {
				RemarkWorkspaceSandbox.VerificationResult result = sandbox.verify(true,
						new File(tempRoot, "artifacts"), _ -> {
						});
				assertTrue(result.success(), String.join("\n", result.lines()));
				assertNotNull(result.artifact());
				assertTrue(result.artifact().isFile());
			}
		} finally {
			workspace.close();
		}
	}

	private static void addAAAParticle(Workspace workspace) throws Exception {
		ModElement modElement = new ModElement(workspace, "PinnedAAA", ModElementType.AAAPARTICLE);
		AAAParticle particle = new AAAParticle(modElement);
		File sourceRoot = new File(workspace.getWorkspaceFolder(), ".remark-assets/aaa/pinned");
		File sourceFile = new File(sourceRoot, "pinned.efkefc");
		assertTrue(sourceRoot.mkdirs() || sourceRoot.isDirectory());
		Files.writeString(sourceFile.toPath(), "remark-pinned-test-effect", StandardCharsets.UTF_8);
		particle.sourceRoot = relative(workspace, sourceRoot);
		particle.sourceFile = relative(workspace, sourceFile);
		particle.sourceName = sourceFile.getName();
		particle.effectPath = "pinned/pinned_aaa";
		particle.spawnMode = "source_follow_view";
		particle.scaleX = 1;
		particle.scaleY = 1;
		particle.scaleZ = 1;
		particle.speed = 1;
		workspace.addModElement(modElement);
		workspace.getModElementManager().storeModElement(particle);
	}

	private static void addFashion(Workspace workspace) {
		ModElement modElement = new ModElement(workspace, "PinnedFashion", ModElementType.FASHION);
		Fashion fashion = new Fashion(modElement);
		fashion.enableHelmet = true;
		fashion.enableBody = true;
		fashion.enableLeggings = true;
		fashion.enableBoots = true;
		fashion.simpleItemWearable = true;
		fashion.singleItemSet = true;
		fashion.textureHelmet = new TextureHolder(workspace, "test");
		fashion.textureBody = new TextureHolder(workspace, "test2");
		fashion.textureLeggings = new TextureHolder(workspace, "test3");
		fashion.textureBoots = new TextureHolder(workspace, "test4");
		fashion.armorTextureFile = "armor_texture";
		fashion.helmetModelTexture = "From armor";
		fashion.bodyModelTexture = "From armor";
		fashion.leggingsModelTexture = "From armor";
		fashion.bootsModelTexture = "From armor";
		workspace.addModElement(modElement);
		workspace.getModElementManager().storeModElement(fashion);
	}

	private static void addCreateUI(Workspace workspace) {
		ModElement modElement = new ModElement(workspace, "PinnedMenu", ModElementType.CREATEUI);
		CreateUI createUI = new CreateUI(modElement);
		createUI.components = List.of(new Button("play", 20, 20, "Play", 80, 20, false, null, null));
		workspace.addModElement(modElement);
		workspace.getModElementManager().storeModElement(createUI);
	}

	private static String relative(Workspace workspace, File file) {
		return workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize()
				.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace(File.separator, "/");
	}
}
