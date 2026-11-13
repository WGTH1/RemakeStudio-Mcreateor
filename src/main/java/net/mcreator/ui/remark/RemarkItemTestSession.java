/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.ui.remark;

import com.google.gson.JsonObject;
import net.mcreator.element.types.Item;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.workspace.Workspace;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.gradle.tooling.GradleConnector;

public final class RemarkItemTestSession {
	private static final Set<File> RUNNING = ConcurrentHashMap.newKeySet();
	private RemarkItemTestSession() {}

	public static void show(MCreator mcreator, Item edited) {
		File origin = mcreator.getWorkspaceFolder().getAbsoluteFile();
		if (RUNNING.contains(origin)) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.workflow.test_running")); return;
		}
		if (JOptionPane.showConfirmDialog(mcreator, L10N.t("remark.workflow.test_confirm"),
				L10N.t("remark.workflow.test_item"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		if (!RUNNING.add(origin)) return;
		String definition = mcreator.getModElementManager().generatableElementToJSON(edited);
		String elementName = edited.getModElement().getName();
		String registry = edited.getModElement().getRegistryName();
		var cancellation = GradleConnector.newCancellationTokenSource();
		JDialog dialog = new JDialog(mcreator, L10N.t("remark.workflow.test_item"), false);
		JTextArea status = new JTextArea(9, 68);
		status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true);
		JButton stop = L10N.button("remark.workflow.stop_test");
		stop.addActionListener(_ -> { cancellation.cancel(); stop.setEnabled(false); });
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override public void windowClosing(java.awt.event.WindowEvent e) { cancellation.cancel(); dialog.dispose(); }
		});
		dialog.add(new JScrollPane(status), BorderLayout.CENTER); dialog.add(stop, BorderLayout.SOUTH);
		dialog.pack(); dialog.setLocationRelativeTo(mcreator); dialog.setVisible(true);
		new SwingWorker<Void, String>() {
			@Override protected Void doInBackground() throws Exception {
				try (RemarkWorkspaceSandbox sandbox = RemarkWorkspaceSandbox.create(mcreator.getWorkspace(), mcreator.getGeneratorConfiguration())) {
					sandbox.keepOnClose();
					publish(L10N.t("remark.workflow.test_folder", sandbox.getRoot()));
					Workspace workspace = sandbox.getWorkspace();
					var element = workspace.getModElementByName(elementName);
					if (element == null) {
						element = new net.mcreator.workspace.elements.ModElement(workspace, elementName, net.mcreator.element.ModElementType.ITEM);
						element.setRegistryName(registry); workspace.addModElement(element);
					}
					var item = workspace.getModElementManager().fromJSONtoGeneratableElementOrNull(definition, element);
					if (!(item instanceof Item)) throw new IOException("Unable to prepare item");
					workspace.getModElementManager().storeModElement(item);
					if (cancellation.token().isCancellationRequested()) return null;
					var target = workspace.getGeneratorConfiguration();
					net.mcreator.generator.setup.WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
					workspace.getGenerator().runResourceSetupTasks();
					File log = new File(sandbox.getRoot(), "remark-item-test.log");
					try (OutputStream output = new FileOutputStream(log)) {
						var connection = GradleUtils.getGradleProjectConnection(workspace);
						publish(L10N.t("remark.workflow.test_sync"));
						GradleUtils.getGradleSyncLauncher(target, connection).withCancellationToken(cancellation.token())
								.setStandardOutput(output).setStandardError(output).run();
						workspace.getGenerator().reloadGradleCaches();
						for (var modElement : workspace.getModElements()) {
							if (cancellation.token().isCancellationRequested()) return null;
							var data = modElement.getGeneratableElement();
							if (data != null) workspace.getGenerator().generateElement(data, true);
						}
						workspace.getGenerator().generateBase(true);
						writeGrantResources(workspace, registry);
						workspace.getFileManager().saveWorkspaceDirectlyAndWait();
						publish(L10N.t("remark.workflow.test_world"));
						String task = target.getGradleTaskFor("run_client");
						if (task == null) throw new IOException("This target has no client run task");
						GradleUtils.getGradleTaskLauncher(target, connection, task).withCancellationToken(cancellation.token())
								.setStandardOutput(output).setStandardError(output).run();
					}
				}
				return null;
			}
			@Override protected void process(java.util.List<String> messages) { for (String message : messages) status.append(message + "\n"); }
			@Override protected void done() {
				RUNNING.remove(origin); stop.setEnabled(false);
				try { get(); status.append(L10N.t("remark.workflow.test_finished")); }
				catch (Exception e) { status.append(cancellation.token().isCancellationRequested() ? L10N.t("remark.workflow.test_cancelled") : e.getMessage()); }
				dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			}
		}.execute();
	}

	public static void writeGrantResources(Workspace workspace, String registry) throws IOException {
		String modid = workspace.getWorkspaceSettings().getModID();
		if (!modid.matches("[a-z0-9_]+") || !registry.matches("[a-z0-9_]+")) throw new IOException("Invalid item identifier");
		String version = workspace.getGeneratorConfiguration().getGeneratorMinecraftVersion();
		boolean legacy = version.startsWith("1.20");
		Path root = workspace.getGenerator().getResourceRoot().toPath().resolve("data/remark_studio_test");
		Path function = root.resolve((legacy ? "functions" : "function") + "/grant.mcfunction");
		Path advancement = root.resolve((legacy ? "advancements" : "advancement") + "/first_join.json");
		Files.createDirectories(function.getParent()); Files.createDirectories(advancement.getParent());
		Files.writeString(function, "give @s " + modid + ":" + registry + " 1\n");
		JsonObject trigger = new JsonObject(); trigger.addProperty("trigger", "minecraft:tick");
		JsonObject criteria = new JsonObject(); criteria.add("enter", trigger);
		JsonObject rewards = new JsonObject(); rewards.addProperty("function", "remark_studio_test:grant");
		JsonObject data = new JsonObject(); data.add("criteria", criteria); data.add("rewards", rewards);
		Files.writeString(advancement, data.toString());
	}
}
