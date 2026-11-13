/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.ui.remark;

import net.mcreator.element.ModElementType;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.ReferencesFinder;
import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public final class RemarkSafeOperations {
	private RemarkSafeOperations() {}
	public static List<String> blockers(Workspace workspace, Collection<ModElement> selected) {
		Set<String> names = new TreeSet<>();
		for (ModElement element : selected) for (ModElement reference : ReferencesFinder.searchModElementUsages(workspace, element))
			if (!selected.contains(reference)) names.add(reference.getName());
		for (ModElement element : workspace.getModElements()) if (element.isCodeLocked() || element.getType() == ModElementType.CODE)
			names.add(element.getName() + " (" + L10N.t("remark.workflow.custom_code") + ")");
		workspace.getTagElements().forEach((tag, entries) -> {
			for (var entry : entries) for (var element : selected)
				if (element.getName().equals(net.mcreator.generator.GeneratorWrapper.getElementPlainName(entry.name()))
						&& !element.getName().equals(entry.owner())) names.add("Tag: " + tag.resourcePath());
		});
		return List.copyOf(names);
	}

	public static File backup(Workspace workspace) throws Exception {
		Path root = workspace.getWorkspaceFolder().toPath().toRealPath();
		Path directory = root.resolve(".remarkstudio/operation-backups/" + UUID.randomUUID());
		Files.createDirectories(directory);
		File result;
		try (RemarkWorkspaceSandbox sandbox = RemarkWorkspaceSandbox.createAt(workspace, workspace.getGeneratorConfiguration(), directory)) {
			sandbox.keepOnClose();
			result = sandbox.getWorkspace().getFileManager().getWorkspaceFile();
		}
		Path pointer = root.resolve(".remarkstudio/last-operation-backup.txt");
		Path temp = Files.createTempFile(pointer.getParent(), "last-backup-", ".tmp");
		Files.writeString(temp, root.relativize(result.toPath()).toString());
		Files.move(temp, pointer, StandardCopyOption.REPLACE_EXISTING);
		return result;
	}

	public static File lastBackup(Workspace workspace) throws IOException {
		Path root = workspace.getWorkspaceFolder().toPath().toRealPath();
		Path target = root.resolve(Files.readString(root.resolve(".remarkstudio/last-operation-backup.txt"))).normalize();
		if (!target.startsWith(root.resolve(".remarkstudio/operation-backups")) || !Files.isRegularFile(target)
				|| !target.toRealPath().startsWith(root)) throw new IOException("Invalid backup path");
		return target.toFile();
	}

	public static ModElement rename(Workspace workspace, ModElement original, String name) throws Exception {
		if (!name.matches("[A-Z][A-Za-z0-9_]{0,79}") || workspace.getModElements().stream().anyMatch(e -> e.getName().equalsIgnoreCase(name)))
			throw new IOException(L10N.t("remark.workflow.rename_invalid"));
		List<String> blockers = blockers(workspace, List.of(original));
		if (!blockers.isEmpty()) throw new IOException(L10N.t("remark.workflow.used_by") + "\n" + String.join("\n", blockers));
		var oldData = original.getGeneratableElement();
		if (oldData == null || original.isCodeLocked()) throw new IOException("Element cannot be safely renamed");
		if (ReferencesFinder.searchModElementUsages(workspace, original, true).contains(original))
			throw new IOException(L10N.t("remark.workflow.used_by") + "\n" + original.getName());
		var manager = workspace.getModElementManager();
		ModElement renamed = new ModElement(workspace, original, name);
		renamed.setRegistryName(original.getRegistryName());
		var newData = manager.fromJSONtoGeneratableElementOrNull(manager.generatableElementToJSON(oldData), renamed);
		if (newData == null) throw new IOException("Element cannot be decoded");
		workspace.getGenerator().generateElement(newData, false, false);
		backup(workspace);
		Set<String> dependencies = new HashSet<>(workspace.getWorkspaceSettings().getMCreatorDependenciesRaw());
		try {
			workspace.removeModElement(original);
			workspace.addModElement(renamed); manager.storeModElement(newData);
			workspace.getWorkspaceSettings().setMCreatorDependencies(dependencies);
			workspace.getGenerator().runResourceSetupTasks();
			workspace.getGenerator().generateElement(newData, true);
			workspace.getGenerator().generateBase(true);
			workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			return renamed;
		} catch (Exception failure) {
			try {
				workspace.removeModElement(renamed); workspace.addModElement(original); manager.storeModElement(oldData);
				workspace.getWorkspaceSettings().setMCreatorDependencies(dependencies);
				workspace.getGenerator().generateElement(oldData, true); workspace.getGenerator().generateBase(true);
				workspace.getFileManager().saveWorkspaceDirectlyAndWait();
			} catch (Exception recovery) { failure.addSuppressed(recovery); }
			throw failure;
		}
	}

	public static boolean confirmAndBackup(MCreator mcreator, Collection<ModElement> selected) {
		List<String> uses = blockers(mcreator.getWorkspace(), selected);
		if (!uses.isEmpty()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.workflow.used_by") + "\n" + String.join("\n", uses)); return false;
		}
		if (hasOpenEditors(mcreator)) return false;
		JDialog progress = progressDialog(mcreator);
		boolean[] success = { false };
		new SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() throws Exception { backup(mcreator.getWorkspace()); return null; }
			@Override protected void done() {
				progress.dispose();
				try { get(); success[0] = true; }
				catch (Exception e) { JOptionPane.showMessageDialog(mcreator, L10N.t("remark.workflow.backup_failed") + "\n" + e.getMessage()); }
			}
		}.execute();
		progress.setVisible(true);
		return success[0];
	}

	public static void showRename(MCreator mcreator, ModElement element) {
		if (hasOpenEditors(mcreator)) return;
		String name = JOptionPane.showInputDialog(mcreator, L10N.t("remark.workflow.rename_prompt"), element.getName());
		if (name == null || name.equals(element.getName())) return;
		JDialog progress = progressDialog(mcreator);
		new SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() throws Exception { rename(mcreator.getWorkspace(), element, name.trim()); return null; }
			@Override protected void done() {
				progress.dispose();
				try { get(); mcreator.reloadWorkspaceTabContents(); }
				catch (Exception e) { JOptionPane.showMessageDialog(mcreator, e.getMessage()); }
			}
		}.execute();
		progress.setVisible(true);
	}

	private static JDialog progressDialog(MCreator mcreator) {
		JDialog dialog = new JDialog(mcreator, L10N.t("remark.workflow.backing_up"), true);
		JProgressBar progress = new JProgressBar(); progress.setIndeterminate(true);
		dialog.add(progress); dialog.setSize(360, 90); dialog.setLocationRelativeTo(mcreator);
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		return dialog;
	}

	public static void openBackup(MCreator mcreator) {
		try { mcreator.getApplication().openWorkspaceInMCreator(lastBackup(mcreator.getWorkspace())); }
		catch (Exception e) { JOptionPane.showMessageDialog(mcreator, L10N.t("remark.workflow.no_backup") + "\n" + e.getMessage()); }
	}

	private static boolean hasOpenEditors(MCreator mcreator) {
		if (mcreator.getTabs().getTabs().stream().anyMatch(tab -> tab.getContent() instanceof net.mcreator.ui.modgui.ModElementGUI<?>)) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.workflow.close_editors")); return true;
		}
		return false;
	}
}
