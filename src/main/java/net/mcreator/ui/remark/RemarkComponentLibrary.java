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
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.ModElementTypeLoader;
import net.mcreator.element.NamespacedGeneratableElement;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.io.UserFolderManager;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;
import net.mcreator.workspace.references.ReferencesFinder;
import net.mcreator.workspace.resources.Model;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class RemarkComponentLibrary {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final long MAX_ENTRY_BYTES = 256L * 1024L * 1024L;

	private RemarkComponentLibrary() {
	}

	public static File libraryFolder() {
		File folder = UserFolderManager.getFileFromUserFolder("remark-components");
		folder.mkdirs();
		return folder;
	}

	public static List<PackageInfo> listPackages() {
		File[] packages = libraryFolder().listFiles(file -> file.isFile()
				&& file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".remarkcomponent"));
		if (packages == null)
			return List.of();
		List<PackageInfo> result = new ArrayList<>();
		for (File file : packages) {
			try {
				Manifest manifest = readManifest(file);
				result.add(new PackageInfo(file, manifest.name, manifest.sourceGenerator, manifest.createdAt,
						manifest.elements.size(), manifest.resources.size()));
			} catch (Exception ignored) {
				result.add(new PackageInfo(file, file.getName(), "invalid package", "", 0, 0));
			}
		}
		result.sort(Comparator.comparing(PackageInfo::name, String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	public static ExportResult exportPackage(Workspace workspace, List<ModElement> selected, String displayName)
			throws IOException {
		if (selected == null || selected.isEmpty())
			throw new IOException("Select at least one mod element.");
		for (ModElement element : selected) {
			if (element.getType() == ModElementType.CODE)
				throw new IOException("Custom code elements cannot be placed in the reusable library.");
		}

		String name = displayName == null || displayName.isBlank() ? selected.getFirst().getName() : displayName.trim();
		Manifest manifest = new Manifest();
		manifest.formatVersion = 1;
		manifest.name = name;
		manifest.createdAt = LocalDateTime.now().toString();
		manifest.sourceModId = workspace.getWorkspaceSettings().getModID();
		manifest.sourceGenerator = workspace.getGeneratorConfiguration().getGeneratorName();
		manifest.dependencies.addAll(workspace.getWorkspaceSettings().getMCreatorDependenciesRaw());

		Map<String, byte[]> elementJson = new LinkedHashMap<>();
		Set<ModElement> selectedSet = new HashSet<>(selected);
		Set<File> resources = new LinkedHashSet<>();
		for (ModElement element : selected) {
			GeneratableElement generatable = element.getGeneratableElement();
			if (generatable == null)
				throw new IOException("Could not load element " + element.getName());
			String entryPath = "elements/" + safeFileName(element.getName()) + ".json";
			byte[] json = workspace.getModElementManager().generatableElementToJSON(generatable)
					.getBytes(StandardCharsets.UTF_8);
			elementJson.put(entryPath, json);
			manifest.elements.add(new ElementEntry(element.getName(), element.getType().getRegistryName(), entryPath));
			resources.addAll(element.getAssociatedFiles());
			if (generatable instanceof AAAParticle particle)
				collectAAAFiles(workspace, particle, resources);
		}

		for (TextureType type : TextureType.values()) {
			for (File texture : workspace.getFolderManager().getTexturesList(type)) {
				if (intersects(ReferencesFinder.searchTextureUsages(workspace, texture, type), selectedSet))
					resources.add(texture);
			}
		}
		for (Model model : Model.getModels(workspace)) {
			if (!intersects(ReferencesFinder.searchModelUsages(workspace, model), selectedSet))
				continue;
			if (model.getFile() != null)
				resources.add(model.getFile());
			if (model.getFiles() != null)
				resources.addAll(List.of(model.getFiles()));
		}
		for (SoundElement sound : workspace.getSoundElements()) {
			if (!intersects(ReferencesFinder.searchSoundUsages(workspace, sound), selectedSet))
				continue;
			manifest.sounds.add(SoundSnapshot.of(sound));
			File soundsDir = workspace.getFolderManager().getSoundsDir();
			if (soundsDir != null) {
				for (String file : sound.getFiles()) {
					File soundFile = new File(soundsDir, file + ".ogg");
					if (soundFile.isFile())
						resources.add(soundFile);
				}
			}
		}

		Map<String, File> resourceEntries = new LinkedHashMap<>();
		for (File resource : resources) {
			if (resource == null || !resource.isFile() || !workspace.getFolderManager().isFileInWorkspace(resource, true))
				continue;
			String relative = workspace.getFolderManager().getPathInWorkspace(resource).replace(File.separatorChar, '/');
			String zipPath = "resources/" + relative;
			if (resourceEntries.putIfAbsent(zipPath, resource) == null)
				manifest.resources.add(new ResourceEntry(relative, zipPath, sha256(resource), resource.length()));
		}

		String baseName = safeFileName(name);
		File output = new File(libraryFolder(), baseName + ".remarkcomponent");
		if (output.exists())
			output = new File(libraryFolder(), baseName + "-" + FILE_TIME.format(LocalDateTime.now())
					+ ".remarkcomponent");
		File temporary = new File(libraryFolder(), output.getName() + ".tmp");
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temporary)))) {
			for (Map.Entry<String, byte[]> entry : elementJson.entrySet())
				writeEntry(zip, entry.getKey(), entry.getValue());
			for (Map.Entry<String, File> entry : resourceEntries.entrySet())
				writeFileEntry(zip, entry.getKey(), entry.getValue());
			writeEntry(zip, "manifest.json", GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			temporary.delete();
			throw e;
		}
		Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return new ExportResult(output, manifest.elements.size(), manifest.resources.size());
	}

	public static ImportResult importPackage(Workspace workspace, File packageFile) throws IOException {
		Manifest manifest = readManifest(packageFile);
		if (manifest.formatVersion != 1 || manifest.elements.isEmpty())
			throw new IOException("Unsupported or empty component package.");

		Path workspaceRoot = workspace.getWorkspaceFolder().getCanonicalFile().toPath();
		Map<String, String> names = new LinkedHashMap<>();
		Set<String> reserved = new HashSet<>();
		for (ElementEntry entry : manifest.elements) {
			String candidate = entry.name;
			int suffix = 2;
			while (workspace.containsModElement(candidate) || reserved.contains(candidate))
				candidate = entry.name + suffix++;
			names.put(entry.name, candidate);
			reserved.add(candidate);
		}

		try (ZipFile zip = new ZipFile(packageFile)) {
			for (ResourceEntry resource : manifest.resources) {
				Path target = safeWorkspaceTarget(workspaceRoot, resource.relativePath);
				if (Files.isRegularFile(target) && !sha256(target.toFile()).equals(resource.sha256))
					throw new IOException("Resource conflict: " + resource.relativePath);
				if (zip.getEntry(resource.zipPath) == null)
					throw new IOException("Package is missing " + resource.zipPath);
			}
			validateSoundConflicts(workspace, manifest.sounds);

			List<PreparedElement> prepared = new ArrayList<>();
			for (ElementEntry entry : manifest.elements) {
				ZipEntry zipEntry = zip.getEntry(entry.jsonPath);
				if (zipEntry == null)
					throw new IOException("Package is missing " + entry.jsonPath);
				String json = new String(readEntry(zip, zipEntry), StandardCharsets.UTF_8);
				json = rewriteReferences(json, manifest.sourceModId, workspace.getWorkspaceSettings().getModID(), names);
				ModElementType<?> type;
				try {
					type = ModElementTypeLoader.getModElementType(entry.type);
				} catch (Exception e) {
					throw new IOException("Unknown element type: " + entry.type, e);
				}
				String newName = names.get(entry.name);
				ModElement modElement = new ModElement(workspace, newName, type);
				GeneratableElement generatable = workspace.getModElementManager()
						.fromJSONtoGeneratableElementOrNull(json, modElement);
				if (generatable == null)
					throw new IOException("Could not parse element " + entry.name);
				if (generatable instanceof NamespacedGeneratableElement namespaced)
					namespaced.name = RegistryNameFixer.fromCamelCase(newName);
				prepared.add(new PreparedElement(modElement, generatable));
			}

			List<File> copied = new ArrayList<>();
			List<ModElement> added = new ArrayList<>();
			List<SoundElement> addedSounds = new ArrayList<>();
			try {
				for (ResourceEntry resource : manifest.resources) {
					Path target = safeWorkspaceTarget(workspaceRoot, resource.relativePath);
					if (Files.isRegularFile(target))
						continue;
					ZipEntry entry = zip.getEntry(resource.zipPath);
					Files.createDirectories(target.getParent());
					copyVerified(zip, entry, target, resource.sha256);
					copied.add(target.toFile());
				}
				for (SoundSnapshot sound : manifest.sounds) {
					boolean exists = workspace.getSoundElements().stream()
							.anyMatch(current -> current.getName().equals(sound.name));
					if (!exists) {
						SoundElement importedSound = sound.toSoundElement();
						workspace.addSoundElement(importedSound);
						addedSounds.add(importedSound);
					}
				}
				for (PreparedElement element : prepared) {
					workspace.addModElement(element.modElement);
					added.add(element.modElement);
					workspace.getModElementManager().storeModElement(element.generatable);
				}
				Set<String> dependencies = new HashSet<>(workspace.getWorkspaceSettings().getMCreatorDependenciesRaw());
				dependencies.addAll(manifest.dependencies);
				workspace.getWorkspaceSettings().setMCreatorDependencies(dependencies);
				workspace.markDirty();
				return new ImportResult(added.stream().map(ModElement::getName).toList(), copied.size(),
						manifest.sounds.size());
			} catch (Exception e) {
				for (int index = added.size() - 1; index >= 0; index--)
					workspace.removeModElement(added.get(index));
				for (SoundElement sound : addedSounds)
					workspace.removeSoundElement(sound);
				for (File file : copied)
					file.delete();
				throw e instanceof IOException io ? io : new IOException(e);
			}
		}
	}

	public static boolean deletePackage(File packageFile) throws IOException {
		Path root = libraryFolder().getCanonicalFile().toPath();
		Path target = packageFile.getCanonicalFile().toPath();
		if (!target.startsWith(root) || !packageFile.getName().toLowerCase(java.util.Locale.ROOT)
				.endsWith(".remarkcomponent"))
			throw new IOException("Refusing to delete a file outside the component library.");
		return Files.deleteIfExists(target);
	}

	public static PackageInfo installPackage(File source) throws IOException {
		Manifest manifest = readManifest(source);
		if (manifest.formatVersion != 1 || manifest.elements.isEmpty())
			throw new IOException("Unsupported or empty component package.");
		File root = libraryFolder().getCanonicalFile();
		if (source.getCanonicalFile().toPath().startsWith(root.toPath()))
			return new PackageInfo(source, manifest.name, manifest.sourceGenerator, manifest.createdAt,
					manifest.elements.size(), manifest.resources.size());
		String requested = safeFileName(manifest.name) + ".remarkcomponent";
		File target = new File(root, requested);
		if (target.exists())
			target = new File(root, safeFileName(manifest.name) + "-" + FILE_TIME.format(LocalDateTime.now())
					+ ".remarkcomponent");
		Files.copy(source.toPath(), target.toPath());
		return new PackageInfo(target, manifest.name, manifest.sourceGenerator, manifest.createdAt,
				manifest.elements.size(), manifest.resources.size());
	}

	private static void collectAAAFiles(Workspace workspace, AAAParticle particle, Set<File> resources) {
		if (particle.sourceFile != null && !particle.sourceFile.isBlank())
			resources.add(new File(workspace.getWorkspaceFolder(), particle.sourceFile.replace('/', File.separatorChar)));
		if (particle.sourceRoot == null || particle.sourceRoot.isBlank())
			return;
		if (particle.sourceFile != null && particle.sourceFile.replace('\\', '/').startsWith("src/main/resources/")) {
			if (particle.importedAssetFiles != null)
				particle.importedAssetFiles.forEach(path -> resources.add(new File(workspace.getWorkspaceFolder(), path)));
			return;
		}
		File root = new File(workspace.getWorkspaceFolder(), particle.sourceRoot.replace('/', File.separatorChar));
		if (!root.isDirectory())
			return;
		try (var paths = Files.walk(root.toPath())) {
			paths.filter(Files::isRegularFile).map(Path::toFile).forEach(resources::add);
		} catch (IOException ignored) {
		}
	}

	private static boolean intersects(Set<ModElement> usages, Set<ModElement> selected) {
		return usages.stream().anyMatch(selected::contains);
	}

	private static void validateSoundConflicts(Workspace workspace, List<SoundSnapshot> sounds) throws IOException {
		for (SoundSnapshot incoming : sounds) {
			SoundElement existing = workspace.getSoundElements().stream()
					.filter(sound -> sound.getName().equals(incoming.name)).findFirst().orElse(null);
			if (existing != null && (!existing.getFiles().equals(incoming.files)
					|| !Objects.equals(existing.getCategory(), incoming.category)
					|| !Objects.equals(existing.getSubtitle(), incoming.subtitle)))
				throw new IOException("Sound conflict: " + incoming.name);
		}
	}

	private static String rewriteReferences(String json, String oldModId, String newModId,
			Map<String, String> names) {
		String rewritten = json;
		if (oldModId != null && newModId != null && !oldModId.equals(newModId))
			rewritten = rewritten.replace(oldModId + ":", newModId + ":");
		for (Map.Entry<String, String> entry : names.entrySet()) {
			if (entry.getKey().equals(entry.getValue()))
				continue;
			rewritten = rewritten.replace("\"" + entry.getKey() + "\"", "\"" + entry.getValue() + "\"")
					.replace("CUSTOM:" + entry.getKey(), "CUSTOM:" + entry.getValue())
					.replace(">" + entry.getKey() + "<", ">" + entry.getValue() + "<")
					.replace(">" + entry.getKey() + ".", ">" + entry.getValue() + ".")
					.replace(">" + entry.getKey() + ":", ">" + entry.getValue() + ":");
		}
		return rewritten;
	}

	private static Manifest readManifest(File packageFile) throws IOException {
		if (packageFile == null || !packageFile.isFile())
			throw new IOException("Component package does not exist.");
		try (ZipFile zip = new ZipFile(packageFile)) {
			ZipEntry entry = zip.getEntry("manifest.json");
			if (entry == null)
				throw new IOException("Component package has no manifest.");
			Manifest manifest = GSON.fromJson(new String(readEntry(zip, entry), StandardCharsets.UTF_8), Manifest.class);
			if (manifest == null)
				throw new IOException("Invalid component manifest.");
			return manifest;
		}
	}

	private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
		if (entry.getSize() > MAX_ENTRY_BYTES)
			throw new IOException("Component entry is too large: " + entry.getName());
		try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			long total = 0;
			for (int read; (read = input.read(buffer)) >= 0; ) {
				total += read;
				if (total > MAX_ENTRY_BYTES)
					throw new IOException("Component entry is too large: " + entry.getName());
				output.write(buffer, 0, read);
			}
			return output.toByteArray();
		}
	}

	private static Path safeWorkspaceTarget(Path workspaceRoot, String relative) throws IOException {
		if (relative == null || relative.isBlank())
			throw new IOException("Invalid empty resource path.");
		Path target = workspaceRoot.resolve(relative.replace('/', File.separatorChar)).normalize();
		if (!target.startsWith(workspaceRoot) || target.equals(workspaceRoot)
				|| target.startsWith(workspaceRoot.resolve(".remarkstudio")))
			throw new IOException("Unsafe resource path: " + relative);
		return target;
	}

	private static void copyVerified(ZipFile zip, ZipEntry entry, Path target, String expectedHash) throws IOException {
		Path temporary = target.resolveSibling(target.getFileName() + ".remark-import");
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (Exception e) {
			throw new IOException(e);
		}
		long total = 0;
		try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
				BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary.toFile()))) {
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0; ) {
				total += read;
				if (total > MAX_ENTRY_BYTES)
					throw new IOException("Component resource is too large: " + entry.getName());
				digest.update(buffer, 0, read);
				output.write(buffer, 0, read);
			}
		} catch (Exception e) {
			Files.deleteIfExists(temporary);
			throw e;
		}
		String actual = java.util.HexFormat.of().formatHex(digest.digest());
		if (!actual.equals(expectedHash)) {
			Files.deleteIfExists(temporary);
			throw new IOException("Checksum mismatch for " + entry.getName());
		}
		Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void writeEntry(ZipOutputStream zip, String path, byte[] data) throws IOException {
		zip.putNextEntry(new ZipEntry(path));
		zip.write(data);
		zip.closeEntry();
	}

	private static void writeFileEntry(ZipOutputStream zip, String path, File file) throws IOException {
		zip.putNextEntry(new ZipEntry(path));
		try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
			input.transferTo(zip);
		}
		zip.closeEntry();
	}

	private static String safeFileName(String value) {
		String safe = value == null ? "component" : value.trim().replaceAll("[^A-Za-z0-9._-]+", "_")
				.replaceAll("^_+|_+$", "");
		return safe.isBlank() ? "component" : safe;
	}

	private static String sha256(File file) {
		try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0; )
				digest.update(buffer, 0, read);
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (Exception e) {
			return "";
		}
	}

	public record PackageInfo(File file, String name, String generator, String createdAt, int elements, int resources) {
		@Override public String toString() {
			return name + "  [" + elements + " elements, " + resources + " assets]";
		}
	}

	public record ExportResult(File file, int elements, int resources) {
	}

	public record ImportResult(List<String> elements, int resources, int sounds) {
	}

	private record PreparedElement(ModElement modElement, GeneratableElement generatable) {
	}

	private record ElementEntry(String name, String type, String jsonPath) {
	}

	private record ResourceEntry(String relativePath, String zipPath, String sha256, long size) {
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
		String name;
		String createdAt;
		String sourceModId;
		String sourceGenerator;
		List<String> dependencies = new ArrayList<>();
		List<ElementEntry> elements = new ArrayList<>();
		List<ResourceEntry> resources = new ArrayList<>();
		List<SoundSnapshot> sounds = new ArrayList<>();
	}
}
