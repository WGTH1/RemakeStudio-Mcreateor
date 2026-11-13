/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.ItemUseAnimation;
import net.mcreator.element.parts.MItemBlock;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.Fashion;
import net.mcreator.element.types.Item;
import net.mcreator.generator.GeneratorStats;
import net.mcreator.io.FileIO;
import net.mcreator.minecraft.MCItem;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.dialogs.SoundElementDialog;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.util.DesktopUtils;
import net.mcreator.util.FilenameUtilsPatched;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.references.ReferencesFinder;
import net.mcreator.workspace.resources.Model;
import net.mcreator.workspace.resources.TexturedModel;

import javax.imageio.ImageIO;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class RemarkStudioElementTools {

	private static final Deque<String> RECENT_IMPORTS = new ArrayDeque<>();

	private RemarkStudioElementTools() {
	}

	public static void installAssetDropTarget(JComponent target, MCreator mcreator,
			Supplier<GeneratableElement> elementSupplier) {
		if (target.getTransferHandler() != null)
			return;

		target.setTransferHandler(new TransferHandler() {
			@Override public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override public boolean importData(TransferSupport support) {
				if (!canImport(support))
					return false;

				try {
					@SuppressWarnings("unchecked") List<File> files = (List<File>) support.getTransferable()
							.getTransferData(DataFlavor.javaFileListFlavor);
					GeneratableElement element = null;
					try {
						element = elementSupplier.get();
					} catch (Exception ignored) {
						// The editor can be partially filled while dropping assets; import still works.
					}
					importAssets(mcreator, element, files.toArray(File[]::new));
					return true;
				} catch (Exception e) {
					JOptionPane.showMessageDialog(mcreator, L10N.t("remark.tools.import.failed", e.getMessage()),
							L10N.t("remark.tools.import.title"), JOptionPane.ERROR_MESSAGE);
					return false;
				}
			}
		});
		target.setToolTipText(L10N.t("remark.tools.import.drop"));
	}

	public static void chooseAndImportAssets(MCreator mcreator, @Nullable GeneratableElement element) {
		File[] files = FileDialogs.getMultiOpenDialog(mcreator,
				new String[] { ".zip", ".ogg", ".png", ".jpg", ".jpeg", ".json", ".bbmodel", ".obj", ".mtl",
						".java", ".efkefc", ".wav" });
		if (files != null)
			importAssets(mcreator, element, files);
	}

	public static void batchCreateFromAssets(MCreator mcreator, @Nullable GeneratableElement element) {
		File[] files = FileDialogs.getMultiOpenDialog(mcreator,
				new String[] { ".zip", ".png", ".jpg", ".jpeg", ".json", ".bbmodel", ".obj", ".mtl", ".java" });
		if (files == null)
			return;
		if (!confirmImportPlan(mcreator, files))
			return;

		ImportResult result = importAssetsInternal(mcreator, element, files);
		int created = createDraftItemsFromImportedModels(mcreator, result);
		if (created > 0) {
			mcreator.getWorkspace().markDirty();
			mcreator.reloadWorkspaceTabContents();
			result.lines.add(L10N.t("remark.tools.batch.created_items", created));
			result.transaction.save();
		}
		showImportReport(mcreator, result);
	}

	public static void importAssets(MCreator mcreator, @Nullable GeneratableElement element, File[] selectedFiles) {
		if (selectedFiles.length > 0 && Arrays.stream(selectedFiles).allMatch(file -> !file.isDirectory()
				&& RemarkAAAParticleImporter.isEffect(file.toPath()))) {
			RemarkAAAParticleImportDialog.show(mcreator, selectedFiles);
			return;
		}
		if (confirmImportPlan(mcreator, selectedFiles))
			showImportReport(mcreator, importAssetsInternal(mcreator, element, selectedFiles));
	}

	private static ImportResult importAssetsInternal(MCreator mcreator, @Nullable GeneratableElement element,
			File[] selectedFiles) {
		ImportResult result = new ImportResult();
		result.transaction = RemarkAssetImportTransaction.begin(mcreator, selectedFiles);
		List<File> files = flattenFiles(selectedFiles);
		if (files.isEmpty()) {
			result.lines.add(L10N.t("remark.tools.import.none"));
			result.transaction.complete();
			return result;
		}
		files = expandZipFiles(mcreator, files, result);

		int sounds = SoundElementDialog.importSoundsBatch(mcreator,
				files.stream().filter(file -> extensionOf(file).equals("ogg")).toArray(File[]::new));
		result.sounds = sounds;
		if (sounds > 0)
			result.lines.add(L10N.t("remark.tools.import.detail.sounds", sounds));

		for (File file : files) {
			String extension = extensionOf(file);
			if (extension.equals("ogg"))
				continue;

			File targetRoot = targetFolderFor(mcreator, extension);
			if (targetRoot == null)
				targetRoot = new File(getAssetInbox(mcreator), extension.isBlank() ? "other" : extension);

			if (isModelAsset(extension)) {
				result.models++;
				if (extension.equals("json")) {
					importJsonModel(mcreator, file, targetRoot, result);
				} else {
					RemarkAssetFiles.CopyResult copy = RemarkAssetFiles.copyCompact(file, targetRoot, file.getName());
					rememberImport(copy.file());
					result.importedModels.add(copy.file());
					addCopyLine(result, file.getName(), copy);
				}
			} else if (extension.equals("efkefc")) {
				RemarkAssetFiles.CopyResult copy = RemarkAssetFiles.copyCompact(file, targetRoot, file.getName());
				rememberImport(copy.file());
				result.aaa++;
				addCopyLine(result, file.getName(), copy);
			} else if (isImageAsset(extension)) {
				importScreenTexture(mcreator, file, result);
			} else {
				RemarkAssetFiles.CopyResult copy = RemarkAssetFiles.copyCompact(file, targetRoot, file.getName());
				rememberImport(copy.file());
				result.inbox++;
				addCopyLine(result, file.getName(), copy);
			}
		}

		if (element instanceof AAAParticle aaaParticle) {
			try {
				aaaParticle.finalizeModElementGeneration();
			} catch (Exception ignored) {
				// The normal save path will report concrete AAA asset problems if they remain.
			}
		}

		mcreator.getWorkspace().markDirty();
		mcreator.reloadWorkspaceTabContents();
		result.transaction.complete();
		return result;
	}

	private static boolean confirmImportPlan(MCreator mcreator, File[] selectedFiles) {
		List<File> files = flattenFiles(selectedFiles);
		if (files.isEmpty())
			return false;
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.tools.import.preview_summary", files.size()));
		lines.add("");
		for (File file : files.stream().limit(150).toList()) {
			String extension = extensionOf(file);
			File target = importPreviewTarget(mcreator, extension);
			File duplicate = target != null && target.isDirectory() ?
					RemarkAssetFiles.findDuplicateByContent(file, target) : null;
			String state = duplicate == null ? L10N.t("remark.tools.import.preview_copy") :
					L10N.t("remark.tools.import.preview_reuse", duplicate.getName());
			lines.add(file.getName() + "  ->  " + (target != null ? target.getAbsolutePath() : getAssetInbox(mcreator))
					+ "  [" + state + "]");
		}
		if (files.size() > 150)
			lines.add(L10N.t("remark.tools.import.preview_more", files.size() - 150));

		JTextArea area = new JTextArea(String.join("\n", lines), 20, 92);
		area.setEditable(false);
		area.setCaretPosition(0);
		return JOptionPane.showConfirmDialog(mcreator, new JScrollPane(area),
				L10N.t("remark.tools.import.preview_title"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
	}

	@Nullable private static File importPreviewTarget(MCreator mcreator, String extension) {
		if (extension.equals("ogg"))
			return mcreator.getFolderManager().getSoundsDir();
		if (isImageAsset(extension))
			return mcreator.getFolderManager().getTexturesFolder(TextureType.SCREEN);
		if (extension.equals("zip"))
			return new File(getAssetInbox(mcreator), "zip");
		File target = targetFolderFor(mcreator, extension);
		return target != null ? target : new File(getAssetInbox(mcreator), extension.isBlank() ? "other" : extension);
	}

	public static void undoLastImport(MCreator mcreator) {
		if (JOptionPane.showConfirmDialog(mcreator, L10N.t("remark.tools.import.undo_confirm"),
				L10N.t("remark.tools.import.undo_title"), JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
			return;
		RemarkAssetImportTransaction.UndoResult result = RemarkAssetImportTransaction.undoLast(mcreator);
		List<String> lines = new ArrayList<>();
		lines.add(result.success() ? L10N.t("remark.tools.import.undo_done", result.removedFiles(),
				result.skippedModifiedFiles(), result.removedSounds(), result.removedElements()) :
				L10N.t("remark.tools.import.undo_failed"));
		lines.addAll(result.notes());
		showTextReport(mcreator, L10N.t("remark.tools.import.undo_title"), lines);
	}

	public static void showRecentImports(MCreator mcreator) {
		if (RECENT_IMPORTS.isEmpty()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.tools.recent.empty"),
					L10N.t("remark.tools.recent.title"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		JTextArea area = new JTextArea(String.join("\n", RECENT_IMPORTS), 12, 72);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);

		Object[] options = { L10N.t("remark.tools.recent.open_inbox"), L10N.t("remark.tools.copy"),
				UIManager.getString("OptionPane.okButtonText") };
		int result = JOptionPane.showOptionDialog(mcreator, new JScrollPane(area),
				L10N.t("remark.tools.recent.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
				null, options, options[2]);
		if (result == JOptionPane.YES_OPTION)
			DesktopUtils.openSafe(getAssetInbox(mcreator));
		else if (result == JOptionPane.NO_OPTION)
			copyToClipboard(String.join("\n", RECENT_IMPORTS));
	}

	public static void showAssetDoctor(MCreator mcreator) {
		List<String> lines = new ArrayList<>();
		File soundsDir = mcreator.getFolderManager().getSoundsDir();
		for (SoundElement sound : mcreator.getWorkspace().getSoundElements()) {
			for (String soundFile : sound.getFiles()) {
				if (soundsDir == null || !new File(soundsDir, soundFile + ".ogg").isFile())
					lines.add(L10N.t("remark.tools.doctor.missing_sound", sound.getName(), soundFile));
			}
		}

		File modelsDir = mcreator.getFolderManager().getModelsDir();
		File[] models = modelsDir.listFiles();
		for (File model : models != null ? models : new File[0]) {
			if (!model.isFile() || !model.getName().endsWith(".json"))
				continue;

			if (!new File(model.getAbsolutePath() + ".textures").isFile())
				lines.add(L10N.t("remark.tools.doctor.missing_texture_map", model.getName()));

			try {
				JsonElement root = JsonParser.parseString(FileIO.readFileToString(model));
				Set<String> references = new LinkedHashSet<>();
				collectTextureReferences(root, references);
				for (String reference : references) {
					TextureReference texture = TextureReference.from(reference);
					if (texture == null)
						continue;
					File textureFile = texture.toWorkspaceFile(mcreator);
					if (textureFile == null || !textureFile.isFile())
						lines.add(L10N.t("remark.tools.doctor.missing_texture", model.getName(), reference));
				}
			} catch (Exception e) {
				lines.add(L10N.t("remark.tools.doctor.bad_json", model.getName()));
			}
		}

		for (ModElement modElement : mcreator.getWorkspace().getModElements()) {
			if (modElement.getGeneratableElement() instanceof AAAParticle aaaParticle && aaaParticle.sourceFile != null
					&& !aaaParticle.sourceFile.isBlank()) {
				File source = new File(mcreator.getWorkspaceFolder(), aaaParticle.sourceFile.replace("/", File.separator));
				if (!source.isFile())
					lines.add(L10N.t("remark.tools.doctor.missing_aaa", modElement.getName(),
							aaaParticle.sourceFile));
			}
		}

		if (lines.isEmpty())
			lines.add(L10N.t("remark.tools.doctor.clean"));
		showTextReport(mcreator, L10N.t("remark.tools.doctor.title"), lines);
	}

	public static void cleanUnusedAssets(MCreator mcreator) {
		RemarkCleanupService.Plan plan = RemarkCleanupService.analyze(mcreator);
		if (plan.files().isEmpty() && !plan.staleAaaDependency()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.tools.cleaner.none"),
					L10N.t("remark.tools.cleaner.title"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		List<String> lines = new ArrayList<>();
		lines.add(plan.describe());
		lines.add("");
		lines.addAll(plan.files().stream().map(File::getAbsolutePath).toList());
		JTextArea area = new JTextArea(String.join("\n", lines), 16, 76);
		area.setEditable(false);
		int result = JOptionPane.showConfirmDialog(mcreator, new JScrollPane(area),
				L10N.t("remark.tools.cleaner.quarantine_confirm", plan.files().size()), JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE);
		if (result != JOptionPane.YES_OPTION)
			return;

		try {
			RemarkCleanupService.OperationResult operation = RemarkCleanupService.quarantine(mcreator, plan);
			JOptionPane.showMessageDialog(mcreator,
					L10N.t("remark.tools.cleaner.quarantine_done", operation.changed(),
							operation.snapshot().getAbsolutePath()), L10N.t("remark.tools.cleaner.title"),
					JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.tools.cleaner.failed", e.getMessage()),
					L10N.t("remark.tools.cleaner.title"), JOptionPane.ERROR_MESSAGE);
		}
	}

	public static void restoreLastCleanup(MCreator mcreator) {
		try {
			RemarkCleanupService.OperationResult operation = RemarkCleanupService.restoreLatest(mcreator);
			String message = operation.snapshot() == null ? L10N.t("remark.tools.cleaner.restore_none") :
					L10N.t("remark.tools.cleaner.restore_done", operation.changed(), operation.skipped(),
							operation.snapshot().getAbsolutePath());
			JOptionPane.showMessageDialog(mcreator, message, L10N.t("remark.tools.cleaner.restore"),
					operation.skipped() == 0 ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.tools.cleaner.failed", e.getMessage()),
					L10N.t("remark.tools.cleaner.restore"), JOptionPane.ERROR_MESSAGE);
		}
	}

	public static void showPreviewAndTest(MCreator mcreator, GeneratableElement element) {
		List<String> commands = getTestCommands(mcreator, element);
		String commandText = commands.isEmpty() ? L10N.t("remark.tools.test.none") : String.join("\n", commands);

		JTextArea commandArea = new JTextArea(commandText, Math.min(Math.max(commands.size(), 3), 10), 56);
		commandArea.setEditable(false);
		commandArea.setLineWrap(true);
		commandArea.setWrapStyleWord(true);

		JPanel panel = new JPanel(new BorderLayout(8, 8));
		JComponent livePreview = RemarkElementLivePreview.create(element);
		if (livePreview != null) {
			panel.add(livePreview, BorderLayout.CENTER);
			JPanel commandPanel = new JPanel(new BorderLayout(4, 4));
			commandPanel.add(new JLabel(L10N.t("remark.tools.live_preview.runtime_note")), BorderLayout.NORTH);
			commandPanel.add(new JScrollPane(commandArea), BorderLayout.CENTER);
			panel.add(commandPanel, BorderLayout.SOUTH);
		} else {
			ImageIcon icon = element.getModElement().getElementIcon();
			if (icon != null)
				panel.add(new JLabel(icon), BorderLayout.WEST);
			panel.add(new JScrollPane(commandArea), BorderLayout.CENTER);
		}

		Object[] options = livePreview == null ?
				new Object[] { L10N.t("remark.tools.copy"), UIManager.getString("OptionPane.okButtonText") } :
				new Object[] { L10N.t("remark.tools.live_preview.test_game"), L10N.t("remark.tools.copy"),
						UIManager.getString("OptionPane.closeButtonText") };
		int result = JOptionPane.showOptionDialog(mcreator, panel, L10N.t("remark.tools.preview.title"),
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		if (livePreview == null && result == 0 && !commands.isEmpty())
			copyToClipboard(commandText);
		else if (livePreview != null && result == 0) {
			if (!commands.isEmpty())
				copyToClipboard(commandText);
			mcreator.getActionRegistry().runClient.doAction();
		} else if (livePreview != null && result == 1 && !commands.isEmpty()) {
			copyToClipboard(commandText);
		}
	}

	public static void oneClickTest(MCreator mcreator, GeneratableElement element) {
		List<String> commands = getTestCommands(mcreator, element);
		String commandText = commands.isEmpty() ? L10N.t("remark.tools.test.none") : String.join("\n", commands);

		JTextArea commandArea = new JTextArea(commandText, Math.min(Math.max(commands.size(), 3), 10), 58);
		commandArea.setEditable(false);
		commandArea.setLineWrap(true);
		commandArea.setWrapStyleWord(true);

		Object[] options = { L10N.t("remark.tools.oneclick.run"), L10N.t("remark.tools.copy"),
				UIManager.getString("OptionPane.cancelButtonText") };
		int result = JOptionPane.showOptionDialog(mcreator, new JScrollPane(commandArea),
				L10N.t("remark.tools.oneclick.title"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
				null, options, options[0]);

		if (result == JOptionPane.YES_OPTION) {
			if (!commands.isEmpty())
				copyToClipboard(commandText);
			mcreator.getActionRegistry().runClient.doAction();
		} else if (result == JOptionPane.NO_OPTION && !commands.isEmpty()) {
			copyToClipboard(commandText);
		}
	}

	public static void showDependencyHelper(MCreator mcreator, GeneratableElement element) {
		if (element instanceof AAAParticle) {
			enableDependency(mcreator, "aaa_particles");
			return;
		}

		JOptionPane.showMessageDialog(mcreator, L10N.t("remark.tools.deps.none"),
				L10N.t("remark.tools.deps.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	public static void showElementWizard(MCreator mcreator, GeneratableElement element) {
		String type = element.getModElement().getTypeString();
		String message = switch (type) {
			case "aaaparticle" -> L10N.t("remark.tools.wizard.aaa");
			case "fashion" -> L10N.t("remark.tools.wizard.fashion");
			case "sound" -> L10N.t("remark.tools.wizard.sound");
			default -> L10N.t("remark.tools.wizard.generic");
		};
		JOptionPane.showMessageDialog(mcreator, message, L10N.t("remark.tools.wizard.title"),
				JOptionPane.INFORMATION_MESSAGE);
	}

	public static void showCompatibility(MCreator mcreator, GeneratableElement element) {
		String generator = mcreator.getGenerator().getGeneratorName();
		String type = element.getModElement().getTypeString();
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.tools.compat.current", generator));
		lines.add(L10N.t("remark.tools.compat.current_support", currentCoverage(mcreator, type)));

		switch (type) {
			case "aaaparticle" -> {
				lines.add(L10N.t("remark.tools.compat.supported",
						"NeoForge 26.1.2, NeoForge 1.21.1, Forge 1.20.1, Fabric 26.1.2, Fabric 1.20.1"));
				lines.add(L10N.t("remark.tools.compat.api", "aaa_particles"));
			}
			case "fashion" -> {
				lines.add(L10N.t("remark.tools.compat.supported",
						"NeoForge 26.1.2, NeoForge 1.21.1, Forge 1.20.1, Fabric 26.1.2, Fabric 1.20.1"));
				Fashion fashion = (Fashion) element;
				if (fashion.singleItemSet)
					lines.add(L10N.t("remark.tools.compat.fashion_single"));
			}
			default -> lines.add(L10N.t("remark.tools.compat.generic"));
		}

		JOptionPane.showMessageDialog(mcreator, "<html>" + String.join("<br>", lines),
				L10N.t("remark.tools.compat.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	public static void fixAssets(MCreator mcreator, GeneratableElement element) {
		List<String> completed = new ArrayList<>();
		ensureDirectory(getAssetInbox(mcreator), completed);
		ensureDirectory(mcreator.getFolderManager().getModelsDir(), completed);
		ensureDirectory(mcreator.getFolderManager().getAAAParticlesDir(), completed);
		File soundsDir = mcreator.getFolderManager().getSoundsDir();
		if (soundsDir != null)
			ensureDirectory(soundsDir, completed);

		if (element instanceof AAAParticle aaaParticle) {
			try {
				mcreator.getGenerator().runResourceSetupTasks();
				aaaParticle.finalizeModElementGeneration();
				completed.add(L10N.t("remark.tools.assets.aaa_done"));
			} catch (Exception e) {
				JOptionPane.showMessageDialog(mcreator, L10N.t("remark.tools.assets.failed", e.getMessage()),
						L10N.t("remark.tools.assets.title"), JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		if (completed.isEmpty())
			completed.add(L10N.t("remark.tools.assets.none"));

		JOptionPane.showMessageDialog(mcreator, "<html>" + String.join("<br>", completed),
				L10N.t("remark.tools.assets.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	public static List<String> explainGradleLog(String log) {
		String lower = log.toLowerCase(Locale.ROOT);
		List<String> explanations = new ArrayList<>();

		if (lower.contains("cannot find a java installation") || lower.contains("languageversion=25")) {
			explanations.add(L10N.t("remark.error.java25"));
		}
		if (lower.contains("could not find or load main class net.mcreator.launcher")) {
			explanations.add(L10N.t("remark.error.launcher_class"));
		}
		if (lower.contains("is abstract; cannot be instantiated")) {
			explanations.add(L10N.t("remark.error.abstract_item"));
		}
		if (lower.contains("package ") && lower.contains("item does not exist")) {
			explanations.add(L10N.t("remark.error.missing_item_class"));
		}
		if (lower.contains("cannot find symbol") && (lower.contains("_helmet") || lower.contains("_chestplate")
				|| lower.contains("_leggings") || lower.contains("_boots"))) {
			explanations.add(L10N.t("remark.error.old_generated_names"));
		}
		if (lower.contains("armor texture files need to follow") || lower.contains("_layer_1.png")
				&& lower.contains("_layer_2.png")) {
			explanations.add(L10N.t("remark.error.armor_layer_name"));
		}
		if (lower.contains("aaaparticle") && (lower.contains("cannot find symbol")
				|| lower.contains("package ") && lower.contains("does not exist"))) {
			explanations.add(L10N.t("remark.error.aaa_compile"));
		}
		if (lower.lines().anyMatch(line -> (line.contains("effek") || line.contains(".efkefc")
				|| line.contains(".efkmodel")) && (line.contains("failed to load") || line.contains("not found")
				|| line.contains("missing") || line.contains("cannot load") || line.contains("could not load")))) {
			explanations.add(L10N.t("remark.error.aaa_assets"));
		}

		return explanations.stream().distinct().toList();
	}

	private static int createDraftItemsFromImportedModels(MCreator mcreator, ImportResult result) {
		int created = 0;
		for (File modelFile : result.importedModels) {
			if (!modelFile.getName().endsWith(".json"))
				continue;

			String baseName = RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(modelFile.getName()));
			String elementName = uniqueModElementName(mcreator, toElementName(baseName));
			ModElement modElement = new ModElement(mcreator.getWorkspace(), elementName, ModElementType.ITEM);
			Item item = new Item(modElement);
			item.name = readableName(baseName);
			item.rarity = "COMMON";
			item.stackSize = 64;
			item.enchantability = 0;
			item.useDuration = 0;
			item.toolType = 1;
			item.damageCount = 0;
			item.recipeRemainder = new MItemBlock(mcreator.getWorkspace(), "Blocks.AIR");
			item.eatResultItem = new MItemBlock(mcreator.getWorkspace(), "Blocks.AIR");
			item.attackSpeed = 1.6;
			item.inventorySize = 9;
			item.inventoryStackSize = 99;
			item.saturation = 0.3;
			item.animation = new ItemUseAnimation(mcreator.getWorkspace(), "none");
			item.musicDiscDescription = "";
			item.musicDiscLengthInTicks = 100;
			item.customModelName = baseName + ":default";
			item.renderType = Item.encodeModelType(Model.Type.JSON);
			item.texture = new TextureHolder(mcreator.getWorkspace(), firstTextureForModel(modelFile));
			item.guiTexture = new TextureHolder(mcreator.getWorkspace(), firstTextureForModel(modelFile));

			mcreator.getWorkspace().addModElement(modElement);
			mcreator.getModElementManager().storeModElement(item);
			result.transaction.recordElement(modElement.getName());
			created++;
		}
		return created;
	}

	public static List<String> repairWorkspace(MCreator mcreator) {
		List<String> completed = new ArrayList<>();
		boolean hasAAA = mcreator.getWorkspace().getModElements().stream()
				.anyMatch(element -> element.getGeneratableElement() instanceof AAAParticle);
		if (hasAAA && ModAPIManager.getModAPIForNameAndGenerator("aaa_particles",
				mcreator.getGenerator().getGeneratorName()) != null
				&& mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw().add("aaa_particles")) {
			completed.add(L10N.t("remark.center.repair.aaa_dependency"));
		}

		for (File directory : List.of(getAssetInbox(mcreator), mcreator.getFolderManager().getModelsDir(),
				mcreator.getFolderManager().getAAAParticlesDir())) {
			if (!directory.isDirectory() && directory.mkdirs())
				completed.add(L10N.t("remark.center.repair.directory", directory.getAbsolutePath()));
		}
		File soundsDir = mcreator.getFolderManager().getSoundsDir();
		if (soundsDir != null && !soundsDir.isDirectory() && soundsDir.mkdirs())
			completed.add(L10N.t("remark.center.repair.directory", soundsDir.getAbsolutePath()));

		for (ModElement modElement : mcreator.getWorkspace().getModElements()) {
			if (modElement.getGeneratableElement() instanceof AAAParticle aaaParticle) {
				try {
					aaaParticle.finalizeModElementGeneration();
					mcreator.getModElementManager().storeModElement(aaaParticle);
					completed.add(L10N.t("remark.center.repair.aaa", modElement.getName()));
				} catch (Exception e) {
					completed.add(L10N.t("remark.center.repair.failed", modElement.getName(), e.getMessage()));
				}
			}
		}

		mcreator.getWorkspace().markDirty();
		mcreator.reloadWorkspaceTabContents();
		if (completed.isEmpty())
			completed.add(L10N.t("remark.center.repair.none"));
		return completed;
	}

	public static List<String> buildAssetDependencyReport(MCreator mcreator) {
		List<String> lines = new ArrayList<>();
		int used = 0;
		int unused = 0;
		for (TextureType type : TextureType.values()) {
			for (File texture : mcreator.getFolderManager().getTexturesList(type)) {
				Set<ModElement> usages = ReferencesFinder.searchTextureUsages(mcreator.getWorkspace(), texture, type);
				if (usages.isEmpty())
					unused++;
				else
					used++;
				lines.add(L10N.t("remark.center.assets.texture", texture.getName(), type.name(),
						usages.isEmpty() ? L10N.t("remark.center.assets.unused") : usages.stream()
								.map(ModElement::getName).sorted().reduce((left, right) -> left + ", " + right).orElse("")));
			}
		}

		for (Model model : Model.getModels(mcreator.getWorkspace())) {
			Set<ModElement> usages = ReferencesFinder.searchModelUsages(mcreator.getWorkspace(), model);
			if (usages.isEmpty())
				unused++;
			else
				used++;
			lines.add(L10N.t("remark.center.assets.model", model.getReadableName(),
					usages.isEmpty() ? L10N.t("remark.center.assets.unused") : usages.stream()
							.map(ModElement::getName).sorted().reduce((left, right) -> left + ", " + right).orElse("")));
		}

		for (SoundElement sound : mcreator.getWorkspace().getSoundElements()) {
			Set<ModElement> usages = ReferencesFinder.searchSoundUsages(mcreator.getWorkspace(), sound);
			if (usages.isEmpty())
				unused++;
			else
				used++;
			lines.add(L10N.t("remark.center.assets.sound", sound.getName(),
					usages.isEmpty() ? L10N.t("remark.center.assets.unused") : usages.stream()
							.map(ModElement::getName).sorted().reduce((left, right) -> left + ", " + right).orElse("")));
		}

		lines.add(0, L10N.t("remark.center.assets.summary", used, unused));
		return lines;
	}

	public static List<File> findUnusedAssetFiles(MCreator mcreator) {
		List<File> unused = new ArrayList<>();
		for (TextureType type : TextureType.values()) {
			for (File texture : mcreator.getFolderManager().getTexturesList(type)) {
				if (ReferencesFinder.searchTextureUsages(mcreator.getWorkspace(), texture, type).isEmpty())
					unused.add(texture);
			}
		}

		for (SoundElement sound : mcreator.getWorkspace().getSoundElements()) {
			if (!ReferencesFinder.searchSoundUsages(mcreator.getWorkspace(), sound).isEmpty())
				continue;
			File soundsDir = mcreator.getFolderManager().getSoundsDir();
			if (soundsDir != null) {
				for (String file : sound.getFiles()) {
					File soundFile = new File(soundsDir, file + ".ogg");
					if (soundFile.isFile())
						unused.add(soundFile);
				}
			}
		}

		for (Model model : Model.getModels(mcreator.getWorkspace())) {
			if (!ReferencesFinder.searchModelUsages(mcreator.getWorkspace(), model).isEmpty() || model.getFile() == null)
				continue;
			unused.add(model.getFile());
			for (File file : model.getFiles() != null ? model.getFiles() : new File[0]) {
				if (!unused.contains(file))
					unused.add(file);
			}
		}
		return unused;
	}

	private static void showImportReport(MCreator mcreator, ImportResult result) {
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.tools.import.done", result.total(), result.sounds, result.models, result.aaa,
				result.textures, result.inbox, getAssetInbox(mcreator).getAbsolutePath()));
		lines.addAll(result.lines);
		if (!result.missingAssets.isEmpty()) {
			lines.add("");
			lines.add(L10N.t("remark.tools.import.missing_heading"));
			lines.addAll(result.missingAssets);
		}
		showTextReport(mcreator, L10N.t("remark.tools.import.report_title"), lines);
	}

	private static void addCopyLine(ImportResult result, String sourceName, RemarkAssetFiles.CopyResult copy) {
		result.transaction.recordFile(copy.file(), copy.copied());
		result.lines.add(copy.reused() ?
				L10N.t("remark.tools.import.detail.reused", sourceName, copy.file().getAbsolutePath()) :
				L10N.t("remark.tools.import.detail.file", sourceName, copy.file().getAbsolutePath()));
	}

	private static void addTextureCopyLine(ImportResult result, String sourceName, RemarkAssetFiles.CopyResult copy) {
		result.transaction.recordFile(copy.file(), copy.copied());
		result.lines.add(copy.reused() ?
				L10N.t("remark.tools.import.detail.texture_reused", sourceName, copy.file().getAbsolutePath()) :
				L10N.t("remark.tools.import.detail.texture", sourceName, copy.file().getAbsolutePath()));
	}

	private static void showTextReport(MCreator mcreator, String title, List<String> lines) {
		JTextArea area = new JTextArea(String.join("\n", lines), 18, 82);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);

		Object[] options = { L10N.t("remark.tools.copy"), UIManager.getString("OptionPane.okButtonText") };
		int result = JOptionPane.showOptionDialog(mcreator, new JScrollPane(area), title, JOptionPane.YES_NO_OPTION,
				JOptionPane.PLAIN_MESSAGE, null, options, options[1]);
		if (result == JOptionPane.YES_OPTION)
			copyToClipboard(String.join("\n", lines));
	}

	private static String firstTextureForModel(File modelFile) {
		File textureMap = new File(modelFile.getAbsolutePath() + ".textures");
		if (!textureMap.isFile())
			return RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(modelFile.getName()));
		try {
			JsonObject root = JsonParser.parseString(FileIO.readFileToString(textureMap)).getAsJsonObject();
			JsonObject mappings = root.getAsJsonObject("mappings");
			if (mappings != null) {
				for (Map.Entry<String, JsonElement> mapping : mappings.entrySet()) {
					JsonObject map = mapping.getValue().getAsJsonObject().getAsJsonObject("map");
					if (map != null) {
						for (Map.Entry<String, JsonElement> texture : map.entrySet())
							return texture.getValue().getAsString();
					}
				}
			}
		} catch (Exception ignored) {
		}
		return RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(modelFile.getName()));
	}

	private static String uniqueModElementName(MCreator mcreator, String baseName) {
		String candidate = baseName;
		int index = 2;
		while (mcreator.getWorkspace().containsModElement(candidate))
			candidate = baseName + index++;
		return candidate;
	}

	private static String toElementName(String registryName) {
		StringBuilder builder = new StringBuilder();
		for (String part : registryName.split("[_\\-\\s]+")) {
			if (part.isBlank())
				continue;
			builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
			if (part.length() > 1)
				builder.append(part.substring(1));
		}
		return builder.isEmpty() ? "ImportedItem" : builder.toString();
	}

	private static String readableName(String registryName) {
		String text = registryName.replace('_', ' ').replace('-', ' ');
		return text.isBlank() ? "Imported item" : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
	}

	private static void enableDependency(MCreator mcreator, String dependency) {
		Set<String> dependencies = mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw();
		boolean added = dependencies.add(dependency);
		if (added)
			mcreator.getWorkspace().markDirty();

		JOptionPane.showMessageDialog(mcreator,
				added ? L10N.t("remark.tools.deps.added", dependency) :
						L10N.t("remark.tools.deps.already", dependency),
				L10N.t("remark.tools.deps.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	private static String currentCoverage(MCreator mcreator, String type) {
		for (Map.Entry<ModElementType<?>, GeneratorStats.CoverageStatus> entry : mcreator.getGeneratorStats()
				.getModElementTypeCoverageInfo().entrySet()) {
			if (entry.getKey().getRegistryName().equals(type))
				return entry.getValue().name();
		}
		return "UNKNOWN";
	}

	private static void ensureDirectory(File directory, List<String> completed) {
		if (!directory.isDirectory() && directory.mkdirs())
			completed.add(L10N.t("remark.tools.assets.created_dir", directory.getAbsolutePath()));
	}

	private static File targetFolderFor(MCreator mcreator, String extension) {
		if (isModelAsset(extension))
			return mcreator.getFolderManager().getModelsDir();
		if (extension.equals("efkefc"))
			return mcreator.getFolderManager().getAAAParticlesDir();
		return null;
	}

	private static boolean isImageAsset(String extension) {
		return List.of("png", "jpg", "jpeg").contains(extension);
	}

	private static boolean isModelAsset(String extension) {
		return List.of("bbmodel", "json", "java", "obj", "mtl").contains(extension);
	}

	private static void importScreenTexture(MCreator mcreator, File imageFile, ImportResult result) {
		File textureRoot = mcreator.getFolderManager().getTexturesFolder(TextureType.SCREEN);
		if (textureRoot == null)
			textureRoot = new File(getAssetInbox(mcreator), "textures/screens");

		String textureName = RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(imageFile.getName()));
		if (textureName.isBlank())
			textureName = "screen_texture";

		RemarkAssetFiles.CopyResult copy;
		if (extensionOf(imageFile).equals("png")) {
			copy = RemarkAssetFiles.copyCompact(imageFile, textureRoot, textureName + ".png");
		} else {
			try {
				BufferedImage image = ImageIO.read(imageFile);
				if (image == null) {
					importImageFallbackToInbox(mcreator, imageFile, result, false);
					return;
				}
				File target = RemarkAssetFiles.uniqueTargetFile(textureRoot, textureName + ".png");
				FileIO.writeImageToPNGFile(image, target);
				copy = new RemarkAssetFiles.CopyResult(target, true, false);
			} catch (Exception e) {
				importImageFallbackToInbox(mcreator, imageFile, result, true);
				return;
			}
		}

		result.textures++;
		rememberImport(copy.file());
		addTextureCopyLine(result, imageFile.getName(), copy);
	}

	private static void importImageFallbackToInbox(MCreator mcreator, File imageFile, ImportResult result,
			boolean reportFailure) {
		RemarkAssetFiles.CopyResult inboxCopy = RemarkAssetFiles.copyCompact(imageFile,
				new File(getAssetInbox(mcreator), extensionOf(imageFile)), imageFile.getName());
		rememberImport(inboxCopy.file());
		result.inbox++;
		addCopyLine(result, imageFile.getName(), inboxCopy);
		if (reportFailure)
			result.missingAssets.add(L10N.t("remark.tools.import.detail.image_failed", imageFile.getName()));
	}

	private static void importJsonModel(MCreator mcreator, File jsonFile, File targetRoot, ImportResult result) {
		try {
			JsonElement root = JsonParser.parseString(FileIO.readFileToString(jsonFile));
			Set<String> textureReferences = new LinkedHashSet<>();
			collectTextureReferences(root, textureReferences);

			Map<String, TexturedModel.TextureMapping> mappings = new LinkedHashMap<>();
			Map<String, TextureHolder> textureMap = new LinkedHashMap<>();
			for (String reference : textureReferences) {
				TextureReference texture = TextureReference.from(reference);
				if (texture == null) {
					result.lines.add(L10N.t("remark.tools.import.detail.texture_skipped", reference));
					continue;
				}

				File source = findTextureSource(jsonFile.getParentFile(), texture);
				if (source == null) {
					result.missingAssets.add(L10N.t("remark.tools.import.detail.texture_missing", reference,
							jsonFile.getName()));
					continue;
				}

				File textureRoot = mcreator.getFolderManager().getTexturesFolder(texture.type());
				if (textureRoot == null)
					textureRoot = new File(getAssetInbox(mcreator), "textures");

				RemarkAssetFiles.CopyResult textureCopy = RemarkAssetFiles.copyCompact(source, textureRoot,
						texture.fileName() + ".png");
				String textureName = FilenameUtilsPatched.removeExtension(textureCopy.file().getName());
				if (textureCopy.copied()) {
					result.textures++;
				}
				rememberImport(textureCopy.file());
				addTextureCopyLine(result, source.getName(), textureCopy);
				textureMap.put(texture.mappingKey(), new TextureHolder(mcreator.getWorkspace(), textureName));
				rewriteTextureReference(root, reference, texture.rewrittenReference(mcreator, textureName));
			}

			RemarkAssetFiles.CopyResult modelCopy = RemarkAssetFiles.writeTextCompact(root.toString(), targetRoot,
					jsonFile.getName());
			rememberImport(modelCopy.file());
			result.importedModels.add(modelCopy.file());
			addCopyLine(result, jsonFile.getName(), modelCopy);

			if (!textureMap.isEmpty()) {
				mappings.put("default", new TexturedModel.TextureMapping("default", textureMap));
				File textureMapping = new File(modelCopy.file().getAbsolutePath() + ".textures");
				boolean createdMapping = !textureMapping.isFile();
				if (modelCopy.copied() || createdMapping)
					FileIO.writeStringToFile(TexturedModel.getJSONForTextureMapping(mappings), textureMapping);
				result.transaction.recordFile(textureMapping, createdMapping);
				result.lines.add(L10N.t("remark.tools.import.detail.rewrite", modelCopy.file().getName()));
			}
		} catch (Exception ignored) {
			RemarkAssetFiles.CopyResult copy = RemarkAssetFiles.copyCompact(jsonFile, targetRoot, jsonFile.getName());
			rememberImport(copy.file());
			result.importedModels.add(copy.file());
			addCopyLine(result, jsonFile.getName(), copy);
			result.missingAssets.add(L10N.t("remark.tools.import.detail.json_failed", jsonFile.getName()));
		}
	}

	private static List<File> expandZipFiles(MCreator mcreator, List<File> files, ImportResult result) {
		List<File> expanded = new ArrayList<>();
		for (File file : files) {
			if (!extensionOf(file).equals("zip")) {
				expanded.add(file);
				continue;
			}

			File targetRoot = new File(getAssetInbox(mcreator), "zip/" + RegistryNameFixer.fix(
					FilenameUtilsPatched.removeExtension(file.getName())) + "_" + System.currentTimeMillis());
			if (!targetRoot.isDirectory())
				targetRoot.mkdirs();
			File zipTargetRoot = targetRoot;

			try (ZipFile zipFile = new ZipFile(file)) {
				zipFile.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> {
					String extension = extensionOf(new File(entry.getName()));
					if (!List.of("ogg", "png", "jpg", "jpeg", "json", "bbmodel", "obj", "mtl", "java", "efkefc", "wav")
							.contains(extension))
						return;

					File target = safeZipTarget(zipTargetRoot, entry);
					if (target == null)
						return;

					try {
						File parent = target.getParentFile();
						if (parent != null && !parent.isDirectory())
							parent.mkdirs();
						try (var inputStream = zipFile.getInputStream(entry)) {
							Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
						}
						result.transaction.recordFile(target, true);
						expanded.add(target);
					} catch (IOException e) {
						result.missingAssets.add(L10N.t("remark.tools.import.detail.zip_failed", entry.getName()));
					}
				});
				result.lines.add(
						L10N.t("remark.tools.import.detail.zip", file.getName(), zipTargetRoot.getAbsolutePath()));
			} catch (IOException e) {
				result.missingAssets.add(L10N.t("remark.tools.import.detail.zip_failed", file.getName()));
			}
		}
		return expanded;
	}

	@Nullable private static File safeZipTarget(File root, ZipEntry entry) {
		try {
			File target = new File(root, entry.getName().replace('\\', '/')).getCanonicalFile();
			File canonicalRoot = root.getCanonicalFile();
			if (!target.toPath().startsWith(canonicalRoot.toPath()))
				return null;
			return target;
		} catch (IOException e) {
			return null;
		}
	}

	private static void collectTextureReferences(JsonElement element, Set<String> references) {
		if (element == null || element.isJsonNull())
			return;

		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				String key = entry.getKey().toLowerCase(Locale.ROOT);
				if (key.equals("texture") || key.equals("textures"))
					collectTextureReferenceValues(entry.getValue(), references);
				else
					collectTextureReferences(entry.getValue(), references);
			}
		} else if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(child -> collectTextureReferences(child, references));
		}
	}

	private static void collectTextureReferenceValues(JsonElement element, Set<String> references) {
		if (element == null || element.isJsonNull())
			return;

		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			String reference = element.getAsString().trim();
			if (!reference.isBlank() && !reference.startsWith("#"))
				references.add(reference);
		} else if (element.isJsonObject()) {
			element.getAsJsonObject().entrySet()
					.forEach(entry -> collectTextureReferenceValues(entry.getValue(), references));
		} else if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(child -> collectTextureReferenceValues(child, references));
		}
	}

	private static void rewriteTextureReference(JsonElement element, String oldReference, String newReference) {
		if (element == null || element.isJsonNull())
			return;

		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()
						&& entry.getValue().getAsString().equals(oldReference)) {
					entry.setValue(new JsonPrimitive(newReference));
				} else {
					rewriteTextureReference(entry.getValue(), oldReference, newReference);
				}
			}
		} else if (element.isJsonArray()) {
			element.getAsJsonArray().forEach(child -> rewriteTextureReference(child, oldReference, newReference));
		}
	}

	@Nullable private static File findTextureSource(File jsonFolder, TextureReference texture) {
		List<File> roots = new ArrayList<>();
		for (File root = jsonFolder; root != null && roots.size() < 6; root = root.getParentFile())
			roots.add(root);

		for (File root : roots) {
			for (File candidate : texture.candidates(root)) {
				if (candidate.isFile())
					return candidate;
			}
		}

		for (File root : roots) {
			File found = findFileByName(root, texture.fileName() + ".png", 5);
			if (found != null)
				return found;
		}

		return null;
	}

	@Nullable private static File findFileByName(File root, String fileName, int depthLeft) {
		if (root == null || !root.isDirectory() || depthLeft < 0)
			return null;

		File[] children = root.listFiles();
		if (children == null)
			return null;

		for (File child : children) {
			if (child.isFile() && child.getName().equalsIgnoreCase(fileName))
				return child;
		}

		for (File child : children) {
			if (child.isDirectory()) {
				File found = findFileByName(child, fileName, depthLeft - 1);
				if (found != null)
					return found;
			}
		}

		return null;
	}

	private static File getAssetInbox(MCreator mcreator) {
		return new File(mcreator.getWorkspaceFolder(), ".remark-assets");
	}

	private static List<File> flattenFiles(File[] selectedFiles) {
		List<File> files = new ArrayList<>();
		if (selectedFiles == null)
			return files;

		Arrays.stream(selectedFiles).filter(file -> file != null && file.exists()).forEach(file -> {
			if (file.isDirectory())
				flattenDirectory(file, files);
			else
				files.add(file);
		});
		return files;
	}

	private static void flattenDirectory(File directory, List<File> files) {
		File[] children = directory.listFiles();
		if (children == null)
			return;

		for (File child : children) {
			if (child.isDirectory())
				flattenDirectory(child, files);
			else
				files.add(child);
		}
	}

	private static String extensionOf(File file) {
		String name = file.getName();
		int dot = name.lastIndexOf('.');
		return dot == -1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static void rememberImport(File target) {
		RECENT_IMPORTS.remove(target.getAbsolutePath());
		RECENT_IMPORTS.addFirst(target.getAbsolutePath());
		while (RECENT_IMPORTS.size() > 40)
			RECENT_IMPORTS.removeLast();
	}

	private static List<String> getTestCommands(MCreator mcreator, GeneratableElement element) {
		ModElement modElement = element.getModElement();
		String modid = mcreator.getWorkspaceSettings().getModID();
		String registryName = modElement.getRegistryName();
		List<String> commands = new ArrayList<>();

		if (element instanceof AAAParticle) {
			commands.add("/aaaparticle " + registryName + " on @p");
			commands.add("/particle " + modid + ":" + registryName + " ~ ~1 ~");
			return commands;
		}

		switch (modElement.getTypeString()) {
			case "livingentity" -> commands.add("/summon " + modid + ":" + registryName + " ~ ~ ~");
			case "particle" -> commands.add("/particle " + modid + ":" + registryName + " ~ ~1 ~");
		}

		for (MCItem item : modElement.getMCItems()) {
			String itemId = customMCItemToItemId(modElement, modid, item.getName());
			if (itemId != null)
				commands.add("/give @p " + itemId);
		}

		return commands;
	}

	private static String customMCItemToItemId(ModElement modElement, String modid, String mcItemName) {
		if (!mcItemName.startsWith("CUSTOM:"))
			return null;

		String suffix = "";
		int dotIndex = mcItemName.lastIndexOf('.');
		if (dotIndex != -1)
			suffix = switch (mcItemName.substring(dotIndex + 1)) {
				case "helmet" -> "_helmet";
				case "body" -> "_chestplate";
				case "legs" -> "_leggings";
				case "boots" -> "_boots";
				default -> "_" + mcItemName.substring(dotIndex + 1);
			};

		return modid + ":" + modElement.getRegistryName() + suffix;
	}

	private static void copyToClipboard(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
	}

	private static final class ImportResult {
		private final List<String> lines = new ArrayList<>();
		private final List<String> missingAssets = new ArrayList<>();
		private final List<File> importedModels = new ArrayList<>();
		private RemarkAssetImportTransaction transaction;
		private int sounds;
		private int models;
		private int aaa;
		private int textures;
		private int inbox;

		private int total() {
			return sounds + models + aaa + textures + inbox;
		}
	}

	private record TextureReference(@Nullable String namespace, String resourcePath, TextureType type,
									String fileName) {
		@Nullable private static TextureReference from(String rawReference) {
			String reference = rawReference.trim().replace('\\', '/');
			if (reference.isBlank() || reference.startsWith("#"))
				return null;

			if (reference.endsWith(".png"))
				reference = FilenameUtilsPatched.removeExtension(reference);

			String namespace = null;
			int namespaceSeparator = reference.indexOf(':');
			if (namespaceSeparator != -1) {
				namespace = reference.substring(0, namespaceSeparator);
				reference = reference.substring(namespaceSeparator + 1);
			}

			if ("minecraft".equals(namespace))
				return null;

			if (reference.startsWith("textures/"))
				reference = reference.substring("textures/".length());

			TextureType type = TextureType.OTHER;
			String texturePath = reference;
			if (texturePath.startsWith("item/")) {
				type = TextureType.ITEM;
				texturePath = texturePath.substring("item/".length());
			} else if (texturePath.startsWith("block/")) {
				type = TextureType.BLOCK;
				texturePath = texturePath.substring("block/".length());
			} else if (texturePath.startsWith("entity/")) {
				type = TextureType.ENTITY;
				texturePath = texturePath.substring("entity/".length());
			} else if (texturePath.startsWith("entities/")) {
				type = TextureType.ENTITY;
				texturePath = texturePath.substring("entities/".length());
			} else if (texturePath.startsWith("particle/")) {
				type = TextureType.PARTICLE;
				texturePath = texturePath.substring("particle/".length());
			} else if (texturePath.startsWith("effect/")) {
				type = TextureType.EFFECT;
				texturePath = texturePath.substring("effect/".length());
			} else if (texturePath.startsWith("mob_effect/")) {
				type = TextureType.EFFECT;
				texturePath = texturePath.substring("mob_effect/".length());
			} else if (texturePath.startsWith("screen/")) {
				type = TextureType.SCREEN;
				texturePath = texturePath.substring("screen/".length());
			} else if (texturePath.startsWith("gui/")) {
				type = TextureType.SCREEN;
				texturePath = texturePath.substring("gui/".length());
			}

			String fileName = texturePath.contains("/") ? texturePath.substring(texturePath.lastIndexOf('/') + 1) :
					texturePath;
			fileName = RegistryNameFixer.fix(fileName);
			if (fileName.isBlank())
				return null;

			return new TextureReference(namespace, reference, type, fileName);
		}

		private List<File> candidates(File root) {
			List<File> candidates = new ArrayList<>();
			candidates.add(new File(root, resourcePath + ".png"));
			candidates.add(new File(root, "textures/" + resourcePath + ".png"));
			candidates.add(new File(root, fileName + ".png"));
			candidates.add(new File(root, "textures/" + fileName + ".png"));
			if (namespace != null)
				candidates.add(new File(root, "assets/" + namespace + "/textures/" + resourcePath + ".png"));
			return candidates;
		}

		private String mappingKey() {
			String path = resourcePath;
			int slash = path.lastIndexOf('/');
			return slash == -1 ? path : path.substring(slash + 1);
		}

		private String rewrittenReference(MCreator mcreator, String actualFileName) {
			String prefix = switch (type) {
				case ITEM -> "item/";
				case BLOCK -> "block/";
				case ENTITY -> "entities/";
				case EFFECT -> "mob_effect/";
				case PARTICLE -> "particle/";
				case SCREEN -> "screens/";
				case ARMOR -> "models/armor/";
				case OTHER -> "";
			};
			return mcreator.getWorkspaceSettings().getModID() + ":" + prefix + actualFileName;
		}

		@Nullable private File toWorkspaceFile(MCreator mcreator) {
			File root = mcreator.getFolderManager().getTexturesFolder(type);
			return root == null ? null : new File(root, fileName + ".png");
		}
	}
}
