/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import net.mcreator.element.ModElementType;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorStats;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RemarkCompatibilityService {

	private RemarkCompatibilityService() {
	}

	public static Report analyze(Workspace workspace, GeneratorConfiguration target) {
		List<Issue> issues = new ArrayList<>();
		Set<ModElementType<?>> usedTypes = new LinkedHashSet<>();
		for (ModElement modElement : workspace.getModElements()) {
			usedTypes.add(modElement.getType());
			if (modElement.getGeneratableElement() instanceof net.mcreator.element.types.Item item)
				for (var problem : net.mcreator.generator.behavior.SimpleItemBehaviorCompiler.validate(item,
						target.getGeneratorName()))
					issues.add(new Issue(Level.BLOCKED, "SIMPLE_BEHAVIOR_" + problem.name(),
							modElement.getName() + ": " + net.mcreator.ui.init.L10N.t(problem.translationKey())));
		}

		for (ModElementType<?> type : usedTypes) {
			GeneratorStats.CoverageStatus coverage = target.getGeneratorStats().getModElementTypeCoverageInfo().get(type);
			if (coverage == null || coverage == GeneratorStats.CoverageStatus.NONE) {
				issues.add(new Issue(Level.BLOCKED, "TYPE_UNSUPPORTED",
						type.getReadableName() + " has no generator definition"));
			} else if (coverage == GeneratorStats.CoverageStatus.PARTIAL) {
				issues.add(new Issue(Level.REVIEW, "TYPE_PARTIAL",
						type.getReadableName() + " has partial generator coverage"));
			}

			if (type == ModElementType.AAAPARTICLE) {
				if (ModAPIManager.getModAPIForNameAndGenerator("aaa_particles", target.getGeneratorName()) == null)
					issues.add(new Issue(Level.BLOCKED, "AAA_API_MISSING",
							"AAA Particles API is not available for this generator"));
				if (target.getSpecificRoot("aaa_particles_effeks_dir") == null)
					issues.add(new Issue(Level.BLOCKED, "AAA_ROOT_MISSING",
							"AAA Particles asset output is not configured"));
			}
		}

		for (String dependency : workspace.getWorkspaceSettings().getMCreatorDependenciesRaw()) {
			if (ModAPIManager.getModAPIForNameAndGenerator(dependency, target.getGeneratorName()) == null)
				issues.add(new Issue(Level.BLOCKED, "API_UNSUPPORTED",
						"Required API '" + dependency + "' is unavailable"));
		}

		GeneratorStats.Status generatorStatus = target.getGeneratorStats().getStatus();
		if (generatorStatus == GeneratorStats.Status.EXPERIMENTAL || generatorStatus == GeneratorStats.Status.DEV)
			issues.add(new Issue(Level.REVIEW, "GENERATOR_EXPERIMENTAL",
					"Generator status is " + generatorStatus.getName()));

		issues = issues.stream().distinct()
				.sorted(Comparator.comparing((Issue issue) -> issue.level() == Level.BLOCKED ? 0 : 1)
						.thenComparing(Issue::code).thenComparing(Issue::message)).toList();
		int blocked = (int) issues.stream().filter(issue -> issue.level() == Level.BLOCKED).count();
		int review = (int) issues.stream().filter(issue -> issue.level() == Level.REVIEW).count();
		Level level = blocked > 0 ? Level.BLOCKED : review > 0 ? Level.REVIEW : Level.READY;
		return new Report(target, level, blocked, review, issues);
	}

	public static List<Report> analyzeAll(Workspace workspace, List<GeneratorConfiguration> targets) {
		return targets.stream().map(target -> analyze(workspace, target)).toList();
	}

	public enum Level {
		READY, REVIEW, BLOCKED
	}

	public record Issue(Level level, String code, String message) {
	}

	public record Report(GeneratorConfiguration generator, Level level, int blocked, int review, List<Issue> issues) {
		public String summary() {
			return level + " | blocked=" + blocked + " | review=" + review;
		}

		public String details() {
			List<String> lines = new ArrayList<>();
			lines.add(generator.getGeneratorName() + " | Minecraft " + generator.getGeneratorMinecraftVersion());
			lines.add(summary());
			if (issues.isEmpty())
				lines.add("[READY] All used elements and APIs are covered.");
			else
				for (Issue issue : issues)
					lines.add("[" + issue.level() + "] " + issue.code() + " - " + issue.message());
			return String.join("\n", lines);
		}
	}
}
