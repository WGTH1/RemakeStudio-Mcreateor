/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.modgui;

import net.mcreator.element.types.AAAParticle;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TranslatedComboBox;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.dialogs.ProgressDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.modgui.util.ComponentFromAnnotation;
import net.mcreator.ui.remark.RemarkStudioElementTools;
import net.mcreator.ui.remark.RemarkAAAParticleImporter;
import net.mcreator.ui.validation.ValidationResult;
import net.mcreator.ui.validation.component.VTextField;
import net.mcreator.ui.validation.validators.ResourceLocationValidator;
import net.mcreator.workspace.elements.ModElement;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class AAAParticleGUI extends ModElementGUI<AAAParticle> {

	private static final Set<String> TEXTURE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".tga", ".dds", ".webp");
	private static final Set<String> MODEL_EXTENSIONS = Set.of(".efkmodel", ".fbx", ".obj", ".glb", ".gltf");
	private static final Set<String> SOUND_EXTENSIONS = Set.of(".ogg", ".wav", ".mp3");

	private final VTextField sourceFile = new VTextField(34);
	private final VTextField effectPath = new VTextField(24);
	private final JLabel sourceName = new JLabel();
	private final JLabel importReport = new JLabel(" ");
	private final JLabel dependencyNotice = new JLabel();
	private final TranslatedComboBox spawnMode = ComponentFromAnnotation.translatedOptions(AAAParticle.class,
			"spawnMode", "elementgui.aaaparticle.spawn_mode.");

	private final JSpinner offsetX = ComponentFromAnnotation.spinner(AAAParticle.class, "offsetX");
	private final JSpinner offsetY = ComponentFromAnnotation.spinner(AAAParticle.class, "offsetY");
	private final JSpinner offsetZ = ComponentFromAnnotation.spinner(AAAParticle.class, "offsetZ");
	private final JSpinner rotationX = ComponentFromAnnotation.spinner(AAAParticle.class, "rotationX");
	private final JSpinner rotationY = ComponentFromAnnotation.spinner(AAAParticle.class, "rotationY");
	private final JSpinner rotationZ = ComponentFromAnnotation.spinner(AAAParticle.class, "rotationZ");
	private final JSpinner scaleX = ComponentFromAnnotation.spinner(AAAParticle.class, "scaleX");
	private final JSpinner scaleY = ComponentFromAnnotation.spinner(AAAParticle.class, "scaleY");
	private final JSpinner scaleZ = ComponentFromAnnotation.spinner(AAAParticle.class, "scaleZ");
	private final JSpinner speed = ComponentFromAnnotation.spinner(AAAParticle.class, "speed");
	private final JCheckBox force = L10N.checkbox("elementgui.aaaparticle.force");
	private final JCheckBox unlimitedDistance = L10N.checkbox("elementgui.aaaparticle.unlimited_distance");

	private String sourceRootPath;
	private List<String> importedAssetFiles = new ArrayList<>();

	public AAAParticleGUI(MCreator mcreator, ModElement modElement, boolean editingMode) {
		super(mcreator, modElement, editingMode);
		this.initGUI();
		super.finalizeGUI();
	}

	@Override protected void initGUI() {
		sourceFile.setEditable(false);
		sourceFile.setValidator(() -> {
			if (sourceFile.getText().isBlank())
				return new ValidationResult(ValidationResult.Type.ERROR,
						L10N.t("elementgui.aaaparticle.error_missing_file"));

			File file = new File(mcreator.getWorkspaceFolder(), sourceFile.getText().replace("/", File.separator));
			if (!file.isFile())
				return new ValidationResult(ValidationResult.Type.ERROR,
						L10N.t("elementgui.aaaparticle.error_file_not_found"));

			return ValidationResult.PASSED;
		});

		effectPath.setValidator(new ResourceLocationValidator(L10N.t("modelement.aaaparticle"), effectPath, false));
		effectPath.setText(modElement.getRegistryName());
		effectPath.enableRealtimeValidation();

		force.setOpaque(false);
		force.setSelected(!isEditingMode());
		unlimitedDistance.setOpaque(false);
		unlimitedDistance.setSelected(!isEditingMode());
		unlimitedDistance.setToolTipText(L10N.t("elementgui.aaaparticle.distance_hint"));
		spawnMode.setToolTipText(L10N.t("elementgui.aaaparticle.transform_hint"));
		for (JSpinner offset : List.of(offsetX, offsetY, offsetZ))
			offset.setToolTipText(L10N.t("elementgui.aaaparticle.transform_hint"));
		importReport.setForeground(Theme.current().getAltForegroundColor());
		dependencyNotice.setForeground(Theme.current().getAltForegroundColor());
		dependencyNotice.setText(L10N.t("elementgui.aaaparticle.dependency_notice"));

		JButton browse = L10N.button("elementgui.aaaparticle.import_file");
		browse.addActionListener(_ -> {
			File file = FileDialogs.getOpenDialog(mcreator, new String[] { "efkefc" });
			if (file != null)
				importSource(file);
		});

		JButton browseFolder = L10N.button("elementgui.aaaparticle.import_folder");
		browseFolder.addActionListener(_ -> chooseSourceFolder());

		JButton scanAssets = L10N.button("elementgui.aaaparticle.scan_assets");
		scanAssets.addActionListener(_ -> scanCurrentAssets());

		JButton openFolder = L10N.button("elementgui.aaaparticle.open_imported_folder");
		openFolder.addActionListener(_ -> openImportedFolder());

		JButton copyParticleCommand = L10N.button("elementgui.aaaparticle.copy_particle_command");
		copyParticleCommand.addActionListener(_ -> copyToClipboard(vanillaParticleCommand()));

		JButton copyAAACommand = L10N.button("elementgui.aaaparticle.copy_aaa_command");
		copyAAACommand.addActionListener(_ -> copyToClipboard(aaaParticleCommand()));
		JButton livePreview = L10N.button("remark.tools.preview.menu");
		livePreview.addActionListener(_ -> RemarkStudioElementTools.showPreviewAndTest(mcreator, getElementFromGUI()));

		JPanel importPanel = new JPanel(new BorderLayout(0, 8));
		importPanel.setOpaque(false);
		importPanel.add("North", createDropPanel());
		importPanel.add("South",
				PanelUtils.column(4,
						PanelUtils.join(FlowLayout.LEFT, browse, browseFolder, sourceName),
						PanelUtils.join(FlowLayout.LEFT, copyParticleCommand, copyAAACommand, livePreview),
						importReport, dependencyNotice));

		JComponent simple = PanelUtils.column(12, importPanel,
				PanelUtils.westAndCenterElement(L10N.label("elementgui.aaaparticle.spawn_mode"), spawnMode, 8, 0));
		addPage(L10N.t("elementgui.aaaparticle.page"), PanelUtils.totalCenterInPanel(simple))
				.validate(sourceFile).validate(effectPath);

		JPanel resourcePanel = new JPanel(new GridLayout(8, 2, 5, 5));
		resourcePanel.setOpaque(false);
		resourcePanel.add(L10N.label("elementgui.aaaparticle.source_file"));
		resourcePanel.add(sourceFile);
		resourcePanel.add(L10N.label("elementgui.aaaparticle.effect_path"));
		resourcePanel.add(effectPath);
		resourcePanel.add(L10N.label("elementgui.aaaparticle.offset"));
		resourcePanel.add(PanelUtils.gridElements(1, 3, 2, 0, offsetX, offsetY, offsetZ));
		resourcePanel.add(L10N.label("elementgui.aaaparticle.rotation"));
		resourcePanel.add(PanelUtils.gridElements(1, 3, 2, 0, rotationX, rotationY, rotationZ));
		resourcePanel.add(L10N.label("elementgui.aaaparticle.scale"));
		resourcePanel.add(PanelUtils.gridElements(1, 3, 2, 0, scaleX, scaleY, scaleZ));
		resourcePanel.add(L10N.label("elementgui.aaaparticle.speed"));
		resourcePanel.add(speed);
		resourcePanel.add(force);
		resourcePanel.add(unlimitedDistance);
		addPage(L10N.t("elementgui.aaaparticle.advanced"), PanelUtils.totalCenterInPanel(
				PanelUtils.column(12, resourcePanel, PanelUtils.join(FlowLayout.LEFT, scanAssets, openFolder))));
	}

	private JPanel createDropPanel() {
		JLabel title = L10N.label("elementgui.aaaparticle.drop_file");
		ComponentUtils.deriveFont(title, 14);
		title.setHorizontalAlignment(SwingConstants.CENTER);

		JLabel hint = L10N.label("elementgui.aaaparticle.drop_hint");
		hint.setForeground(Theme.current().getAltForegroundColor());
		hint.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setOpaque(false);
		panel.setPreferredSize(new Dimension(480, 115));
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Theme.current().getAltForegroundColor(), 1),
				BorderFactory.createEmptyBorder(18, 24, 18, 24)));
		panel.add("Center", title);
		panel.add("South", hint);

		new DropTarget(panel, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override public void drop(DropTargetDropEvent event) {
				try {
					if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						event.rejectDrop();
						return;
					}

					event.acceptDrop(DnDConstants.ACTION_COPY);
					List<?> files = (List<?>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					if (!files.isEmpty() && files.getFirst() instanceof File file) {
						SwingUtilities.invokeLater(() -> importSource(file));
						event.dropComplete(true);
					} else {
						event.dropComplete(false);
					}
				} catch (Exception e) {
					event.dropComplete(false);
					JOptionPane.showMessageDialog(mcreator, e.getMessage(),
							L10N.t("elementgui.aaaparticle.import_error"), JOptionPane.ERROR_MESSAGE);
				}
			}
		}, true);

		return panel;
	}

	private void chooseSourceFolder() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(L10N.t("elementgui.aaaparticle.import_folder_title"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showOpenDialog(mcreator) == JFileChooser.APPROVE_OPTION)
			importSource(chooser.getSelectedFile());
	}

	private void importSource(File selected) {
		AAAParticle imported = getElementFromGUI();
		ProgressDialog progress = new ProgressDialog(mcreator, L10N.t("elementgui.aaaparticle.import_file"));
		new SwingWorker<ImportStats, Void>() {
			@Override protected ImportStats doInBackground() throws Exception {
				List<Path> effects = RemarkAAAParticleImporter.findEffects(List.of(selected.toPath()));
				Path imports = mcreator.getFolderManager().getAAAParticlesDir().toPath().resolve(modElement.getRegistryName());
				Files.createDirectories(imports);
				Path target = Files.createTempDirectory(imports, "import-");
				Path primary = effects.getFirst();
				RemarkAAAParticleImporter.stage(primary, target);
				imported.sourceRoot = workspacePath(target.toFile());
				imported.sourceFile = workspacePath(target.resolve(primary.getFileName()).toFile());
				imported.sourceName = primary.getFileName().toString();
				if (!isEditingMode() && imported.effectPath.equals(modElement.getRegistryName()))
					imported.effectPath = modElement.getRegistryName() + "/" + modElement.getRegistryName();
				imported.importedAssetFiles = new ArrayList<>();
				imported.useModAssetFiles(true);
				ImportStats stats = new ImportStats();
				scanImportedFolder(target.toFile(), stats);
				for (Path effect : effects.subList(1, effects.size())) {
					try {
						AAAParticle extra = RemarkAAAParticleImporter.create(mcreator.getWorkspace(), effect, imported.spawnMode);
						mcreator.getGenerator().generateElement(extra, true);
						mcreator.getModElementManager().storeModElement(extra);
						stats.batchCreated++;
					} catch (Exception e) {
						stats.errors.add(effect.getFileName() + ": " + e.getMessage());
					}
				}
				return stats;
			}
			@Override protected void done() {
				progress.dispose();
				try {
					ImportStats stats = get();
					setImportedPaths(imported);
					sourceName.setText(imported.sourceName);
					effectPath.setText(imported.effectPath);
					updateImportReport(stats, stats.batchCreated + 1);
					sourceFile.getValidationStatus();
					if (!stats.errors.isEmpty())
						JOptionPane.showMessageDialog(mcreator, String.join("\n", stats.errors),
								L10N.t("elementgui.aaaparticle.import_error"), JOptionPane.WARNING_MESSAGE);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(mcreator, e.getCause() == null ? e.getMessage() : e.getCause().getMessage(),
							L10N.t("elementgui.aaaparticle.import_error"), JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
		progress.setVisible(true);
	}


	private void scanImportedFolder(File root, ImportStats stats) throws IOException {
		Path rootPath = root.toPath();
		try (Stream<Path> files = Files.walk(rootPath)) {
			files.filter(Files::isRegularFile).forEach(path -> {
				String relative = rootPath.relativize(path).toString().replace(File.separator, "/");
				stats.recordCopiedFile(relative, path.toFile().length());
			});
		}
	}

	private String workspacePath(File file) {
		return mcreator.getFolderManager().getPathInWorkspace(file).replace(File.separator, "/");
	}


	private void updateImportReport(ImportStats stats, int effectCount) {
		List<String> parts = new ArrayList<>();
		parts.add(effectCount + " effect" + (effectCount == 1 ? "" : "s"));
		parts.add(stats.copiedFiles + " asset file" + (stats.copiedFiles == 1 ? "" : "s"));
		if (stats.textureFiles > 0)
			parts.add(stats.textureFiles + " texture" + (stats.textureFiles == 1 ? "" : "s"));
		if (stats.modelFiles > 0)
			parts.add(stats.modelFiles + " model" + (stats.modelFiles == 1 ? "" : "s"));
		if (stats.soundFiles > 0)
			parts.add(stats.soundFiles + " sound" + (stats.soundFiles == 1 ? "" : "s"));
		if (stats.batchCreated > 0)
			parts.add(stats.batchCreated + " extra element" + (stats.batchCreated == 1 ? "" : "s"));

		String summary = "Imported " + String.join(", ", parts) + ".";

		importReport.setText(summary);
		importReport.setToolTipText("<html>" + summary + "<br>" + L10N.t("elementgui.aaaparticle.import_report_hint")
				+ "</html>");
	}

	private void scanCurrentAssets() {
		try {
			if (sourceFile.getText().isBlank()) {
				importReport.setText(L10N.t("elementgui.aaaparticle.scan_no_file"));
				return;
			}

			File effectFile = new File(mcreator.getWorkspaceFolder(), sourceFile.getText().replace("/", File.separator));
			if (!effectFile.isFile()) {
				importReport.setText(L10N.t("elementgui.aaaparticle.error_file_not_found"));
				return;
			}

			Set<String> references = net.mcreator.io.EffekseerAssetReferences.read(effectFile.toPath());
			Path parent = effectFile.toPath().getParent();
			Path fallback = parent.resolve(effectFile.getName().replaceFirst("(?i)\\.efkefc$", ""));
			List<String> missing = references.stream().filter(reference ->
					!Files.isRegularFile(parent.resolve(reference)) && !Files.isRegularFile(fallback.resolve(reference)))
					.sorted().toList();

			String report;
			if (references.isEmpty()) {
				report = L10N.t("elementgui.aaaparticle.scan_no_refs");
			} else if (missing.isEmpty()) {
				report = L10N.t("elementgui.aaaparticle.scan_ok", references.size());
			} else {
				report = L10N.t("elementgui.aaaparticle.scan_missing", missing.size(), references.size());
			}

			importReport.setText(report);
			importReport.setToolTipText("<html>" + report + "<br>" + String.join("<br>", missing.stream().limit(24)
					.toList()) + (missing.size() > 24 ? "<br>..." : "") + "</html>");
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("elementgui.aaaparticle.import_error"),
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void openImportedFolder() {
		try {
			File root = sourceRootPath == null || sourceRootPath.isBlank() ?
					mcreator.getFolderManager().getAAAParticlesDir() :
					new File(mcreator.getWorkspaceFolder(), sourceRootPath.replace("/", File.separator));
			if (!root.isDirectory()) {
				importReport.setText(L10N.t("elementgui.aaaparticle.open_no_folder"));
				return;
			}
			if (Desktop.isDesktopSupported())
				Desktop.getDesktop().open(root);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("elementgui.aaaparticle.import_error"),
					JOptionPane.ERROR_MESSAGE);
		}
	}



	private String vanillaParticleCommand() {
		return "/particle " + mcreator.getWorkspaceSettings().getModID() + ":" + modElement.getRegistryName()
				+ " ~ ~1 ~ 0 0 0 0 1 force";
	}

	private String aaaParticleCommand() {
		return "/aaaparticle " + modElement.getRegistryName() + " on @p";
	}

	private void copyToClipboard(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		importReport.setText(L10N.t("elementgui.aaaparticle.copied_command") + " " + text);
		if (text.startsWith("/particle "))
			importReport.setToolTipText(L10N.t("elementgui.aaaparticle.vanilla_command_hint"));
	}

	private void ensureAAAParticlesDependency() {
		Set<String> dependencies = new HashSet<>(mcreator.getWorkspaceSettings().getMCreatorDependenciesRaw());
		if (dependencies.add("aaa_particles")) {
			mcreator.getWorkspaceSettings().setMCreatorDependencies(dependencies);
			mcreator.getWorkspace().markDirty();
		}
	}


	private static String extensionOf(String path) {
		int dot = path.lastIndexOf('.');
		return dot == -1 ? "" : path.substring(dot).toLowerCase(Locale.ROOT);
	}

	@Override protected void afterGeneratableElementStored() {
		ensureAAAParticlesDependency();
	}

	@Override public void openInEditingMode(AAAParticle particle) {
		try {
			particle.useModAssetFiles(false);
		} catch (IOException e) {
			importReport.setText(e.getMessage());
		}
		setImportedPaths(particle);
		sourceName.setText(particle.sourceName == null ? "" : particle.sourceName);
		effectPath.setText(particle.effectPath == null || particle.effectPath.isBlank() ?
				modElement.getRegistryName() :
				particle.effectPath);
		spawnMode.setSelectedItem(particle.getSpawnModeSafe());
		offsetX.setValue(particle.offsetX);
		offsetY.setValue(particle.offsetY);
		offsetZ.setValue(particle.offsetZ);
		rotationX.setValue(particle.rotationX);
		rotationY.setValue(particle.rotationY);
		rotationZ.setValue(particle.rotationZ);
		scaleX.setValue(particle.scaleX);
		scaleY.setValue(particle.scaleY);
		scaleZ.setValue(particle.scaleZ);
		speed.setValue(particle.speed);
		force.setSelected(particle.force);
		unlimitedDistance.setSelected(particle.unlimitedDistance);
	}

	@Override public AAAParticle getElementFromGUI() {
		AAAParticle particle = new AAAParticle(modElement);
		particle.sourceRoot = sourceRootPath;
		particle.sourceFile = sourceFile.getText();
		particle.sourceName = sourceName.getText();
		particle.importedAssetFiles = new ArrayList<>(importedAssetFiles);
		particle.effectPath = effectPath.getText();
		particle.spawnMode = (String) spawnMode.getSelectedItem();
		particle.offsetX = spinnerValue(offsetX);
		particle.offsetY = spinnerValue(offsetY);
		particle.offsetZ = spinnerValue(offsetZ);
		particle.rotationX = spinnerValue(rotationX);
		particle.rotationY = spinnerValue(rotationY);
		particle.rotationZ = spinnerValue(rotationZ);
		particle.scaleX = spinnerValue(scaleX);
		particle.scaleY = spinnerValue(scaleY);
		particle.scaleZ = spinnerValue(scaleZ);
		particle.speed = spinnerValue(speed);
		particle.force = force.isSelected();
		particle.unlimitedDistance = unlimitedDistance.isSelected();
		return particle;
	}

	private void setImportedPaths(AAAParticle particle) {
		sourceRootPath = particle.sourceRoot;
		sourceFile.setText(particle.sourceFile);
		importedAssetFiles = particle.importedAssetFiles == null ? new ArrayList<>() :
				new ArrayList<>(particle.importedAssetFiles);
	}

	private static double spinnerValue(JSpinner spinner) {
		return ((Number) spinner.getValue()).doubleValue();
	}

	private static class ImportStats {
		private int copiedFiles;
		private int textureFiles;
		private int modelFiles;
		private int soundFiles;
		private int batchCreated;

		private final Set<String> seenFiles = new LinkedHashSet<>();
		private final List<String> errors = new ArrayList<>();


		private void recordCopiedFile(String relativePath, long size) {
			String normalized = relativePath.replace('\\', '/');
			if (!seenFiles.add(normalized))
				return;

			copiedFiles++;
			String extension = extensionOf(normalized);
			if (TEXTURE_EXTENSIONS.contains(extension))
				textureFiles++;
			else if (MODEL_EXTENSIONS.contains(extension))
				modelFiles++;
			else if (SOUND_EXTENSIONS.contains(extension))
				soundFiles++;
		}
	}

	@Override public @Nullable URI contextURL() throws URISyntaxException {
		return new URI("https://github.com/ChloePrime/AAAParticles");
	}
}
