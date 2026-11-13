package net.mcreator.integration.generator;

import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.types.CreateUI;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFile;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class RemarkCreateUIPauseMenuTest {
    @Test void fabric26ArchiveIncludesTheSamePauseMenuHelper() throws Exception {
        Path archive = Path.of(".remarkstudio-pending/generator-fabric-26.1.2-2026.2.zip");
        if (!Files.isRegularFile(archive)) archive = Path.of("plugins/generator-fabric-26.1.2-2026.2.zip");
        try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
            var helper = zip.stream().filter(entry -> entry.getName().replace('\\', '/').endsWith("/createui/pause_menu.java.ftl"))
                    .findFirst().orElseThrow();
            var template = zip.stream().filter(entry -> entry.getName().replace('\\', '/').endsWith("/createui/createui.java.ftl"))
                    .findFirst().orElseThrow();
            String text = new String(zip.getInputStream(template).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.contains("<#include \"pause_menu.java.ftl\">"));
            assertTrue(text.contains("matchesPauseMenu(client.screen)"));
            assertFalse(text.contains("drawCoverBackground(guiGraphics, index, 1.0F - alpha)"),
                    "An opaque background crossfade must not expose the world");
            assertEquals(Files.readString(Path.of("plugins/generator-forge-1.20.1/forge-1.20.1/templates/createui/pause_menu.java.ftl")),
                    new String(zip.getInputStream(helper).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test void modernBackgroundCrossfadesKeepTheCurrentImageOpaque() throws Exception {
        String template = Files.readString(Path.of("plugins/generator-26.1.x/neoforge-26.1.2/templates/createui/createui.java.ftl"));
        assertFalse(template.contains("drawCoverBackground(guiGraphics, index, 1.0F - alpha)"));
        assertTrue(template.contains("drawCoverBackground(guiGraphics, (index + 1) % BACKGROUNDS.length, alpha)"));
    }

    @TestFactory Stream<DynamicTest> pauseMenusAndTitleMenusRemainSeparate() {
        return Generator.GENERATOR_CACHE.values().stream()
                .filter(generator -> generator.getDefinitionsProvider().getModElementDefinition(ModElementType.CREATEUI) != null)
                .sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName))
                .map(generator -> DynamicTest.dynamicTest(generator.getGeneratorName(), () -> {
                    try (var workspace = TestWorkspaceDataProvider.createTestWorkspace(
                            Files.createTempDirectory("remake_pause_ui").toFile(), generator, false, false, null)) {
                        var element = new net.mcreator.workspace.elements.ModElement(workspace, "PauseMenu", ModElementType.CREATEUI);
                        CreateUI ui = new CreateUI(element);
                        ui.targetScreen = "Pause menu";
                        ui.components = List.of(new Button("resume", 114, 70, "Back to Game", 200, 20, false, null, null));
                        ui.buttonActions = List.of(new CreateUI.CreateUIButtonAction("resume", "RESUME_GAME", ""));
                        String pause = code(workspace.getGenerator().generateElement(ui, false, false));
                        assertTrue(pause.contains("instanceof net.minecraft.client.gui.screens.PauseScreen"));
                        assertTrue(pause.contains("vanillaMenuBypass"));
                        assertTrue(pause.contains("menu.game"), "Do not replace the hidden/loading PauseScreen(false)");
                        assertTrue(pause.contains("case \"RESUME_GAME\""));
                        assertTrue(pause.contains("case \"DISCONNECT\""));
                        assertTrue(pause.contains("draftReportHandled"), "Preserve pending report confirmation before disconnect");
                        assertTrue(pause.matches("(?s).*boolean isPauseScreen\\(\\)\\s*\\{\\s*return true;.*"));
                        assertTrue(pause.contains("void onClose()"));
                        assertFalse(pause.contains("0xFF120814"), "Without an imported background, the world must remain visible");
						if (generator.getGeneratorName().startsWith("neoforge-1.21.1")) {
							assertTrue(pause.contains("void renderBackground("));
							assertEquals(1, pause.split("renderRemarkBackground\\(guiGraphics\\);", -1).length - 1,
									"Render the custom background once, through Screen.renderBackground");
						}
						if (generator.getGeneratorName().equals("neoforge-26.1.2"))
							assertTrue(pause.contains("this.minecraft.options, true)"));

                        ui.targetScreen = "Main menu";
                        String title = code(workspace.getGenerator().generateElement(ui, false, false));
                        assertTrue(title.contains("instanceof net.minecraft.client.gui.screens.TitleScreen"));
                        assertTrue(title.matches("(?s).*boolean isPauseScreen\\(\\)\\s*\\{\\s*return false;.*"));
                    }
                }));
    }

    private static String code(List<GeneratorFile> files) {
        assertFalse(files.isEmpty());
        return files.stream().map(GeneratorFile::contents).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
