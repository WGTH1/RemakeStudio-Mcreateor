package net.mcreator.integration.ui;

import net.mcreator.element.ModElementType;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.generator.Generator;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.modgui.AAAParticleGUI;
import net.mcreator.ui.modgui.ModElementGUI;
import net.mcreator.ui.remark.RemarkAAAParticleImportDialog;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class AAAParticleQuickImportUITest {
    @TempDir Path temporary;

    @Test void fileDropsWorkOnListsWithoutRemovingExistingTransferBehavior() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JList<String> list = new JList<>();
            AtomicBoolean previousCalled = new AtomicBoolean();
            list.setTransferHandler(new TransferHandler() {
                @Override public boolean canImport(TransferSupport support) {
                    previousCalled.set(true);
                    return support.isDataFlavorSupported(DataFlavor.stringFlavor);
                }
            });
            RemarkAAAParticleImportDialog.installDropTarget(list, null);
            Transferable files = new Transferable() {
                public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
                public boolean isDataFlavorSupported(DataFlavor flavor) { return flavor.equals(DataFlavor.javaFileListFlavor); }
                public Object getTransferData(DataFlavor flavor) { return List.of(new File("effect.efkefc")); }
            };
            assertTrue(list.getTransferHandler().canImport(new TransferHandler.TransferSupport(list, files)),
                    "JList installs a transfer handler by default, but must still accept effect files");
            assertTrue(list.getTransferHandler().canImport(new TransferHandler.TransferSupport(list, new StringSelection("existing"))));
            assertTrue(previousCalled.get());
        });
    }

    @Test void simplifiedEditorRoundTripsAdvancedSettingsAndPaintsAtSmallWindowSizes() throws Exception {
        MCreator app = MCreator.create(null, TestWorkspaceDataProvider.createTestWorkspace(temporary.resolve("workspace").toFile(),
                Generator.GENERATOR_CACHE.get("neoforge-1.21.1"), false, false, null));
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var element = new ModElement(app.getWorkspace(), "SimpleVfx", ModElementType.AAAPARTICLE);
                    AAAParticleGUI editor = new AAAParticleGUI(app, element, false);
                    assertTrue(editor.getElementFromGUI().unlimitedDistance);
                    Field pages = ModElementGUI.class.getDeclaredField("pages");
                    pages.setAccessible(true);
                    assertEquals(2, ((List<?>) pages.get(editor)).size());
                    var data = new AAAParticle(element);
                    data.spawnMode = "source_follow_view";
                    data.offsetY = 2.5;
                    data.scaleX = 0.75;
                    data.speed = 1.25;
                    data.unlimitedDistance = false;
                    editor.openInEditingMode(data);
                    var saved = editor.getElementFromGUI();
                    assertEquals(data.spawnMode, saved.spawnMode);
                    assertEquals(data.offsetY, saved.offsetY);
                    assertEquals(data.scaleX, saved.scaleX);
                    assertEquals(data.speed, saved.speed);
                    assertFalse(saved.unlimitedDistance);
                    for (int width : new int[]{960, 1280}) {
                        editor.setSize(width, 640);
                        layout(editor);
                        var image = new BufferedImage(width, 640, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        editor.paint(graphics);
                        graphics.dispose();
                        Path output = Path.of("build/reports/aaa-quick-import/" + width + ".png");
                        Files.createDirectories(output.getParent());
                        ImageIO.write(image, "png", output.toFile());
                    }
                    editor.onViewClosed();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } finally { app.dispose(); app.getWorkspace().close(); }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container nested) layout(nested);
    }
}
