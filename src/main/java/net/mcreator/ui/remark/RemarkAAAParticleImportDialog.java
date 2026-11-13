package net.mcreator.ui.remark;

import net.mcreator.element.ModElementType;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.TranslatedComboBox;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.ProgressDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.modgui.util.ComponentFromAnnotation;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Clipboard;
import java.awt.event.InputEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RemarkAAAParticleImportDialog {

	private RemarkAAAParticleImportDialog() {}

	public static void choose(MCreator mcreator) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(L10N.t("elementgui.aaaparticle.quick_import"));
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setFileFilter(new FileNameExtensionFilter("Effekseer (.efkefc)", "efkefc"));
		if (chooser.showOpenDialog(mcreator) == JFileChooser.APPROVE_OPTION)
			show(mcreator, chooser.getSelectedFiles());
	}

	public static void installDropTarget(JComponent target, MCreator mcreator) {
		if (Boolean.TRUE.equals(target.getClientProperty("remake.aaa.drop")))
			return;
		target.putClientProperty("remake.aaa.drop", true);
		TransferHandler previous = target.getTransferHandler();
		target.setTransferHandler(new TransferHandler() {
			@Override public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
						|| previous != null && previous.canImport(support);
			}
			@Override public boolean importData(TransferSupport support) {
				if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
					return previous != null && previous.importData(support);
				try {
					List<?> values = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					File[] files = values.stream().filter(File.class::isInstance).map(File.class::cast)
							.filter(file -> file.isDirectory() || RemarkAAAParticleImporter.isEffect(file.toPath()))
							.toArray(File[]::new);
					if (files.length == 0)
						return false;
					SwingUtilities.invokeLater(() -> show(mcreator, files));
					return true;
				} catch (Exception e) {
					return false;
				}
			}
			@Override public int getSourceActions(JComponent component) {
				return previous == null ? NONE : previous.getSourceActions(component);
			}
			@Override public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
				if (previous != null) previous.exportToClipboard(component, clipboard, action);
			}
			@Override public void exportAsDrag(JComponent component, InputEvent event, int action) {
				if (previous != null) previous.exportAsDrag(component, event, action);
			}
		});
	}

	public static void show(MCreator mcreator, File[] files) {
		if (!mcreator.getGeneratorStats().getSupportedModElementTypes().contains(ModElementType.AAAPARTICLE)) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.aaaparticle.unsupported_generator"));
			return;
		}
		TranslatedComboBox mode = ComponentFromAnnotation.translatedOptions(AAAParticle.class,
				"spawnMode", "elementgui.aaaparticle.spawn_mode.");
		JPanel options = PanelUtils.column(8, L10N.label("elementgui.aaaparticle.spawn_mode"), mode);
		if (JOptionPane.showConfirmDialog(mcreator, options, L10N.t("elementgui.aaaparticle.quick_import"),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		String selectedMode = (String) mode.getSelectedItem();
		ProgressDialog progress = new ProgressDialog(mcreator, L10N.t("elementgui.aaaparticle.quick_import"));
		new SwingWorker<List<String>, Void>() {
			@Override protected List<String> doInBackground() throws Exception {
				List<Path> effects = RemarkAAAParticleImporter.findEffects(Arrays.stream(files).map(File::toPath).toList());
				List<AAAParticle> created = new ArrayList<>();
				List<String> report = new ArrayList<>();
				for (Path effect : effects) {
					ProgressDialog.ProgressUnit unit = new ProgressDialog.ProgressUnit(effect.getFileName().toString());
					progress.addProgressUnit(unit);
					try {
						AAAParticle particle = RemarkAAAParticleImporter.create(mcreator.getWorkspace(), effect, selectedMode);
						created.add(particle);
						report.add("/particle " + mcreator.getWorkspaceSettings().getModID() + ":"
								+ particle.getModElement().getRegistryName());
						unit.markStateOk();
					} catch (Exception e) {
						report.add(effect.getFileName() + ": " + e.getMessage());
						unit.markStateError();
					}
				}
				if (!created.isEmpty()) {
					mcreator.getGenerator().generateBase(true);
					for (AAAParticle particle : created) {
						mcreator.getGenerator().generateElement(particle, true);
						mcreator.getModElementManager().storeModElement(particle);
					}
					mcreator.getWorkspace().getFileManager().saveWorkspaceDirectlyAndWait();
				}
				return report;
			}
			@Override protected void done() {
				progress.dispose();
				mcreator.reloadWorkspaceTabContents();
				try {
					JTextArea report = new JTextArea(String.join("\n", get()), 12, 52);
					report.setEditable(false);
					JOptionPane.showMessageDialog(mcreator, new JScrollPane(report),
							L10N.t("elementgui.aaaparticle.quick_import"), JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception e) {
					JOptionPane.showMessageDialog(mcreator, e.getCause() == null ? e.getMessage() : e.getCause().getMessage(),
							L10N.t("elementgui.aaaparticle.import_error"), JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
		progress.setVisible(true);
	}
}
