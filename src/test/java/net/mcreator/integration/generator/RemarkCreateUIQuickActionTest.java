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
import net.mcreator.element.parts.gui.Image;
import net.mcreator.element.types.CreateUI;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFile;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.io.FileIO;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkCreateUIQuickActionTest {

	@TestFactory Stream<DynamicTest> generateQuickActionAndResizableImageForEverySupportedGenerator() {
		return Generator.GENERATOR_CACHE.values().stream()
				.filter(generator -> generator.getDefinitionsProvider().getModElementDefinition(ModElementType.CREATEUI)
						!= null)
				.sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName))
				.map(generator -> DynamicTest.dynamicTest(generator.getGeneratorName(), () -> {
					Workspace workspace = TestWorkspaceDataProvider.createTestWorkspace(
							Files.createTempDirectory("remark_createui_quick_action").toFile(), generator, false, false,
							null);
					FileIO.writeImageToPNGFile(new BufferedImage(16, 8, BufferedImage.TYPE_INT_ARGB),
							workspace.getFolderManager().getTextureFile("resizable_preview", TextureType.SCREEN));

					ModElement modElement = new ModElement(workspace, "QuickActionMenu", ModElementType.CREATEUI);
					CreateUI createUI = new CreateUI(modElement);
					Button joinServer = new Button("join_server", 40, 80, "Join Server", 100, 20, false, null,
							null);
					Image image = new Image(12, 14, "resizable_preview.png", false, null);
					image.customWidth = 123;
					image.customHeight = 45;
					createUI.components = List.of(joinServer, image);
					createUI.buttonActions = List.of(new CreateUI.CreateUIButtonAction("join_server",
							CreateUI.ACTION_JOIN_SERVER, "play.example.com", ""));

					List<GeneratorFile> files = workspace.getGenerator().generateElement(createUI, false, false);
					assertFalse(files.isEmpty());
					String generated = files.stream().map(GeneratorFile::contents).filter(Objects::nonNull)
							.collect(java.util.stream.Collectors.joining("\n"));
					assertTrue(java.util.regex.Pattern.compile(
							"handleButtonAction\\(\\\"JOIN_SERVER\\\"\\s*,\\s*\\\"play\\.example\\.com\\\"\\s*,\\s*\\\"\\\"\\)")
							.matcher(generated).find());
					assertTrue(generated.contains("int imageWidth0 = 123;"));
					assertTrue(generated.contains("int imageHeight0 = 45;"));
				}));
	}
}
