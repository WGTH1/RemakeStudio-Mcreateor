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
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.io.FileIO;
import net.mcreator.workspace.Workspace;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class RemarkBuildMatrixService {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private RemarkBuildMatrixService() {
	}

	public static MatrixResult run(Workspace workspace, List<GeneratorConfiguration> targets, boolean fullBuild,
			File releaseRoot, Consumer<String> progress) {
		File output = new File(releaseRoot, "matrix-" + FOLDER_TIME.format(LocalDateTime.now()));
		File artifacts = new File(output, "artifacts");
		output.mkdirs();
		List<RemarkWorkspaceSandbox.VerificationResult> results = new ArrayList<>();

		for (int index = 0; index < targets.size(); index++) {
			GeneratorConfiguration target = targets.get(index);
			progress.accept("[" + (index + 1) + "/" + targets.size() + "] " + target.getGeneratorName());
			try (RemarkWorkspaceSandbox sandbox = RemarkWorkspaceSandbox.create(workspace, target)) {
				results.add(sandbox.verify(fullBuild, artifacts, progress));
			} catch (Exception e) {
				results.add(new RemarkWorkspaceSandbox.VerificationResult(false, target.getGeneratorName(),
						List.of("Failed to create sandbox: " + e.getMessage()), null, output, 0));
			}
		}

		int passed = (int) results.stream().filter(RemarkWorkspaceSandbox.VerificationResult::success).count();
		MatrixManifest manifest = new MatrixManifest();
		manifest.createdAt = LocalDateTime.now().toString();
		manifest.fullBuild = fullBuild;
		manifest.passed = passed;
		manifest.failed = results.size() - passed;
		manifest.results = results;
		FileIO.writeStringToFile(GSON.toJson(manifest), new File(output, "matrix-report.json"));
		return new MatrixResult(passed == results.size(), passed, results.size() - passed, output, results);
	}

	public record MatrixResult(boolean success, int passed, int failed, File outputFolder,
			List<RemarkWorkspaceSandbox.VerificationResult> results) {}

	private static final class MatrixManifest {
		String createdAt;
		boolean fullBuild;
		int passed;
		int failed;
		List<RemarkWorkspaceSandbox.VerificationResult> results = new ArrayList<>();
	}
}
