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
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.types.CreateUI;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.io.FileIO;
import net.mcreator.ui.remark.RemarkBuildMatrixService;
import net.mcreator.ui.remark.RemarkWorkspaceSandbox;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkWorkspaceSandboxTest {

	@Test void verifiesAndBuildsGenerationMatrixWithoutChangingSource(@TempDir File tempRoot) throws Exception {
		GeneratorConfiguration sourceGenerator = GeneratorConfiguration.getRecommendedGeneratorForBaseLanguage(
				Generator.GENERATOR_CACHE.values(), GeneratorFlavor.BaseLanguage.JAVA);
		assertNotNull(sourceGenerator);
		File workspaceFolder = new File(tempRoot, "source");
		Workspace workspace = TestWorkspaceDataProvider.createTestWorkspace(workspaceFolder, sourceGenerator, false,
				false, null);
		try {
			ModElement modElement = new ModElement(workspace, "SandboxMenu", ModElementType.CREATEUI);
			CreateUI createUI = new CreateUI(modElement);
			createUI.components = List.of(new Button("play", 20, 20, "Play", 80, 20, false, null, null));
			workspace.addModElement(modElement);
			workspace.getModElementManager().storeModElement(createUI);
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();

			List<GeneratorConfiguration> targets = Generator.GENERATOR_CACHE.values().stream()
					.filter(generator -> generator.getDefinitionsProvider().getModElementDefinition(ModElementType.CREATEUI)
							!= null)
					.sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName)).limit(2).toList();
			assertFalse(targets.isEmpty());

			File sandboxRoot;
			try (RemarkWorkspaceSandbox sandbox = RemarkWorkspaceSandbox.create(workspace, targets.getFirst())) {
				sandboxRoot = sandbox.getRoot();
				RemarkWorkspaceSandbox.VerificationResult verification = sandbox.verify(false, null, _ -> {
				});
				assertTrue(verification.success(), String.join("\n", verification.lines()));
			}
			assertFalse(sandboxRoot.exists(), "Successful sandbox should be removed");

			RemarkBuildMatrixService.MatrixResult matrix = RemarkBuildMatrixService.run(workspace, targets, false,
					new File(tempRoot, "release"), _ -> {
					});
			assertTrue(matrix.success());
			assertEquals(targets.size(), matrix.passed());
			assertTrue(new File(matrix.outputFolder(), "matrix-report.json").isFile());
			assertEquals(sourceGenerator.getGeneratorName(), workspace.getGeneratorConfiguration().getGeneratorName());
		} finally {
			workspace.close();
			FileIO.deleteDir(workspaceFolder);
		}
	}
}
