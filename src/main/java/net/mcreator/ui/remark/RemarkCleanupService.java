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
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.RemarkWorkspaceDependencyManager;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.references.ReferencesFinder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RemarkCleanupService {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private RemarkCleanupService() {
	}

	public static Plan analyze(MCreator mcreator) {
		List<File> unused = RemarkStudioElementTools.findUnusedAssetFiles(mcreator).stream().filter(File::isFile)
				.distinct().sorted(Comparator.comparing(File::getAbsolutePath)).toList();
		long bytes = unused.stream().mapToLong(File::length).sum();

		Map<String, List<File>> hashes = new LinkedHashMap<>();
		for (File file : collectAssets(mcreator)) {
			String hash = sha256(file);
			if (!hash.isBlank())
				hashes.computeIfAbsent(hash, _ -> new ArrayList<>()).add(file);
		}
		List<List<File>> duplicateGroups = hashes.values().stream().filter(group -> group.size() > 1).toList();
		long duplicateBytes = duplicateGroups.stream()
				.mapToLong(group -> group.getFirst().length() * (group.size() - 1L)).sum();
		boolean staleAaa = !mcreator.getWorkspace().containsType(net.mcreator.element.ModElementType.AAAPARTICLE)
				&& mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw()
				.contains(RemarkWorkspaceDependencyManager.AAA_PARTICLES);
		return new Plan(unused, bytes, duplicateGroups, duplicateBytes, staleAaa);
	}

	public static OperationResult quarantine(MCreator mcreator, Plan plan) throws IOException {
		File workspaceRoot = mcreator.getWorkspaceFolder().getCanonicalFile();
		File trashRoot = new File(workspaceRoot,
				".remarkstudio/trash/" + FOLDER_TIME.format(LocalDateTime.now())).getCanonicalFile();
		Files.createDirectories(trashRoot.toPath());

		Manifest manifest = new Manifest();
		manifest.formatVersion = 1;
		manifest.createdAt = LocalDateTime.now().toString();
		manifest.workspace = workspaceRoot.getAbsolutePath();
		List<MovedFile> moved = new ArrayList<>();

		LinkedHashSet<File> candidates = new LinkedHashSet<>(plan.files());
		for (File file : plan.files()) {
			for (String suffix : List.of(".textures", ".mcmeta")) {
				File sidecar = new File(file.getAbsolutePath() + suffix);
				if (sidecar.isFile())
					candidates.add(sidecar);
			}
		}

		List<SoundElement> unusedSounds = unusedSounds(mcreator, new HashSet<>(plan.files()));
		for (SoundElement sound : unusedSounds)
			manifest.sounds.add(SoundSnapshot.of(sound));

		try {
			for (File candidate : candidates) {
				Path relative = safeRelative(workspaceRoot, candidate);
				if (relative == null || !candidate.isFile())
					continue;
				File target = new File(trashRoot, "files/" + relative.toString()).getCanonicalFile();
				if (!target.toPath().startsWith(trashRoot.toPath()))
					throw new IOException("Unsafe cleanup target: " + candidate);
				Files.createDirectories(target.toPath().getParent());
				String hash = sha256(candidate);
				Files.move(candidate.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
				MovedFile entry = new MovedFile(relative.toString().replace(File.separatorChar, '/'),
						workspaceRoot.toPath().relativize(target.toPath()).toString().replace(File.separatorChar, '/'), hash);
				manifest.files.add(entry);
				moved.add(entry);
			}
		} catch (Exception e) {
			rollbackMoves(workspaceRoot, moved);
			throw e instanceof IOException io ? io : new IOException(e);
		}

		mcreator.getWorkspace().getHistoryManager().importantCheckpoint("remark_cleanup", manifest.files.size());
		for (SoundElement sound : unusedSounds)
			mcreator.getWorkspace().removeSoundElement(sound);
		if (plan.staleAaaDependency() && RemarkWorkspaceDependencyManager.removeAAAParticlesIfUnused(mcreator.getWorkspace()))
			manifest.removedDependencies.add(RemarkWorkspaceDependencyManager.AAA_PARTICLES);

		FileIO.writeStringToFile(GSON.toJson(manifest), new File(trashRoot, "manifest.json"));
		mcreator.getWorkspace().markDirty();
		javax.swing.SwingUtilities.invokeLater(mcreator::reloadWorkspaceTabContents);
		return new OperationResult(manifest.files.size(), 0, trashRoot,
				"Quarantined " + manifest.files.size() + " files and " + manifest.sounds.size() + " sound entries.");
	}

	public static OperationResult restoreLatest(MCreator mcreator) throws IOException {
		File trashBase = new File(mcreator.getWorkspaceFolder(), ".remarkstudio/trash");
		File[] folders = trashBase.listFiles(File::isDirectory);
		if (folders == null)
			return new OperationResult(0, 0, null, "No cleanup snapshot is available.");

		List<File> candidates = java.util.Arrays.stream(folders)
				.filter(folder -> new File(folder, "manifest.json").isFile())
				.sorted(Comparator.comparing(File::getName).reversed()).toList();
		File selected = null;
		Manifest manifest = null;
		for (File folder : candidates) {
			Manifest parsed = GSON.fromJson(FileIO.readFileToString(new File(folder, "manifest.json")), Manifest.class);
			if (parsed != null && (parsed.restoredAt == null || parsed.restoredAt.isBlank())) {
				selected = folder;
				manifest = parsed;
				break;
			}
		}
		if (selected == null || manifest == null)
			return new OperationResult(0, 0, null, "No unrestored cleanup snapshot is available.");

		File workspaceRoot = mcreator.getWorkspaceFolder().getCanonicalFile();
		int restored = 0;
		int skipped = 0;
		for (MovedFile entry : manifest.files) {
			File source = new File(workspaceRoot, entry.trashPath()).getCanonicalFile();
			File target = new File(workspaceRoot, entry.originalPath()).getCanonicalFile();
			if (!target.toPath().startsWith(workspaceRoot.toPath()) || !source.isFile()
					|| !sha256(source).equals(entry.sha256()) || target.exists()) {
				skipped++;
				continue;
			}
			Files.createDirectories(target.toPath().getParent());
			Files.move(source.toPath(), target.toPath());
			restored++;
		}

		for (SoundSnapshot snapshot : manifest.sounds) {
			boolean exists = mcreator.getWorkspace().getSoundElements().stream()
					.anyMatch(sound -> sound.getName().equals(snapshot.name));
			if (!exists)
				mcreator.getWorkspace().addSoundElement(snapshot.toSoundElement());
		}
		if (!manifest.removedDependencies.isEmpty()) {
			Set<String> dependencies = new HashSet<>(mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw());
			dependencies.addAll(manifest.removedDependencies);
			mcreator.getWorkspaceSettings().setMCreatorDependencies(dependencies);
		}

		if (skipped == 0) {
			manifest.restoredAt = LocalDateTime.now().toString();
			FileIO.writeStringToFile(GSON.toJson(manifest), new File(selected, "manifest.json"));
		}
		mcreator.getWorkspace().markDirty();
		javax.swing.SwingUtilities.invokeLater(mcreator::reloadWorkspaceTabContents);
		return new OperationResult(restored, skipped, selected,
				"Restored " + restored + " files; skipped " + skipped + ".");
	}

	private static List<SoundElement> unusedSounds(MCreator mcreator, Set<File> plannedFiles) {
		List<SoundElement> sounds = new ArrayList<>();
		File soundsDir = mcreator.getFolderManager().getSoundsDir();
		for (SoundElement sound : mcreator.getWorkspace().getSoundElements()) {
			if (!ReferencesFinder.searchSoundUsages(mcreator.getWorkspace(), sound).isEmpty())
				continue;
			boolean allPlanned = soundsDir == null || sound.getFiles().stream()
					.map(name -> new File(soundsDir, name + ".ogg"))
					.filter(File::exists).allMatch(plannedFiles::contains);
			if (allPlanned)
				sounds.add(sound);
		}
		return sounds;
	}

	private static Path safeRelative(File workspaceRoot, File file) throws IOException {
		Path root = workspaceRoot.getCanonicalFile().toPath();
		Path path = file.getCanonicalFile().toPath();
		if (!path.startsWith(root))
			return null;
		Path relative = root.relativize(path);
		if (relative.getNameCount() == 0 || relative.startsWith(".remarkstudio"))
			return null;
		return relative;
	}

	private static void rollbackMoves(File workspaceRoot, List<MovedFile> moved) {
		for (int index = moved.size() - 1; index >= 0; index--) {
			MovedFile entry = moved.get(index);
			try {
				File source = new File(workspaceRoot, entry.trashPath());
				File target = new File(workspaceRoot, entry.originalPath());
				Files.createDirectories(target.toPath().getParent());
				if (source.isFile() && !target.exists())
					Files.move(source.toPath(), target.toPath());
			} catch (Exception ignored) {
				// The manifest still records every successful move for manual recovery.
			}
		}
	}

	private static Set<File> collectAssets(MCreator mcreator) {
		Set<File> files = new LinkedHashSet<>();
		for (TextureType type : TextureType.values())
			files.addAll(mcreator.getFolderManager().getTexturesList(type));
		for (File root : List.of(mcreator.getFolderManager().getModelsDir(),
				mcreator.getFolderManager().getAAAParticlesDir(), mcreator.getFolderManager().getSoundsDir())) {
			if (root == null || !root.isDirectory())
				continue;
			try (var paths = Files.walk(root.toPath())) {
				paths.filter(Files::isRegularFile).map(Path::toFile).forEach(files::add);
			} catch (IOException ignored) {
			}
		}
		return files;
	}

	private static String sha256(File file) {
		try (FileInputStream input = new FileInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0; )
				digest.update(buffer, 0, read);
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (Exception e) {
			return "";
		}
	}

	public record Plan(List<File> files, long bytes, List<List<File>> duplicateGroups, long duplicateBytes,
			boolean staleAaaDependency) {
		public String describe() {
			return L10N.t("remark.tools.cleaner.plan", files.size(),
					RemarkStudioQualityAnalyzer.formatBytes(bytes), duplicateGroups.size(),
					RemarkStudioQualityAnalyzer.formatBytes(duplicateBytes),
					L10N.t(staleAaaDependency ? "remark.tools.cleaner.yes" : "remark.tools.cleaner.no"));
		}
	}

	public record OperationResult(int changed, int skipped, File snapshot, String message) {
	}

	private record MovedFile(String originalPath, String trashPath, String sha256) {
	}

	private static final class SoundSnapshot {
		String name;
		List<String> files = new ArrayList<>();
		String category;
		String subtitle;

		static SoundSnapshot of(SoundElement sound) {
			SoundSnapshot snapshot = new SoundSnapshot();
			snapshot.name = sound.getName();
			snapshot.files = new ArrayList<>(sound.getFiles());
			snapshot.category = sound.getCategory();
			snapshot.subtitle = sound.getSubtitle();
			return snapshot;
		}

		SoundElement toSoundElement() {
			return new SoundElement(name, new ArrayList<>(files), category, subtitle);
		}
	}

	private static final class Manifest {
		int formatVersion;
		String createdAt;
		String restoredAt;
		String workspace;
		List<MovedFile> files = new ArrayList<>();
		List<SoundSnapshot> sounds = new ArrayList<>();
		List<String> removedDependencies = new ArrayList<>();
	}
}
