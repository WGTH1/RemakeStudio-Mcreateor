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
import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.CreateUI;
import net.mcreator.element.types.Fashion;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.ItemDisplayPreview;
import net.mcreator.ui.remark.RemarkElementLivePreview;
import net.mcreator.ui.remark.RemarkStudioQualityAnalyzer;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.resources.Model;
import net.mcreator.workspace.resources.TexturedModel;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkLivePreviewAndQualityTest {

	@Test void paintsAAAAndSlotSpecificFashionPreviewAndFindsLayoutIssues(@TempDir File tempDir) throws Exception {
		GeneratorConfiguration generator = GeneratorConfiguration.getRecommendedGeneratorForBaseLanguage(
				Generator.GENERATOR_CACHE.values(), GeneratorFlavor.BaseLanguage.JAVA);
		assertNotNull(generator);
		MCreator mcreator = MCreator.create(null,
				TestWorkspaceDataProvider.createTestWorkspace(tempDir, generator, false, false, null));
		try {
			AAAParticle particle = new AAAParticle(
					new ModElement(mcreator.getWorkspace(), "PreviewAAA", ModElementType.AAAPARTICLE));
			particle.sourceName = "preview.efkefc";
			particle.spawnMode = "source_follow_view";
			particle.scaleX = particle.scaleY = particle.scaleZ = 1;
			particle.speed = 1;
			JComponent aaaPreview = RemarkElementLivePreview.create(particle);
			assertNotNull(aaaPreview);
			assertTrue(paintHasContent(aaaPreview));

			Fashion fashion = new Fashion(
					new ModElement(mcreator.getWorkspace(), "PreviewFashion", ModElementType.FASHION));
			fashion.enableHelmet = true;
			fashion.enableBody = true;
			fashion.enableLeggings = true;
			fashion.enableBoots = true;
			fashion.textureHelmet = new TextureHolder(mcreator.getWorkspace(), "test");
			fashion.textureBody = new TextureHolder(mcreator.getWorkspace(), "test2");
			fashion.textureLeggings = new TextureHolder(mcreator.getWorkspace(), "test3");
			fashion.textureBoots = new TextureHolder(mcreator.getWorkspace(), "test4");
			JComponent fashionPreview = RemarkElementLivePreview.create(fashion);
			assertNotNull(fashionPreview);
			assertTrue(countEnabledSlotButtons(fashionPreview) == 4);
			assertTrue(paintHasContent(fashionPreview));

			ModElement uiElement = new ModElement(mcreator.getWorkspace(), "QualityUI", ModElementType.CREATEUI);
			CreateUI createUI = new CreateUI(uiElement);
			Button first = new Button("first", 10, 10, "ข้อความภาษาไทยที่ยาว", 20, 12, false, null, null);
			Button second = new Button("second", 15, 13, "Second", 60, 20, false, null, null);
			createUI.components = List.of(first, second);
			mcreator.getWorkspace().addModElement(uiElement);
			mcreator.getModElementManager().storeModElement(createUI);
			List<RemarkStudioQualityAnalyzer.Finding> findings =
					RemarkStudioQualityAnalyzer.analyzeLanguageAndLayout(mcreator);
			assertTrue(findings.stream().anyMatch(finding -> "text_overflow".equals(finding.code())));
			assertTrue(findings.stream().anyMatch(finding -> "button_overlap".equals(finding.code())));
		} finally {
			mcreator.dispose();
		}
	}

	@Test void paintsOBJItemDisplayPreviewAndSupportsBlockbenchStyleControls(@TempDir File tempDir) throws Exception {
		GeneratorConfiguration generator = GeneratorConfiguration.getRecommendedGeneratorForBaseLanguage(
				Generator.GENERATOR_CACHE.values(), GeneratorFlavor.BaseLanguage.JAVA);
		assertNotNull(generator);
		MCreator mcreator = MCreator.create(null,
				TestWorkspaceDataProvider.createTestWorkspace(tempDir, generator, false, false, null));
		try {
			File modelsDirectory = mcreator.getWorkspace().getFolderManager().getModelsDir();
			assertTrue(modelsDirectory.mkdirs() || modelsDirectory.isDirectory());
			Files.writeString(new File(modelsDirectory, "preview.obj").toPath(), """
					mtllib preview.mtl
					v 0 0 0.5
					v 1 0 0.5
					v 1 1 0.5
					v 0 1 0.5
					vt 0 0
					vt 1 0
					vt 1 1
					vt 0 1
					usemtl preview
					f 1/1 2/2 3/3 4/4
					""", StandardCharsets.UTF_8);
			Files.writeString(new File(modelsDirectory, "preview.mtl").toPath(), """
					newmtl preview
					map_Kd null
					""", StandardCharsets.UTF_8);
			Files.writeString(new File(modelsDirectory, "preview.obj.textures").toPath(), """
					{
					  "mappings": {
					    "default": {
					      "name": "default",
					      "map": {
					        "preview": "preview_texture"
					      }
					    }
					  }
					}
					""", StandardCharsets.UTF_8);
			BufferedImage texture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Graphics2D textureGraphics = texture.createGraphics();
			textureGraphics.setColor(new Color(225, 35, 125));
			textureGraphics.fillRect(0, 0, 16, 16);
			textureGraphics.dispose();
			File textureFile = mcreator.getWorkspace().getFolderManager()
					.getTextureFile("preview_texture", TextureType.BLOCK);
			assertNotNull(textureFile.getParentFile());
			assertTrue(textureFile.getParentFile().mkdirs() || textureFile.getParentFile().isDirectory());
			assertTrue(ImageIO.write(texture, "PNG", textureFile));
			Model model = Model.getModelByParams(mcreator.getWorkspace(), "preview:default", Model.Type.OBJ);
			assertNotNull(model);
			assertTrue(model instanceof TexturedModel);
			Files.writeString(new File(modelsDirectory, "offset.obj").toPath(),
					"mtllib preview.mtl\nv 1 0 0\nv 2 0 0\nv 2 1 1\nv 1 1 1\nusemtl preview\nf 1 2 3 4\n");
			Files.copy(new File(modelsDirectory, "preview.mtl").toPath(), new File(modelsDirectory, "offset.mtl").toPath());
			Model offsetModel = Model.getModelByParams(mcreator.getWorkspace(), "offset", Model.Type.OBJ);
			AtomicReference<Transform> fittedValue = new AtomicReference<>(new Transform());
			ItemDisplayPreview fitPreview = new ItemDisplayPreview(offsetModel, fittedValue::get, fittedValue::set);
			fitPreview.setViewContext(ItemDisplayPreview.ViewContext.FIRST_LEFT);
			Transform leftFit = fitPreview.fitToReference();
			assertNotNull(leftFit);
			assertEquals(12.8, leftFit.translationX, .0001, "Left-hand fit must compensate mirrored X translation");

			AtomicReference<Transform> transform = new AtomicReference<>(
					new Transform(0, 0, 0, 0, 0, 0, 1, 1, 1));
			ItemDisplayPreview preview = new ItemDisplayPreview(model, transform::get, transform::set);
			assertEquals(2, preview.getFaceCount());
			assertTrue(paintHasContent(preview));
			Rectangle modelBounds = findModelBounds(paint(preview));
			assertNotNull(modelBounds);
			assertTrue(Math.abs(modelBounds.width - 150) <= 2);
			assertTrue(Math.abs(modelBounds.height - 150) <= 2);

			assertEquals(ItemDisplayPreview.ToolMode.MOVE, preview.getToolMode());
			preview.getActionMap().get("tool.rotate")
					.actionPerformed(new ActionEvent(preview, ActionEvent.ACTION_PERFORMED, "rotate"));
			assertEquals(ItemDisplayPreview.ToolMode.ROTATE, preview.getToolMode());
			preview.getActionMap().get("tool.scale")
					.actionPerformed(new ActionEvent(preview, ActionEvent.ACTION_PERFORMED, "scale"));
			assertEquals(ItemDisplayPreview.ToolMode.SCALE, preview.getToolMode());

			preview.dispatchEvent(new MouseWheelEvent(preview, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
					200, 150, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1));
			assertTrue(preview.getCameraZoom() > 1);
			assertEquals(1, transform.get().scaleX);

			preview.dispatchEvent(new MouseWheelEvent(preview, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
					InputEvent.CTRL_DOWN_MASK, 200, 150, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1));
			assertTrue(transform.get().scaleX > 1);
			assertEquals(transform.get().scaleX, transform.get().scaleY);
			assertEquals(transform.get().scaleY, transform.get().scaleZ);

			preview.resetCamera();
			assertEquals(1, preview.getCameraZoom());

			transform.set(new Transform(0, 0, 0, 0, 0, 0, 1, 1, 1));
			preview.setToolMode(ItemDisplayPreview.ToolMode.MOVE);
			paint(preview);
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
					InputEvent.BUTTON1_DOWN_MASK, 340, 213, 1, false, MouseEvent.BUTTON1));
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
					InputEvent.BUTTON1_DOWN_MASK, 360, 203, 0, false, MouseEvent.NOBUTTON));
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
					360, 203, 1, false, MouseEvent.BUTTON1));
			assertTrue(transform.get().translationX > 0);
			assertTrue(transform.get().translationY > 0);

			// Empty-space dragging must not silently move the item.
			double savedX = transform.get().translationX;
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_PRESSED, 1,
					InputEvent.BUTTON1_DOWN_MASK, 20, 20, 1, false, MouseEvent.BUTTON1));
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_DRAGGED, 2,
					InputEvent.BUTTON1_DOWN_MASK, 60, 60, 0, false, MouseEvent.NOBUTTON));
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_RELEASED, 3,
					0, 60, 60, 1, false, MouseEvent.BUTTON1));
			assertEquals(savedX, transform.get().translationX);

			transform.set(new Transform());
			preview.setViewContext(ItemDisplayPreview.ViewContext.FIRST_LEFT);
			paint(preview);
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_PRESSED, 4,
					InputEvent.BUTTON1_DOWN_MASK, 300, 213, 1, false, MouseEvent.BUTTON1));
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_DRAGGED, 5,
					InputEvent.BUTTON1_DOWN_MASK, 280, 213, 0, false, MouseEvent.NOBUTTON));
			preview.dispatchEvent(new MouseEvent(preview, MouseEvent.MOUSE_RELEASED, 6,
					0, 280, 213, 1, false, MouseEvent.BUTTON1));
			assertTrue(transform.get().translationX > 0, "Left-hand X handle must follow its arrow");
			File screenshots = new File("build/obj-display-smoke");
			assertTrue(screenshots.mkdirs() || screenshots.isDirectory());
			transform.set(new Transform(90, 0, 0, 0, 0, 0, .5, .5, .5));
			preview.setViewContext(ItemDisplayPreview.ViewContext.THIRD_RIGHT);
			BufferedImage third = paint(preview);
			assertNotNull(findModelBounds(third));
			ImageIO.write(third, "PNG", new File(screenshots, "third-person.png"));
			transform.set(new Transform());
			for (String slot : new String[] { "helmet", "body", "leggings", "boots" }) {
				preview.setWornContext(slot, new Transform());
				Transform fitted = preview.fitToReference();
				assertNotNull(fitted);
				assertTrue(Double.isFinite(fitted.scaleX) && fitted.scaleX > 0 && fitted.scaleX <= 4);
				BufferedImage worn = paint(preview);
				assertNotNull(findModelBounds(worn), "Worn OBJ must be visible: " + slot);
				ImageIO.write(worn, "PNG", new File(screenshots, "worn-" + slot + ".png"));
				for (ItemDisplayPreview.Pose pose : ItemDisplayPreview.Pose.values()) {
					preview.setPose(pose);
					assertNotNull(findModelBounds(paint(preview)));
				}
			}
			preview.setWornContext("leggings", new Transform());
			preview.setPose(ItemDisplayPreview.Pose.STANDING);
			var yaw = ItemDisplayPreview.class.getDeclaredField("cameraYaw"); yaw.setAccessible(true); yaw.setDouble(preview, 90);
			var facesMethod = ItemDisplayPreview.class.getDeclaredMethod("buildPaintFaces", Transform.class, int.class, int.class, double.class);
			facesMethod.setAccessible(true);
			List<?> faces = (List<?>) facesMethod.invoke(preview, new Transform(), 340, 213, 100);
			assertEquals(4, faces.size(), "Both attachments share one depth-sorted list");
			var depth = faces.getFirst().getClass().getDeclaredMethod("depth"); depth.setAccessible(true);
			double previousDepth = -Double.MAX_VALUE;
			for (Object face : faces) {
				double value = (double) depth.invoke(face);
				assertTrue(value >= previousDepth); previousDepth = value;
			}
			assertTrue((double) depth.invoke(faces.getLast()) - (double) depth.invoke(faces.getFirst()) >= 24.9,
					"Side attachment depth must participate in sorting");
		} finally {
			mcreator.dispose();
		}
	}

	private static int countEnabledSlotButtons(Component root) {
		int count = root instanceof JToggleButton button && button.isEnabled() ? 1 : 0;
		if (root instanceof Container container) {
			for (Component child : container.getComponents())
				count += countEnabledSlotButtons(child);
		}
		return count;
	}

	private static boolean paintHasContent(JComponent component) {
		BufferedImage image = paint(component);
		int sample = image.getRGB(340, 205);
		return sample != 0;
	}

	private static BufferedImage paint(JComponent component) {
		component.setSize(680, 410);
		component.doLayout();
		for (Component child : component.getComponents()) {
			if (child instanceof JComponent childComponent) {
				childComponent.setSize(680, Math.max(360, childComponent.getPreferredSize().height));
				childComponent.doLayout();
			}
		}
		BufferedImage image = new BufferedImage(680, 410, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		component.paint(graphics);
		graphics.dispose();
		return image;
	}

	private static Rectangle findModelBounds(BufferedImage image) {
		int minimumX = image.getWidth();
		int minimumY = image.getHeight();
		int maximumX = -1;
		int maximumY = -1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				Color color = new Color(image.getRGB(x, y), true);
				if (color.getRed() > 160 && color.getBlue() > 80 && color.getGreen() < 100) {
					minimumX = Math.min(minimumX, x);
					minimumY = Math.min(minimumY, y);
					maximumX = Math.max(maximumX, x);
					maximumY = Math.max(maximumY, y);
				}
			}
		}
		return maximumX >= minimumX ?
				new Rectangle(minimumX, minimumY, maximumX - minimumX + 1, maximumY - minimumY + 1) :
				null;
	}
}
