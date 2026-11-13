/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.integration.ui;

import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.types.CreateUI;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.modgui.CreateUIGUI;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.validation.component.VTextField;
import net.mcreator.ui.wysiwyg.WYSIWYGEditor;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkCreateUIPreviewEditorTest {

	@Test void opensSinglePagePreviewEditorAndRoundTrips(@TempDir File tempDir) throws Exception {
		GeneratorConfiguration generator = GeneratorConfiguration.getRecommendedGeneratorForBaseLanguage(
				Generator.GENERATOR_CACHE.values(), GeneratorFlavor.BaseLanguage.JAVA);
		MCreator mcreator = MCreator.create(null,
				TestWorkspaceDataProvider.createTestWorkspace(tempDir, generator, true, true, null));
		ModElement modElement = new ModElement(mcreator.getWorkspace(), "PreviewEditor", ModElementType.CREATEUI);
		CreateUI createUI = new CreateUI(modElement);
		createUI.components = List.of(new Button("play", 100, 90, "Play", 80, 20, false, null, null));

		ModElementGUI<?> gui = UITestUtil.openModElementGUIFor(mcreator, createUI);
		CreateUIGUI createUIGUI = assertInstanceOf(CreateUIGUI.class, gui);
		Field targetScreenField = CreateUIGUI.class.getDeclaredField("targetScreen");
		targetScreenField.setAccessible(true);
		JComboBox<?> targets = (JComboBox<?>) targetScreenField.get(createUIGUI);
		assertTrue(java.util.stream.IntStream.range(0, targets.getItemCount())
				.anyMatch(index -> "Pause menu".equals(targets.getItemAt(index))));
		Field layerListField = CreateUIGUI.class.getDeclaredField("previewLayerList");
		layerListField.setAccessible(true);
		assertNotNull(layerListField.get(createUIGUI));
		Field previewToolsField = CreateUIGUI.class.getDeclaredField("previewTools");
		previewToolsField.setAccessible(true);
		JTabbedPane previewTools = (JTabbedPane) previewToolsField.get(createUIGUI);
		assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, previewTools.getTabLayoutPolicy());
		for (int index : new int[] { 0, 2, 3 }) {
			JScrollPane scroll = assertInstanceOf(JScrollPane.class, previewTools.getComponentAt(index));
			assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
					scroll.getHorizontalScrollBarPolicy());
		}
		JScrollPane layerControlsScroll = assertInstanceOf(JScrollPane.class,
				findComponentByName((Container) previewTools.getComponentAt(1), "createui.layers.controls.scroll"));
		assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED,
				layerControlsScroll.getHorizontalScrollBarPolicy());
		Field previewSplitField = CreateUIGUI.class.getDeclaredField("previewWorkspaceSplit");
		previewSplitField.setAccessible(true);
		JSplitPane previewSplit = (JSplitPane) previewSplitField.get(createUIGUI);
		assertTrue(previewSplit.isContinuousLayout());
		assertTrue(previewSplit.isOneTouchExpandable());
		assertEquals(1.0, previewSplit.getResizeWeight());
		Field editorField = CreateUIGUI.class.getDeclaredField("editor");
		editorField.setAccessible(true);
		WYSIWYGEditor editor = (WYSIWYGEditor) editorField.get(createUIGUI);
		Field previewColorField = CreateUIGUI.class.getDeclaredField("previewColor");
		previewColorField.setAccessible(true);
		Method addElement = CreateUIGUI.class.getDeclaredMethod("addPreviewElement", String.class, Point.class);
		addElement.setAccessible(true);
		Method addActionButton = CreateUIGUI.class.getDeclaredMethod("addActionButton", String.class, Point.class,
				String.class, String.class);
		addActionButton.setAccessible(true);
		AtomicReference<Button> joinButtonRef = new AtomicReference<>();
		AtomicReference<CreateUI> savedRef = new AtomicReference<>();
		net.mcreator.element.parts.gui.Image image = new net.mcreator.element.parts.gui.Image(10, 10,
				"preview_resize_test.png", false, null);
		image.customWidth = 80;
		image.customHeight = 40;
		Field previewPanelField = CreateUIGUI.class.getDeclaredField("previewPanel");
		previewPanelField.setAccessible(true);
		Object previewPanel = previewPanelField.get(createUIGUI);
		Method findResizeHandle = previewPanel.getClass()
				.getDeclaredMethod("findResizeHandle", GUIComponent.class, Point.class);
		findResizeHandle.setAccessible(true);
		Method setBounds = previewPanel.getClass().getDeclaredMethod("setPreviewComponentBounds", GUIComponent.class,
				int.class, int.class, int.class, int.class);
		setBounds.setAccessible(true);
		SwingUtilities.invokeAndWait(() -> {
			try {
				editor.list.setSelectedIndex(0);
				((VTextField) previewColorField.get(createUIGUI)).setText("12AB34");
				assertEquals("12AB34", ((Button) editor.getComponentList().getFirst()).normalTextColor);
				assertTrue((boolean) addElement.invoke(createUIGUI, "checkbox", new Point(20, 20)));
				assertTrue((boolean) addElement.invoke(createUIGUI, "text_input", new Point(20, 50)));
				assertTrue((boolean) addElement.invoke(createUIGUI, "tooltip", new Point(20, 80)));
				joinButtonRef.set((Button) addActionButton.invoke(createUIGUI, CreateUI.ACTION_JOIN_SERVER,
						new Point(100, 120), "play.example.com", ""));
				savedRef.set(createUIGUI.getElementFromGUI());
				editor.editor.addComponent(image);
				editor.list.setSelectedValue(image, true);
				assertNotNull(findResizeHandle.invoke(previewPanel, image, new Point(90, 50)));
				setBounds.invoke(previewPanel, image, 20, 30, 120, 55);
			} catch (ReflectiveOperationException exception) {
				throw new RuntimeException(exception);
			}
		});
		SwingUtilities.invokeAndWait(() -> {
		});
		Button joinButton = joinButtonRef.get();
		CreateUI saved = savedRef.get();
		assertTrue(saved.buttonActions.stream().anyMatch(action -> joinButton.getName().equals(action.buttonName)
				&& CreateUI.ACTION_JOIN_SERVER.equals(action.actionType)
				&& "play.example.com".equals(action.value)));
		assertEquals(20, image.x);
		assertEquals(30, image.y);
		assertEquals(120, image.customWidth);
		assertEquals(55, image.customHeight);
		assertFalse(saved.components.isEmpty());
		Method pausePreset = CreateUIGUI.class.getDeclaredMethod("applyPauseMenuPreset");
		pausePreset.setAccessible(true);
		SwingUtilities.invokeAndWait(() -> {
			try {
				pausePreset.invoke(createUIGUI);
				CreateUI pause = createUIGUI.getElementFromGUI();
				assertEquals(7, pause.components.size());
				for (GUIComponent component : pause.components) {
					if (component instanceof Button button) {
						assertFalse(button.autoSize, button.getName());
						assertEquals(200, button.width, button.getName());
					}
				}
				assertEquals(CreateUI.TARGET_PAUSE_MENU, pause.targetScreen);
				assertTrue(pause.isPauseMenu());
				for (String action : List.of(CreateUI.ACTION_RESUME_GAME, CreateUI.ACTION_ADVANCEMENTS,
						CreateUI.ACTION_STATISTICS, CreateUI.ACTION_OPTIONS, CreateUI.ACTION_DISCONNECT,
						CreateUI.ACTION_VANILLA_PAUSE_MENU))
					assertTrue(pause.buttonActions.stream().anyMatch(binding -> action.equals(binding.actionType)));
				createUIGUI.openInEditingMode(pause);
				assertEquals(CreateUI.TARGET_PAUSE_MENU, createUIGUI.getElementFromGUI().targetScreen);
				assertEquals(7, createUIGUI.getElementFromGUI().components.size(), "Reopening a draft must not clear its own component list");
				for (GUIComponent component : createUIGUI.getElementFromGUI().components) {
					if (component instanceof Button button) {
						assertFalse(button.autoSize, button.getName());
						assertEquals(200, button.width, button.getName());
					}
				}
				for (int width : new int[] { 960, 1280, 1920 }) {
					createUIGUI.setSize(width, 720);
					layout(createUIGUI);
					previewSplit.setDividerLocation(Math.max(360, previewSplit.getWidth() - 420));
					layout(createUIGUI);
					var screenshot = new java.awt.image.BufferedImage(width, 720, java.awt.image.BufferedImage.TYPE_INT_ARGB);
					Graphics2D graphics = screenshot.createGraphics();
					createUIGUI.paint(graphics);
					graphics.dispose();
					var output = java.nio.file.Path.of("build/reports/createui-pause/" + width + ".png");
					java.nio.file.Files.createDirectories(output.getParent());
					javax.imageio.ImageIO.write(screenshot, "png", output.toFile());
				}
			} catch (Exception exception) {
				throw new RuntimeException(exception);
			}
		});

		SwingUtilities.invokeAndWait(createUIGUI::onViewClosed);
		mcreator.dispose();
		mcreator.getWorkspace().close();
	}

	private static void layout(Container container) {
		container.doLayout();
		for (Component child : container.getComponents())
			if (child instanceof Container nested) layout(nested);
	}

	private static Component findComponentByName(Container root, String name) {
		for (Component component : root.getComponents()) {
			if (name.equals(component.getName()))
				return component;
			if (component instanceof Container container) {
				Component nested = findComponentByName(container, name);
				if (nested != null)
					return nested;
			}
		}
		return null;
	}
}
