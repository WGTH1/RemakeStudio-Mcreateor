/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import net.mcreator.element.GeneratableElement;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.io.FileIO;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.ProjectConnection;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public final class RemarkWorkspaceSandbox implements AutoCloseable {
	private static final Set<String> EXCLUDED_ROOT_DIRECTORIES = Set.of(".git", ".gradle", ".idea", ".remark-studio",
			".remarkstudio", "build", "run", "run-proof", "saves", "logs", "crash-reports", "out", "bin");

	private final File root;
	private final Workspace workspace;
	private final GeneratorConfiguration target;
	private boolean keep;

	private RemarkWorkspaceSandbox(File root, Workspace workspace, GeneratorConfiguration target) {
		this.root = root;
		this.workspace = workspace;
		this.target = target;
	}

	public static RemarkWorkspaceSandbox create(Workspace source, GeneratorConfiguration target) throws Exception {
		return createAt(source, target, Files.createTempDirectory("remark-studio-sandbox-"));
	}

	public static RemarkWorkspaceSandbox createAt(Workspace source, GeneratorConfiguration target, Path sandboxRoot) throws Exception {
		source.getFileManager().saveWorkspaceDirectlyAndWait();
		Path sourceRoot = source.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		if (sandboxRoot.toAbsolutePath().normalize().equals(sourceRoot)) throw new IOException("Backup must be separate from source");
		if (sandboxRoot.toAbsolutePath().normalize().startsWith(sourceRoot)
				&& !sandboxRoot.toAbsolutePath().normalize().startsWith(sourceRoot.resolve(".remarkstudio")))
			throw new IOException("Nested backup must use the excluded .remarkstudio directory");
		if (!sandboxRoot.toRealPath().equals(sandboxRoot.toAbsolutePath().normalize()))
			throw new IOException("Backup directory must not be linked");
		copyWorkspace(sourceRoot, sandboxRoot);

		Path workspaceRelative = sourceRoot.relativize(
				source.getFileManager().getWorkspaceFile().toPath().toAbsolutePath().normalize());
		File sandboxWorkspaceFile = sandboxRoot.resolve(workspaceRelative).toFile();
		Workspace sandboxWorkspace = Workspace.readFromFSUnsafe(sandboxWorkspaceFile, target);
		return new RemarkWorkspaceSandbox(sandboxRoot.toFile(), sandboxWorkspace, target);
	}

	public VerificationResult verify(boolean runGradleBuild, @Nullable File artifactFolder,
			Consumer<String> progress) {
		return verify(runGradleBuild, artifactFolder, progress, null);
	}

	public VerificationResult verify(boolean runGradleBuild, @Nullable File artifactFolder,
			Consumer<String> progress, @Nullable org.gradle.tooling.CancellationToken cancellation) {
		Instant started = Instant.now();
		List<String> lines = new ArrayList<>();
		File artifact = null;
		try {
			checkCancellation(cancellation);
			progress.accept("Preparing " + target.getGeneratorName());
			WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
			workspace.getGenerator().runResourceSetupTasks();
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ByteArrayOutputStream errors = new ByteArrayOutputStream();

			if (runGradleBuild) {
				progress.accept("Synchronizing Gradle for " + target.getGeneratorName());
				ProjectConnection connection = GradleUtils.getGradleProjectConnection(workspace);
				String syncTask = target.getGradleTaskFor("sync_task");
				BuildActionExecuter<Void> sync = syncTask == null ?
						GradleUtils.getGradleSyncLauncher(target, connection) :
						GradleUtils.getGradleSyncLauncher(target, connection, syncTask);
				sync.setStandardOutput(output);
				sync.setStandardError(errors);
				if (cancellation != null) sync.withCancellationToken(cancellation);
				sync.run();
				workspace.getGenerator().reloadGradleCaches();
			}

			int generated = 0;
			for (ModElement modElement : workspace.getModElements()) {
				checkCancellation(cancellation);
				GeneratableElement element = modElement.getGeneratableElement();
				if (element == null)
					continue;
				if (target.getDefinitionsProvider().getModElementDefinition(modElement.getType()) == null)
					throw new IllegalStateException("Unsupported element: " + modElement.getName() + " ("
							+ modElement.getType().getReadableName() + ")");
				progress.accept("Generating " + modElement.getName());
				workspace.getGenerator().generateElement(element, runGradleBuild);
				generated++;
			}
			workspace.getGenerator().generateBase(runGradleBuild);
			lines.add("Generated " + generated + " element(s)");

			if (runGradleBuild) {
				ProjectConnection connection = GradleUtils.getGradleProjectConnection(workspace);
				progress.accept("Building " + target.getGeneratorName());
				BuildLauncher build = GradleUtils.getGradleTaskLauncher(target, connection, "build");
				build.setStandardOutput(output);
				build.setStandardError(errors);
				if (cancellation != null) build.withCancellationToken(cancellation);
				build.run();
				String errorText = errors.toString(java.nio.charset.StandardCharsets.UTF_8);
				if (!errorText.isBlank())
					lines.addAll(errorText.lines().filter(line -> line.contains("warning:") || line.contains("ERROR"))
							.limit(40).toList());

				String exportPath = target.getGradleTaskFor("export_file");
				if (exportPath != null) {
					File builtArtifact = new File(workspace.getWorkspaceFolder(), exportPath);
					if (builtArtifact.isFile() && artifactFolder != null) {
						artifactFolder.mkdirs();
						String extension = builtArtifact.getName().contains(".") ?
								builtArtifact.getName().substring(builtArtifact.getName().lastIndexOf('.')) : ".jar";
						artifact = new File(artifactFolder,
								workspace.getWorkspaceSettings().getModID() + "-"
										+ workspace.getWorkspaceSettings().getVersion() + "-"
										+ target.getGeneratorName().replaceAll("[^A-Za-z0-9_.-]", "_") + extension);
						FileIO.copyFile(builtArtifact, artifact);
					}
				}
			}

			long elapsed = Duration.between(started, Instant.now()).toMillis();
			checkCancellation(cancellation);
			lines.add("Completed in " + formatDuration(elapsed));
			return new VerificationResult(true, target.getGeneratorName(), lines, artifact, root, elapsed);
		} catch (Exception e) {
			keep = true;
			long elapsed = Duration.between(started, Instant.now()).toMillis();
			lines.add("Failed: " + readableMessage(e));
			lines.add("Sandbox kept at " + root.getAbsolutePath());
			return new VerificationResult(false, target.getGeneratorName(), lines, artifact, root, elapsed);
		}
	}

	public File getRoot() {
		return root;
	}

	private static void checkCancellation(@Nullable org.gradle.tooling.CancellationToken cancellation) {
		if (cancellation != null && cancellation.isCancellationRequested())
			throw new java.util.concurrent.CancellationException("Verification cancelled");
	}

	public Workspace getWorkspace() { return workspace; }

	public void keepOnClose() {
		keep = true;
	}

	@Override public void close() {
		workspace.close();
		if (!keep)
			FileIO.deleteDir(root);
	}

	private static void copyWorkspace(Path sourceRoot, Path targetRoot) throws IOException {
		Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
			@Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
					throws IOException {
				if (!directory.toRealPath().startsWith(sourceRoot.toRealPath())) throw new IOException("Linked directory outside workspace: " + directory);
				Path relative = sourceRoot.relativize(directory);
				if (relative.getNameCount() > 0 && EXCLUDED_ROOT_DIRECTORIES.contains(
						relative.getName(0).toString().toLowerCase(Locale.ROOT)))
					return FileVisitResult.SKIP_SUBTREE;
				Files.createDirectories(targetRoot.resolve(relative));
				return FileVisitResult.CONTINUE;
			}

			@Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
				if (Files.isSymbolicLink(file) || !file.toRealPath().startsWith(sourceRoot.toRealPath()))
					throw new IOException("Cannot copy a linked file outside the test workspace: " + file);
				Path relative = sourceRoot.relativize(file);
				Files.copy(file, targetRoot.resolve(relative), StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static String readableMessage(Exception exception) {
		Throwable current = exception;
		while (current.getCause() != null)
			current = current.getCause();
		String message = current.getMessage();
		return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
	}

	private static String formatDuration(long milliseconds) {
		long seconds = Math.max(0, milliseconds / 1000);
		return seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
	}

	public record VerificationResult(boolean success, String generator, List<String> lines, @Nullable File artifact,
			File sandboxFolder, long elapsedMillis) {}
}
