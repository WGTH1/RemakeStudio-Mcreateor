package net.mcreator.ui.dialogs;

import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.io.UserFolderManager;
import net.mcreator.ui.init.UIRES;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class DisplayPresetControls extends JPanel {
	private final JComboBox<String> names = new JComboBox<>();
	DisplayPresetControls(Component parent, Supplier<Transform> reader, Consumer<Transform> writer) {
		super(new BorderLayout(5, 4));
		add(new JLabel("Saved presets"), BorderLayout.NORTH);
		add(names, BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		add(actions, BorderLayout.SOUTH);
		JButton load = new JButton(UIRES.get("16px.open")); load.setToolTipText("Apply selected preset");
		load.addActionListener(_ -> run(parent, () -> {
			if (names.getSelectedItem() != null) {
				Transform value = store().load((String) names.getSelectedItem());
				if (value != null) writer.accept(value);
			}
		}));
		JButton save = new JButton(UIRES.get("16px.add")); save.setToolTipText("Save current transform as a preset");
		save.addActionListener(_ -> run(parent, () -> {
			String name = JOptionPane.showInputDialog(parent, "Preset name");
			if (name == null) return;
			DisplayPresetStore store = store();
			if (store.load(name) != null && JOptionPane.showConfirmDialog(parent, "Replace preset: " + name + "?",
					"Preset", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
			store.save(name, reader.get()); refresh(); names.setSelectedItem(name);
		}));
		JButton delete = new JButton(UIRES.get("16px.delete")); delete.setToolTipText("Delete selected preset");
		delete.addActionListener(_ -> run(parent, () -> {
			String name = (String) names.getSelectedItem();
			if (name != null && JOptionPane.showConfirmDialog(parent, "Delete preset: " + name + "?",
					"Preset", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) { store().delete(name); refresh(); }
		}));
		actions.add(load); actions.add(save); actions.add(delete);
		try { refresh(); } catch (Exception e) { names.setToolTipText(e.getMessage()); }
	}
	private static DisplayPresetStore store() throws java.io.IOException {
		return new DisplayPresetStore(UserFolderManager.getFileFromUserFolder("remake-display-presets.json").toPath());
	}
	private void refresh() throws java.io.IOException {
		var presets = store().list(); names.removeAllItems(); presets.forEach(names::addItem);
	}
	private interface Action { void run() throws Exception; }
	private static void run(Component parent, Action action) {
		try { action.run(); } catch (Exception e) { JOptionPane.showMessageDialog(parent, e.getMessage(), "Preset error", JOptionPane.ERROR_MESSAGE); }
	}
}
