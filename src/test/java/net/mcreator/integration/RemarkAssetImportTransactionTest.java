/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.integration;

import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.remark.RemarkAssetImportTransaction;
import net.mcreator.ui.remark.RemarkStudioElementTools;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.elements.SoundElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkAssetImportTransactionTest {

	@Test void importCanUndoNewFilesAndPreservesFilesModifiedAfterImport(@TempDir File tempRoot) throws Exception {
		GeneratorConfiguration generator = GeneratorConfiguration.getRecommendedGeneratorForBaseLanguage(
				Generator.GENERATOR_CACHE.values(), GeneratorFlavor.BaseLanguage.JAVA);
		assertNotNull(generator);
		File workspaceFolder = new File(tempRoot, "workspace");
		MCreator mcreator = MCreator.create(null,
				TestWorkspaceDataProvider.createTestWorkspace(workspaceFolder, generator, false, false, null));
		try {
			File firstSource = createImage(new File(tempRoot, "first.png"), new Color(220, 40, 120));
			File firstImported = importAndFindCreatedTexture(mcreator, firstSource);
			assertTrue(firstImported.isFile());
			RemarkAssetImportTransaction.UndoResult firstUndo = RemarkAssetImportTransaction.undoLast(mcreator);
			assertTrue(firstUndo.success());
			assertEquals(1, firstUndo.removedFiles());
			assertFalse(firstImported.exists());

			File secondSource = createImage(new File(tempRoot, "second.png"), new Color(225, 180, 73));
			File secondImported = importAndFindCreatedTexture(mcreator, secondSource);
			Files.writeString(secondImported.toPath(), "user-modified-after-import");
			RemarkAssetImportTransaction.UndoResult secondUndo = RemarkAssetImportTransaction.undoLast(mcreator);
			assertTrue(secondUndo.success());
			assertEquals(0, secondUndo.removedFiles());
			assertEquals(1, secondUndo.skippedModifiedFiles());
			assertTrue(secondImported.isFile());

			RemarkAssetImportTransaction soundTransaction = RemarkAssetImportTransaction.begin(mcreator, new File[0]);
			File soundFile = new File(mcreator.getFolderManager().getSoundsDir(), "transaction_sound.ogg");
			soundFile.getParentFile().mkdirs();
			Files.writeString(soundFile.toPath(), "imported-sound");
			mcreator.getWorkspace().addSoundElement(new SoundElement("transaction_sound",
					List.of("transaction_sound"), "master", null));
			soundTransaction.complete();
			Files.writeString(soundFile.toPath(), "user-modified-sound");
			RemarkAssetImportTransaction.UndoResult soundUndo = RemarkAssetImportTransaction.undoLast(mcreator);
			assertTrue(soundUndo.success());
			assertEquals(1, soundUndo.skippedModifiedFiles());
			assertTrue(soundFile.isFile());
			assertTrue(mcreator.getWorkspace().getSoundElements().stream()
					.anyMatch(sound -> "transaction_sound".equals(sound.getName())));
		} finally {
			mcreator.dispose();
		}
	}

	private static File importAndFindCreatedTexture(MCreator mcreator, File source) throws Exception {
		Set<File> before = new LinkedHashSet<>(mcreator.getFolderManager().getTexturesList(TextureType.SCREEN));
		Method importer = RemarkStudioElementTools.class.getDeclaredMethod("importAssetsInternal", MCreator.class,
				net.mcreator.element.GeneratableElement.class, File[].class);
		importer.setAccessible(true);
		importer.invoke(null, mcreator, null, new File[] { source });
		return mcreator.getFolderManager().getTexturesList(TextureType.SCREEN).stream()
				.filter(file -> !before.contains(file)).findFirst().orElseThrow();
	}

	private static File createImage(File target, Color color) throws Exception {
		BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(color);
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.dispose();
		ImageIO.write(image, "png", target);
		return target;
	}
}
