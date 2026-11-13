/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.mcreator.io.FileIO;
import net.mcreator.ui.MCreator;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RemarkAssetImportTransaction {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private final MCreator mcreator;
	private final Manifest manifest = new Manifest();
	private final Set<String> soundsBefore;
	private final Set<String> soundFilesBefore;
	private final Set<String> elementsBefore;
	private File manifestFile;

	private RemarkAssetImportTransaction(MCreator mcreator, File[] sourceFiles) {
		this.mcreator = mcreator;
		manifest.createdAt = LocalDateTime.now().toString();
		manifest.sources = sourceFiles == null ? List.of() : java.util.Arrays.stream(sourceFiles)
				.filter(java.util.Objects::nonNull).map(File::getAbsolutePath).toList();
		soundsBefore = mcreator.getWorkspace().getSoundElements().stream().map(SoundElement::getName)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		soundFilesBefore = soundFiles(mcreator);
		elementsBefore = mcreator.getWorkspace().getModElements().stream().map(ModElement::getName)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}

	public static RemarkAssetImportTransaction begin(MCreator mcreator, File[] sourceFiles) {
		return new RemarkAssetImportTransaction(mcreator, sourceFiles);
	}

	public void recordFile(File file, boolean created) {
		if (!created || file == null || !file.isFile() || !isInsideWorkspace(file))
			return;
		String relative = relativePath(file);
		if (manifest.createdFiles.stream().noneMatch(record -> relative.equals(record.path)))
			manifest.createdFiles.add(new FileRecord(relative, sha256(file)));
	}

	public void recordElement(String elementName) {
		if (elementName != null && !elementName.isBlank() && !manifest.createdElements.contains(elementName))
			manifest.createdElements.add(elementName);
	}

	public File complete() {
		for (SoundElement sound : mcreator.getWorkspace().getSoundElements()) {
			if (!soundsBefore.contains(sound.getName()) && !manifest.createdSounds.contains(sound.getName()))
				manifest.createdSounds.add(sound.getName());
		}
		for (ModElement element : mcreator.getWorkspace().getModElements()) {
			if (!elementsBefore.contains(element.getName()))
				recordElement(element.getName());
		}
		for (String relative : soundFiles(mcreator)) {
			if (!soundFilesBefore.contains(relative))
				recordFile(new File(mcreator.getWorkspaceFolder(), relative.replace("/", File.separator)), true);
		}
		return save();
	}

	public File save() {
		File folder = transactionFolder(mcreator);
		folder.mkdirs();
		if (manifestFile == null)
			manifestFile = new File(folder, "import-" + FILE_TIME.format(LocalDateTime.now()) + ".json");
		FileIO.writeStringToFile(GSON.toJson(manifest), manifestFile);
		FileIO.writeStringToFile(manifestFile.getName(), new File(folder, "latest.txt"));
		return manifestFile;
	}

	public static UndoResult undoLast(MCreator mcreator) {
		File folder = transactionFolder(mcreator);
		File latest = new File(folder, "latest.txt");
		if (!latest.isFile())
			return new UndoResult(false, 0, 0, 0, 0, List.of("No import transaction found"));
		try {
			String name = FileIO.readFileToString(latest).trim();
			File manifestFile = new File(folder, name);
			Manifest manifest = GSON.fromJson(FileIO.readFileToString(manifestFile), Manifest.class);
			if (manifest == null)
				throw new IllegalStateException("Invalid import manifest");

			Workspace workspace = mcreator.getWorkspace();
			List<String> notes = new ArrayList<>();
			int removedElements = 0;
			for (String elementName : safe(manifest.createdElements)) {
				ModElement element = workspace.getModElements().stream().filter(candidate -> elementName.equals(candidate.getName()))
						.findFirst().orElse(null);
				if (element != null) {
					workspace.removeModElement(element);
					removedElements++;
				}
			}

			int removedSounds = 0;
			for (String soundName : safe(manifest.createdSounds)) {
				SoundElement sound = workspace.getSoundElements().stream().filter(candidate -> soundName.equals(candidate.getName()))
						.findFirst().orElse(null);
				if (sound != null) {
					if (hasModifiedImportedSound(mcreator, manifest, sound)) {
						notes.add("Kept modified sound: " + soundName);
						continue;
					}
					workspace.removeSoundElement(sound);
					workspace.removeLocalizationEntryByKey("subtitles." + soundName);
					removedSounds++;
				}
			}

			int removedFiles = 0;
			int skippedFiles = 0;
			for (FileRecord record : safeFiles(manifest.createdFiles)) {
				File file = new File(mcreator.getWorkspaceFolder(), record.path.replace("/", File.separator));
				if (!isInsideWorkspace(mcreator, file) || !file.isFile())
					continue;
				if (!record.sha256.equals(sha256(file))) {
					skippedFiles++;
					notes.add("Kept modified file: " + record.path);
					continue;
				}
				if (file.delete()) {
					removedFiles++;
					deleteEmptyParents(file.getParentFile(), mcreator.getWorkspaceFolder());
				}
			}

			workspace.markDirty();
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			mcreator.reloadWorkspaceTabContents();
			File undone = new File(folder, manifestFile.getName().replace(".json", ".undone.json"));
			Files.move(manifestFile.toPath(), undone.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			latest.delete();
			return new UndoResult(true, removedFiles, skippedFiles, removedSounds, removedElements, notes);
		} catch (Exception e) {
			return new UndoResult(false, 0, 0, 0, 0, List.of(e.getMessage()));
		}
	}

	private boolean isInsideWorkspace(File file) {
		return isInsideWorkspace(mcreator, file);
	}

	private static boolean isInsideWorkspace(MCreator mcreator, File file) {
		try {
			return file.getCanonicalFile().toPath().startsWith(mcreator.getWorkspaceFolder().getCanonicalFile().toPath());
		} catch (Exception e) {
			return false;
		}
	}

	private String relativePath(File file) {
		return mcreator.getWorkspaceFolder().toPath().toAbsolutePath().normalize()
				.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace(File.separator, "/");
	}

	private static void deleteEmptyParents(@Nullable File folder, File workspaceRoot) {
		try {
			File root = workspaceRoot.getCanonicalFile();
			for (File current = folder; current != null && !current.getCanonicalFile().equals(root); current = current.getParentFile()) {
				File[] children = current.listFiles();
				if (children != null && children.length == 0)
					current.delete();
				else
					break;
			}
		} catch (Exception ignored) {
		}
	}

	private static String sha256(File file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (var stream = Files.newInputStream(file.toPath())) {
				byte[] buffer = new byte[8192];
				for (int read; (read = stream.read(buffer)) != -1; )
					digest.update(buffer, 0, read);
			}
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (Exception e) {
			return "";
		}
	}

	private static File transactionFolder(MCreator mcreator) {
		return new File(mcreator.getWorkspaceFolder(), ".remark-assets/transactions");
	}

	private static Set<String> soundFiles(MCreator mcreator) {
		Set<String> files = new LinkedHashSet<>();
		File root = mcreator.getFolderManager().getSoundsDir();
		if (root == null || !root.isDirectory())
			return files;
		try (var paths = Files.walk(root.toPath())) {
			Path workspaceRoot = mcreator.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
			paths.filter(Files::isRegularFile).forEach(path -> files.add(
					workspaceRoot.relativize(path.toAbsolutePath().normalize()).toString().replace(File.separator, "/")));
		} catch (Exception ignored) {
		}
		return files;
	}

	private static boolean hasModifiedImportedSound(MCreator mcreator, Manifest manifest, SoundElement sound) {
		File soundsRoot = mcreator.getFolderManager().getSoundsDir();
		if (soundsRoot == null)
			return false;
		for (String fileName : sound.getFiles()) {
			File file = new File(soundsRoot, fileName + ".ogg");
			if (!file.isFile())
				continue;
			String relative = mcreator.getWorkspaceFolder().toPath().toAbsolutePath().normalize()
					.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace(File.separator, "/");
			FileRecord record = safeFiles(manifest.createdFiles).stream()
					.filter(candidate -> relative.equals(candidate.path)).findFirst().orElse(null);
			if (record != null && !record.sha256.equals(sha256(file)))
				return true;
		}
		return false;
	}

	private static List<String> safe(@Nullable List<String> values) {
		return values == null ? List.of() : values;
	}

	private static List<FileRecord> safeFiles(@Nullable List<FileRecord> values) {
		return values == null ? List.of() : values;
	}

	public record UndoResult(boolean success, int removedFiles, int skippedModifiedFiles, int removedSounds,
			int removedElements, List<String> notes) {}

	private static final class Manifest {
		String createdAt;
		List<String> sources = new ArrayList<>();
		List<FileRecord> createdFiles = new ArrayList<>();
		List<String> createdSounds = new ArrayList<>();
		List<String> createdElements = new ArrayList<>();
	}

	private static final class FileRecord {
		String path;
		String sha256;

		private FileRecord(String path, String sha256) {
			this.path = path;
			this.sha256 = sha256;
		}
	}
}
