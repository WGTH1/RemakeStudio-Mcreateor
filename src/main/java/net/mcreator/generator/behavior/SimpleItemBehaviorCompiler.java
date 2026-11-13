/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.generator.behavior;

import net.mcreator.element.parts.SimpleItemBehavior;
import net.mcreator.element.types.Item;
import java.util.ArrayList;
import java.util.List;

/** Validation and typed lowering shared by the editor, migration checks and generators. */
public final class SimpleItemBehaviorCompiler {
	public static final String TARGET = "neoforge-1.21.1-21.1.219";

	private SimpleItemBehaviorCompiler() {}

	public static boolean supports(String generator) {
		return TARGET.equals(generator) || "neoforge-1.21.1".equals(generator);
	}

	public static List<Problem> validate(Item item, String generator) {
		SimpleItemBehavior behavior = item.getSimpleBehavior();
		List<Problem> problems = new ArrayList<>();
		if (behavior.schemaVersion != 1)
			problems.add(Problem.SCHEMA);
		if (!behavior.enabled)
			return List.copyOf(problems);
		if (!supports(generator))
			problems.add(Problem.TARGET);
		if (behavior.durationSeconds < 1 || behavior.durationSeconds > 600)
			problems.add(Problem.DURATION);
		if (behavior.cooldownSeconds < 1 || behavior.cooldownSeconds > 3600)
			problems.add(Problem.COOLDOWN);
		if (behavior.effectLevel < 1 || behavior.effectLevel > 5)
			problems.add(Problem.LEVEL);
		if (item.isFood || item.enableRanged || item.useDuration > 0 || item.hasInventory())
			problems.add(Problem.USE_CONFLICT);
		if (item.onRightClickedInAir != null && item.onRightClickedInAir.getName() != null
				&& !item.onRightClickedInAir.getName().isBlank())
			problems.add(Problem.PROCEDURE_CONFLICT);
		return List.copyOf(problems);
	}

	public static Plan compile(Item item, String generator) {
		List<Problem> problems = validate(item, generator);
		if (!problems.isEmpty())
			throw new IllegalArgumentException(problems.toString());
		SimpleItemBehavior behavior = item.getSimpleBehavior();
		if (!behavior.enabled)
			return new Plan(false, 0, 0, 0, 0);
		return new Plan(true, behavior.durationSeconds * 20, behavior.cooldownSeconds * 20,
				behavior.effectLevel - 1, behavior.particles ? 8 : 0);
	}

	public enum Problem {
		SCHEMA, TARGET, DURATION, COOLDOWN, LEVEL, USE_CONFLICT, PROCEDURE_CONFLICT;
		public String translationKey() {
			return "remark.behavior.error." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public record Plan(boolean enabled, int durationTicks, int cooldownTicks, int amplifier, int particleCount) {
		public boolean isEnabled() { return enabled; }
		public int getDurationTicks() { return durationTicks; }
		public int getCooldownTicks() { return cooldownTicks; }
		public int getAmplifier() { return amplifier; }
		public int getParticleCount() { return particleCount; }
	}
}
