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
import net.mcreator.element.types.Fashion;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.io.JavaWriter;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.gradle.GradleResultCode;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.io.FileIO;
import net.mcreator.io.OutputStreamEventHandler;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.gradle.GradleConsole;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkFabricOBJBuildTest {

	@TestFactory @EnabledIfEnvironmentVariable(named = "REMARK_FULL_GENERATOR_BUILD", matches = "true")
	Stream<DynamicTest> buildOBJFashionForFabricGenerators() {
		return Generator.GENERATOR_CACHE.values().stream()
				.filter(generator -> generator.getGeneratorName().equals("fabric-1.20.1")
						|| generator.getGeneratorName().equals("fabric-26.1.2"))
				.sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName))
				.map(generator -> DynamicTest.dynamicTest(generator.getGeneratorName(), () -> runBuild(generator)));
	}

	private void runBuild(GeneratorConfiguration generator) throws Exception {
		File workspaceFolder = Files.createTempDirectory("remark_fabric_obj_build").toFile();
		Workspace workspace = null;
		MCreator mcreator = null;
		try {
			workspace = TestWorkspaceDataProvider.createTestWorkspace(workspaceFolder, generator, false, false, null);
			WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);

			CountDownLatch syncLatch = new CountDownLatch(1);
			AtomicReference<GradleResultCode> syncResult = new AtomicReference<>();
			mcreator = MCreator.create(null, workspace);
			mcreator.getGradleConsole().exec(GradleConsole.GRADLE_SYNC_TASK, result -> {
				syncResult.set(result);
				syncLatch.countDown();
			});
			assertTrue(syncLatch.await(10, TimeUnit.MINUTES), "Gradle sync timed out");
			assertEquals(GradleResultCode.STATUS_OK, syncResult.get(), "Gradle MDK setup failed");
			workspace.getGenerator().reloadGradleCaches();

			createOBJModel(workspace, "matrix_fashion");
			ModElement modElement = new ModElement(workspace, "MatrixFashion", ModElementType.FASHION);
			Fashion fashion = new Fashion(modElement);
			fashion.enableHelmet = true;
			fashion.enableBody = false;
			fashion.enableLeggings = false;
			fashion.enableBoots = false;
			fashion.simpleItemWearable = true;
			fashion.singleItemSet = true;
			fashion.textureHelmet = new TextureHolder(workspace, "test");
			fashion.textureBody = new TextureHolder(workspace, "test2");
			fashion.textureLeggings = new TextureHolder(workspace, "test3");
			fashion.textureBoots = new TextureHolder(workspace, "test4");
			fashion.helmetName = "Matrix OBJ Hat";
			fashion.bodyName = "Matrix Body";
			fashion.leggingsName = "Matrix Legs";
			fashion.bootsName = "Matrix Feet";
			fashion.armorTextureFile = "armor_texture";
			fashion.helmetModelTexture = "From armor";
			fashion.bodyModelTexture = "From armor";
			fashion.leggingsModelTexture = "From armor";
			fashion.bootsModelTexture = "From armor";
			fashion.helmetItemRenderType = 2;
			fashion.helmetItemCustomModelName = "matrix_fashion";
			workspace.addModElement(modElement);
			assertTrue(workspace.getGenerator().generateElement(fashion));
			workspace.getModElementManager().storeModElement(fashion);
			assertTrue(workspace.getGenerator().generateBase());
			workspace.getGenerator().runResourceSetupTasks();

			try (Stream<Path> entries = Files.walk(workspace.getGenerator().getSourceRoot().toPath())) {
				JavaWriter.formatAndOrganiseImportsForFiles(workspace,
						entries.filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toList()), null);
			}
			assertFalse(workspace.checkFailingGradleDependenciesAndClear());
			runGradleBuild(generator, workspace);
			assertEmbeddedOBJLoader(workspace);
		} finally {
			if (mcreator != null)
				mcreator.dispose();
			if (workspace != null)
				workspace.close();
			FileIO.deleteDir(workspaceFolder);
		}
	}

	private static void createOBJModel(Workspace workspace, String name) throws Exception {
		File modelsDir = workspace.getFolderManager().getModelsDir();
		assertTrue(modelsDir.mkdirs() || modelsDir.isDirectory());
		Files.writeString(new File(modelsDir, name + ".obj").toPath(), """
				mtllib matrix_fashion.mtl
				o triangle
				v 0 0 0
				v 1 0 0
				v 0 1 0
				vt 0 0
				vt 1 0
				vt 0 1
				vn 0 0 1
				usemtl material
				f 1/1/1 2/2/1 3/3/1
				""", StandardCharsets.UTF_8);
		Files.writeString(new File(modelsDir, name + ".mtl").toPath(), """
				newmtl material
				map_Kd test_mod:block/test
				""", StandardCharsets.UTF_8);
	}

	private static void runGradleBuild(GeneratorConfiguration generator, Workspace workspace) {
		BuildLauncher buildLauncher = GradleUtils.getGradleTaskLauncher(generator,
				GradleUtils.getGradleProjectConnection(workspace), "build");
		StringBuilder errors = new StringBuilder();
		buildLauncher.setStandardError(
				new OutputStreamEventHandler(line -> errors.append(line).append(System.lineSeparator())));
		try {
			buildLauncher.run();
		} catch (GradleConnectionException | IllegalStateException exception) {
			throw new AssertionError("Gradle build failed for " + generator.getGeneratorName() + ":\n" + errors,
					exception);
		}
		assertFalse(errors.toString().contains(": error: "), errors.toString());
	}

	private static void assertEmbeddedOBJLoader(Workspace workspace) throws Exception {
		File libsDir = new File(workspace.getWorkspaceFolder(), "build/libs");
		assertTrue(libsDir.isDirectory(), "Missing build/libs");
		File[] modJars = Objects.requireNonNull(libsDir.listFiles(
				file -> file.getName().endsWith(".jar") && !file.getName().contains("-sources")));
		boolean embedded = false;
		StringBuilder inspected = new StringBuilder();
		for (File modJar : modJars) {
			try (ZipFile zip = new ZipFile(modJar)) {
				String nestedJars = zip.stream().map(entry -> entry.getName()).filter(name -> name.startsWith("META-INF/jars/"))
						.collect(Collectors.joining(", "));
				inspected.append(modJar.getName()).append(": [").append(nestedJars).append("]\n");
				for (var entry : java.util.Collections.list(zip.entries())) {
					if (!entry.isDirectory() && entry.getName().startsWith("META-INF/jars/")) {
						try (ZipInputStream nested = new ZipInputStream(zip.getInputStream(entry))) {
							for (var nestedEntry = nested.getNextEntry(); nestedEntry != null;
									nestedEntry = nested.getNextEntry()) {
								if (nestedEntry.getName().equals("fabric.mod.json")) {
									String metadata = new String(nested.readAllBytes(), StandardCharsets.UTF_8);
									embedded |= metadata.contains("\"special-model-loader\"");
									break;
								}
							}
						}
					}
				}
			}
		}
		assertTrue(embedded, "Exported Fabric mod must embed its OBJ loader. Inspected:\n" + inspected);
	}
}
