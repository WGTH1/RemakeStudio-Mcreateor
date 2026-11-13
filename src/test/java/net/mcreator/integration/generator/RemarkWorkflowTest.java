/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.integration.generator;

import net.mcreator.element.types.Item;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.generator.Generator;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.modgui.ItemGUI;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.modgui.ModElementGUIPage;
import net.mcreator.ui.remark.*;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import javax.swing.*;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class RemarkWorkflowTest {
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "forge-1.20.1", "fabric-1.20.1", "neoforge-26.1.2", "neoforge-1.21.1" })
	void sharedItemEditorPreservesFieldsOnExistingAdapters(String target, @TempDir File root) throws Exception {
		Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root, target);
		MCreator app = MCreator.create(null, workspace);
		try {
			SwingUtilities.invokeAndWait(() -> {
				Item item = (Item) workspace.getModElementByName("SpeedWand").getGeneratableElement();
				ItemGUI editor = new ItemGUI(app, item.getModElement(), true);
				Item result = editor.getElementFromGUI();
				assertEquals(item.name, result.name); assertEquals(item.stackSize, result.stackSize);
				assertEquals(item.texture.getRawTextureName(), result.texture.getRawTextureName());
				assertTrue(editor.validateAllPages().validateIsErrorFree());
				editor.onViewClosed();
			});
		} finally { app.dispose(); workspace.close(); }
	}

	@Test void catalogOnlyAllowsPublishedStable121PatchesAndSortsNumerically() {
		assertEquals(List.of("21.1.250", "21.1.10", "21.1.1"), RemarkLoaderVersionDialog.parseCatalog(
				"{\"versions\":[\"21.1.1\",\"21.1.10\",\"21.1.250\",\"21.1.10\",\"21.2.1\",\"21.1.2-beta\",\"oops\"]}"));
	}

	@Test void versionOverrideIsPersistedAndIsolatedFromOtherTargets(@TempDir File root) throws Exception {
		try (Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root)) {
			var settings = workspace.getWorkspaceSettings(); settings.setLoaderBuildVersion("21.1.1");
			assertEquals("21.1.1", new WorkspaceSettings(settings).getLoaderBuildVersion());
			assertThrows(IllegalArgumentException.class, () -> settings.setLoaderBuildVersion("21.1.1\";println(1)"));
			WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
			assertTrue(Files.readString(root.toPath().resolve("build.gradle")).contains("version = \"21.1.1\""));
			workspace.getGenerator().generateBase(false);
			assertTrue(Files.readString(root.toPath().resolve("src/main/resources/META-INF/neoforge.mods.toml")).contains("[21.1.1,21.2)"));
			var other = new WorkspaceSettings(settings); other.setCurrentGenerator("forge-1.20.1");
			assertEquals(Generator.GENERATOR_CACHE.get("forge-1.20.1").getGeneratorBuildFileVersion(), other.getLoaderBuildVersion());
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
		}
		try (Workspace reopened = Workspace.readFromFSUnsafe(new File(root, "remark_first_mod.mcreator"), Generator.GENERATOR_CACHE.get("neoforge-1.21.1-21.1.219"))) {
			assertEquals("21.1.1", reopened.getWorkspaceSettings().getLoaderBuildVersion());
		}
	}

	@Test void basicAndAdvancedUseSameItemDataAndErrorsNavigate(@TempDir File root) throws Exception {
		Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root); MCreator app = MCreator.create(null, workspace);
		try {
			SwingUtilities.invokeAndWait(() -> {
				Item item = (Item) workspace.getModElementByName("SpeedWand").getGeneratableElement();
				ItemGUI editor = new ItemGUI(app, item.getModElement(), true); editor.openInEditingMode(item);
				editor.showView();
				assertEquals(item.name, editor.getElementFromGUI().name);
				assertTrue(editor.validateAllPages().validateIsErrorFree(), editor.validateAllPages().getValidationProblemMessages().toString());
				try {
					var field = ModElementGUI.class.getDeclaredField("pages"); field.setAccessible(true);
					@SuppressWarnings("unchecked") List<ModElementGUIPage> pages = (List<ModElementGUIPage>) field.get(editor);
					assertEquals(2, pages.size());
					paint(pages.getFirst().getComponent(), "simple-item-round2.png");
				} catch (Exception e) { throw new RuntimeException(e); }
				item.isFood = true; editor.openInEditingMode(item);
				assertSame(editor, RemarkProblemPanel.openProblem(app, new RemarkProblemPanel.Problem("SpeedWand",
						net.mcreator.ui.init.L10N.t("remark.behavior.error.use_conflict"))));
				assertTrue(editor.getElementFromGUI().isFood);
				var issues = editor.validateAllPages().getValidationProblemMessages(); assertFalse(issues.isEmpty());
				editor.goToProblem(net.mcreator.ui.init.L10N.t("remark.behavior.error.use_conflict"));
				try {
					var field = ItemGUI.class.getDeclaredField("advancedTabs"); field.setAccessible(true);
					assertEquals(2, ((JTabbedPane) field.get(editor)).getSelectedIndex());
				} catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
				editor.onViewClosed();
			});
		} finally { app.dispose(); workspace.close(); }
	}

	@Test void sandboxExcludesWorldsAndGrantCannotInjectCommands(@TempDir File root) throws Exception {
		try (Workspace source = RemarkSimpleItemBehaviorTest.createExample(root)) {
			Files.createDirectories(root.toPath().resolve("run/saves/RealWorld"));
			Files.writeString(root.toPath().resolve("run/saves/RealWorld/level.dat"), "Do not touch");
			try (var sandbox = RemarkWorkspaceSandbox.create(source, source.getGeneratorConfiguration())) {
				assertFalse(new File(sandbox.getRoot(), "run/saves/RealWorld/level.dat").exists());
				RemarkItemTestSession.writeGrantResources(sandbox.getWorkspace(), "speed_wand");
				assertTrue(Files.readString(sandbox.getRoot().toPath().resolve("src/main/resources/data/remark_studio_test/function/grant.mcfunction")).equals("give @s remark_first_mod:speed_wand 1\n"));
				Path proof = Path.of("build/no-code-proof/item-test-resources/data/remark_studio_test");
				for (String relative : List.of("function/grant.mcfunction", "advancement/first_join.json")) {
					Path target = proof.resolve(relative); Files.createDirectories(target.getParent());
					Files.copy(sandbox.getRoot().toPath().resolve("src/main/resources/data/remark_studio_test").resolve(relative),
							target, StandardCopyOption.REPLACE_EXISTING);
				}
				assertThrows(java.io.IOException.class, () -> RemarkItemTestSession.writeGrantResources(sandbox.getWorkspace(), "speed_wand\nkill @a"));
			}
			assertFalse(root.toPath().resolve("src/main/resources/data/remark_studio_test").toFile().exists());
			assertEquals("Do not touch", Files.readString(root.toPath().resolve("run/saves/RealWorld/level.dat")));
		}
	}

	@Test void referencesBlockRenameAndBackupSurvivesReload(@TempDir File root) throws Exception {
		try (Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root)) {
			ModElement original = workspace.getModElementByName("SpeedWand");
			ModElement consumer = new ModElement(workspace, "Consumer", net.mcreator.element.ModElementType.ITEM);
			Item data = new Item(consumer); data.recipeRemainder = new MItemBlock(workspace, "CUSTOM:SpeedWand");
			workspace.addModElement(consumer); workspace.getModElementManager().storeModElement(data);
			assertTrue(RemarkSafeOperations.blockers(workspace, List.of(original)).contains("Consumer"));
			assertThrows(java.io.IOException.class, () -> RemarkSafeOperations.rename(workspace, original, "RenamedWand"));
			assertNotNull(workspace.getModElementByName("SpeedWand"));
			File backup = RemarkSafeOperations.backup(workspace);
			assertEquals(backup.getCanonicalFile(), RemarkSafeOperations.lastBackup(workspace).getCanonicalFile());
			try (Workspace copy = Workspace.readFromFSUnsafe(backup, workspace.getGeneratorConfiguration())) {
				assertNotNull(copy.getModElementByName("SpeedWand"));
				assertEquals("คทาเร่งความเร็ว", ((Item) copy.getModElementByName("SpeedWand").getGeneratableElement()).name);
			}
		}
	}

	@Test void unreferencedRenameKeepsRuntimeId(@TempDir File root) throws Exception {
		try (Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root)) {
			ModElement original = workspace.getModElementByName("SpeedWand");
			String registry = original.getRegistryName();
			ModElement renamed = RemarkSafeOperations.rename(workspace, original, "RenamedWand");
			assertEquals(registry, renamed.getRegistryName()); assertNull(workspace.getModElementByName("SpeedWand"));
			assertNotNull(workspace.getModElementByName("RenamedWand")); assertTrue(RemarkSafeOperations.lastBackup(workspace).isFile());
		}
	}

	@Test void selfReferencesAndUnmanagedTagsCannotBeRenamedSilently(@TempDir File root) throws Exception {
		try (Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root)) {
			ModElement element = workspace.getModElementByName("SpeedWand");
			Item item = (Item) element.getGeneratableElement();
			item.recipeRemainder = new MItemBlock(workspace, "CUSTOM:SpeedWand");
			workspace.getModElementManager().storeModElement(item);
			assertThrows(java.io.IOException.class, () -> RemarkSafeOperations.rename(workspace, element, "RenamedWand"));
			var tag = new net.mcreator.workspace.elements.TagElement(net.mcreator.minecraft.TagType.ITEMS, "mod:tools");
			workspace.getTagElements().put(tag, new java.util.ArrayList<>(List.of(
					net.mcreator.workspace.elements.TagElement.Entry.unmanaged("CUSTOM:SpeedWand"))));
			assertTrue(RemarkSafeOperations.blockers(workspace, List.of(element)).contains("Tag: mod:tools"));
		}
	}

	@Test void cancelledVerificationDoesNotGenerateOrMutateTheSource(@TempDir File root) throws Exception {
		try (Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root);
				var sandbox = RemarkWorkspaceSandbox.create(workspace, workspace.getGeneratorConfiguration())) {
			var cancellation = org.gradle.tooling.GradleConnector.newCancellationTokenSource(); cancellation.cancel();
			String settings = workspace.getWorkspaceSettings().getLoaderBuildVersion();
			var messages = new java.util.ArrayList<String>();
			var result = sandbox.verify(false, null, messages::add, cancellation.token());
			assertFalse(result.success()); assertTrue(messages.isEmpty());
			assertEquals(settings, workspace.getWorkspaceSettings().getLoaderBuildVersion());
		}
	}

	@Test void resourceReviewFindsDuplicatesWithoutDeleting(@TempDir File root) throws Exception {
		Workspace workspace = RemarkSimpleItemBehaviorTest.createExample(root); MCreator app = MCreator.create(null, workspace);
		try {
			Path texture = root.toPath().resolve("src/main/resources/assets/remark_first_mod/textures/item/speed_wand.png");
			Path duplicate = texture.resolveSibling("unused_copy.png"); Files.copy(texture, duplicate);
			List<RemarkAssetReviewPanel.Asset> report = RemarkAssetReviewPanel.analyze(app);
			assertTrue(report.stream().filter(a -> a.hash().equals(report.stream().filter(v -> v.file().equals(texture.toFile())).findFirst().orElseThrow().hash())).count() >= 2);
			assertTrue(Files.exists(texture)); assertTrue(Files.exists(duplicate));
			assertFalse(report.stream().filter(a -> a.file().equals(texture.toFile())).findFirst().orElseThrow().removable());
			var result = RemarkCleanupService.quarantine(app, new RemarkCleanupService.Plan(List.of(duplicate.toFile()),
					Files.size(duplicate), List.of(), 0, false));
			assertFalse(Files.exists(duplicate)); assertTrue(Files.exists(texture));
			RemarkCleanupService.restoreLatest(app);
			assertArrayEquals(Files.readAllBytes(texture), Files.readAllBytes(duplicate));
		} finally { app.dispose(); workspace.close(); }
	}

	private static void paint(JComponent component, String name) throws Exception {
		component.setSize(900, 660); layout(component);
		var image = new java.awt.image.BufferedImage(900, 660, java.awt.image.BufferedImage.TYPE_INT_RGB);
		var graphics = image.createGraphics(); graphics.setColor(new java.awt.Color(30, 30, 30)); graphics.fillRect(0, 0, 900, 660);
		component.paint(graphics); graphics.dispose();
		Path file = Path.of("build/no-code-proof", name); Files.createDirectories(file.getParent()); javax.imageio.ImageIO.write(image, "png", file.toFile());
	}
	private static void layout(java.awt.Container container) { container.doLayout(); for (var child : container.getComponents()) if (child instanceof java.awt.Container nested) layout(nested); }
}
