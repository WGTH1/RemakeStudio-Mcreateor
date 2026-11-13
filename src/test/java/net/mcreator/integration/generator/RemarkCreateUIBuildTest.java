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
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.types.CreateUI;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkCreateUIBuildTest {

	private static final Logger LOG = LogManager.getLogger("Remake Create UI Build Test");

	@TestFactory @EnabledIfEnvironmentVariable(named = "REMARK_FULL_GENERATOR_BUILD", matches = "true")
	Stream<DynamicTest> buildAutoSizedButtonForEverySupportedGenerator() {
		String filter = System.getenv().getOrDefault("REMARK_GENERATOR_FILTER", ".*");
		return Generator.GENERATOR_CACHE.values().stream()
				.filter(generator -> generator.getDefinitionsProvider().getModElementDefinition(ModElementType.CREATEUI)
						!= null)
				.filter(generator -> generator.getGeneratorName().matches(filter))
				.sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName))
				.map(generator -> DynamicTest.dynamicTest(generator.getGeneratorName(), () -> runBuild(generator)));
	}

	private void runBuild(GeneratorConfiguration generator) throws Exception {
		File workspaceFolder = Files.createTempDirectory("remark_createui_build").toFile();
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

			assertTrue(workspace.getGenerator().generateBase());

			ModElement modElement = new ModElement(workspace, "AutoSizeMenu", ModElementType.CREATEUI);
			CreateUI createUI = new CreateUI(modElement);
			Button button = new Button("thai_button", 30, 40, "เข้าเซิร์ฟเวอร์", 20, 20, true, null, null);
			button.autoSize = true;
			button.autoSizePaddingX = 12;
			button.autoSizePaddingY = 5;
			button.autoSizeMinWidth = 40;
			button.autoSizeMinHeight = 20;
			createUI.components = List.of(button);
			workspace.addModElement(modElement);
			assertTrue(workspace.getGenerator().generateElement(createUI));
			workspace.getModElementManager().storeModElement(createUI);
			assertTrue(workspace.getGenerator().generateBase());

			try (Stream<Path> entries = Files.walk(workspace.getGenerator().getSourceRoot().toPath())) {
				JavaWriter.formatAndOrganiseImportsForFiles(workspace,
						entries.filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toList()), null);
			}
			assertFalse(workspace.checkFailingGradleDependenciesAndClear());
			runGradleBuild(generator, workspace);
		} finally {
			if (mcreator != null)
				mcreator.dispose();
			if (workspace != null)
				workspace.close();
			FileIO.deleteDir(workspaceFolder);
		}
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
		LOG.info("[{}] Gradle build OK ({} warning lines)", generator.getGeneratorName(),
				errors.toString().lines().filter(line -> line.contains(": warning:")).count());
	}
}
