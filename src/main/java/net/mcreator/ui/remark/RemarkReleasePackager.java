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
import net.mcreator.workspace.Workspace;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class RemarkReleasePackager {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Set<String> SOURCE_EXCLUDES = Set.of(".git", ".gradle", ".idea", "build", "run", "runs");

	private RemarkReleasePackager() {
	}

	public static PackageResult packageRelease(Workspace workspace, RemarkBuildMatrixService.MatrixResult matrix,
			boolean includeSource) throws IOException {
		if (matrix == null)
			throw new IOException("Run the build matrix before creating a delivery package.");
		if (!matrix.success())
			throw new IOException("Release package was blocked because the build matrix has failures.");

		List<File> artifacts = matrix.results().stream().filter(RemarkWorkspaceSandbox.VerificationResult::success)
				.map(RemarkWorkspaceSandbox.VerificationResult::artifact).filter(java.util.Objects::nonNull)
				.filter(File::isFile).toList();
		if (artifacts.isEmpty())
			throw new IOException("The build matrix did not produce any mod JARs.");

		File releaseRoot = new File(workspace.getWorkspaceFolder(), ".remarkstudio/release");
		File delivery = new File(releaseRoot, "delivery-" + FOLDER_TIME.format(LocalDateTime.now()));
		File mods = new File(delivery, "mods");
		Files.createDirectories(mods.toPath());

		Manifest manifest = new Manifest();
		manifest.formatVersion = 1;
		manifest.createdAt = LocalDateTime.now().toString();
		manifest.modId = workspace.getWorkspaceSettings().getModID();
		manifest.name = workspace.getWorkspaceSettings().getModName();
		manifest.version = workspace.getWorkspaceSettings().getVersion();
		manifest.author = workspace.getWorkspaceSettings().getAuthor();
		manifest.license = workspace.getWorkspaceSettings().getLicense();
		manifest.requiredMods.addAll(workspace.getWorkspaceSettings().getRequiredMods());
		manifest.dependencies.addAll(workspace.getWorkspaceSettings().getDependencies());

		Set<String> usedNames = new HashSet<>();
		for (int index = 0; index < matrix.results().size(); index++) {
			RemarkWorkspaceSandbox.VerificationResult verification = matrix.results().get(index);
			manifest.targets.add(new Target(verification.generator(), verification.success(), verification.lines()));
			File source = verification.artifact();
			if (source == null || !source.isFile())
				continue;
			String name = uniqueFileName(source.getName(), usedNames, verification.generator());
			File target = new File(mods, name);
			Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			manifest.artifacts.add(new Artifact("mods/" + name, target.length(), sha256(target),
					verification.generator()));
		}

		writeText(new File(delivery, "README.md"), buildReadme(workspace, manifest));
		File changelogSource = new File(releaseRoot, "CHANGELOG.md");
		if (changelogSource.isFile())
			Files.copy(changelogSource.toPath(), new File(delivery, "CHANGELOG.md").toPath(),
					StandardCopyOption.REPLACE_EXISTING);
		else
			writeText(new File(delivery, "CHANGELOG.md"), "# Changelog\n\n## " + manifest.version
					+ "\n\n- Initial packaged release.\n");
		writeText(new File(delivery, "THIRD_PARTY_NOTICES.md"), buildNotices(workspace));
		File matrixReport = new File(matrix.outputFolder(), "matrix-report.json");
		if (matrixReport.isFile())
			Files.copy(matrixReport.toPath(), new File(delivery, "matrix-report.json").toPath(),
					StandardCopyOption.REPLACE_EXISTING);
		copyExtras(new File(releaseRoot, "extras"), new File(delivery, "extras"));

		if (includeSource) {
			File sourceZip = new File(delivery, "source/workspace.zip");
			Files.createDirectories(sourceZip.toPath().getParent());
			zipWorkspace(workspace.getWorkspaceFolder().toPath(), sourceZip.toPath());
			manifest.sourceIncluded = true;
		}

		writeText(new File(delivery, "release-manifest.json"), GSON.toJson(manifest));
		writeChecksums(delivery);
		File archive = new File(releaseRoot, delivery.getName() + ".zip");
		zipDirectory(delivery.toPath(), archive.toPath());
		return new PackageResult(delivery, archive, manifest.artifacts.size(), includeSource);
	}

	private static String buildReadme(Workspace workspace, Manifest manifest) {
		StringBuilder builder = new StringBuilder();
		builder.append("# ").append(manifest.name).append("\n\n");
		String description = workspace.getWorkspaceSettings().getDescription();
		if (description != null && !description.isBlank())
			builder.append(description).append("\n\n");
		builder.append("Version: `").append(manifest.version).append("`  \n");
		builder.append("Author: ").append(manifest.author).append("  \n");
		builder.append("License: ").append(manifest.license).append("\n\n");
		builder.append("## Installation\n\nPlace the JAR matching your Minecraft version and loader from `mods/` into the Minecraft `mods` folder.\n\n");
		if (!manifest.requiredMods.isEmpty())
			builder.append("Required mods: ").append(String.join(", ", manifest.requiredMods)).append("\n\n");
		builder.append("## Included Builds\n\n");
		for (Artifact artifact : manifest.artifacts)
			builder.append("- `").append(artifact.path).append("` - ").append(artifact.generator).append("\n");
		return builder.toString();
	}

	private static String buildNotices(Workspace workspace) {
		StringBuilder builder = new StringBuilder("# Third-Party Notices\n\n");
		builder.append("Review and complete the license details for every dependency before distribution.\n\n");
		builder.append("## MCreator APIs\n\n");
		if (workspace.getWorkspaceSettings().getMCreatorDependenciesRaw().isEmpty())
			builder.append("None declared.\n");
		else
			workspace.getWorkspaceSettings().getMCreatorDependenciesRaw().stream().sorted()
					.forEach(dependency -> builder.append("- ").append(dependency).append("\n"));
		builder.append("\n## Runtime Dependencies\n\n");
		List<String> runtime = new ArrayList<>(workspace.getWorkspaceSettings().getDependencies());
		runtime.addAll(workspace.getWorkspaceSettings().getRequiredMods());
		if (runtime.isEmpty())
			builder.append("None declared.\n");
		else
			runtime.stream().distinct().sorted().forEach(dependency -> builder.append("- ").append(dependency).append("\n"));
		return builder.toString();
	}

	private static void copyExtras(File source, File target) throws IOException {
		if (!source.isDirectory())
			return;
		Path sourceRoot = source.toPath();
		try (var paths = Files.walk(sourceRoot)) {
			for (Path path : paths.sorted().toList()) {
				if (Files.isSymbolicLink(path))
					continue;
				Path relative = sourceRoot.relativize(path);
				Path destination = target.toPath().resolve(relative);
				if (Files.isDirectory(path))
					Files.createDirectories(destination);
				else if (Files.isRegularFile(path)) {
					Files.createDirectories(destination.getParent());
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void zipWorkspace(Path workspaceRoot, Path output) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output.toFile())));
				var paths = Files.walk(workspaceRoot)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				if (Files.isSymbolicLink(path))
					continue;
				Path relative = workspaceRoot.relativize(path);
				if (isSourceExcluded(relative))
					continue;
				writeZipFile(zip, relative.toString().replace(File.separatorChar, '/'), path.toFile());
			}
		}
	}

	private static boolean isSourceExcluded(Path relative) {
		if (relative.getNameCount() == 0)
			return true;
		String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
		if (SOURCE_EXCLUDES.contains(first))
			return true;
		return first.equals(".remarkstudio") && relative.getNameCount() > 1
				&& relative.getName(1).toString().equalsIgnoreCase("release");
	}

	private static void writeChecksums(File delivery) throws IOException {
		List<String> lines = new ArrayList<>();
		Path root = delivery.toPath();
		try (var paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
				if (path.getFileName().toString().equals("checksums.sha256"))
					continue;
				lines.add(sha256(path.toFile()) + "  " + root.relativize(path).toString()
						.replace(File.separatorChar, '/'));
			}
		}
		writeText(new File(delivery, "checksums.sha256"), String.join("\n", lines) + "\n");
	}

	private static void zipDirectory(Path source, Path output) throws IOException {
		try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output.toFile())));
				var paths = Files.walk(source)) {
			for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
				writeZipFile(zip, source.relativize(path).toString().replace(File.separatorChar, '/'), path.toFile());
		}
	}

	private static void writeZipFile(ZipOutputStream zip, String name, File file) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
			input.transferTo(zip);
		}
		zip.closeEntry();
	}

	private static String uniqueFileName(String requested, Set<String> used, String generator) {
		String candidate = requested;
		if (used.add(candidate.toLowerCase(Locale.ROOT)))
			return candidate;
		String suffix = generator.replaceAll("[^A-Za-z0-9._-]+", "_");
		int dot = requested.lastIndexOf('.');
		candidate = dot > 0 ? requested.substring(0, dot) + "-" + suffix + requested.substring(dot) :
				requested + "-" + suffix;
		int index = 2;
		while (!used.add(candidate.toLowerCase(Locale.ROOT)))
			candidate = requested + "-" + suffix + "-" + index++;
		return candidate;
	}

	private static void writeText(File file, String text) {
		FileIO.writeStringToFile(text, file);
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

	public record PackageResult(File folder, File archive, int artifacts, boolean sourceIncluded) {
	}

	private record Artifact(String path, long size, String sha256, String generator) {
	}

	private record Target(String generator, boolean passed, List<String> notes) {
	}

	private static final class Manifest {
		int formatVersion;
		String createdAt;
		String modId;
		String name;
		String version;
		String author;
		String license;
		boolean sourceIncluded;
		List<String> requiredMods = new ArrayList<>();
		List<String> dependencies = new ArrayList<>();
		List<Artifact> artifacts = new ArrayList<>();
		List<Target> targets = new ArrayList<>();
	}
}
