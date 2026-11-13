package net.mcreator.integration.generator;

import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.types.CreateUI;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.ui.component.ConsolePane;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class RemarkCreateUIPauseBuildTest {
    @TestFactory @EnabledIfEnvironmentVariable(named = "REMAKE_PAUSE_BUILD", matches = "true")
    Stream<DynamicTest> buildsTitlePauseAndEmptyPauseMenus() {
        String filter = System.getenv().getOrDefault("REMAKE_PAUSE_BUILD_TARGET", "forge-1.20.1");
        return Generator.GENERATOR_CACHE.values().stream()
                .filter(generator -> generator.getGeneratorName().matches(filter))
                .sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName))
                .map(generator -> DynamicTest.dynamicTest(generator.getGeneratorName(), () -> build(generator)));
    }

    private void build(GeneratorConfiguration generator) throws Exception {
        Path root = Path.of(System.getenv("REMAKE_PAUSE_BUILD_OUTPUT")).resolve(generator.getGeneratorName());
        assertFalse(Files.exists(root.resolve("pause_proof.mcreator")), "Use an unused verification directory");
        var settings = new WorkspaceSettings("pause_proof");
        settings.setCurrentGenerator(generator.getGeneratorName());
        settings.setModName("Remake Pause menu verification");
        settings.setVersion("1.0.0");
        boolean debugConsole = ConsolePane.DEBUG_CONTENTS_TO_LOG;
        ConsolePane.DEBUG_CONTENTS_TO_LOG = false;
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("pause_proof.mcreator").toFile(), settings)) {
            WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
            workspace.getGenerator().runResourceSetupTasks();
            var connection = GradleUtils.getGradleProjectConnection(workspace);
            GradleUtils.getGradleSyncLauncher(generator, connection).addArguments("--max-workers=1")
                    .setStandardOutput(System.out).setStandardError(System.out).run();
            workspace.getGenerator().reloadGradleCaches();
            for (String name : List.of("pause_background_a", "pause_background_b")) {
                var image = new java.awt.image.BufferedImage(32, 18, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                graphics.setColor(name.endsWith("a") ? java.awt.Color.BLUE : java.awt.Color.GRAY);
                graphics.fillRect(0, 0, 32, 18);
                graphics.dispose();
                net.mcreator.io.FileIO.writeImageToPNGFile(image, workspace.getFolderManager()
                        .getTextureFile(name, net.mcreator.ui.workspace.resources.TextureType.SCREEN));
            }
            for (String name : List.of("CustomPause", "EmptyPause", "TitleMenu")) {
                var element = new ModElement(workspace, name, ModElementType.CREATEUI);
                var ui = new CreateUI(element);
                ui.targetScreen = name.equals("TitleMenu") ? CreateUI.TARGET_MAIN_MENU : CreateUI.TARGET_PAUSE_MENU;
                ui.replaceVanillaMenu = !name.equals("EmptyPause");
                if (!name.equals("EmptyPause")) {
                    List<net.mcreator.element.parts.gui.GUIComponent> components = new ArrayList<>();
                    for (String action : List.of(CreateUI.ACTION_RESUME_GAME, CreateUI.ACTION_OPTIONS,
                            CreateUI.ACTION_ADVANCEMENTS, CreateUI.ACTION_STATISTICS, CreateUI.ACTION_DISCONNECT,
                            CreateUI.ACTION_VANILLA_PAUSE_MENU)) {
                        var button = new Button(action.toLowerCase(java.util.Locale.ROOT), 114, 30 + components.size() * 24,
                                action, 200, 20, false, null, null);
                        button.autoSize = true;
                        components.add(button);
                        ui.buttonActions.add(new CreateUI.CreateUIButtonAction(button.getName(), action, ""));
                    }
                    if (name.equals("CustomPause")) {
                        ui.backgroundImages = List.of("pause_background_a.png", "pause_background_b.png");
                        ui.backgroundOverlayOpacity = 0;
                        var logo = new net.mcreator.element.parts.gui.Image(10, 15, "pause_background_a.png", false, null);
                        logo.customWidth = 80;
                        logo.customHeight = 45;
                        components.add(logo);
                    }
                    ui.components = components;
                }
                workspace.addModElement(element);
                workspace.getGenerator().generateElement(ui, true);
                workspace.getModElementManager().storeModElement(ui);
            }
            workspace.getGenerator().generateBase(true);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            GradleUtils.getGradleTaskLauncher(generator, connection, "build").addArguments("--max-workers=1")
                    .setStandardOutput(System.out).setStandardError(System.out).run();
            Path artifact = root.resolve(generator.getGradleTaskFor("export_file"));
            assertTrue(Files.isRegularFile(artifact));
            try (ZipFile zip = new ZipFile(artifact.toFile())) {
                assertTrue(zip.stream().anyMatch(entry -> entry.getName().endsWith("/CustomPauseCreateUIScreen.class")));
                assertTrue(zip.stream().anyMatch(entry -> entry.getName().endsWith("/TitleMenuCreateUIScreen.class")));
                assertNotNull(zip.getEntry("assets/pause_proof/textures/screens/pause_background_a.png"));
            }
            System.out.println("PAUSE_MENU_BUILD_PROOF=" + artifact);
        } finally {
            ConsolePane.DEBUG_CONTENTS_TO_LOG = debugConsole;
        }
    }
}
