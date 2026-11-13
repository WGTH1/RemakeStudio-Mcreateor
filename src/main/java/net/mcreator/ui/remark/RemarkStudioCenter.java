/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.types.CreateUI;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.generator.GeneratorStats;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.io.FileIO;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.plugin.modapis.ModAPIManager;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.action.impl.workspace.RegenerateCodeAction;
import net.mcreator.ui.dialogs.workspace.WorkspaceGeneratorSetupDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.validation.RemarkStudioBuildValidator;
import net.mcreator.ui.wysiwyg.WYSIWYG;
import net.mcreator.workspace.elements.ModElement;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class RemarkStudioCenter {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<ModElementType<?>> REMARK_TYPES = List.of(ModElementType.AAAPARTICLE,
			ModElementType.FASHION, ModElementType.CREATEUI);

	private final MCreator mcreator;
	private final JDialog dialog;
	private final DefaultTableModel compatibilityModel = new ReadOnlyTableModel();
	private final JTable compatibilityTable = new JTable(compatibilityModel);
	private final JTextArea compatibilityDetails = reportArea();
	private final Map<String, RemarkCompatibilityService.Report> compatibilityReports = new LinkedHashMap<>();
	private final JTextArea jarReport = reportArea();
	private final JComboBox<GeneratorConfiguration> jarTarget = new JComboBox<>();
	@Nullable private RemarkModJarInspector.Inspection jarInspection;
	private final DefaultListModel<ModElement> componentElementModel = new DefaultListModel<>();
	private final JList<ModElement> componentElements = new JList<>(componentElementModel);
	private final DefaultListModel<RemarkComponentLibrary.PackageInfo> componentPackageModel = new DefaultListModel<>();
	private final JList<RemarkComponentLibrary.PackageInfo> componentPackages = new JList<>(componentPackageModel);
	private final JTextField componentName = new JTextField(22);
	private final JTextArea componentReport = reportArea();
	private final JComboBox<GeneratorConfiguration> migrationTarget = new JComboBox<>();
	private final JTextArea migrationReport = reportArea();
	private final JTextArea doctorReport = reportArea();
	@Nullable private RemarkProjectHealthService.Report projectHealth;
	private final JTextArea regressionReport = reportArea();
	private final JTextArea assetReport = reportArea();
	private RemarkAssetReviewPanel assetReview;
	private final JTextArea languageReport = reportArea();
	private final JTextArea performanceReport = reportArea();
	private final JTextArea pluginReport = reportArea();
	private final JTextArea releaseReport = reportArea();
	private final DefaultListModel<GeneratorConfiguration> releaseTargetModel = new DefaultListModel<>();
	private final JList<GeneratorConfiguration> releaseTargets = new JList<>(releaseTargetModel);
	private final JCheckBox releaseAutoPackage = L10N.checkbox("remark.center.release.auto_package");
	private final JCheckBox releaseIncludeSource = L10N.checkbox("remark.center.release.include_source");
	@Nullable private RemarkBuildMatrixService.MatrixResult lastMatrixResult;

	private RemarkStudioCenter(MCreator mcreator) {
		this.mcreator = mcreator;
		this.dialog = new JDialog(mcreator, L10N.t("remark.center.title"), Dialog.ModalityType.MODELESS);
	}

	public static void show(MCreator mcreator) {
		RemarkStudioCenter center = new RemarkStudioCenter(mcreator);
		center.build();
		center.dialog.setVisible(true);
	}

	private void build() {
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setMinimumSize(new Dimension(980, 650));
		dialog.setSize(new Dimension(1180, 760));

		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab(L10N.t("remark.center.tab.home"), createHomeTab());
		tabs.addTab(L10N.t("remark.center.tab.jar_inspector"), createJarInspectorTab());
		tabs.addTab(L10N.t("remark.center.tab.components"), createComponentsTab());
		tabs.addTab(L10N.t("remark.center.tab.compatibility"), createCompatibilityTab());
		tabs.addTab(L10N.t("remark.center.tab.migration"), createMigrationTab());
		tabs.addTab(L10N.t("remark.center.tab.doctor"), createDoctorTab());
		tabs.addTab(L10N.t("remark.center.tab.regression"), createRegressionTab());
		tabs.addTab(L10N.t("remark.center.tab.assets"), createAssetsTab());
		tabs.addTab(L10N.t("remark.center.tab.language"), createLanguageTab());
		tabs.addTab(L10N.t("remark.center.tab.performance"), createPerformanceTab());
		tabs.addTab(L10N.t("remark.center.tab.plugins"), createPluginsTab());
		tabs.addTab(L10N.t("remark.center.tab.release"), createReleaseTab());
		dialog.setContentPane(tabs);
		dialog.setLocationRelativeTo(mcreator);

		refreshCompatibility();
		refreshComponentLibrary();
		refreshDoctor();
		refreshRegression();
		refreshAssets();
		refreshLanguage();
		refreshPerformance();
		refreshPlugins();
		refreshRelease();
	}

	private JPanel createHomeTab() {
		JCheckBox advancedMode = L10N.checkbox("remark.center.home.advanced_mode");
		advancedMode.setSelected(RemarkStudioPreferences.isAdvancedMode());
		advancedMode.addActionListener(_ -> {
			RemarkStudioPreferences.setAdvancedMode(advancedMode.isSelected());
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.home.mode_saved"),
					L10N.t("remark.center.title"), JOptionPane.INFORMATION_MESSAGE);
		});

		JTextArea summary = reportArea();
		List<String> warnings = RemarkStudioBuildValidator.collectWarnings(mcreator);
		summary.setText(String.join("\n", List.of(
				L10N.t("remark.center.home.workspace", mcreator.getWorkspaceSettings().getModName()),
				L10N.t("remark.center.home.generator", mcreator.getGenerator().getGeneratorName()),
				L10N.t("remark.center.home.elements", mcreator.getWorkspace().getModElements().size()),
				L10N.t("remark.center.home.warnings", warnings.size()), "",
				L10N.t("remark.center.home.tip"))));

		JPanel mode = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		mode.setBorder(BorderFactory.createTitledBorder(L10N.t("remark.center.home.mode")));
		mode.add(advancedMode);
		JLabel loader = new JLabel(mcreator.getWorkspaceSettings().getLoaderBuildVersion());
		mode.add(button("remark.workflow.loader", () -> {
			RemarkLoaderVersionDialog.show(mcreator);
			loader.setText(mcreator.getWorkspaceSettings().getLoaderBuildVersion());
		}));
		mode.add(loader);
		mode.add(new JLabel(L10N.t("remark.center.home.mode_hint")));

		JPanel actions = actionBar(button("remark.center.refresh_all", () -> {
			refreshCompatibility();
			refreshComponentLibrary();
			refreshDoctor();
			refreshRegression();
			refreshAssets();
			refreshLanguage();
			refreshPerformance();
			refreshPlugins();
			refreshRelease();
		}), button("remark.center.run_build", () -> mcreator.getActionRegistry().buildWorkspace.doAction()),
				button("remark.center.open_history", this::openLocalHistory));
		return reportTab(summary, mode, actions);
	}

	private JPanel createJarInspectorTab() {
		for (GeneratorConfiguration generator : javaGenerators())
			jarTarget.addItem(generator);
		jarTarget.setSelectedItem(mcreator.getGeneratorConfiguration());
		jarTarget.addActionListener(_ -> refreshJarReport());
		jarReport.setText(L10N.t("remark.center.jar.drop_hint"));
		jarReport.setTransferHandler(new TransferHandler() {
			@Override public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override public boolean importData(TransferSupport support) {
				if (!canImport(support))
					return false;
				try {
					List<?> files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					File file = files.stream().filter(File.class::isInstance).map(File.class::cast)
							.filter(candidate -> candidate.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
							.findFirst().orElse(null);
					if (file == null)
						return false;
					inspectJar(file);
					return true;
				} catch (Exception e) {
					showError("remark.center.tab.jar_inspector", e);
					return false;
				}
			}
		});

		JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		selector.add(new JLabel(L10N.t("remark.center.jar.target")));
		selector.add(jarTarget);
		return reportTab(jarReport, selector,
				actionBar(button("remark.center.jar.choose", this::chooseJar),
						button("remark.center.refresh", this::refreshJarReport),
						button("remark.center.copy", () -> copyText(jarReport.getText()))));
	}

	private void chooseJar() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter(L10N.t("remark.center.jar.filter"), "jar"));
		if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION)
			inspectJar(chooser.getSelectedFile());
	}

	private void inspectJar(File file) {
		try {
			jarInspection = RemarkModJarInspector.inspect(file);
			refreshJarReport();
		} catch (Exception e) {
			showError("remark.center.tab.jar_inspector", e);
		}
	}

	private void refreshJarReport() {
		if (jarInspection == null) {
			jarReport.setText(L10N.t("remark.center.jar.drop_hint"));
			return;
		}
		jarReport.setText(RemarkModJarInspector.formatReport(jarInspection,
				(GeneratorConfiguration) jarTarget.getSelectedItem()));
		jarReport.setCaretPosition(0);
	}

	private JPanel createComponentsTab() {
		componentElements.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		componentPackages.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		componentPackages.setTransferHandler(new TransferHandler() {
			@Override public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override public boolean importData(TransferSupport support) {
				if (!canImport(support))
					return false;
				try {
					List<?> files = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					int installed = 0;
					for (Object item : files) {
						if (item instanceof File file && file.getName().toLowerCase(Locale.ROOT)
								.endsWith(".remarkcomponent")) {
							RemarkComponentLibrary.installPackage(file);
							installed++;
						}
					}
					refreshComponentLibrary();
					componentReport.setText(L10N.t("remark.center.components.installed", installed));
					return installed > 0;
				} catch (Exception e) {
					showError("remark.center.tab.components", e);
					return false;
				}
			}
		});

		JPanel elementsPanel = new JPanel(new BorderLayout(5, 5));
		elementsPanel.setBorder(BorderFactory.createTitledBorder(L10N.t("remark.center.components.workspace")));
		elementsPanel.add(new JScrollPane(componentElements), BorderLayout.CENTER);
		JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
		namePanel.add(new JLabel(L10N.t("remark.center.components.name")));
		namePanel.add(componentName);
		elementsPanel.add(namePanel, BorderLayout.SOUTH);

		JPanel packagesPanel = new JPanel(new BorderLayout(5, 5));
		packagesPanel.setBorder(BorderFactory.createTitledBorder(L10N.t("remark.center.components.library")));
		packagesPanel.add(new JScrollPane(componentPackages), BorderLayout.CENTER);

		JSplitPane lists = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, elementsPanel, packagesPanel);
		lists.setResizeWeight(0.5);
		JSplitPane content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, lists, new JScrollPane(componentReport));
		content.setResizeWeight(0.68);

		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		panel.add(content, BorderLayout.CENTER);
		panel.add(actionBar(button("remark.center.components.export", this::exportSelectedComponents),
				button("remark.center.components.import", this::importSelectedComponent),
				button("remark.center.components.add_file", this::installComponentPackage),
				button("remark.center.components.delete", this::deleteSelectedComponent),
				button("remark.center.refresh", this::refreshComponentLibrary)), BorderLayout.SOUTH);
		return panel;
	}

	private void refreshComponentLibrary() {
		componentElementModel.clear();
		mcreator.getWorkspace().getModElements().stream().filter(element -> element.getType() != ModElementType.CODE)
				.sorted(Comparator.comparing(ModElement::getName))
				.forEach(componentElementModel::addElement);
		componentPackageModel.clear();
		RemarkComponentLibrary.listPackages().forEach(componentPackageModel::addElement);
		if (componentPackageModel.isEmpty())
			componentReport.setText(L10N.t("remark.center.components.empty",
					RemarkComponentLibrary.libraryFolder().getAbsolutePath()));
	}

	private void exportSelectedComponents() {
		try {
			RemarkComponentLibrary.ExportResult result = RemarkComponentLibrary.exportPackage(mcreator.getWorkspace(),
					componentElements.getSelectedValuesList(), componentName.getText());
			componentReport.setText(L10N.t("remark.center.components.exported", result.elements(), result.resources(),
					result.file().getAbsolutePath()));
			refreshComponentLibrary();
		} catch (Exception e) {
			showError("remark.center.tab.components", e);
		}
	}

	private void importSelectedComponent() {
		RemarkComponentLibrary.PackageInfo selected = componentPackages.getSelectedValue();
		if (selected == null)
			return;
		try {
			RemarkComponentLibrary.ImportResult result = RemarkComponentLibrary.importPackage(mcreator.getWorkspace(),
					selected.file());
			mcreator.reloadWorkspaceTabContents();
			componentReport.setText(L10N.t("remark.center.components.imported", result.elements().size(),
					result.resources(), String.join(", ", result.elements())));
			refreshComponentLibrary();
			refreshCompatibility();
			refreshDoctor();
		} catch (Exception e) {
			showError("remark.center.tab.components", e);
		}
	}

	private void installComponentPackage() {
		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new FileNameExtensionFilter(L10N.t("remark.center.components.filter"),
				"remarkcomponent"));
		if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION)
			return;
		try {
			RemarkComponentLibrary.PackageInfo installed = RemarkComponentLibrary.installPackage(chooser.getSelectedFile());
			refreshComponentLibrary();
			componentReport.setText(L10N.t("remark.center.components.installed_name", installed.name()));
		} catch (Exception e) {
			showError("remark.center.tab.components", e);
		}
	}

	private void deleteSelectedComponent() {
		RemarkComponentLibrary.PackageInfo selected = componentPackages.getSelectedValue();
		if (selected == null)
			return;
		if (JOptionPane.showConfirmDialog(dialog, L10N.t("remark.center.components.delete_confirm", selected.name()),
				L10N.t("remark.center.tab.components"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
			return;
		try {
			RemarkComponentLibrary.deletePackage(selected.file());
			refreshComponentLibrary();
		} catch (Exception e) {
			showError("remark.center.tab.components", e);
		}
	}

	private JPanel createCompatibilityTab() {
		compatibilityModel.setColumnIdentifiers(new Object[] { L10N.t("remark.center.compat.generator"),
				L10N.t("remark.center.compat.minecraft"), L10N.t("remark.center.compat.loader"),
				L10N.t("remark.center.compat.project_state"), L10N.t("remark.center.compat.blocked"),
				L10N.t("remark.center.compat.review"),
				L10N.t("modelement.aaaparticle"), L10N.t("modelement.fashion"),
				L10N.t("modelement.createui"), L10N.t("remark.center.compat.generator_state") });
		compatibilityTable.setAutoCreateRowSorter(true);
		compatibilityTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		compatibilityTable.getSelectionModel().addListSelectionListener(_ -> showSelectedCompatibilityDetails());

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(compatibilityTable),
				new JScrollPane(compatibilityDetails));
		split.setResizeWeight(0.64);
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		panel.add(split, BorderLayout.CENTER);
		panel.add(actionBar(button("remark.center.refresh", this::refreshCompatibility),
				button("remark.center.compat.preflight", this::refreshDoctor)), BorderLayout.SOUTH);
		return panel;
	}

	private JPanel createMigrationTab() {
		for (GeneratorConfiguration generator : javaGenerators())
			migrationTarget.addItem(generator);
		migrationTarget.setSelectedItem(mcreator.getGeneratorConfiguration());
		migrationTarget.addActionListener(_ -> refreshMigrationReport());

		JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		selector.add(new JLabel(L10N.t("remark.center.migration.target")));
		selector.add(migrationTarget);
		JPanel actions = actionBar(button("remark.center.migration.analyze", this::refreshMigrationReport),
				button("remark.center.migration.checkpoint", this::createMigrationCheckpoint),
				button("remark.center.migration.switch", this::startSelectedMigration),
				button("remark.center.open_history", this::openLocalHistory));
		refreshMigrationReport();
		return reportTab(migrationReport, selector, actions);
	}

	private JPanel createDoctorTab() {
		JPanel result = reportTab(doctorReport, actionBar(button("remark.center.refresh", this::refreshDoctor),
				button("remark.workflow.fix_here", () -> RemarkProblemPanel.show(mcreator)),
				button("remark.center.doctor.repair", this::runSafeRepair),
				button("remark.center.regenerate", () -> mcreator.getActionRegistry().regenerateCode.doAction()),
				button("remark.center.run_build", () -> mcreator.getActionRegistry().buildWorkspace.doAction()),
				button("remark.center.doctor.export_json", this::saveHealthJson),
				button("remark.center.copy", () -> copyText(doctorReport.getText()))));
		return result;
	}

	private JPanel createRegressionTab() {
		return reportTab(regressionReport, actionBar(button("remark.center.regression.scan", this::refreshRegression),
				button("remark.center.regression.save", () -> saveReport("regression", regressionReport.getText())),
				button("remark.center.run_build", () -> mcreator.getActionRegistry().buildWorkspace.doAction()),
				button("remark.center.regenerate", () -> mcreator.getActionRegistry().regenerateCode.doAction())));
	}

	private JPanel createAssetsTab() {
		assetReview = new RemarkAssetReviewPanel(mcreator);
		return assetReview;
	}

	private JPanel createLanguageTab() {
		return reportTab(languageReport, actionBar(button("remark.center.refresh", this::refreshLanguage),
				button("remark.center.language.auto_size_all", this::autoSizeAllCreateUIButtons),
				button("remark.center.copy", () -> copyText(languageReport.getText()))));
	}

	private JPanel createPerformanceTab() {
		return reportTab(performanceReport, actionBar(button("remark.center.refresh", this::refreshPerformance),
				button("remark.center.assets.clean", this::runCleanup),
				button("remark.center.assets.restore", this::restoreCleanup),
				button("remark.center.run_build", () -> mcreator.getActionRegistry().buildWorkspace.doAction()),
				button("remark.center.copy", () -> copyText(performanceReport.getText()))));
	}

	private void runCleanup() {
		RemarkStudioElementTools.cleanUnusedAssets(mcreator);
		refreshAssets();
		refreshPerformance();
		refreshDoctor();
	}

	private void restoreCleanup() {
		RemarkStudioElementTools.restoreLastCleanup(mcreator);
		refreshAssets();
		refreshPerformance();
		refreshDoctor();
	}

	private JPanel createPluginsTab() {
		return reportTab(pluginReport, actionBar(button("remark.center.refresh", this::refreshPlugins),
				button("remark.center.plugins.backup", this::backupPlugins),
				button("remark.center.plugins.restore", this::restorePlugins)));
	}

	private JPanel createReleaseTab() {
		releaseTargets.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		releaseTargets.setVisibleRowCount(8);
		for (GeneratorConfiguration generator : javaGenerators())
			releaseTargetModel.addElement(generator);
		loadReleaseProfile();

		JPanel targets = new JPanel(new BorderLayout(5, 5));
		targets.setBorder(BorderFactory.createTitledBorder(L10N.t("remark.center.release.targets")));
		targets.add(new JScrollPane(releaseTargets), BorderLayout.CENTER);
		targets.setPreferredSize(new Dimension(330, 180));
		JPanel releaseOptions = new JPanel();
		releaseOptions.setLayout(new BoxLayout(releaseOptions, BoxLayout.Y_AXIS));
		releaseOptions.setBorder(BorderFactory.createTitledBorder(L10N.t("remark.center.release.options")));
		releaseOptions.add(releaseAutoPackage);
		releaseOptions.add(releaseIncludeSource);
		JPanel sidebar = new JPanel(new BorderLayout(5, 5));
		sidebar.add(targets, BorderLayout.CENTER);
		sidebar.add(releaseOptions, BorderLayout.SOUTH);

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(sidebar, BorderLayout.WEST);
		content.add(new JScrollPane(releaseReport), BorderLayout.CENTER);
		content.add(actionBar(button("remark.center.release.save", this::saveReleaseProfile),
				button("remark.center.release.manifest", this::writeReleaseManifest),
				button("remark.center.release.build_matrix", this::runSelectedBuildMatrix),
				button("remark.center.release.package", this::packageLastBuild),
				button("remark.center.release.build_current", this::buildCurrentRelease),
				button("remark.center.release.next", this::moveToNextReleaseTarget),
				button("remark.center.refresh", this::refreshRelease)), BorderLayout.SOUTH);
		return content;
	}

	private void refreshCompatibility() {
		compatibilityModel.setRowCount(0);
		compatibilityReports.clear();
		for (GeneratorConfiguration generator : javaGenerators()) {
			RemarkCompatibilityService.Report project = RemarkCompatibilityService.analyze(mcreator.getWorkspace(),
					generator);
			compatibilityReports.put(generator.getGeneratorName(), project);
			compatibilityModel.addRow(new Object[] { generator.getGeneratorName(),
					generator.getGeneratorMinecraftVersion(), generator.getGeneratorFlavor().name(),
					project.level(), project.blocked(), project.review(),
					support(generator, ModElementType.AAAPARTICLE).label(),
					support(generator, ModElementType.FASHION).label(),
					support(generator, ModElementType.CREATEUI).label(), generator.getGeneratorStats().getStatus().getName() });
		}
		if (compatibilityModel.getRowCount() > 0)
			compatibilityTable.setRowSelectionInterval(0, 0);
	}

	private void showSelectedCompatibilityDetails() {
		int viewRow = compatibilityTable.getSelectedRow();
		if (viewRow < 0)
			return;
		int row = compatibilityTable.convertRowIndexToModel(viewRow);
		String generatorName = String.valueOf(compatibilityModel.getValueAt(row, 0));
		GeneratorConfiguration generator = Generator.GENERATOR_CACHE.values().stream()
				.filter(candidate -> candidate.getGeneratorName().equals(generatorName)).findFirst().orElse(null);
		if (generator == null)
			return;

		List<String> lines = new ArrayList<>();
		RemarkCompatibilityService.Report project = compatibilityReports.get(generatorName);
		if (project != null) {
			lines.add(project.details());
			lines.add("");
		}
		lines.add(generator + " [" + generatorName + "] - custom Remake Studio element support");
		for (ModElementType<?> type : REMARK_TYPES) {
			Support support = support(generator, type);
			lines.add(type.getReadableName() + ": " + support.label() + " - " + support.reason());
		}
		compatibilityDetails.setText(String.join("\n", lines));
		compatibilityDetails.setCaretPosition(0);
	}

	private Support support(GeneratorConfiguration generator, ModElementType<?> type) {
		GeneratorStats.CoverageStatus coverage = generator.getGeneratorStats().getModElementTypeCoverageInfo().get(type);
		if (coverage == null || coverage == GeneratorStats.CoverageStatus.NONE)
			return new Support(Health.UNSUPPORTED, L10N.t("remark.center.reason.no_definition"));
		if (type == ModElementType.AAAPARTICLE) {
			if (ModAPIManager.getModAPIForNameAndGenerator("aaa_particles", generator.getGeneratorName()) == null)
				return new Support(Health.UNSUPPORTED, L10N.t("remark.center.reason.no_aaa_api"));
			if (generator.getSpecificRoot("aaa_particles_effeks_dir") == null)
				return new Support(Health.EXPERIMENTAL, L10N.t("remark.center.reason.no_aaa_root"));
		}
		if (coverage == GeneratorStats.CoverageStatus.PARTIAL)
			return new Support(Health.EXPERIMENTAL, L10N.t("remark.center.reason.partial"));
		if (generator.getGeneratorStats().getStatus() == GeneratorStats.Status.EXPERIMENTAL
				|| generator.getGeneratorStats().getStatus() == GeneratorStats.Status.DEV)
			return new Support(Health.EXPERIMENTAL, L10N.t("remark.center.reason.experimental_generator"));
		return new Support(Health.READY, L10N.t("remark.center.reason.ready"));
	}

	private void refreshMigrationReport() {
		GeneratorConfiguration target = (GeneratorConfiguration) migrationTarget.getSelectedItem();
		if (target == null)
			return;
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.center.migration.from_to", mcreator.getGenerator().getGeneratorName(),
				target.getGeneratorName()));
		lines.add("");
		int unsupported = 0;
		int experimental = 0;
		for (ModElement modElement : mcreator.getWorkspace().getModElements()) {
			Support status = support(target, modElement.getType());
			if (status.health() == Health.UNSUPPORTED) {
				unsupported++;
				lines.add(L10N.t("remark.center.migration.element_issue", modElement.getName(),
						modElement.getType().getReadableName(), status.label(), status.reason()));
			} else if (status.health() == Health.EXPERIMENTAL) {
				experimental++;
				lines.add(L10N.t("remark.center.migration.element_issue", modElement.getName(),
						modElement.getType().getReadableName(), status.label(), status.reason()));
			}
		}
		lines.add(0, L10N.t("remark.center.migration.summary", unsupported, experimental));
		if (unsupported == 0 && experimental == 0)
			lines.add(L10N.t("remark.center.migration.clean"));
		migrationReport.setText(String.join("\n", lines));
		migrationReport.setCaretPosition(0);
	}

	private void createMigrationCheckpoint() {
		GeneratorConfiguration target = (GeneratorConfiguration) migrationTarget.getSelectedItem();
		if (target == null)
			return;
		mcreator.getWorkspace().getHistoryManager().importantCheckpoint("remark_migration",
				mcreator.getGenerator().getGeneratorName(), target.getGeneratorName());
		JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.migration.checkpoint_done"),
				L10N.t("remark.center.tab.migration"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void startSelectedMigration() {
		GeneratorConfiguration target = (GeneratorConfiguration) migrationTarget.getSelectedItem();
		if (target != null)
			startMigration(target);
	}

	private void startMigration(GeneratorConfiguration target) {
		GeneratorConfiguration original = mcreator.getGeneratorConfiguration();
		String current = original.getGeneratorName();
		if (current.equals(target.getGeneratorName())) {
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.migration.already_current"),
					L10N.t("remark.center.tab.migration"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		refreshMigrationReport();
		int answer = JOptionPane.showConfirmDialog(dialog,
				L10N.t("remark.center.migration.confirm", current, target.getGeneratorName()),
				L10N.t("remark.center.tab.migration"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION)
			return;

		if (!closeEditorsBeforeBulkAction())
			return;
		migrationReport.setText(L10N.t("remark.center.migration.sandbox_start", target.getGeneratorName()));
		new Thread(() -> {
			RemarkWorkspaceSandbox.VerificationResult preflight;
			try (RemarkWorkspaceSandbox sandbox = RemarkWorkspaceSandbox.create(mcreator.getWorkspace(), target)) {
				preflight = sandbox.verify(true, null, this::appendMigrationProgress);
			} catch (Exception e) {
				showMigrationFailure(L10N.t("remark.center.migration.sandbox_create_failed", readableMessage(e)));
				return;
			}

			appendMigrationLines(preflight.lines());
			if (!preflight.success()) {
				showMigrationFailure(L10N.t("remark.center.migration.sandbox_failed",
						preflight.sandboxFolder().getAbsolutePath()));
				return;
			}

			appendMigrationProgress(L10N.t("remark.center.migration.sandbox_passed"));
			mcreator.getWorkspace().getHistoryManager().importantCheckpoint("remark_migration",
					original.getGeneratorName(), target.getGeneratorName());
			try {
				SwingUtilities.invokeLater(dialog::dispose);
				WorkspaceGeneratorSetup.cleanupGeneratorForSwitchTo(mcreator.getWorkspace(), target);
				mcreator.getWorkspace().switchGenerator(target.getGeneratorName());
				mcreator.getWorkspace().getFileManager().saveWorkspaceDirectlyAndWait();
				mcreator.workspaceGeneratorSwitched();
				WorkspaceGeneratorSetupDialog.runSetup(mcreator, false);
				RegenerateCodeAction.regenerateCode(mcreator, true, true);
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mcreator,
						L10N.t("remark.center.migration.completed", target.getGeneratorName()),
						L10N.t("remark.center.tab.migration"), JOptionPane.INFORMATION_MESSAGE));
			} catch (Exception e) {
				String failure = readableMessage(e);
				boolean rolledBack = rollbackMigration(original);
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(mcreator,
						L10N.t(rolledBack ? "remark.center.migration.failed_rolled_back" :
								"remark.center.migration.failed_rollback_failed", failure),
						L10N.t("remark.center.tab.migration"), JOptionPane.ERROR_MESSAGE));
			}
		}, "RemarkStudio-Migration").start();
	}

	private boolean rollbackMigration(GeneratorConfiguration original) {
		try {
			if (!mcreator.getGeneratorConfiguration().getGeneratorName().equals(original.getGeneratorName())) {
				WorkspaceGeneratorSetup.cleanupGeneratorForSwitchTo(mcreator.getWorkspace(), original);
				mcreator.getWorkspace().switchGenerator(original.getGeneratorName());
				mcreator.getWorkspace().getFileManager().saveWorkspaceDirectlyAndWait();
				mcreator.workspaceGeneratorSwitched();
				WorkspaceGeneratorSetupDialog.runSetup(mcreator, false);
				RegenerateCodeAction.regenerateCode(mcreator, true, true);
			}
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private void appendMigrationProgress(String message) {
		SwingUtilities.invokeLater(() -> {
			String current = migrationReport.getText();
			migrationReport.setText(current + (current.isBlank() ? "" : "\n") + message);
			migrationReport.setCaretPosition(migrationReport.getDocument().getLength());
		});
	}

	private void appendMigrationLines(List<String> messages) {
		for (String message : messages)
			appendMigrationProgress(message);
	}

	private void showMigrationFailure(String message) {
		appendMigrationProgress(message);
		SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(dialog, message,
				L10N.t("remark.center.tab.migration"), JOptionPane.ERROR_MESSAGE));
	}

	private void refreshDoctor() {
		projectHealth = RemarkProjectHealthService.analyze(mcreator);
		doctorReport.setText(RemarkProjectHealthService.format(projectHealth));
		doctorReport.setCaretPosition(0);
	}

	private void runSafeRepair() {
		List<String> completed = RemarkStudioElementTools.repairWorkspace(mcreator);
		if (net.mcreator.workspace.RemarkWorkspaceDependencyManager.removeAAAParticlesIfUnused(mcreator.getWorkspace())) {
			completed.add(L10N.t("remark.center.doctor.removed_stale_aaa"));
			mcreator.getWorkspace().markDirty();
		}
		doctorReport.setText(String.join("\n", completed));
		mcreator.getActionRegistry().regenerateCode.doAction();
	}

	private void saveHealthJson() {
		if (projectHealth == null)
			refreshDoctor();
		File target = new File(remarkFolder(),
				"reports/project-health-" + FILE_TIME.format(LocalDateTime.now()) + ".json");
		FileIO.writeStringToFile(RemarkProjectHealthService.toJson(projectHealth), target);
		JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.report_saved", target.getAbsolutePath()),
				L10N.t("remark.center.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void refreshRegression() {
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.center.regression.static_notice"));
		for (GeneratorConfiguration generator : javaGenerators()) {
			List<String> failures = new ArrayList<>();
			for (ModElementType<?> type : REMARK_TYPES) {
				Support status = support(generator, type);
				if (status.health() == Health.UNSUPPORTED)
					failures.add(type.getReadableName() + ": " + status.reason());
			}
			if (generator.getGradleTaskFor("run_client") == null)
				failures.add(L10N.t("remark.center.regression.no_client_task"));
			lines.add((failures.isEmpty() ? "[OK] " : "[WARN] ") + generator.getGeneratorName());
			for (String failure : failures)
				lines.add("  - " + failure);
		}
		regressionReport.setText(String.join("\n", lines));
		regressionReport.setCaretPosition(0);
	}

	private void refreshAssets() {
		if (assetReview != null) { assetReview.refresh(); return; }
		List<String> lines = new ArrayList<>(RemarkStudioElementTools.buildAssetDependencyReport(mcreator));
		Map<String, List<File>> duplicates = duplicateAssets();
		lines.add("");
		lines.add(L10N.t("remark.center.assets.duplicates", duplicates.size()));
		for (List<File> duplicateGroup : duplicates.values())
			lines.add(duplicateGroup.stream().map(File::getAbsolutePath).collect(Collectors.joining(" | ")));
		assetReport.setText(String.join("\n", lines));
		assetReport.setCaretPosition(0);
	}

	private Map<String, List<File>> duplicateAssets() {
		Map<String, List<File>> byHash = new LinkedHashMap<>();
		Set<File> files = new LinkedHashSet<>();
		for (var type : net.mcreator.ui.workspace.resources.TextureType.values())
			files.addAll(mcreator.getFolderManager().getTexturesList(type));
		File sounds = mcreator.getFolderManager().getSoundsDir();
		if (sounds != null && sounds.isDirectory())
			collectFiles(sounds, files);
		collectFiles(mcreator.getFolderManager().getModelsDir(), files);
		collectFiles(mcreator.getFolderManager().getAAAParticlesDir(), files);
		for (File file : files) {
			String hash = sha256(file);
			if (!hash.isBlank())
				byHash.computeIfAbsent(hash, _ -> new ArrayList<>()).add(file);
		}
		byHash.entrySet().removeIf(entry -> entry.getValue().size() < 2);
		return byHash;
	}

	private void refreshLanguage() {
		List<String> lines = new ArrayList<>();
		Map<String, LinkedHashMap<String, String>> languages = mcreator.getWorkspace().getLanguageMap();
		lines.add(L10N.t("remark.center.language.summary", languages.size()));
		lines.add(L10N.t("remark.center.language.matrix"));
		List<RemarkStudioQualityAnalyzer.Finding> findings =
				RemarkStudioQualityAnalyzer.analyzeLanguageAndLayout(mcreator);
		lines.addAll(findings.stream().map(RemarkStudioQualityAnalyzer.Finding::toString).toList());
		if (findings.isEmpty())
			lines.add(L10N.t("remark.center.language.clean"));
		languageReport.setText(String.join("\n", lines));
		languageReport.setCaretPosition(0);
	}

	private void refreshPerformance() {
		RemarkStudioQualityAnalyzer.QualityReport report = RemarkStudioQualityAnalyzer.analyzePerformance(mcreator);
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.center.performance.summary", report.assetCount(), report.textureCount()));
		lines.add(L10N.t("remark.center.performance.assets", RemarkStudioQualityAnalyzer.formatBytes(report.assetBytes())));
		lines.add(L10N.t("remark.center.performance.estimate",
				RemarkStudioQualityAnalyzer.formatBytes(report.estimatedModBytes())));
		lines.add("");
		if (report.findings().isEmpty())
			lines.add(L10N.t("remark.center.performance.clean"));
		else
			lines.addAll(report.findings().stream().map(RemarkStudioQualityAnalyzer.Finding::toString).toList());
		performanceReport.setText(String.join("\n", lines));
		performanceReport.setCaretPosition(0);
	}

	private void autoSizeAllCreateUIButtons() {
		if (!closeEditorsBeforeBulkAction())
			return;
		int changed = 0;
		for (ModElement modElement : mcreator.getWorkspace().getModElementsByType(ModElementType.CREATEUI)) {
			if (!(modElement.getGeneratableElement() instanceof CreateUI createUI) || createUI.components == null)
				continue;
			for (GUIComponent component : createUI.components) {
				if (component instanceof Button button) {
					button.autoSize = true;
					applyButtonAutoSize(button);
					changed++;
				}
			}
			mcreator.getModElementManager().storeModElement(createUI);
		}
		mcreator.getWorkspace().markDirty();
		mcreator.reloadWorkspaceTabContents();
		refreshLanguage();
		JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.language.auto_size_done", changed),
				L10N.t("remark.center.tab.language"), JOptionPane.INFORMATION_MESSAGE);
	}

	private static void applyButtonAutoSize(Button button) {
		int desiredWidth = textWidth(button.text) + Math.max(0, button.autoSizePaddingX) * 2;
		int desiredHeight = textHeight(button.text) + Math.max(0, button.autoSizePaddingY) * 2;
		button.width = clamp(desiredWidth, Math.max(1, button.autoSizeMinWidth), button.autoSizeMaxWidth);
		button.height = clamp(desiredHeight, Math.max(1, button.autoSizeMinHeight), button.autoSizeMaxHeight);
	}

	private void refreshPlugins() {
		List<String> lines = new ArrayList<>();
		for (Plugin plugin : PluginLoader.INSTANCE.getPlugins()) {
			String name = plugin.getInfo() != null && plugin.getInfo().getName() != null ? plugin.getInfo().getName() :
					plugin.getID();
			lines.add((plugin.isLoaded() ? "[OK] " : "[ERROR] ") + name + " | " + plugin.getPluginVersion()
					+ " | " + plugin.getFile().getAbsolutePath());
			if (!plugin.isLoaded())
				lines.add("  " + plugin.getLoadFailure());
		}
		lines.add(0, L10N.t("remark.center.plugins.summary", PluginLoader.INSTANCE.getPlugins().size(), 0));
		pluginReport.setText(String.join("\n", lines));
		pluginReport.setCaretPosition(0);
	}

	private void backupPlugins() {
		File folder = new File(remarkFolder(), "plugin-backups/" + FILE_TIME.format(LocalDateTime.now()));
		folder.mkdirs();
		PluginBackupManifest manifest = new PluginBackupManifest();
		manifest.createdAt = LocalDateTime.now().toString();
		for (Plugin plugin : PluginLoader.INSTANCE.getPlugins()) {
			File source = plugin.getFile();
			if (!source.exists() || source.isDirectory() && plugin.isBuiltin())
				continue;
			String safeName = plugin.getID().replaceAll("[^A-Za-z0-9_.-]", "_")
					+ (source.isFile() && source.getName().contains(".") ?
					source.getName().substring(source.getName().lastIndexOf('.')) : "");
			File backup = new File(folder, safeName);
			if (source.isDirectory())
				FileIO.copyDirectory(source, backup);
			else
				FileIO.copyFile(source, backup);
			manifest.plugins.add(new PluginBackupEntry(plugin.getID(), source.getAbsolutePath(), safeName,
					sha256(backup), source.isDirectory()));
		}
		FileIO.writeStringToFile(GSON.toJson(manifest), new File(folder, "manifest.json"));
		JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.plugins.backup_done", folder.getAbsolutePath()),
				L10N.t("remark.center.tab.plugins"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void restorePlugins() {
		JFileChooser chooser = new JFileChooser(new File(remarkFolder(), "plugin-backups"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle(L10N.t("remark.center.plugins.restore"));
		if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION)
			return;
		File folder = chooser.getSelectedFile();
		File manifestFile = new File(folder, "manifest.json");
		try {
			PluginBackupManifest manifest = GSON.fromJson(FileIO.readFileToString(manifestFile),
					PluginBackupManifest.class);
			if (manifest == null || manifest.plugins == null)
				throw new IOException(L10N.t("remark.center.plugins.invalid_backup"));
			int restored = 0;
			int skipped = 0;
			for (PluginBackupEntry entry : manifest.plugins) {
				File source = new File(folder, entry.backupName());
				File target = new File(entry.originalPath());
				if (!source.exists() || !sha256(source).equals(entry.sha256()) || !isAllowedPluginTarget(target)) {
					skipped++;
					continue;
				}
				if (entry.directory()) {
					if (target.isDirectory())
						FileIO.deleteDir(target);
					FileIO.copyDirectory(source, target);
				} else
					FileIO.copyFile(source, target);
				restored++;
			}
			JOptionPane.showMessageDialog(dialog,
					L10N.t("remark.center.plugins.restore_done_detail", restored, skipped),
					L10N.t("remark.center.tab.plugins"), JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.plugins.restore_failed", e.getMessage()),
					L10N.t("remark.center.tab.plugins"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void refreshRelease() {
		List<GeneratorConfiguration> selected = releaseTargets.getSelectedValuesList();
		List<String> lines = new ArrayList<>();
		lines.add(L10N.t("remark.center.release.current", mcreator.getGenerator().getGeneratorName()));
		lines.add(L10N.t("remark.center.release.selected", selected.size()));
		for (GeneratorConfiguration target : selected) {
			RemarkCompatibilityService.Report compatibility = RemarkCompatibilityService.analyze(mcreator.getWorkspace(),
					target);
			lines.add("[" + compatibility.level() + "] " + target.getGeneratorName() + " | blocked="
					+ compatibility.blocked() + " | review=" + compatibility.review());
		}
		lines.add("");
		lines.add(L10N.t("remark.center.release.naming", suggestedReleaseName()));
		lines.add(L10N.t("remark.center.release.package_status", lastMatrixResult == null ?
				L10N.t("remark.center.release.no_matrix") :
				(lastMatrixResult.success() ? L10N.t("remark.center.release.matrix_ready") :
						L10N.t("remark.center.release.matrix_failed"))));
		lines.add(L10N.t("remark.center.release.queue_hint"));
		releaseReport.setText(String.join("\n", lines));
		releaseReport.setCaretPosition(0);
	}

	private void saveReleaseProfile() {
		persistReleaseProfile(true);
	}

	private void persistReleaseProfile(boolean notify) {
		ReleaseProfile profile = new ReleaseProfile();
		profile.targets = releaseTargets.getSelectedValuesList().stream().map(GeneratorConfiguration::getGeneratorName)
				.toList();
		profile.autoPackage = releaseAutoPackage.isSelected();
		profile.includeSource = releaseIncludeSource.isSelected();
		File target = releaseProfileFile();
		FileIO.writeStringToFile(GSON.toJson(profile), target);
		refreshRelease();
		if (notify)
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.release.saved", target.getAbsolutePath()),
					L10N.t("remark.center.tab.release"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void loadReleaseProfile() {
		ReleaseProfile profile = null;
		try {
			if (releaseProfileFile().isFile())
				profile = GSON.fromJson(FileIO.readFileToString(releaseProfileFile()), ReleaseProfile.class);
		} catch (Exception ignored) {
			// A malformed profile should not prevent the Center from opening.
		}
		List<String> targets = profile != null && profile.targets != null && !profile.targets.isEmpty() ? profile.targets :
				List.of(mcreator.getGenerator().getGeneratorName());
		releaseAutoPackage.setSelected(profile == null || profile.autoPackage);
		releaseIncludeSource.setSelected(profile != null && profile.includeSource);
		List<Integer> selected = new ArrayList<>();
		for (int i = 0; i < releaseTargetModel.size(); i++) {
			if (targets.contains(releaseTargetModel.get(i).getGeneratorName()))
				selected.add(i);
		}
		releaseTargets.setSelectedIndices(selected.stream().mapToInt(Integer::intValue).toArray());
	}

	private void writeReleaseManifest() {
		persistReleaseProfile(false);
		JsonObject manifest = new JsonObject();
		manifest.addProperty("formatVersion", 1);
		manifest.addProperty("modId", mcreator.getWorkspaceSettings().getModID());
		manifest.addProperty("name", mcreator.getWorkspaceSettings().getModName());
		manifest.addProperty("version", mcreator.getWorkspaceSettings().getVersion());
		manifest.addProperty("author", mcreator.getWorkspaceSettings().getAuthor());
		manifest.addProperty("license", mcreator.getWorkspaceSettings().getLicense());
		manifest.addProperty("suggestedFileName", suggestedReleaseName());
		JsonArray generators = new JsonArray();
		for (GeneratorConfiguration generator : releaseTargets.getSelectedValuesList())
			generators.add(generator.getGeneratorName());
		manifest.add("targets", generators);
		manifest.add("requiredMods", GSON.toJsonTree(mcreator.getWorkspaceSettings().getRequiredMods()));
		manifest.add("dependencies", GSON.toJsonTree(mcreator.getWorkspaceSettings().getDependencies()));
		File target = new File(remarkFolder(), "release/release-manifest.json");
		FileIO.writeStringToFile(GSON.toJson(manifest), target);
		JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.release.manifest_done", target.getAbsolutePath()),
				L10N.t("remark.center.tab.release"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void buildCurrentRelease() {
		if (!releaseTargets.getSelectedValuesList().contains(mcreator.getGeneratorConfiguration())) {
			int answer = JOptionPane.showConfirmDialog(dialog, L10N.t("remark.center.release.current_not_selected"),
					L10N.t("remark.center.tab.release"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.YES_OPTION)
				return;
		}
		writeReleaseManifest();
		mcreator.getActionRegistry().exportToJAR.doAction();
	}

	private void runSelectedBuildMatrix() {
		List<GeneratorConfiguration> selected = releaseTargets.getSelectedValuesList();
		if (selected.isEmpty()) {
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.release.no_targets"),
					L10N.t("remark.center.tab.release"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		List<RemarkCompatibilityService.Report> blockedTargets = RemarkCompatibilityService
				.analyzeAll(mcreator.getWorkspace(), selected).stream()
				.filter(report -> report.level() == RemarkCompatibilityService.Level.BLOCKED).toList();
		if (!blockedTargets.isEmpty()) {
			String details = blockedTargets.stream().map(RemarkCompatibilityService.Report::details)
					.collect(Collectors.joining("\n\n"));
			releaseReport.setText(L10N.t("remark.center.release.preflight_blocked") + "\n\n" + details);
			releaseReport.setCaretPosition(0);
			return;
		}
		Object[] options = { L10N.t("remark.center.release.matrix_full"),
				L10N.t("remark.center.release.matrix_generate"), L10N.t("common.cancel") };
		int mode = JOptionPane.showOptionDialog(dialog, L10N.t("remark.center.release.matrix_prompt"),
				L10N.t("remark.center.release.build_matrix"), JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
		if (mode < 0 || mode > 1)
			return;
		if (!closeEditorsBeforeBulkAction())
			return;

		boolean fullBuild = mode == 0;
		boolean autoPackage = releaseAutoPackage.isSelected();
		boolean includeSource = releaseIncludeSource.isSelected();
		releaseReport.setText(L10N.t("remark.center.release.matrix_start", selected.size()));
		new Thread(() -> {
			RemarkBuildMatrixService.MatrixResult result = RemarkBuildMatrixService.run(mcreator.getWorkspace(),
					selected, fullBuild, new File(remarkFolder(), "release"), this::appendReleaseProgress);
			lastMatrixResult = result;
			List<String> summary = new ArrayList<>();
			summary.add(L10N.t("remark.center.release.matrix_done", result.passed(), result.failed()));
			summary.add(L10N.t("remark.center.release.matrix_output", result.outputFolder().getAbsolutePath()));
			for (RemarkWorkspaceSandbox.VerificationResult verification : result.results()) {
				summary.add("");
				summary.add((verification.success() ? "[PASS] " : "[FAIL] ") + verification.generator());
				 summary.addAll(verification.lines());
			}
			if (autoPackage && fullBuild && result.success()) {
				try {
					RemarkReleasePackager.PackageResult packaged = RemarkReleasePackager.packageRelease(
							mcreator.getWorkspace(), result, includeSource);
					summary.add("");
					summary.add(L10N.t("remark.center.release.package_done", packaged.archive().getAbsolutePath()));
				} catch (Exception e) {
					summary.add("");
					summary.add(L10N.t("remark.center.release.package_failed", readableMessage(e)));
				}
			}
			SwingUtilities.invokeLater(() -> {
				releaseReport.setText(String.join("\n", summary));
				releaseReport.setCaretPosition(0);
			});
		}, "RemarkStudio-BuildMatrix").start();
	}

	private void packageLastBuild() {
		if (lastMatrixResult == null) {
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.release.no_matrix"),
					L10N.t("remark.center.tab.release"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		boolean includeSource = releaseIncludeSource.isSelected();
		appendReleaseProgress(L10N.t("remark.center.release.packaging"));
		new Thread(() -> {
			try {
				RemarkReleasePackager.PackageResult packaged = RemarkReleasePackager.packageRelease(
						mcreator.getWorkspace(), lastMatrixResult, includeSource);
				appendReleaseProgress(L10N.t("remark.center.release.package_done",
						packaged.archive().getAbsolutePath()));
			} catch (Exception e) {
				appendReleaseProgress(L10N.t("remark.center.release.package_failed", readableMessage(e)));
			}
		}, "RemarkStudio-ReleasePackager").start();
	}

	private void appendReleaseProgress(String message) {
		SwingUtilities.invokeLater(() -> {
			String current = releaseReport.getText();
			releaseReport.setText(current + (current.isBlank() ? "" : "\n") + message);
			releaseReport.setCaretPosition(releaseReport.getDocument().getLength());
		});
	}

	private void moveToNextReleaseTarget() {
		List<GeneratorConfiguration> selected = releaseTargets.getSelectedValuesList();
		if (selected.isEmpty()) {
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.release.no_targets"),
					L10N.t("remark.center.tab.release"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String current = mcreator.getGenerator().getGeneratorName();
		int currentIndex = -1;
		for (int i = 0; i < selected.size(); i++) {
			if (selected.get(i).getGeneratorName().equals(current)) {
				currentIndex = i;
				break;
			}
		}
		GeneratorConfiguration next = currentIndex < 0 ? selected.getFirst() :
				currentIndex + 1 < selected.size() ? selected.get(currentIndex + 1) : null;
		if (next == null) {
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.release.no_next"),
					L10N.t("remark.center.tab.release"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		startMigration(next);
	}

	private String suggestedReleaseName() {
		String loader = mcreator.getGeneratorConfiguration().getGeneratorFlavor().name().toLowerCase(Locale.ROOT);
		return mcreator.getWorkspaceSettings().getModID() + "-" + mcreator.getWorkspaceSettings().getVersion() + "-"
				+ loader + "-" + mcreator.getGenerator().getGeneratorMinecraftVersion() + ".jar";
	}

	private List<GeneratorConfiguration> javaGenerators() {
		return Generator.GENERATOR_CACHE.values().stream()
				.filter(generator -> generator.getGeneratorFlavor().getBaseLanguage() == GeneratorFlavor.BaseLanguage.JAVA)
				.filter(generator -> generator.getGeneratorFlavor().getGamePlatform()
						== GeneratorFlavor.GamePlatform.JAVAEDITION)
				.sorted(Comparator.comparing(GeneratorConfiguration::getGeneratorMinecraftVersion).reversed()
						.thenComparing(generator -> generator.getGeneratorFlavor().name()))
				.toList();
	}

	private void openLocalHistory() {
		mcreator.getLeftDockRegion().setDockVisibility(MCreator.DOCK_LOCAL_HISTORY, true);
		dialog.toBack();
	}

	private boolean closeEditorsBeforeBulkAction() {
		boolean hasEditors = mcreator.getTabs().getTabs().stream().anyMatch(tab -> tab.isCloseable());
		if (!hasEditors)
			return true;
		int answer = JOptionPane.showConfirmDialog(dialog, L10N.t("remark.center.editors.close_confirm"),
				L10N.t("remark.center.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.OK_OPTION)
			return false;
		mcreator.getTabs().closeAllTabs(false);
		if (mcreator.getTabs().getTabs().stream().anyMatch(tab -> tab.isCloseable())) {
			JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.editors.close_cancelled"),
					L10N.t("remark.center.title"), JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		return true;
	}

	private void saveReport(String name, String content) {
		File target = new File(remarkFolder(), "reports/" + name + "-" + FILE_TIME.format(LocalDateTime.now()) + ".txt");
		FileIO.writeStringToFile(content, target);
		JOptionPane.showMessageDialog(dialog, L10N.t("remark.center.report_saved", target.getAbsolutePath()),
				L10N.t("remark.center.title"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void showError(String titleKey, Exception exception) {
		JOptionPane.showMessageDialog(dialog, readableMessage(exception), L10N.t(titleKey),
				JOptionPane.ERROR_MESSAGE);
	}

	private File remarkFolder() {
		return new File(mcreator.getWorkspaceFolder(), ".remarkstudio");
	}

	private File releaseProfileFile() {
		return new File(remarkFolder(), "release/release-profile.json");
	}

	private static JPanel reportTab(JTextArea area, Component... additions) {
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		panel.add(new JScrollPane(area), BorderLayout.CENTER);
		if (additions.length == 1)
			panel.add(additions[0], BorderLayout.SOUTH);
		else if (additions.length > 1) {
			panel.add(additions[0], BorderLayout.NORTH);
			panel.add(additions[1], BorderLayout.SOUTH);
		}
		return panel;
	}

	private static JPanel actionBar(Component... components) {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 4));
		for (Component component : components)
			panel.add(component);
		return panel;
	}

	private static JButton button(String key, Runnable action) {
		JButton button = L10N.button(key);
		button.addActionListener(_ -> action.run());
		return button;
	}

	private static JTextArea reportArea() {
		JTextArea area = new JTextArea();
		area.setEditable(false);
		area.setLineWrap(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		return area;
	}

	private static void copyText(String text) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new java.awt.datatransfer.StringSelection(text == null ? "" : text), null);
	}

	private static int textWidth(String text) {
		int width = 0;
		for (String line : (text == null ? "" : text).split("\\R", -1))
			width = Math.max(width, (int) Math.ceil(WYSIWYG.fontMC.getStringBounds(line, WYSIWYG.frc).getWidth()));
		return width;
	}

	private static int textHeight(String text) {
		int lineHeight = Math.max(1,
				(int) Math.ceil(WYSIWYG.fontMC.getStringBounds("Ag", WYSIWYG.frc).getHeight()));
		return lineHeight * (text == null ? "" : text).split("\\R", -1).length;
	}

	private static int clamp(int value, int minimum, int maximum) {
		int result = Math.max(minimum, value);
		return maximum > 0 ? Math.min(maximum, result) : result;
	}

	private static String readableMessage(Exception exception) {
		Throwable current = exception;
		while (current.getCause() != null)
			current = current.getCause();
		String message = current.getMessage();
		return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
	}

	private static void collectFiles(File root, Set<File> target) {
		if (root == null || !root.isDirectory())
			return;
		try (var paths = Files.walk(root.toPath())) {
			paths.filter(Files::isRegularFile).map(Path::toFile).forEach(target::add);
		} catch (IOException ignored) {
		}
	}

	private static String sha256(File file) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			if (file.isDirectory()) {
				try (var paths = Files.walk(file.toPath())) {
					for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
						digest.update(file.toPath().relativize(path).toString().getBytes(StandardCharsets.UTF_8));
						digest.update(Files.readAllBytes(path));
					}
				}
			} else {
				digest.update(Files.readAllBytes(file.toPath()));
			}
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (Exception ignored) {
			return "";
		}
	}

	private static boolean isAllowedPluginTarget(File target) {
		Path targetPath = target.toPath().toAbsolutePath().normalize();
		List<Path> roots = List.of(new File("plugins").toPath().toAbsolutePath().normalize(),
				new File(System.getProperty("user.home"), ".mcreator/plugins").toPath().toAbsolutePath().normalize(),
				new File(System.getProperty("user.home"), ".mcreator/plugin").toPath().toAbsolutePath().normalize());
		return roots.stream().anyMatch(root -> !targetPath.equals(root) && targetPath.startsWith(root));
	}

	private enum Health {
		READY, EXPERIMENTAL, UNSUPPORTED
	}

	private record Support(Health health, String reason) {
		String label() {
			return switch (health) {
				case READY -> L10N.t("remark.center.status.ready");
				case EXPERIMENTAL -> L10N.t("remark.center.status.experimental");
				case UNSUPPORTED -> L10N.t("remark.center.status.unsupported");
			};
		}
	}

	private static final class ReadOnlyTableModel extends DefaultTableModel {
		@Override public boolean isCellEditable(int row, int column) {
			return false;
		}
	}

	private static final class PluginBackupManifest {
		String createdAt;
		List<PluginBackupEntry> plugins = new ArrayList<>();
	}

	private record PluginBackupEntry(String id, String originalPath, String backupName, String sha256,
			boolean directory) {}

	private static final class ReleaseProfile {
		int formatVersion = 2;
		List<String> targets = new ArrayList<>();
		boolean autoPackage = true;
		boolean includeSource;
	}
}
