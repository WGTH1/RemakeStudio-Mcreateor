/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.integration.generator;

import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.*;
import net.mcreator.element.parts.procedure.LogicProcedure;
import net.mcreator.element.parts.procedure.StringListProcedure;
import net.mcreator.element.types.Item;
import net.mcreator.generator.Generator;
import net.mcreator.generator.behavior.SimpleItemBehaviorCompiler;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.generator.template.TemplateGeneratorException;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.ui.component.SimpleItemBehaviorPanel;
import net.mcreator.ui.remark.RemarkCompatibilityService;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class RemarkSimpleItemBehaviorTest {
	private static final String TARGET = SimpleItemBehaviorCompiler.TARGET;

	@Test void formRejectsInvalidTextAndPreservesUnknownSettings() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			SimpleItemBehaviorPanel panel = new SimpleItemBehaviorPanel(TARGET);
			SimpleItemBehavior settings = new SimpleItemBehavior();
			settings.enabled = true;
			panel.load(settings);
			JSpinner duration = (JSpinner) java.util.Arrays.stream(panel.getComponents())
					.filter(c -> "simpleBehavior.duration".equals(c.getName())).findFirst().orElseThrow();
			var field = ((JSpinner.DefaultEditor) duration.getEditor()).getTextField();
			field.setText("invalid");
			assertFalse(panel.validateInput().validateIsErrorFree());
			field.setText("7");
			assertTrue(panel.validateInput().validateIsErrorFree());
			assertEquals(7, panel.read().durationSeconds);
			JButton reset = (JButton) java.util.Arrays.stream(panel.getComponents())
					.filter(c -> "simpleBehavior.reset".equals(c.getName())).findFirst().orElseThrow();
			reset.doClick();
			assertTrue(panel.read().enabled);
			assertEquals(5, panel.read().durationSeconds);
			settings.schemaVersion = 9;
			settings.cooldownSeconds = 17;
			panel.load(settings);
			assertFalse(reset.isEnabled());
			assertFalse(duration.isEnabled());
			assertEquals(9, panel.read().schemaVersion);
			assertEquals(17, panel.read().cooldownSeconds);
		});
	}

	@Test void itemEditorRetainsBehaviorAndReportsConflicts(@TempDir File root) throws Exception {
		Workspace workspace = createExample(root);
		var mcreator = net.mcreator.ui.MCreator.create(null, workspace);
		try {
			SwingUtilities.invokeAndWait(() -> {
				Item original = (Item) workspace.getModElementByName("SpeedWand").getGeneratableElement();
				var editor = new net.mcreator.ui.modgui.ItemGUI(mcreator, original.getModElement(), true);
				editor.openInEditingMode(original);
				Item edited = editor.getElementFromGUI();
				assertTrue(edited.getSimpleBehavior().enabled);
				assertEquals(10, edited.getSimpleBehavior().cooldownSeconds);
				assertEquals(original.name, edited.name);
				assertEquals(original.texture.getRawTextureName(), edited.texture.getRawTextureName());
				assertTrue(editor.validateAllPages().validateIsErrorFree(),
						editor.validateAllPages().getGroupedValidationResults().toString());
				original.isFood = true;
				editor.openInEditingMode(original);
				assertFalse(editor.validateAllPages().validateIsErrorFree());
				editor.onViewClosed();
			});
		} finally {
			mcreator.dispose();
			workspace.close();
		}
	}

	@Test void formRoundTripPersistsAndUnsupportedMigrationIsBlocked(@TempDir File root) throws Exception {
		Workspace workspace = createExample(root);
		File workspaceFile = workspace.getFileManager().getWorkspaceFile();
		try {
			Item item = (Item) workspace.getModElementByName("SpeedWand").getGeneratableElement();
			AtomicReference<SimpleItemBehavior> edited = new AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				SimpleItemBehaviorPanel panel = new SimpleItemBehaviorPanel(TARGET);
				assertEquals("Simple behavior", net.mcreator.ui.init.L10N.t("remark.behavior.title"));
				panel.load(item.getSimpleBehavior());
				for (Component component : panel.getComponents())
					if ("simpleBehavior.cooldown".equals(component.getName()))
						((JSpinner) component).setValue(17);
				assertTrue(panel.validateInput().validateIsErrorFree());
				edited.set(panel.read());
				panel.load(edited.get().copy());
				assertEquals(17, panel.read().cooldownSeconds);
				panel.setSize(680, 440);
				layoutChildren(panel);
				BufferedImage preview = new BufferedImage(680, 440, BufferedImage.TYPE_INT_RGB);
				Graphics2D graphics = preview.createGraphics();
				graphics.setColor(new Color(30, 30, 30));
				graphics.fillRect(0, 0, 680, 440);
				panel.paint(graphics);
				graphics.dispose();
				try {
					File output = new File("build/no-code-proof/behavior-panel.png");
					Files.createDirectories(output.toPath().getParent());
					ImageIO.write(preview, "png", output);
				} catch (Exception exception) { throw new RuntimeException(exception); }
			});
			item.simpleBehavior = edited.get();
			workspace.getModElementManager().storeModElement(item);
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			var other = Generator.GENERATOR_CACHE.get("forge-1.20.1");
			var report = RemarkCompatibilityService.analyze(workspace, other);
			assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("SIMPLE_BEHAVIOR_TARGET")));
			item.simpleBehavior.cooldownSeconds = 0;
			assertThrows(TemplateGeneratorException.class, () -> workspace.getGenerator().generateElement(item, false));
		} finally { workspace.close(); }
		Workspace reopened = Workspace.readFromFSUnsafe(workspaceFile, Generator.GENERATOR_CACHE.get(TARGET));
		try {
			Item item = (Item) reopened.getModElementByName("SpeedWand").getGeneratableElement();
			assertEquals("คทาเร่งความเร็ว", item.name);
			assertEquals(17, item.getSimpleBehavior().cooldownSeconds);
			assertTrue(item.getSimpleBehavior().enabled);
			assertEquals(340, item.getSimpleBehaviorPlan().cooldownTicks());
			var files = reopened.getGenerator().generateElement(item, false, false);
			assertFalse(files.isEmpty());
		} finally { reopened.close(); }
	}

	@Test @EnabledIfEnvironmentVariable(named = "REMARK_FIRST_MOD_BUILD", matches = "true")
	void buildsPlayableExampleWithPinnedTarget() throws Exception {
		String output = System.getenv("REMARK_FIRST_MOD_OUTPUT");
		assertNotNull(output, "Set an unused directory for the example project");
		File root = new File(output);
		assertFalse(new File(root, "remark_first_mod.mcreator").exists(), "Do not overwrite an existing project");
		Workspace workspace = createExample(root);
		try {
			var generator = workspace.getGenerator();
			WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
			generator.runResourceSetupTasks();
			var connection = GradleUtils.getGradleProjectConnection(workspace);
			GradleUtils.getGradleSyncLauncher(workspace.getGeneratorConfiguration(), connection)
					.addArguments(buildArguments())
					.setStandardOutput(System.out).setStandardError(System.err).run();
			generator.reloadGradleCaches();
			generator.generateElement(workspace.getModElementByName("SpeedWand").getGeneratableElement(), true);
			generator.generateBase(true);
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			GradleUtils.getGradleTaskLauncher(workspace.getGeneratorConfiguration(), connection, "build")
					.addArguments(buildArguments())
					.setStandardOutput(System.out).setStandardError(System.err).run();
			File artifact = new File(root, "build/libs/modid-1.0.jar");
			assertTrue(artifact.isFile());
			try (ZipFile zip = new ZipFile(artifact)) {
				assertNotNull(zip.getEntry("META-INF/neoforge.mods.toml"));
				assertNotNull(zip.getEntry("assets/remark_first_mod/textures/item/speed_wand.png"));
				assertNotNull(zip.getEntry("assets/remark_first_mod/models/item/speed_wand.json"));
				assertTrue(zip.stream().anyMatch(entry -> entry.getName().endsWith("/item/SpeedWandItem.class")));
			}
			System.out.println("FIRST_MOD_ARTIFACT=" + artifact.getAbsolutePath());
		} finally { workspace.close(); }
	}

	private static void layoutChildren(Container container) {
		container.doLayout();
		for (Component child : container.getComponents())
			if (child instanceof Container nested)
				layoutChildren(nested);
	}

	private static String[] buildArguments() {
		return "true".equals(System.getenv("REMARK_FIRST_MOD_ONLINE")) ? new String[] { "--max-workers=1" }
				: new String[] { "--offline", "--max-workers=1" };
	}

	static Workspace createExample(File root) throws Exception {
		return createExample(root, System.getenv().getOrDefault("REMARK_FIRST_MOD_TARGET", TARGET));
	}

	static Workspace createExample(File root, String target) throws Exception {
		WorkspaceSettings settings = new WorkspaceSettings("remark_first_mod");
		settings.setModName("Remake First Mod");
		settings.setVersion("1.0.0");
		settings.setAuthor("Remake Studio");
		settings.setLicense("All Rights Reserved");
		settings.setDescription("No-code speed wand example");
		settings.setCurrentGenerator(target);
		if (System.getenv("REMARK_FIRST_MOD_LOADER_VERSION") != null)
			settings.setLoaderBuildVersion(System.getenv("REMARK_FIRST_MOD_LOADER_VERSION"));
		Workspace workspace = Workspace.createWorkspace(new File(root, "remark_first_mod.mcreator"), settings);
		ModElement element = new ModElement(workspace, "SpeedWand", ModElementType.ITEM);
		Item item = new Item(element);
		item.name = "คทาเร่งความเร็ว";
		item.stackSize = 1;
		item.toolType = 1;
		item.customModelName = "Tool";
		item.texture = new TextureHolder(workspace, "speed_wand");
		item.guiTexture = new TextureHolder(workspace, "");
		item.recipeRemainder = new MItemBlock(workspace, "");
		item.eatResultItem = new MItemBlock(workspace, "");
		item.animation = new ItemUseAnimation(workspace, "none");
		item.glowCondition = new LogicProcedure(null, false);
		item.openGUIOnRightClick = new LogicProcedure(null, false);
		item.specialInformation = new StringListProcedure(null, List.of());
		item.simpleBehavior.enabled = net.mcreator.generator.behavior.SimpleItemBehaviorCompiler.supports(target);
		BufferedImage texture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = texture.createGraphics();
		graphics.setColor(new Color(184, 140, 65));
		graphics.drawLine(3, 13, 10, 6);
		graphics.drawLine(4, 13, 11, 6);
		graphics.setColor(new Color(58, 220, 191));
		graphics.fillRect(9, 2, 4, 4);
		graphics.setColor(Color.WHITE);
		graphics.fillRect(10, 2, 2, 2);
		graphics.dispose();
		File textureFile = new File(workspace.getFolderManager().getTexturesFolder(TextureType.ITEM), "speed_wand.png");
		Files.createDirectories(textureFile.toPath().getParent());
		ImageIO.write(texture, "png", textureFile);
		workspace.addModElement(element);
		workspace.getModElementManager().storeModElement(item);
		workspace.getFileManager().saveWorkspaceDirectlyAndWait();
		return workspace;
	}
}
