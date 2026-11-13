/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.ui.remark;

import net.mcreator.element.types.Item;
import net.mcreator.generator.behavior.SimpleItemBehaviorCompiler;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class RemarkProblemPanel extends JPanel {
	public record Problem(String element, String message) {}
	private final MCreator mcreator;
	private final DefaultTableModel model = new DefaultTableModel(new String[] { L10N.t("remark.workflow.element"), L10N.t("remark.workflow.problem") }, 0) {
		@Override public boolean isCellEditable(int row, int column) { return false; }
	};
	private final JTable table = new JTable(model);
	private List<Problem> problems = List.of();
	public RemarkProblemPanel(MCreator mcreator) {
		super(new BorderLayout(6, 6)); this.mcreator = mcreator;
		table.setAutoCreateRowSorter(true); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		add(new JScrollPane(table), BorderLayout.CENTER);
		JButton go = L10N.button("remark.workflow.fix_here"); go.addActionListener(_ -> openSelected());
		JButton refresh = L10N.button("remark.center.refresh"); refresh.addActionListener(_ -> refresh());
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING)); buttons.add(go); buttons.add(refresh);
		add(buttons, BorderLayout.SOUTH);
		table.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) openSelected(); }
		});
		refresh();
	}

	public static List<Problem> analyze(MCreator mcreator) {
		List<Problem> result = new ArrayList<>();
		for (var element : mcreator.getWorkspace().getModElements()) {
			if (element.getGeneratableElement() instanceof Item item) {
				for (var problem : SimpleItemBehaviorCompiler.validate(item, mcreator.getGenerator().getGeneratorName()))
					result.add(new Problem(element.getName(), L10N.t(problem.translationKey())));
				if (item.name == null || item.name.isBlank()) result.add(new Problem(element.getName(), L10N.t("elementgui.item.error_item_needs_name")));
				if (item.texture == null || item.texture.isEmpty()) result.add(new Problem(element.getName(), L10N.t("elementgui.item.error_item_needs_texture")));
			}
			if (!element.doesCompile()) result.add(new Problem(element.getName(), "Build failed: " + element.getName()));
		}
		return List.copyOf(result);
	}

	public void refresh() {
		problems = analyze(mcreator); model.setRowCount(0);
		for (Problem problem : problems) model.addRow(new Object[] { problem.element(), problem.message() });
		if (!problems.isEmpty()) table.setRowSelectionInterval(0, 0);
	}
	private void openSelected() {
		if (table.getSelectedRow() < 0) return;
		Problem problem = problems.get(table.convertRowIndexToModel(table.getSelectedRow()));
		openProblem(mcreator, problem);
	}
	public static net.mcreator.ui.modgui.ModElementGUI<?> openProblem(MCreator mcreator, Problem problem) {
		var element = mcreator.getWorkspace().getModElementByName(problem.element());
		if (element == null || element.isCodeLocked()) return null;
		for (var tab : mcreator.getTabs().getTabs())
			if (tab.getContent() instanceof net.mcreator.ui.modgui.ModElementGUI<?> existing && existing.getModElement().equals(element)) {
				mcreator.getTabs().showTabNoNotify(tab);
				existing.goToProblem(problem.message()); return existing;
			}
		var editor = element.getType().getModElementGUI(mcreator, element, true);
		if (editor != null && editor.showView() instanceof net.mcreator.ui.modgui.ModElementGUI<?> shown) {
			shown.goToProblem(problem.message()); return shown;
		}
		return null;
	}
	public static void show(MCreator mcreator) {
		JDialog dialog = new JDialog(mcreator, L10N.t("remark.workflow.problem"), false);
		dialog.setContentPane(new RemarkProblemPanel(mcreator)); dialog.setSize(800, 400);
		dialog.setLocationRelativeTo(mcreator); dialog.setVisible(true);
	}
}
