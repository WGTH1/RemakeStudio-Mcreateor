package net.mcreator.ui.dialogs;

import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.element.types.Fashion;
import net.mcreator.generator.Generator;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
class FashionDisplayEditorPanelTest {
	@Test void switchesModesWithoutLosingValuesAndRendersAtTwoSizes(@TempDir File temp) throws Exception {
		var generator = Generator.GENERATOR_CACHE.values().stream().filter(g -> g.getGeneratorName().equals("forge-1.20.1")).findFirst().orElseThrow();
		try (var workspace = TestWorkspaceDataProvider.createTestWorkspace(temp, generator, false, false, null)) {
			Fashion source = new Fashion(new ModElement(workspace, "DisplayTest", ModElementType.FASHION));
			source.enableHelmet = source.enableBody = source.enableLeggings = source.enableBoots = true;
			source.singleItemSet = true;
			source.helmetItemRenderType = 2;
			source.helmetItemCustomModelName = "display_preview";
			var models = workspace.getFolderManager().getModelsDir().toPath();
			Files.createDirectories(models);
			Files.writeString(models.resolve("display_preview.obj"), "v 0 0 .5\nv 1 0 .5\nv .5 1 .5\nf 1 2 3\n");
			Files.writeString(models.resolve("display_preview.mtl"), "newmtl preview\n");
			SwingUtilities.invokeAndWait(() -> {
				var panel = new FashionDisplayEditorPanel(source, "forge-1.20.1", "helmet", false);
				panel.activeEditor().load(new Transform(0, 0, 0, 12, 0, 0, .5, .5, .5));
				panel.selectView(FashionDisplaySession.WORN);
				panel.selectSlot(3);
				panel.activeEditor().load(new Transform(0, 0, 0, 0, 4, 0, .8, .8, .8));
				panel.selectSlot(0);
				assertEquals(0, panel.activeEditor().snapshot().translationY);
				panel.selectView(0);
				assertEquals(12, panel.activeEditor().snapshot().translationX);
				assertNotEquals(12, source.helmetItemDisplay.getGui().translationX, "Cancel leaves source untouched");
				panel.applyTo(source);
				assertEquals(12, source.helmetItemDisplay.getGui().translationX);
				assertEquals(4, source.getWornDisplay().boots.translationY);
				assertEquals(0, source.getWornDisplay().helmet.translationY);
				panel.selectView(FashionDisplaySession.WORN);
				panel.selectSlot(3);
				panel.setSidesLinked(false);
				panel.selectSide(1);
				panel.activeEditor().load(new Transform(0, 0, 0, 0, 9, 0, .7, .7, .7));
				panel.setSideVisible(false, false);
				panel.selectSide(0);
				assertEquals(4, panel.activeEditor().snapshot().translationY);
				panel.selectView(0);
				panel.selectView(FashionDisplaySession.WORN);
				panel.selectSide(1);
				assertEquals(9, panel.activeEditor().snapshot().translationY);
				panel.applyTo(source);
				assertEquals(9, source.getWornDisplay().getForSide("boots", false).translationY);
				assertEquals(4, source.getWornDisplay().getForSide("boots", true).translationY);
				assertFalse(source.getWornDisplay().isSideEnabled("boots", false));
				panel.selectSlot(0);
				panel.selectView(FashionDisplaySession.WORN);
				for (Dimension size : new Dimension[] { new Dimension(1080, 580), new Dimension(800, 480) }) {
					panel.setSize(size); layout(panel);
					for (JSpinner spinner : spinners(panel.activeEditor())) {
						Rectangle bounds = SwingUtilities.convertRectangle(spinner.getParent(), spinner.getBounds(), panel);
						assertTrue(bounds.x >= 0 && bounds.x + bounds.width <= size.width, "Spinner clipped at " + size);
					}
					BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
					Graphics2D g = image.createGraphics(); panel.paint(g); g.dispose();
					try {
						File dir = new File("build/unified-display-smoke"); dir.mkdirs();
						ImageIO.write(image, "png", new File(dir, "editor-" + size.width + ".png"));
					} catch (Exception e) { throw new RuntimeException(e); }
				}
			});
		}
	}
	private static void layout(Container root) {
		root.doLayout();
		for (Component child : root.getComponents()) if (child instanceof Container container) layout(container);
	}
	private static java.util.List<JSpinner> spinners(Container root) {
		java.util.List<JSpinner> result = new java.util.ArrayList<>();
		for (Component child : root.getComponents()) {
			if (child instanceof JSpinner spinner) result.add(spinner);
			else if (child instanceof Container container) result.addAll(spinners(container));
		}
		return result;
	}
}
