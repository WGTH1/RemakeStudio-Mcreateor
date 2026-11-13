package net.mcreator.ui.dialogs;

import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.element.parts.WornDisplaySettings;
import net.mcreator.element.types.Fashion;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.ItemDisplayPreview;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.workspace.resources.Model;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class WornDisplaySettingsDialog {
	private WornDisplaySettingsDialog() {}

	@Nullable public static WornDisplaySettings open(MCreator owner, Fashion fashion) {
		WornDisplaySettings working = fashion.getWornDisplay().copy();
		Map<String, String> slots = new LinkedHashMap<>();
		if (fashion.enableHelmet) slots.put(L10N.t("elementgui.fashion.slot.head"), "helmet");
		if (fashion.enableBody) slots.put(L10N.t("elementgui.fashion.slot.body"), "body");
		if (fashion.enableLeggings) slots.put(L10N.t("elementgui.fashion.slot.legs"), "leggings");
		if (fashion.enableBoots) slots.put(L10N.t("elementgui.fashion.slot.feet"), "boots");
		if (slots.isEmpty()) return null;
		JComboBox<String> slotBox = new JComboBox<>(slots.keySet().toArray(String[]::new));
		Map<String, ItemDisplaySettingsDialog.TransformEditor> editors = new LinkedHashMap<>();
		slots.values().forEach(slot -> editors.put(slot, new ItemDisplaySettingsDialog.TransformEditor(working.get(slot))));
		JPanel previewHost = new JPanel(new BorderLayout());
		JPanel inspectorHost = new JPanel(new BorderLayout());
		inspectorHost.add(slotBox, BorderLayout.NORTH);
		JPanel fields = new JPanel(new BorderLayout());
		inspectorHost.add(fields, BorderLayout.CENTER);
		AtomicReference<Transform> clipboard = new AtomicReference<>();
		Runnable select = () -> {
			String slot = slots.get(slotBox.getSelectedItem());
			var editor = editors.get(slot);
			String modelSlot = fashion.singleItemSet ? slots.values().iterator().next() : slot;
			Model model = switch (modelSlot) {
				case "helmet" -> fashion.getHelmetItemModel();
				case "body" -> fashion.getBodyItemModel();
				case "leggings" -> fashion.getLeggingsItemModel();
				default -> fashion.getBootsItemModel();
			};
			ItemDisplaySettings itemSettings = fashion.getItemDisplaySettingsFor(modelSlot);
			Transform base = slot.equals("helmet") ? new Transform() :
					itemSettings.isEnabled() ? itemSettings.getFixed() : ItemDisplaySettings.vanillaBlock().getFixed();
			ItemDisplayPreview preview = new ItemDisplayPreview(model, editor::snapshot, editor::load);
			preview.setWornContext(slot, base);
			preview.addPropertyChangeListener("transformGesture", event -> {
				if (Boolean.TRUE.equals(event.getNewValue())) editor.beginGesture(); else editor.endGesture();
			});
			editor.setChangeListener(preview::repaint);
			JToolBar toolbar = ItemDisplaySettingsDialog.createPreviewToolbar(preview);
			addAction(toolbar, UIRES.get("img_editor.undo"), "Undo", editor::undo);
			addAction(toolbar, UIRES.get("img_editor.redo"), "Redo", editor::redo);
			JButton copy = new JButton(L10N.t("dialog.item_display.copy"));
			copy.addActionListener(_ -> clipboard.set(editor.snapshot()));
			toolbar.add(copy);
			JButton paste = new JButton(L10N.t("dialog.item_display.paste"));
			paste.addActionListener(_ -> { if (clipboard.get() != null) editor.load(clipboard.get()); });
			toolbar.add(paste);
			addAction(toolbar, UIRES.get("16px.reset"), L10N.t("dialog.item_display.reset_transform"), () -> editor.load(new Transform()));
			previewHost.removeAll();
			previewHost.add(toolbar, BorderLayout.NORTH);
			previewHost.add(preview, BorderLayout.CENTER);
			fields.removeAll();
			fields.add(editor, BorderLayout.CENTER);
			previewHost.revalidate();
			previewHost.repaint();
			fields.revalidate();
			fields.repaint();
		};
		slotBox.addActionListener(_ -> select.run());
		select.run();
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewHost, new JScrollPane(inspectorHost));
		split.setResizeWeight(.65);
		split.setDividerLocation(650);
		split.setPreferredSize(new Dimension(1060, 560));
		String apply = L10N.t("dialog.item_display.save");
		JOptionPane pane = new JOptionPane(split, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
				null, new String[] { apply, L10N.t("dialog.item_display.cancel") });
		JDialog dialog = pane.createDialog(owner, L10N.t("dialog.worn_display.title"));
		dialog.setResizable(true);
		Rectangle screen = owner.getGraphicsConfiguration().getBounds();
		dialog.setSize(Math.min(dialog.getWidth(), screen.width - 60), Math.min(dialog.getHeight(), screen.height - 80));
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		dialog.dispose();
		if (!apply.equals(pane.getValue())) return null;
		if (editors.containsKey("helmet")) working.helmet = editors.get("helmet").snapshot();
		if (editors.containsKey("body")) working.body = editors.get("body").snapshot();
		if (editors.containsKey("leggings")) working.leggings = editors.get("leggings").snapshot();
		if (editors.containsKey("boots")) working.boots = editors.get("boots").snapshot();
		return working;
	}

	private static void addAction(JToolBar toolbar, Icon icon, String tooltip, Runnable action) {
		JButton button = new JButton(icon);
		button.setToolTipText(tooltip);
		button.addActionListener(_ -> action.run());
		toolbar.add(button);
	}
}
