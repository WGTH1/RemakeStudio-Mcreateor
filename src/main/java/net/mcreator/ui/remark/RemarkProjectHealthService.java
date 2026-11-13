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
import net.mcreator.element.ModElementType;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.validation.RemarkStudioBuildValidator;
import net.mcreator.workspace.RemarkWorkspaceDependencyManager;
import net.mcreator.workspace.elements.ModElement;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RemarkProjectHealthService {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private RemarkProjectHealthService() {
	}

	public static Report analyze(MCreator mcreator) {
		List<Finding> findings = new ArrayList<>();

		for (String warning : RemarkStudioBuildValidator.collectWarnings(mcreator))
			findings.add(new Finding(Severity.WARNING, "BUILD_VALIDATION", "elements", warning, true));

		Map<String, List<String>> registries = new LinkedHashMap<>();
		for (ModElement element : mcreator.getWorkspace().getModElements()) {
			if (!element.doesCompile())
				findings.add(new Finding(Severity.ERROR, "ELEMENT_NOT_COMPILING", "elements",
						element.getName() + " is marked as not compiling", false));
			if (element.getType() != ModElementType.CODE && element.getGeneratableElement() == null)
				findings.add(new Finding(Severity.ERROR, "ELEMENT_LOAD_FAILED", "elements",
						element.getName() + " could not be loaded", false));
			if (element.getRegistryName() != null)
				registries.computeIfAbsent(element.getRegistryName(), _ -> new ArrayList<>()).add(element.getName());
		}
		for (Map.Entry<String, List<String>> entry : registries.entrySet()) {
			if (entry.getValue().size() > 1)
				findings.add(new Finding(Severity.ERROR, "DUPLICATE_REGISTRY", "elements",
						"Registry name '" + entry.getKey() + "' is shared by " + String.join(", ", entry.getValue()), false));
		}

		addMissingMetadata(findings, "version", mcreator.getWorkspaceSettings().getVersion(), Severity.ERROR);
		addMissingMetadata(findings, "author", mcreator.getWorkspaceSettings().getAuthor(), Severity.WARNING);
		addMissingMetadata(findings, "license", mcreator.getWorkspaceSettings().getLicense(), Severity.WARNING);
		addMissingMetadata(findings, "description", mcreator.getWorkspaceSettings().getDescription(), Severity.INFO);

		RemarkCompatibilityService.Report compatibility = RemarkCompatibilityService.analyze(mcreator.getWorkspace(),
				mcreator.getGeneratorConfiguration());
		for (RemarkCompatibilityService.Issue issue : compatibility.issues())
			findings.add(new Finding(issue.level() == RemarkCompatibilityService.Level.BLOCKED ? Severity.ERROR :
					Severity.WARNING, issue.code(), "compatibility", issue.message(), false));

		if (!mcreator.getWorkspace().containsType(ModElementType.AAAPARTICLE)
				&& mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw()
				.contains(RemarkWorkspaceDependencyManager.AAA_PARTICLES))
			findings.add(new Finding(Severity.WARNING, "STALE_AAA_DEPENDENCY", "dependencies",
					"AAA Particles is enabled but no AAA Particle element remains", true));

		List<File> unused = RemarkStudioElementTools.findUnusedAssetFiles(mcreator);
		long unusedBytes = unused.stream().filter(File::isFile).mapToLong(File::length).sum();
		if (!unused.isEmpty())
			findings.add(new Finding(Severity.INFO, "UNUSED_ASSETS", "assets",
					unused.size() + " unused files use " + RemarkStudioQualityAnalyzer.formatBytes(unusedBytes), true));

		RemarkStudioQualityAnalyzer.QualityReport quality = RemarkStudioQualityAnalyzer.analyzePerformance(mcreator);
		for (RemarkStudioQualityAnalyzer.Finding finding : quality.findings())
			findings.add(new Finding(switch (finding.severity()) {
				case ERROR -> Severity.ERROR;
				case WARNING -> Severity.WARNING;
				case INFO -> Severity.INFO;
			}, finding.code(), "performance", finding.message(), false));

		for (String line : RemarkStudioElementTools.explainGradleLog(mcreator.getGradleConsole().getConsoleText()))
			findings.add(new Finding(Severity.WARNING, "GRADLE_LOG", "build", line, false));

		findings = findings.stream().distinct().toList();
		int score = 100;
		for (Finding finding : findings)
			score -= switch (finding.severity()) {
				case ERROR -> 15;
				case WARNING -> 5;
				case INFO -> 1;
			};
		score = Math.max(0, score);
		return new Report(score, findings, unused.size(), unusedBytes,
				findings.stream().filter(Finding::fixable).count());
	}

	private static void addMissingMetadata(List<Finding> findings, String field, String value, Severity severity) {
		if (value == null || value.isBlank())
			findings.add(new Finding(severity, "MISSING_" + field.toUpperCase(), "metadata",
					"Project " + field + " is empty", false));
	}

	public static String format(Report report) {
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.health.score", report.score()));
		lines.add(L10N.t("remark.health.summary", report.errors(), report.warnings(), report.info(),
				report.fixable()));
		lines.add(L10N.t("remark.health.unused", report.unusedAssets(),
				RemarkStudioQualityAnalyzer.formatBytes(report.unusedBytes())));
		lines.add("");
		if (report.findings().isEmpty())
			lines.add(L10N.t("remark.health.clean"));
		else
			for (Finding finding : report.findings())
				lines.add("[" + L10N.t("remark.health.severity." + finding.severity().name().toLowerCase()) + "] ["
						+ finding.area() + "] " + finding.message()
						+ (finding.fixable() ? " [" + L10N.t("remark.health.safe_fix") + "]" : ""));
		return String.join("\n", lines);
	}

	public static String toJson(Report report) {
		return GSON.toJson(report);
	}

	public enum Severity {
		INFO, WARNING, ERROR
	}

	public record Finding(Severity severity, String code, String area, String message, boolean fixable) {
	}

	public record Report(int score, List<Finding> findings, int unusedAssets, long unusedBytes, long fixable) {
		public long errors() {
			return findings.stream().filter(finding -> finding.severity() == Severity.ERROR).count();
		}

		public long warnings() {
			return findings.stream().filter(finding -> finding.severity() == Severity.WARNING).count();
		}

		public long info() {
			return findings.stream().filter(finding -> finding.severity() == Severity.INFO).count();
		}
	}
}
