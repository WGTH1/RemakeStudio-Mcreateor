/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.validation;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.Fashion;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.workspace.elements.ModElement;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public final class RemarkStudioBuildValidator {

	private RemarkStudioBuildValidator() {
	}

	public static boolean confirmWorkspaceReady(MCreator mcreator) {
		List<String> errors = collectErrors(mcreator);
		if (!errors.isEmpty()) {
			Runnable showErrors = () -> net.mcreator.ui.remark.RemarkProblemPanel.show(mcreator);
			if (SwingUtilities.isEventDispatchThread())
				showErrors.run();
			else
				SwingUtilities.invokeLater(showErrors);
			return false;
		}
		List<String> warnings = collectWarnings(mcreator);
		if (warnings.isEmpty())
			return true;

		if (SwingUtilities.isEventDispatchThread())
			return showConfirmation(mcreator, warnings);

		boolean[] result = new boolean[] { false };
		try {
			SwingUtilities.invokeAndWait(() -> result[0] = showConfirmation(mcreator, warnings));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (InvocationTargetException ignored) {
			return false;
		}
		return result[0];
	}

	public static List<String> collectErrors(MCreator mcreator) {
		List<String> errors = new ArrayList<>();
		for (ModElement modElement : mcreator.getWorkspace().getModElements())
			if (modElement.getGeneratableElement() instanceof net.mcreator.element.types.Item item)
				for (var problem : net.mcreator.generator.behavior.SimpleItemBehaviorCompiler.validate(item,
						mcreator.getGenerator().getGeneratorName()))
					errors.add(modElement.getName() + ": " + L10N.t(problem.translationKey()));
		return errors;
	}

	public static List<String> collectWarnings(MCreator mcreator) {
		List<String> warnings = new ArrayList<>();
		for (ModElement modElement : mcreator.getWorkspace().getModElements()) {
			GeneratableElement element = modElement.getGeneratableElement();
			if (element instanceof AAAParticle aaaParticle) {
				validateAAAParticle(mcreator, modElement, aaaParticle, warnings);
			} else if (element instanceof Fashion fashion) {
				validateFashion(modElement, fashion, warnings);
			}
		}
		return warnings;
	}

	private static void validateAAAParticle(MCreator mcreator, ModElement modElement, AAAParticle aaaParticle,
			List<String> warnings) {
		if (!mcreator.getWorkspace().getWorkspaceSettings().getDependencies().contains("aaa_particles")) {
			warnings.add(L10N.t("dialog.remark_build_check.aaa_dependency", modElement.getName()));
		}

		if (aaaParticle.sourceFile == null || aaaParticle.sourceFile.isBlank()) {
			warnings.add(L10N.t("dialog.remark_build_check.aaa_source_missing", modElement.getName()));
			return;
		}

		File sourceFile = new File(mcreator.getWorkspaceFolder(),
				aaaParticle.sourceFile.replace("/", File.separator).replace("\\", File.separator));
		if (!sourceFile.isFile()) {
			warnings.add(L10N.t("dialog.remark_build_check.aaa_source_not_found", modElement.getName(),
					aaaParticle.sourceFile));
		} else if (!sourceFile.getName().toLowerCase().endsWith(".efkefc")) {
			warnings.add(L10N.t("dialog.remark_build_check.aaa_source_extension", modElement.getName()));
		}

		if (aaaParticle.getSafeEffectPath().isBlank()) {
			warnings.add(L10N.t("dialog.remark_build_check.aaa_effect_path", modElement.getName()));
		}
	}

	private static void validateFashion(ModElement modElement, Fashion fashion, List<String> warnings) {
		if (!fashion.enableHelmet && !fashion.enableBody && !fashion.enableLeggings && !fashion.enableBoots) {
			warnings.add(L10N.t("dialog.remark_build_check.fashion_no_slot", modElement.getName()));
		}

		int enabledSlots = (fashion.enableHelmet ? 1 : 0) + (fashion.enableBody ? 1 : 0)
				+ (fashion.enableLeggings ? 1 : 0) + (fashion.enableBoots ? 1 : 0);
		if (!fashion.simpleItemWearable && fashion.singleItemSet && enabledSlots > 1) {
			warnings.add(L10N.t("dialog.remark_build_check.fashion_advanced_single", modElement.getName()));
		}

		if (fashion.simpleItemWearable) {
			if (fashion.shouldGenerateHelmetItem() && (fashion.textureHelmet == null || fashion.textureHelmet.isEmpty()))
				warnings.add(L10N.t("dialog.remark_build_check.fashion_texture", modElement.getName(), "Head"));
			if (fashion.shouldGenerateBodyItem() && (fashion.textureBody == null || fashion.textureBody.isEmpty()))
				warnings.add(L10N.t("dialog.remark_build_check.fashion_texture", modElement.getName(), "Body"));
			if (fashion.shouldGenerateLeggingsItem() && (fashion.textureLeggings == null || fashion.textureLeggings.isEmpty()))
				warnings.add(L10N.t("dialog.remark_build_check.fashion_texture", modElement.getName(), "Legs"));
			if (fashion.shouldGenerateBootsItem() && (fashion.textureBoots == null || fashion.textureBoots.isEmpty()))
				warnings.add(L10N.t("dialog.remark_build_check.fashion_texture", modElement.getName(), "Feet"));
		}
	}

	private static boolean showConfirmation(MCreator mcreator, List<String> warnings) {
		StringBuilder message = new StringBuilder(L10N.t("dialog.remark_build_check.message"));
		message.append("<ul>");
		for (int i = 0; i < Math.min(warnings.size(), 12); i++) {
			message.append("<li>").append(escapeHTML(warnings.get(i))).append("</li>");
		}
		if (warnings.size() > 12)
			message.append("<li>").append(L10N.t("dialog.remark_build_check.more", warnings.size() - 12)).append("</li>");
		message.append("</ul>");
		message.append(L10N.t("dialog.remark_build_check.continue"));

		return JOptionPane.showConfirmDialog(mcreator, message.toString(), L10N.t("dialog.remark_build_check.title"),
				JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private static String escapeHTML(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
