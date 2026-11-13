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
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.remark.RemarkBuildMatrixService;
import net.mcreator.ui.remark.RemarkCleanupService;
import net.mcreator.ui.remark.RemarkCompatibilityService;
import net.mcreator.ui.remark.RemarkComponentLibrary;
import net.mcreator.ui.remark.RemarkModJarInspector;
import net.mcreator.ui.remark.RemarkReleasePackager;
import net.mcreator.ui.remark.RemarkWorkspaceSandbox;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.RemarkWorkspaceDependencyManager;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkProductionToolsTest {

	private static final String GENERATOR_ID = "neoforge-1.21.1-21.1.219";

	@Test void inspectsFabricJarWithoutExtractingIt(@TempDir File tempRoot) throws Exception {
		File jar = new File(tempRoot, "example-fabric.jar");
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jar))) {
			writeZip(zip, "fabric.mod.json", """
					{
					  "schemaVersion": 1,
					  "id": "remark_example",
					  "version": "1.2.3",
					  "name": "Remake Example",
					  "license": "MIT",
					  "depends": { "fabricloader": ">=0.15.0", "minecraft": "1.20.1" }
					}
					""");
			writeZip(zip, "example/Main.class", "class-bytes");
			writeZip(zip, "assets/remark_example/textures/item/logo.png", "png");
		}

		RemarkModJarInspector.Inspection inspection = RemarkModJarInspector.inspect(jar);
		assertEquals("remark_example", inspection.id());
		assertEquals("1.2.3", inspection.version());
		assertTrue(inspection.loaders().contains(RemarkModJarInspector.Loader.FABRIC));
		assertEquals(1, inspection.classCount());
		assertEquals(1, inspection.textureCount());
		assertFalse(new File(tempRoot, "fabric.mod.json").exists(), "Inspector must not extract the JAR");

		GeneratorConfiguration neoforge = generator();
		assertEquals(RemarkModJarInspector.CompatibilityStatus.BLOCKED,
				RemarkModJarInspector.compare(inspection, neoforge).status());
	}

	@Test void exportsAndImportsReusableElementWithAAAAssets(@TempDir File tempRoot) throws Exception {
		String previousHome = System.getProperty("user.home");
		System.setProperty("user.home", new File(tempRoot, "user-home").getAbsolutePath());
		Workspace source = createWorkspace(new File(tempRoot, "source"));
		Workspace target = createWorkspace(new File(tempRoot, "target"));
		File packageFile = null;
		try {
			File effectRoot = new File(source.getWorkspaceFolder(), ".remark-assets/aaa/fire");
			File effect = new File(effectRoot, "Fire.efkefc");
			File texture = new File(effectRoot, "textures/glow.png");
			Files.createDirectories(texture.toPath().getParent());
			Files.write(effect.toPath(), new byte[] { 1, 2, 3, 4 });
			Files.write(texture.toPath(), new byte[] { 5, 6, 7 });

			ModElement modElement = new ModElement(source, "FireBurst", ModElementType.AAAPARTICLE);
			AAAParticle particle = new AAAParticle(modElement);
			particle.sourceRoot = ".remark-assets/aaa/fire";
			particle.sourceFile = ".remark-assets/aaa/fire/Fire.efkefc";
			particle.sourceName = "Fire.efkefc";
			particle.effectPath = "fire_burst";
			particle.scaleX = 1;
			particle.scaleY = 1;
			particle.scaleZ = 1;
			particle.speed = 1;
			source.addModElement(modElement);
			source.getModElementManager().storeModElement(particle);
			source.getWorkspaceSettings().setMCreatorDependencies(Set.of(RemarkWorkspaceDependencyManager.AAA_PARTICLES));

			RemarkComponentLibrary.ExportResult exported = RemarkComponentLibrary.exportPackage(source,
					List.of(modElement), "Fire FX");
			packageFile = exported.file();
			assertTrue(packageFile.isFile());
			assertEquals(1, exported.elements());
			assertTrue(exported.resources() >= 2);

			RemarkComponentLibrary.ImportResult imported = RemarkComponentLibrary.importPackage(target, packageFile);
			assertEquals(List.of("FireBurst"), imported.elements());
			assertNotNull(target.getModElementByName("FireBurst"));
			assertTrue(target.getWorkspaceSettings().getMCreatorDependenciesRaw()
					.contains(RemarkWorkspaceDependencyManager.AAA_PARTICLES));
			assertArrayEquals(new byte[] { 1, 2, 3, 4 }, Files.readAllBytes(
					new File(target.getWorkspaceFolder(), ".remark-assets/aaa/fire/Fire.efkefc").toPath()));
			assertTrue(RemarkCompatibilityService.analyze(target, generator()).level()
					!= RemarkCompatibilityService.Level.BLOCKED);
		} finally {
			if (packageFile != null && packageFile.exists())
				RemarkComponentLibrary.deletePackage(packageFile);
			source.close();
			target.close();
			System.setProperty("user.home", previousHome);
		}
	}

	@Test void createsDeliveryZipWithChecksumsAndCleanSource(@TempDir File tempRoot) throws Exception {
		Workspace workspace = createWorkspace(new File(tempRoot, "workspace"));
		try {
			File artifact = new File(tempRoot, "remark-test.jar");
			Files.write(artifact.toPath(), new byte[] { 9, 8, 7, 6 });
			RemarkWorkspaceSandbox.VerificationResult verification = new RemarkWorkspaceSandbox.VerificationResult(true,
					generator().getGeneratorName(), List.of("Build passed"), artifact, tempRoot, 100);
			RemarkBuildMatrixService.MatrixResult matrix = new RemarkBuildMatrixService.MatrixResult(true, 1, 0,
					new File(tempRoot, "matrix"), List.of(verification));

			RemarkReleasePackager.PackageResult result = RemarkReleasePackager.packageRelease(workspace, matrix, true);
			assertTrue(result.archive().isFile());
			assertEquals(1, result.artifacts());
			try (ZipFile zip = new ZipFile(result.archive())) {
				assertNotNull(zip.getEntry("README.md"));
				assertNotNull(zip.getEntry("checksums.sha256"));
				assertNotNull(zip.getEntry("release-manifest.json"));
				assertNotNull(zip.getEntry("mods/remark-test.jar"));
				assertNotNull(zip.getEntry("source/workspace.zip"));
			}
		} finally {
			workspace.close();
		}
	}

	@Test void quarantinesAndRestoresUnusedAssetsAndDependencies(@TempDir File tempRoot) throws Exception {
		MCreator mcreator = MCreator.create(null, createWorkspace(new File(tempRoot, "cleanup")));
		try {
			File textures = mcreator.getFolderManager().getTexturesFolder(TextureType.SCREEN);
			assertNotNull(textures);
			textures.mkdirs();
			File unused = new File(textures, "unused_screen.png");
			File sidecar = new File(unused.getAbsolutePath() + ".textures");
			Files.write(unused.toPath(), new byte[] { 1, 3, 3, 7 });
			Files.writeString(sidecar.toPath(), "{}");
			mcreator.getWorkspaceSettings().setMCreatorDependencies(
					Set.of(RemarkWorkspaceDependencyManager.AAA_PARTICLES));

			RemarkCleanupService.Plan plan = RemarkCleanupService.analyze(mcreator);
			assertTrue(plan.files().contains(unused));
			assertTrue(plan.staleAaaDependency());
			RemarkCleanupService.OperationResult quarantined = RemarkCleanupService.quarantine(mcreator, plan);
			assertEquals(2, quarantined.changed());
			assertFalse(unused.exists());
			assertFalse(sidecar.exists());
			assertFalse(mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw()
					.contains(RemarkWorkspaceDependencyManager.AAA_PARTICLES));

			RemarkCleanupService.OperationResult restored = RemarkCleanupService.restoreLatest(mcreator);
			assertEquals(2, restored.changed());
			assertTrue(unused.isFile());
			assertTrue(sidecar.isFile());
			assertTrue(mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw()
					.contains(RemarkWorkspaceDependencyManager.AAA_PARTICLES));
		} finally {
			mcreator.dispose();
		}
	}

	private static Workspace createWorkspace(File root) {
		return TestWorkspaceDataProvider.createTestWorkspace(root, generator(), false, false, null);
	}

	private static GeneratorConfiguration generator() {
		GeneratorConfiguration generator = Generator.GENERATOR_CACHE.get(GENERATOR_ID);
		assertNotNull(generator, "Pinned NeoForge generator must be loaded");
		return generator;
	}

	private static void writeZip(ZipOutputStream zip, String path, String value) throws Exception {
		zip.putNextEntry(new ZipEntry(path));
		zip.write(value.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}
}
