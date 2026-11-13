/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.element.parts;

/** Persisted input for the first no-code behavior. Times are seconds, not game ticks. */
public final class SimpleItemBehavior {
	public int schemaVersion = 1;
	public boolean enabled;
	public int durationSeconds = 5;
	public int cooldownSeconds = 10;
	public int effectLevel = 1;
	public boolean particles = true;

	public SimpleItemBehavior copy() {
		SimpleItemBehavior copy = new SimpleItemBehavior();
		copy.schemaVersion = schemaVersion;
		copy.enabled = enabled;
		copy.durationSeconds = durationSeconds;
		copy.cooldownSeconds = cooldownSeconds;
		copy.effectLevel = effectLevel;
		copy.particles = particles;
		return copy;
	}
}
