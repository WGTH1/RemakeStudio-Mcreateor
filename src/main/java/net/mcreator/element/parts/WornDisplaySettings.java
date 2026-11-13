package net.mcreator.element.parts;

import net.mcreator.element.parts.ItemDisplaySettings.Transform;

/** Additional local transforms; identity preserves existing wearable rendering. */
public class WornDisplaySettings {
	public Transform helmet = new Transform();
	public Transform body = new Transform();
	public Transform leggings = new Transform();
	public Transform boots = new Transform();
	public Transform leggingsRight;
	public Transform bootsRight;
	public boolean leggingsLinked = true, bootsLinked = true;
	public boolean leggingsLeftEnabled = true, leggingsRightEnabled = true;
	public boolean bootsLeftEnabled = true, bootsRightEnabled = true;

	public boolean isLinked(String slot) { return slot.equals("leggings") ? leggingsLinked : !slot.equals("boots") || bootsLinked; }
	public void setLinked(String slot, boolean linked) {
		if (slot.equals("leggings")) {
			if (!linked && leggingsRight == null) leggingsRight = get(slot).copy();
			leggingsLinked = linked;
		} else if (slot.equals("boots")) {
			if (!linked && bootsRight == null) bootsRight = get(slot).copy();
			bootsLinked = linked;
		}
	}
	public Transform getForSide(String slot, boolean left) {
		if (left || isLinked(slot)) return get(slot);
		if (slot.equals("leggings")) return leggingsRight == null ? (leggingsRight = get(slot).copy()) : leggingsRight;
		return bootsRight == null ? (bootsRight = get(slot).copy()) : bootsRight;
	}
	public boolean isSideEnabled(String slot, boolean left) {
		return switch (slot) {
			case "leggings" -> left ? leggingsLeftEnabled : leggingsRightEnabled;
			case "boots" -> left ? bootsLeftEnabled : bootsRightEnabled;
			default -> true;
		};
	}
	public void setSideEnabled(String slot, boolean left, boolean enabled) {
		if (slot.equals("leggings")) { if (left) leggingsLeftEnabled = enabled; else leggingsRightEnabled = enabled; }
		else if (slot.equals("boots")) { if (left) bootsLeftEnabled = enabled; else bootsRightEnabled = enabled; }
	}

	public Transform get(String slot) {
		return switch (slot) {
			case "helmet" -> helmet == null ? (helmet = new Transform()) : helmet;
			case "body" -> body == null ? (body = new Transform()) : body;
			case "leggings" -> leggings == null ? (leggings = new Transform()) : leggings;
			case "boots" -> boots == null ? (boots = new Transform()) : boots;
			default -> throw new IllegalArgumentException("Unknown fashion slot: " + slot);
		};
	}

	public WornDisplaySettings copy() {
		WornDisplaySettings copy = new WornDisplaySettings();
		copy.helmet = get("helmet").copy();
		copy.body = get("body").copy();
		copy.leggings = get("leggings").copy();
		copy.boots = get("boots").copy();
		copy.leggingsRight = leggingsRight == null ? null : leggingsRight.copy();
		copy.bootsRight = bootsRight == null ? null : bootsRight.copy();
		copy.leggingsLinked = leggingsLinked; copy.bootsLinked = bootsLinked;
		copy.leggingsLeftEnabled = leggingsLeftEnabled; copy.leggingsRightEnabled = leggingsRightEnabled;
		copy.bootsLeftEnabled = bootsLeftEnabled; copy.bootsRightEnabled = bootsRightEnabled;
		return copy;
	}

	public static boolean supports(String generator) {
		return generator.equals("forge-1.20.1") || generator.equals("fabric-1.20.1")
				|| generator.equals("neoforge-1.21.1") || generator.equals("neoforge-1.21.1-21.1.219");
	}
}
