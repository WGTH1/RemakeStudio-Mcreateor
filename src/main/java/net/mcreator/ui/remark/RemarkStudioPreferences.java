/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import java.util.prefs.Preferences;

public final class RemarkStudioPreferences {

	private static final Preferences PREFERENCES = Preferences.userNodeForPackage(RemarkStudioPreferences.class);
	private static final String ADVANCED_MODE = "advanced_mode";

	private RemarkStudioPreferences() {
	}

	public static boolean isAdvancedMode() {
		return PREFERENCES.getBoolean(ADVANCED_MODE, false);
	}

	public static void setAdvancedMode(boolean advancedMode) {
		PREFERENCES.putBoolean(ADVANCED_MODE, advancedMode);
	}
}
