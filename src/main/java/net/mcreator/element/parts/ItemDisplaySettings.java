/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.element.parts;

/**
 * Per-perspective transforms for custom item models. The equipped Fashion renderer uses the head context separately,
 * so these values can fix inventory and held views without moving a model already fitted to the player's head.
 */
@SuppressWarnings("unused") public class ItemDisplaySettings {

	public boolean enabled;
	public Transform gui;
	public Transform ground;
	public Transform fixed;
	public Transform thirdPersonRightHand;
	public Transform thirdPersonLeftHand;
	public Transform firstPersonRightHand;
	public Transform firstPersonLeftHand;

	public ItemDisplaySettings() {
		applyCompactFashionPreset();
		enabled = false;
	}

	public static ItemDisplaySettings inherited() {
		return new ItemDisplaySettings();
	}

	public static ItemDisplaySettings compactFashion() {
		ItemDisplaySettings settings = new ItemDisplaySettings();
		settings.enabled = true;
		return settings;
	}

	public static ItemDisplaySettings vanillaBlock() {
		ItemDisplaySettings settings = new ItemDisplaySettings();
		settings.applyVanillaBlockPreset();
		settings.enabled = true;
		return settings;
	}

	public void applyCompactFashionPreset() {
		gui = new Transform(30, 225, 0, 0, 0, 0, 0.4, 0.4, 0.4);
		ground = new Transform(0, 0, 0, 0, 2, 0, 0.2, 0.2, 0.2);
		fixed = new Transform(0, 180, 0, 0, 0, 0, 0.5, 0.5, 0.5);
		thirdPersonRightHand = new Transform(75, 45, 0, 0, 2.5, 0, 0.4, 0.4, 0.4);
		thirdPersonLeftHand = new Transform(75, 225, 0, 0, 2.5, 0, 0.4, 0.4, 0.4);
		firstPersonRightHand = new Transform(0, 45, 0, 0, 0, 0, 0.4, 0.4, 0.4);
		firstPersonLeftHand = new Transform(0, 225, 0, 0, 0, 0, 0.4, 0.4, 0.4);
	}

	public void applyVanillaBlockPreset() {
		gui = new Transform(30, 225, 0, 0, 0, 0, 0.625, 0.625, 0.625);
		ground = new Transform(0, 0, 0, 0, 3, 0, 0.25, 0.25, 0.25);
		fixed = new Transform(0, 180, 0, 0, 0, 0, 0.5, 0.5, 0.5);
		thirdPersonRightHand = new Transform(75, 45, 0, 0, 2.5, 0, 0.375, 0.375, 0.375);
		thirdPersonLeftHand = new Transform(75, 225, 0, 0, 2.5, 0, 0.375, 0.375, 0.375);
		firstPersonRightHand = new Transform(0, 45, 0, 0, 0, 0, 0.4, 0.4, 0.4);
		firstPersonLeftHand = new Transform(0, 225, 0, 0, 0, 0, 0.4, 0.4, 0.4);
	}

	public ItemDisplaySettings copy() {
		ItemDisplaySettings copy = new ItemDisplaySettings();
		copy.enabled = enabled;
		copy.gui = getGui().copy();
		copy.ground = getGround().copy();
		copy.fixed = getFixed().copy();
		copy.thirdPersonRightHand = getThirdPersonRightHand().copy();
		copy.thirdPersonLeftHand = getThirdPersonLeftHand().copy();
		copy.firstPersonRightHand = getFirstPersonRightHand().copy();
		copy.firstPersonLeftHand = getFirstPersonLeftHand().copy();
		return copy;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Transform getGui() {
		if (gui == null)
			gui = compactFashion().gui;
		return gui;
	}

	public Transform getGround() {
		if (ground == null)
			ground = compactFashion().ground;
		return ground;
	}

	public Transform getFixed() {
		if (fixed == null)
			fixed = compactFashion().fixed;
		return fixed;
	}

	public Transform getThirdPersonRightHand() {
		if (thirdPersonRightHand == null)
			thirdPersonRightHand = compactFashion().thirdPersonRightHand;
		return thirdPersonRightHand;
	}

	public Transform getThirdPersonLeftHand() {
		if (thirdPersonLeftHand == null)
			thirdPersonLeftHand = compactFashion().thirdPersonLeftHand;
		return thirdPersonLeftHand;
	}

	public Transform getFirstPersonRightHand() {
		if (firstPersonRightHand == null)
			firstPersonRightHand = compactFashion().firstPersonRightHand;
		return firstPersonRightHand;
	}

	public Transform getFirstPersonLeftHand() {
		if (firstPersonLeftHand == null)
			firstPersonLeftHand = compactFashion().firstPersonLeftHand;
		return firstPersonLeftHand;
	}

	public static final class Transform {

		public double rotationX;
		public double rotationY;
		public double rotationZ;
		public double translationX;
		public double translationY;
		public double translationZ;
		public double scaleX;
		public double scaleY;
		public double scaleZ;

		public Transform() {
			this(0, 0, 0, 0, 0, 0, 1, 1, 1);
		}

		public Transform(double rotationX, double rotationY, double rotationZ, double translationX,
				double translationY, double translationZ, double scaleX, double scaleY, double scaleZ) {
			this.rotationX = rotationX;
			this.rotationY = rotationY;
			this.rotationZ = rotationZ;
			this.translationX = translationX;
			this.translationY = translationY;
			this.translationZ = translationZ;
			this.scaleX = scaleX;
			this.scaleY = scaleY;
			this.scaleZ = scaleZ;
		}

		public Transform copy() {
			return new Transform(rotationX, rotationY, rotationZ, translationX, translationY, translationZ, scaleX,
					scaleY, scaleZ);
		}

		public double getRotationX() {
			return rotationX;
		}

		public double getRotationY() {
			return rotationY;
		}

		public double getRotationZ() {
			return rotationZ;
		}

		public double getTranslationX() {
			return translationX;
		}

		public double getTranslationY() {
			return translationY;
		}

		public double getTranslationZ() {
			return translationZ;
		}

		public double getScaleX() {
			return scaleX;
		}

		public double getScaleY() {
			return scaleY;
		}

		public double getScaleZ() {
			return scaleZ;
		}
	}
}
