/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.ui.remark;

import com.google.gson.JsonParser;
import net.mcreator.generator.behavior.SimpleItemBehaviorCompiler;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.List;

public final class RemarkLoaderVersionDialog {
	public static final String CATALOG = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";
	private RemarkLoaderVersionDialog() {}
	public static List<String> parseCatalog(String json) {
		var versions = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("versions");
		List<String> result = new ArrayList<>();
		for (var entry : versions) {
			String value = entry.getAsString();
			if (value.matches("21\\.1\\.[0-9]{1,6}")) result.add(value);
		}
		return result.stream().distinct().sorted(Comparator.comparingInt((String v) -> Integer.parseInt(v.substring(5))).reversed()).toList();
	}

	public static List<String> fetchVersions() throws Exception {
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
			var request = HttpRequest.newBuilder(URI.create(CATALOG)).timeout(Duration.ofSeconds(25)).GET().build();
			var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
			try (var stream = response.body()) {
				if (response.statusCode() != 200) throw new java.io.IOException("Version catalog HTTP " + response.statusCode());
				byte[] data = stream.readNBytes(1_000_001);
				if (data.length > 1_000_000) throw new java.io.IOException("Version catalog is too large");
				return parseCatalog(new String(data, java.nio.charset.StandardCharsets.UTF_8));
			}
		}
	}

	public static void show(MCreator mcreator) {
		if (mcreator.getTabs().getTabs().stream().anyMatch(tab -> tab.getContent() instanceof net.mcreator.ui.modgui.ModElementGUI<?>)) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.workflow.close_editors")); return;
		}
		if (!SimpleItemBehaviorCompiler.supports(mcreator.getGenerator().getGeneratorName())) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("remark.workflow.loader_unavailable")); return;
		}
		JDialog dialog = new JDialog(mcreator, L10N.t("remark.workflow.loader_title"), true);
		JComboBox<String> versions = new JComboBox<>();
		versions.addItem(mcreator.getWorkspaceSettings().getLoaderBuildVersion());
		JTextArea log = new JTextArea(10, 65); log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
		log.setText(L10N.t("remark.workflow.loader_notice"));
		JButton refresh = L10N.button("remark.workflow.loader_fetch");
		JButton apply = L10N.button("remark.workflow.loader_apply");
		JButton cancel = L10N.button("common.cancel"); cancel.setEnabled(false);
		org.gradle.tooling.CancellationTokenSource[] running = { null };
		cancel.addActionListener(_ -> { if (running[0] != null) running[0].cancel(); cancel.setEnabled(false); });
		apply.setEnabled(false);
		JPanel controls = new JPanel(new FlowLayout()); controls.add(versions); controls.add(refresh); controls.add(apply); controls.add(cancel);
		dialog.add(controls, BorderLayout.NORTH); dialog.add(new JScrollPane(log), BorderLayout.CENTER);
		refresh.addActionListener(_ -> {
			refresh.setEnabled(false); apply.setEnabled(false);
			new SwingWorker<List<String>, Void>() {
				@Override protected List<String> doInBackground() throws Exception { return fetchVersions(); }
				@Override protected void done() {
					try {
						var list = get(); versions.removeAllItems(); list.forEach(versions::addItem);
						versions.setSelectedItem(mcreator.getWorkspaceSettings().getLoaderBuildVersion()); apply.setEnabled(!list.isEmpty());
					} catch (Exception e) { log.append("\n" + e.getMessage()); }
					refresh.setEnabled(true);
				}
			}.execute();
		});
		apply.addActionListener(_ -> {
			String selected = (String) versions.getSelectedItem();
			if (selected == null) return;
			var cancellation = org.gradle.tooling.GradleConnector.newCancellationTokenSource();
			running[0] = cancellation; cancel.setEnabled(true);
			apply.setEnabled(false); refresh.setEnabled(false); versions.setEnabled(false);
			dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			new SwingWorker<Void, String>() {
				@Override protected Void doInBackground() throws Exception {
					try (var sandbox = RemarkWorkspaceSandbox.create(mcreator.getWorkspace(), mcreator.getGeneratorConfiguration())) {
						sandbox.getWorkspace().getWorkspaceSettings().setLoaderBuildVersion(selected);
						var result = sandbox.verify(true, null, text -> publish(text), cancellation.token());
						if (!result.success()) throw new java.io.IOException(String.join("\n", result.lines()));
					}
					if (cancellation.token().isCancellationRequested()) throw new java.util.concurrent.CancellationException();
					RemarkSafeOperations.backup(mcreator.getWorkspace());
					if (cancellation.token().isCancellationRequested()) throw new java.util.concurrent.CancellationException();
					SwingUtilities.invokeAndWait(() -> cancel.setEnabled(false));
					String previous = mcreator.getWorkspaceSettings().getLoaderBuildVersion();
					try { applyVersion(mcreator, selected); }
					catch (Exception failure) {
						try { applyVersion(mcreator, previous); } catch (Exception recovery) { failure.addSuppressed(recovery); }
						throw failure;
					}
					return null;
				}
				@Override protected void process(List<String> chunks) {
					chunks.forEach(text -> log.append("\n" + text));
					if (log.getDocument().getLength() > 40000) log.setText(log.getText().substring(log.getText().length() - 30000));
				}
				@Override protected void done() {
					try { get(); log.append("\n" + L10N.t("remark.workflow.loader_ready")); }
					catch (Exception e) { log.append("\n" + e.getMessage()); }
					apply.setEnabled(true); refresh.setEnabled(true); versions.setEnabled(true);
					running[0] = null; cancel.setEnabled(false);
					dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
				}
			}.execute();
		});
		dialog.pack(); dialog.setLocationRelativeTo(mcreator); dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); dialog.setVisible(true);
	}

	private static void applyVersion(MCreator mcreator, String version) throws Exception {
		mcreator.getWorkspaceSettings().setLoaderBuildVersion(version);
		mcreator.getWorkspace().markDirty();
		WorkspaceGeneratorSetup.setupWorkspaceBase(mcreator.getWorkspace());
		mcreator.getGenerator().generateBase(true);
		mcreator.getWorkspace().getFileManager().saveWorkspaceDirectlyAndWait();
	}
}
