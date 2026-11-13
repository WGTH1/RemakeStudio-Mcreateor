/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.ui.remark;

import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

public final class RemarkAssetReviewPanel extends JPanel {
	public record Asset(File file, long bytes, String hash, String status, boolean removable) {}
	private final MCreator mcreator;
	private final DefaultTableModel model = new DefaultTableModel(new String[] { "", "Status", "File", "Bytes" }, 0) {
		@Override public Class<?> getColumnClass(int column) { return column == 0 ? Boolean.class : column == 3 ? Long.class : String.class; }
		@Override public boolean isCellEditable(int row, int column) { return column == 0 && rows.get(row).removable(); }
	};
	private final JTable table = new JTable(model);
	private final JLabel status = new JLabel();
	private final JButton scan = L10N.button("remark.center.refresh");
	private final JButton quarantine = L10N.button("remark.workflow.quarantine");
	private List<Asset> rows = List.of();

	public RemarkAssetReviewPanel(MCreator mcreator) {
		super(new BorderLayout(8, 8));
		this.mcreator = mcreator;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
		table.setRowSorter(sorter);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		table.getColumnModel().getColumn(0).setMaxWidth(36);
		table.getColumnModel().getColumn(1).setPreferredWidth(220);
		table.getColumnModel().getColumn(2).setPreferredWidth(540);
		JTextField filter = new JTextField();
		filter.setToolTipText(L10N.t("remark.workflow.filter"));
		filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			private void changed() {
				for (int i = 0; i < model.getRowCount(); i++) model.setValueAt(false, i, 0);
				sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(filter.getText()), 1, 2));
			}
			public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
		});
		add(filter, BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING));
		JButton restore = L10N.button("remark.center.assets.restore");
		JButton doctor = L10N.button("remark.center.assets.doctor");
		JButton importAssets = L10N.button("remark.center.assets.import");
		JButton undoImport = L10N.button("remark.center.assets.undo_import");
		importAssets.addActionListener(_ -> { RemarkStudioElementTools.chooseAndImportAssets(mcreator, null); refresh(); });
		undoImport.addActionListener(_ -> { RemarkStudioElementTools.undoLastImport(mcreator); refresh(); });
		JPanel importActions = new JPanel(new FlowLayout(FlowLayout.LEADING));
		importActions.add(importAssets); importActions.add(undoImport);
		actions.add(scan); actions.add(quarantine); actions.add(restore); actions.add(doctor); actions.add(status);
		JPanel footer = new JPanel(new GridLayout(0, 1)); footer.add(importActions); footer.add(actions);
		add(footer, BorderLayout.SOUTH);
		scan.addActionListener(_ -> refresh());
		doctor.addActionListener(_ -> RemarkStudioElementTools.showAssetDoctor(mcreator));
		restore.addActionListener(_ -> { RemarkStudioElementTools.restoreLastCleanup(mcreator); refresh(); });
		quarantine.addActionListener(_ -> quarantineSelected());
	}

	public void refresh() {
		scan.setEnabled(false); quarantine.setEnabled(false);
		status.setText(L10N.t("remark.workflow.scanning"));
		new SwingWorker<List<Asset>, Void>() {
			@Override protected List<Asset> doInBackground() throws Exception { return analyze(mcreator); }
			@Override protected void done() {
				try {
					rows = get(); model.setRowCount(0);
					Path root = mcreator.getWorkspaceFolder().toPath().toAbsolutePath().normalize();
					for (Asset row : rows) model.addRow(new Object[] { false, row.status(), root.relativize(row.file().toPath().toAbsolutePath()), row.bytes() });
					status.setText(L10N.t("remark.workflow.asset_count", rows.size()));
				} catch (Exception e) { status.setText(e.getMessage()); }
				scan.setEnabled(true); quarantine.setEnabled(true);
			}
		}.execute();
	}

	public static List<Asset> analyze(MCreator mcreator) throws Exception {
		Path root = mcreator.getWorkspaceFolder().toPath().toRealPath();
		Path resources = root.resolve("src/main/resources");
		if (!Files.isDirectory(resources)) return List.of();
		Set<Path> unused = new HashSet<>();
		for (File file : RemarkStudioElementTools.findUnusedAssetFiles(mcreator)) unused.add(file.toPath().toAbsolutePath().normalize());
		boolean customCode = mcreator.getWorkspace().getModElements().stream().anyMatch(e -> e.isCodeLocked()
				|| e.getType() == net.mcreator.element.ModElementType.CODE);
		List<Path> files;
		try (var stream = Files.walk(resources)) {
			files = stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
		}
		List<String> resourceDocuments = new ArrayList<>();
		long documentBytes = 0;
		for (Path file : files) if (file.toString().endsWith(".json")) {
			documentBytes += Files.size(file);
			if (Files.size(file) > 4 * 1024 * 1024 || documentBytes > 32 * 1024 * 1024) customCode = true;
			else resourceDocuments.add(Files.readString(file));
		}
		Map<Path, String> hashes = new LinkedHashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		for (Path file : files) {
			if (!file.toRealPath().startsWith(root)) throw new java.io.IOException("Resource is outside the workspace: " + file);
			String hash = hash(file); hashes.put(file, hash); counts.merge(hash, 1, Integer::sum);
		}
		List<Asset> result = new ArrayList<>();
		for (Path file : files) {
			String name = file.getFileName().toString();
			String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
			boolean mentioned = resourceDocuments.stream().anyMatch(text -> text.contains(stem));
			boolean removable = !customCode && unused.contains(file.toAbsolutePath().normalize()) && !mentioned;
			List<String> notes = new ArrayList<>();
			if (counts.get(hashes.get(file)) > 1) notes.add(L10N.t("remark.workflow.duplicate"));
			if (Files.size(file) > 4 * 1024 * 1024) notes.add(L10N.t("remark.workflow.large"));
			if (unused.contains(file.toAbsolutePath().normalize())) notes.add(L10N.t(removable ? "remark.workflow.unreferenced" : "remark.workflow.review_reference"));
			if (notes.isEmpty()) notes.add(L10N.t("remark.workflow.keep"));
			result.add(new Asset(file.toFile(), Files.size(file), hashes.get(file), String.join(" | ", notes), removable));
		}
		for (var element : mcreator.getWorkspace().getModElements()) {
			if (element.getGeneratableElement() instanceof net.mcreator.element.types.Item item && item.texture != null && !item.texture.isEmpty()) {
				try {
					File texture = item.texture.toFile(net.mcreator.ui.workspace.resources.TextureType.ITEM);
					if (!texture.isFile()) result.add(new Asset(texture, 0, "", L10N.t("remark.workflow.missing") + ": " + element.getName(), false));
				} catch (java.io.IOException ignored) { /* Vanilla textures do not have workspace files. */ }
			}
		}
		return List.copyOf(result);
	}

	public static String hash(Path file) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(file)) {
			byte[] buffer = new byte[65536];
			for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private void quarantineSelected() {
		List<Asset> selected = new ArrayList<>();
		for (int view = 0; view < table.getRowCount(); view++) {
			int row = table.convertRowIndexToModel(view);
			if (Boolean.TRUE.equals(model.getValueAt(row, 0)) && rows.get(row).removable()) selected.add(rows.get(row));
		}
		if (selected.isEmpty()) return;
		if (JOptionPane.showConfirmDialog(this, L10N.t("remark.workflow.quarantine_confirm", selected.size()),
				L10N.t("remark.workflow.quarantine"), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
		quarantine.setEnabled(false); scan.setEnabled(false);
		new SwingWorker<Void, Void>() {
			@Override protected Void doInBackground() throws Exception {
				Map<File, Asset> current = new HashMap<>();
				for (Asset asset : analyze(mcreator)) current.put(asset.file(), asset);
				for (Asset asset : selected) if (!current.containsKey(asset.file()) || !current.get(asset.file()).removable()
						|| !asset.hash().equals(current.get(asset.file()).hash())) throw new java.io.IOException(L10N.t("remark.workflow.changed_rescan"));
				RemarkCleanupService.quarantine(mcreator, new RemarkCleanupService.Plan(selected.stream().map(Asset::file).toList(),
						selected.stream().mapToLong(Asset::bytes).sum(), List.of(), 0, false));
				return null;
			}
			@Override protected void done() {
				try { get(); } catch (Exception e) { JOptionPane.showMessageDialog(RemarkAssetReviewPanel.this, e.getMessage()); }
				refresh();
			}
		}.execute();
	}
}
