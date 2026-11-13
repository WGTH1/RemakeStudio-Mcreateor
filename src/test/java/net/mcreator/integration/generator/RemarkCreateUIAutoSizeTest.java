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
import net.mcreator.generator.GeneratorFile;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkCreateUIAutoSizeTest {

	@TestFactory Stream<DynamicTest> generateAutoSizedButtonForEverySupportedGenerator() {
		return Generator.GENERATOR_CACHE.values().stream()
				.filter(generator -> generator.getDefinitionsProvider().getModElementDefinition(ModElementType.CREATEUI)
						!= null)
				.sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName))
				.map(generator -> DynamicTest.dynamicTest(generator.getGeneratorName(), () -> {
					Workspace workspace = TestWorkspaceDataProvider.createTestWorkspace(
							Files.createTempDirectory("remark_createui_autosize").toFile(), generator, false, false,
							null);
					ModElement modElement = new ModElement(workspace, "AutoSizeMenu", ModElementType.CREATEUI);
					CreateUI createUI = new CreateUI(modElement);
					Button button = new Button("thai_button", 30, 40, "เข้าเซิร์ฟเวอร์", 20, 20, true, null,
							null);
					button.autoSize = true;
					button.autoSizePaddingX = 12;
					button.autoSizePaddingY = 5;
					button.autoSizeMinWidth = 40;
					button.autoSizeMinHeight = 20;
					button.autoSizeMaxWidth = 0;
					button.autoSizeMaxHeight = 0;
					createUI.components = List.of(button);

					List<GeneratorFile> files = workspace.getGenerator().generateElement(createUI, false, false);
					assertFalse(files.isEmpty());
					String generated = files.stream().map(GeneratorFile::contents).filter(Objects::nonNull)
							.collect(java.util.stream.Collectors.joining("\n"));
					assertTrue(generated.contains("autoButtonWidth"));
					assertTrue(generated.contains("autoButtonHeight"));
					assertTrue(generated.contains("setRemarkSize"));
					assertTrue(generated.contains("split(\"\\\\R\", -1)"));
				}));
	}
}
