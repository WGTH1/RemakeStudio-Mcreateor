/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.unit.generator;

import com.google.gson.Gson;
import net.mcreator.element.parts.SimpleItemBehavior;
import net.mcreator.element.parts.procedure.Procedure;
import net.mcreator.element.types.Item;
import net.mcreator.generator.behavior.SimpleItemBehaviorCompiler;
import org.junit.jupiter.api.Test;
import static net.mcreator.generator.behavior.SimpleItemBehaviorCompiler.*;
import static org.junit.jupiter.api.Assertions.*;

class SimpleItemBehaviorCompilerTest {
	@Test void absentLegacyBehaviorIsDisabledForEveryLoader() {
		Item item = new Item(null);
		item.simpleBehavior = null;
		assertTrue(validate(item, "forge-1.20.1").isEmpty());
		assertFalse(compile(item, "forge-1.20.1").enabled());
	}

	@Test void lowersSecondsToTicksAndVisibleLevelToAmplifierDeterministically() {
		Item item = enabledItem();
		Plan plan = compile(item, TARGET);
		assertEquals(new Plan(true, 100, 200, 0, 8), plan);
		assertEquals(plan, compile(item, TARGET));
		item.simpleBehavior.particles = false;
		assertEquals(0, compile(item, TARGET).particleCount());
	}

	@Test void rejectsUnsupportedTargetsAndOutOfRangeValues() {
		Item item = enabledItem();
		item.simpleBehavior.durationSeconds = Integer.MAX_VALUE;
		item.simpleBehavior.cooldownSeconds = 0;
		item.simpleBehavior.effectLevel = 6;
		assertTrue(validate(item, "fabric-1.20.1").containsAll(
				java.util.List.of(Problem.TARGET, Problem.DURATION, Problem.COOLDOWN, Problem.LEVEL)));
		assertThrows(IllegalArgumentException.class, () -> compile(item, TARGET));
	}

	@Test void doesNotSilentlyCombineRightClickHandlers() {
		Item item = enabledItem();
		item.onRightClickedInAir = new Procedure("ExistingAction");
		assertTrue(validate(item, TARGET).contains(Problem.PROCEDURE_CONFLICT));
		item.isFood = true;
		assertTrue(validate(item, TARGET).contains(Problem.USE_CONFLICT));
	}

	@Test void preservesUnknownSchemaAndStoredValuesAcrossJsonRoundTrip() {
		SimpleItemBehavior input = new SimpleItemBehavior();
		input.schemaVersion = 9;
		input.cooldownSeconds = 17;
		SimpleItemBehavior restored = new Gson().fromJson(new Gson().toJson(input), SimpleItemBehavior.class);
		Item item = new Item(null);
		item.simpleBehavior = restored;
		assertEquals(17, restored.copy().cooldownSeconds);
		assertTrue(validate(item, TARGET).contains(Problem.SCHEMA));
	}

	private static Item enabledItem() {
		Item item = new Item(null);
		item.simpleBehavior.enabled = true;
		return item;
	}
}
