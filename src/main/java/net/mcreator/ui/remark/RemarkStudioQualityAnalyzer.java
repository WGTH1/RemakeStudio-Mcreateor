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
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.parts.gui.Label;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.CreateUI;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.ui.wysiwyg.WYSIWYG;
import net.mcreator.workspace.elements.ModElement;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RemarkStudioQualityAnalyzer {
	private static final long MIB = 1024L * 1024L;

	private RemarkStudioQualityAnalyzer() {
	}

	public static QualityReport analyzePerformance(MCreator mcreator) {
		List<Finding> findings = new ArrayList<>();
		Set<File> assets = collectAssets(mcreator);
		long totalBytes = assets.stream().mapToLong(RemarkStudioQualityAnalyzer::fileSize).sum();
		int textureCount = 0;
		for (TextureType type : TextureType.values()) {
			for (File texture : mcreator.getFolderManager().getTexturesList(type)) {
				textureCount++;
				long bytes = fileSize(texture);
				if (bytes > 4 * MIB)
					findings.add(new Finding(Severity.WARNING, "texture_bytes",
							L10N.t("remark.quality.texture_bytes", texture.getName(), formatBytes(bytes))));
				try {
					var image = ImageIO.read(texture);
					if (image != null && (image.getWidth() > 4096 || image.getHeight() > 4096))
						findings.add(new Finding(Severity.ERROR, "texture_dimensions",
								L10N.t("remark.quality.texture_dimensions_error", texture.getName(), image.getWidth(),
										image.getHeight())));
					else if (image != null && (image.getWidth() > 2048 || image.getHeight() > 2048))
						findings.add(new Finding(Severity.WARNING, "texture_dimensions",
								L10N.t("remark.quality.texture_dimensions", texture.getName(), image.getWidth(),
										image.getHeight())));
				} catch (Exception ignored) {
					findings.add(new Finding(Severity.ERROR, "texture_invalid",
							L10N.t("remark.quality.texture_invalid", texture.getName())));
				}
			}
		}

		for (ModElement modElement : mcreator.getWorkspace().getModElements()) {
			if (modElement.getGeneratableElement() instanceof AAAParticle particle) {
				File root = particle.sourceRoot == null ? null :
						new File(mcreator.getWorkspaceFolder(), particle.sourceRoot.replace("/", File.separator));
				List<File> files = root != null && root.isDirectory() ? recursiveFiles(root) : List.of();
				if (particle.sourceFile != null && particle.sourceFile.replace('\\', '/').startsWith("src/main/resources/")) {
					files = new ArrayList<>();
					files.add(new File(mcreator.getWorkspaceFolder(), particle.sourceFile));
					if (particle.importedAssetFiles != null)
						for (String path : particle.importedAssetFiles)
							files.add(new File(mcreator.getWorkspaceFolder(), path));
				}
				long bytes = files.stream().mapToLong(RemarkStudioQualityAnalyzer::fileSize).sum();
				if (files.size() > 128)
					findings.add(new Finding(Severity.WARNING, "aaa_file_count",
							L10N.t("remark.quality.aaa_files", modElement.getName(), files.size())));
				if (bytes > 16 * MIB)
					findings.add(new Finding(Severity.WARNING, "aaa_bytes",
							L10N.t("remark.quality.aaa_bytes", modElement.getName(), formatBytes(bytes))));
			} else if (modElement.getGeneratableElement() instanceof CreateUI createUI) {
				int components = createUI.components == null ? 0 : createUI.components.size();
				long animated = createUI.components == null ? 0 : createUI.components.stream()
						.filter(component -> component.animationType != null && !"NONE".equals(component.animationType)).count();
				if (components > 100)
					findings.add(new Finding(Severity.WARNING, "ui_components",
							L10N.t("remark.quality.ui_components", modElement.getName(), components)));
				if (animated > 50)
					findings.add(new Finding(Severity.WARNING, "ui_animations",
							L10N.t("remark.quality.ui_animations", modElement.getName(), animated)));
				if (createUI.backgroundImages != null && createUI.backgroundImages.size() > 8)
					findings.add(new Finding(Severity.WARNING, "ui_backgrounds",
							L10N.t("remark.quality.ui_backgrounds", modElement.getName(), createUI.backgroundImages.size())));
			}
		}

		if (totalBytes > 64 * MIB)
			findings.add(new Finding(Severity.WARNING, "total_assets",
					L10N.t("remark.quality.total_assets", formatBytes(totalBytes))));
		int unused = RemarkStudioElementTools.findUnusedAssetFiles(mcreator).size();
		if (unused > 0)
			findings.add(new Finding(Severity.INFO, "unused_assets", L10N.t("remark.quality.unused", unused)));
		findings.sort(Comparator.comparing(Finding::severity).reversed());
		return new QualityReport(findings, totalBytes, totalBytes + MIB, textureCount, assets.size());
	}

	public static List<Finding> analyzeLanguageAndLayout(MCreator mcreator) {
		List<Finding> findings = new ArrayList<>();
		Map<String, java.util.LinkedHashMap<String, String>> languages = mcreator.getWorkspace().getLanguageMap();
		for (ModElement modElement : mcreator.getWorkspace().getModElementsByType(ModElementType.CREATEUI)) {
			if (!(modElement.getGeneratableElement() instanceof CreateUI createUI) || createUI.components == null)
				continue;
			for (GUIComponent component : createUI.components) {
				String text = componentText(component);
				if (text != null && component instanceof Button button && !button.autoSize
						&& textWidth(text) + 16 > button.width)
					findings.add(new Finding(Severity.WARNING, "text_overflow",
							L10N.t("remark.center.language.overflow", modElement.getName(), component.getName(),
									textWidth(text) + 16, button.width)));
				if (text != null && containsThai(text) && editorFont().canDisplayUpTo(text) >= 0)
					findings.add(new Finding(Severity.WARNING, "thai_font",
							L10N.t("remark.quality.thai_font", modElement.getName(), component.getName())));
				String key = "gui." + mcreator.getWorkspaceSettings().getModID() + "." + modElement.getRegistryName()
						+ "." + component.getName();
				for (Map.Entry<String, java.util.LinkedHashMap<String, String>> language : languages.entrySet()) {
					if (!language.getValue().containsKey(key))
						findings.add(new Finding(Severity.WARNING, "missing_translation",
								L10N.t("remark.center.language.missing", language.getKey(), key)));
				}
			}
			for (int guiScale : new int[] { 2, 3 }) {
				for (Dimension physical : List.of(new Dimension(854, 480), new Dimension(1280, 720)))
					analyzeViewport(modElement, createUI, guiScale, physical, findings);
			}
			analyzeButtonOverlap(modElement, createUI, findings);
		}
		findings.sort(Comparator.comparing(Finding::severity).reversed());
		return findings;
	}

	private static void analyzeViewport(ModElement modElement, CreateUI createUI, int guiScale, Dimension physical,
			List<Finding> findings) {
		int logicalWidth = (physical.width + guiScale - 1) / guiScale;
		int logicalHeight = (physical.height + guiScale - 1) / guiScale;
		double layoutScale = 1.0;
		if (createUI.responsiveLayout) {
			double fit = Math.min((logicalWidth - createUI.safeMargin * 2.0) / WYSIWYG.W,
					(logicalHeight - createUI.safeMargin * 2.0) / WYSIWYG.H);
			if (CreateUI.RESPONSIVE_FIT.equals(createUI.responsiveMode))
				layoutScale = Math.max(0.1, fit);
			else if (CreateUI.RESPONSIVE_FIT_DOWN.equals(createUI.responsiveMode))
				layoutScale = Math.max(0.1, Math.min(1.0, fit));
		}
		double baseX = (logicalWidth - WYSIWYG.W * layoutScale) / 2.0;
		double baseY = (logicalHeight - WYSIWYG.H * layoutScale) / 2.0;
		for (GUIComponent component : createUI.components) {
			if (!component.visible)
				continue;
			double left = baseX + component.x * layoutScale;
			double top = baseY + component.y * layoutScale;
			double right = left + component.getWidth(modElement.getWorkspace()) * layoutScale;
			double bottom = top + component.getHeight(modElement.getWorkspace()) * layoutScale;
			if (left < 0 || top < 0 || right > logicalWidth || bottom > logicalHeight)
				findings.add(new Finding(Severity.WARNING, "viewport_outside",
						L10N.t("remark.quality.viewport", modElement.getName(), component.getName(), guiScale,
								physical.width, physical.height)));
		}
	}

	private static void analyzeButtonOverlap(ModElement modElement, CreateUI createUI, List<Finding> findings) {
		List<Button> buttons = createUI.components.stream().filter(Button.class::isInstance).map(Button.class::cast)
				.filter(button -> button.visible).toList();
		for (int leftIndex = 0; leftIndex < buttons.size(); leftIndex++) {
			Button left = buttons.get(leftIndex);
			Rectangle leftBounds = new Rectangle(left.x, left.y, left.width, left.height);
			for (int rightIndex = leftIndex + 1; rightIndex < buttons.size(); rightIndex++) {
				Button right = buttons.get(rightIndex);
				Rectangle overlap = leftBounds.intersection(new Rectangle(right.x, right.y, right.width, right.height));
				if (overlap.width > 2 && overlap.height > 2)
					findings.add(new Finding(Severity.WARNING, "button_overlap",
							L10N.t("remark.quality.overlap", modElement.getName(), left.getName(), right.getName())));
			}
		}
	}

	private static Set<File> collectAssets(MCreator mcreator) {
		Set<File> files = new LinkedHashSet<>();
		for (TextureType type : TextureType.values())
			files.addAll(mcreator.getFolderManager().getTexturesList(type));
		for (File root : List.of(mcreator.getFolderManager().getModelsDir(),
				mcreator.getFolderManager().getAAAParticlesDir()))
			files.addAll(recursiveFiles(root));
		File sounds = mcreator.getFolderManager().getSoundsDir();
		if (sounds != null)
			files.addAll(recursiveFiles(sounds));
		return files;
	}

	private static List<File> recursiveFiles(File root) {
		if (root == null || !root.isDirectory())
			return List.of();
		try (var paths = Files.walk(root.toPath())) {
			return paths.filter(Files::isRegularFile).map(java.nio.file.Path::toFile).toList();
		} catch (Exception e) {
			return List.of();
		}
	}

	private static long fileSize(File file) {
		try {
			return file.isFile() ? Files.size(file.toPath()) : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	private static String componentText(GUIComponent component) {
		if (component instanceof Button button)
			return button.text;
		if (component instanceof Label label)
			return label.getRenderText();
		return null;
	}

	private static int textWidth(String text) {
		Font font = editorFont();
		return java.util.Arrays.stream(text.split("\\R", -1))
				.mapToInt(line -> (int) Math.ceil(font.getStringBounds(line, WYSIWYG.frc).getWidth()))
				.max().orElse(0);
	}

	private static Font editorFont() {
		return WYSIWYG.fontMC != null ? WYSIWYG.fontMC : new Font(Font.MONOSPACED, Font.PLAIN, 10);
	}

	private static boolean containsThai(String text) {
		return text.codePoints().anyMatch(codePoint -> codePoint >= 0x0E00 && codePoint <= 0x0E7F);
	}

	public static String formatBytes(long bytes) {
		if (bytes < 1024)
			return bytes + " B";
		if (bytes < MIB)
			return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
		return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (double) MIB);
	}

	public enum Severity { INFO, WARNING, ERROR }

	public record Finding(Severity severity, String code, String message) {
		@Override public String toString() {
			return "[" + severity + "] " + message;
		}
	}

	public record QualityReport(List<Finding> findings, long assetBytes, long estimatedModBytes, int textureCount,
			int assetCount) {}
}
