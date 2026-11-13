/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.ui.action.impl.workspace;

import net.mcreator.gradle.GradleResultCode;
import net.mcreator.io.FileIO;
import net.mcreator.io.net.analytics.AnalyticsConstants;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.action.ActionRegistry;
import net.mcreator.ui.action.impl.gradle.GradleAction;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.validation.RemarkStudioBuildValidator;
import net.mcreator.util.DesktopUtils;
import net.mcreator.util.FilenameUtilsPatched;

import javax.swing.*;
import java.io.File;

public class ExportWorkspaceForDistAction extends GradleAction {

	public ExportWorkspaceForDistAction(ActionRegistry actionRegistry) {
		super(actionRegistry, L10N.t("action.workspace.export_mod"), e -> exportImpl(actionRegistry));
	}

	private static void exportImpl(ActionRegistry actionRegistry) {
		if (!RemarkStudioBuildValidator.confirmWorkspaceReady(actionRegistry.getMCreator()))
			return;

		String[] profiles = { L10N.t("dialog.workspace.export.profile.dev"),
				L10N.t("dialog.workspace.export.profile.release"), L10N.t("dialog.workspace.export.profile.modpack"),
				UIManager.getString("OptionPane.cancelButtonText") };
		int profile = JOptionPane.showOptionDialog(actionRegistry.getMCreator(),
				L10N.t("dialog.workspace.export.profile.message"), L10N.t("dialog.workspace.export.profile.title"),
				JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, profiles, profiles[0]);
		if (profile == JOptionPane.CLOSED_OPTION || profile == 3)
			return;

		String gradleTask = profile == 0 ? "assemble" : "build";
		String profileSuffix = switch (profile) {
			case 0 -> "dev";
			case 2 -> "modpack";
			default -> "release";
		};

		actionRegistry.getMCreator().getGenerator().runResourceSetupTasks();
		actionRegistry.getMCreator().getGenerator().generateBase();

		actionRegistry.getMCreator().showConsole();

		actionRegistry.getMCreator().getGradleConsole().exec(gradleTask, taskResult -> {
			String exportFile = actionRegistry.getMCreator().getGeneratorConfiguration()
					.getGradleTaskFor("export_file");
			String exportExtension = FilenameUtilsPatched.getExtension(exportFile);
			File exportFileObject = new File(actionRegistry.getMCreator().getWorkspaceFolder(), exportFile);

			if (taskResult == GradleResultCode.STATUS_OK && exportFileObject.isFile()) {
				String suggestedFileName = actionRegistry.getMCreator().getWorkspaceSettings().getModID() + "-"
						+ actionRegistry.getMCreator().getWorkspaceSettings().getVersion() + "-"
						+ actionRegistry.getMCreator().getWorkspaceSettings().getCurrentGenerator() + "-"
						+ profileSuffix + "." + exportExtension;

				File exportTargetFile = FileDialogs.getSaveDialog(actionRegistry.getMCreator(), suggestedFileName,
						new String[] { "." + exportExtension });
				if (exportTargetFile != null) {
					actionRegistry.getMCreator().getApplication().getAnalytics()
							.trackEvent(AnalyticsConstants.EVENT_EXPORT_FOR_DIST, "build");

					FileIO.copyFile(exportFileObject, exportTargetFile);
				}
			} else {
				JOptionPane.showMessageDialog(actionRegistry.getMCreator(),
						L10N.t("dialog.workspace.export.error.message"), L10N.t("dialog.workspace.export.error.title"),
						JOptionPane.ERROR_MESSAGE);
			}
		});
	}

}
