package net.mcreator.ui.dialogs;

import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.element.parts.WornDisplaySettings;
import net.mcreator.element.types.Fashion;
import net.mcreator.ui.component.ItemDisplayPreview;
import net.mcreator.ui.component.ScrollablePanel;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

final class FashionDisplayEditorPanel extends JPanel {
	private final FashionDisplaySession session;
	private final Map<String, ItemDisplaySettingsDialog.TransformEditor> editors = new LinkedHashMap<>();
	private final JComboBox<String> slots;
	private final JComboBox<String> views;
	private final JCheckBox enabled = L10N.checkbox("dialog.item_display.enabled");
	private final JPanel previewHost = new JPanel(new BorderLayout());
	private final JPanel fields = new JPanel(new BorderLayout());
	private Transform clipboard;
	private ItemDisplaySettingsDialog.TransformEditor active;
	private String selectedSlot;
	private int selectedView;
	private ItemDisplayPreview preview;
	private boolean selecting;
	private Runnable changeListener = () -> {};
	private final JComboBox<String> side = new JComboBox<>(new String[] { "Left", "Right" });
	private final JCheckBox linked = new JCheckBox("Link left / right", true);
	private final JCheckBox leftVisible = new JCheckBox("Left", true), rightVisible = new JCheckBox("Right", true);
	private final JPanel sideControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
	private final JComboBox<String> pose = new JComboBox<>(new String[] { "Standing", "Walking pose", "Crouching", "Head turn" });

	FashionDisplayEditorPanel(Fashion fashion, String generator, String initialSlot, boolean worn) {
		super(new BorderLayout(8, 8));
		session = new FashionDisplaySession(fashion);
		slots = new JComboBox<>(session.slots().stream().map(FashionDisplayEditorPanel::slotLabel).toArray(String[]::new));
		String[] keys = { "gui", "ground", "fixed", "third_right", "third_left", "first_right", "first_left" };
		java.util.List<String> labels = new java.util.ArrayList<>();
		for (String key : keys) labels.add(L10N.t("dialog.item_display." + key));
		boolean supportsWorn = fashion.simpleItemWearable && WornDisplaySettings.supports(generator);
		if (supportsWorn) labels.add(L10N.t("dialog.model_display.worn"));
		views = new JComboBox<>(labels.toArray(String[]::new));
		JPanel selectors = new JPanel(new GridLayout(1, 4, 8, 0));
		selectors.add(L10N.label("dialog.worn_display.slot")); selectors.add(slots);
		selectors.add(L10N.label("dialog.model_display.view")); selectors.add(views);
		add(selectors, BorderLayout.NORTH);
		JPanel inspector = new ScrollablePanel();
		inspector.setLayout(new BorderLayout(8, 8));
		JPanel inspectorHeader = new JPanel(new GridLayout(0, 1, 2, 4));
		inspectorHeader.add(enabled);
		sideControls.setLayout(new GridLayout(2, 2, 4, 2));
		sideControls.add(side); sideControls.add(linked); sideControls.add(leftVisible); sideControls.add(rightVisible);
		inspectorHeader.add(sideControls);
		inspectorHeader.add(pose);
		inspector.add(inspectorHeader, BorderLayout.NORTH);
		inspector.add(fields, BorderLayout.CENTER);
		inspector.add(new DisplayPresetControls(this, () -> active.snapshot(), value -> active.load(value)), BorderLayout.SOUTH);
		JScrollPane inspectorScroll = new JScrollPane(inspector);
		inspectorScroll.setMinimumSize(new Dimension(340, 200));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewHost, inspectorScroll);
		split.setResizeWeight(.65); split.setDividerLocation(650); split.setBorder(null);
		add(split, BorderLayout.CENTER);
		setPreferredSize(new Dimension(1080, 580));
		slots.setSelectedIndex(Math.max(0, session.slots().indexOf(initialSlot)));
		views.setSelectedIndex(worn && supportsWorn ? FashionDisplaySession.WORN : 0);
		slots.addActionListener(_ -> select()); views.addActionListener(_ -> select());
		side.addActionListener(_ -> { if (!selecting) select(); });
		pose.addActionListener(_ -> { if (preview != null) preview.setPose(ItemDisplayPreview.Pose.values()[pose.getSelectedIndex()]); });
		linked.addActionListener(_ -> {
			if (selecting) return;
			session.wornSettings().setLinked(selectedSlot, linked.isSelected());
			editors.keySet().removeIf(key -> key.startsWith(selectedSlot + ":" + FashionDisplaySession.WORN));
			select();
			changeListener.run();
		});
		leftVisible.addActionListener(_ -> { session.wornSettings().setSideEnabled(selectedSlot, true, leftVisible.isSelected()); preview.repaint(); changeListener.run(); });
		rightVisible.addActionListener(_ -> { session.wornSettings().setSideEnabled(selectedSlot, false, rightVisible.isSelected()); preview.repaint(); changeListener.run(); });
		enabled.addActionListener(_ -> {
			if (!selecting && selectedView != FashionDisplaySession.WORN)
				session.itemSettings(selectedSlot).enabled = enabled.isSelected();
			if (!selecting) changeListener.run();
		});
		select();
	}

	private void select() {
		if (session.slots().isEmpty()) return;
		selecting = true;
		selectedSlot = session.slots().get(slots.getSelectedIndex());
		selectedView = views.getSelectedIndex();
		String slot = selectedSlot;
		int view = selectedView;
		boolean left = side.getSelectedIndex() == 0;
		String editorKey = session.key(slot, view) + (view == FashionDisplaySession.WORN && !left && !session.wornSettings().isLinked(slot) ? ":right" : "");
		active = editors.computeIfAbsent(editorKey,
				_ -> new ItemDisplaySettingsDialog.TransformEditor(session.transform(slot, view, left)));
		var editor = active;
		preview = new ItemDisplayPreview(session.model(slot), editor::snapshot, editor::load);
		if (view == FashionDisplaySession.WORN) {
			ItemDisplaySettings settings = session.itemSettings(slot);
			Transform base = slot.equals("helmet") ? new Transform() :
					settings.isEnabled() ? settings.getFixed() : ItemDisplaySettings.vanillaBlock().getFixed();
			preview.setWornContext(slot, base);
			preview.setWornSides(session.wornSettings(), left);
			preview.setPose(ItemDisplayPreview.Pose.values()[pose.getSelectedIndex()]);
		} else preview.setViewContext(ItemDisplayPreview.ViewContext.values()[view]);
		ItemDisplayPreview currentPreview = preview;
		editor.setChangeListener(() -> {
			session.update(slot, view, left, editor.snapshot());
			if (view != FashionDisplaySession.WORN) enabled.setSelected(true);
			currentPreview.repaint();
			changeListener.run();
		});
		preview.addPropertyChangeListener("transformGesture", event -> {
			if (Boolean.TRUE.equals(event.getNewValue())) editor.beginGesture(); else editor.endGesture();
		});
		enabled.setVisible(view != FashionDisplaySession.WORN);
		pose.setVisible(view == FashionDisplaySession.WORN);
		sideControls.setVisible(view == FashionDisplaySession.WORN && (slot.equals("boots") || slot.equals("leggings")));
		linked.setSelected(session.wornSettings().isLinked(slot));
		leftVisible.setSelected(session.wornSettings().isSideEnabled(slot, true));
		rightVisible.setSelected(session.wornSettings().isSideEnabled(slot, false));
		enabled.setSelected(session.itemSettings(slot).enabled);
		JToolBar tools = ItemDisplaySettingsDialog.createPreviewToolbar(preview);
		JToolBar edits = new JToolBar(); edits.setFloatable(false);
		addAction(edits, UIRES.get("img_editor.undo"), "Undo", editor::undo);
		addAction(edits, UIRES.get("img_editor.redo"), "Redo", editor::redo);
		JButton copy = L10N.button("dialog.item_display.copy");
		copy.addActionListener(_ -> clipboard = editor.snapshot()); edits.add(copy);
		JButton paste = L10N.button("dialog.item_display.paste");
		paste.addActionListener(_ -> { if (clipboard != null) editor.load(clipboard); }); edits.add(paste);
		addAction(edits, UIRES.get("16px.reset"), L10N.t("dialog.item_display.reset_transform"), () -> editor.load(new Transform()));
		JButton fit = new JButton(UIRES.get("img_editor.resizecanvas"));
		fit.setToolTipText("Fit model to reference (starting size)");
		fit.setEnabled(preview.getFaceCount() > 0);
		fit.addActionListener(_ -> { Transform fitted = currentPreview.fitToReference(); if (fitted != null) editor.load(fitted); });
		edits.add(fit);
		JPanel toolbarRows = new JPanel(new GridLayout(2, 1, 0, 2)); toolbarRows.add(tools); toolbarRows.add(edits);
		previewHost.removeAll(); previewHost.add(toolbarRows, BorderLayout.NORTH); previewHost.add(preview, BorderLayout.CENTER);
		if (view != FashionDisplaySession.WORN) {
			JComboBox<String> presets = new JComboBox<>(new String[] { L10N.t("dialog.model_display.presets"),
					L10N.t("dialog.item_display.preset_compact"), L10N.t("dialog.item_display.preset_vanilla"),
					L10N.t("dialog.item_display.mirror_hands") });
			presets.addActionListener(_ -> {
				int preset = presets.getSelectedIndex();
				if (preset == 0) return;
				if (preset == 3) {
					updateEditor(slot, 4, session.transform(slot, 3).copy());
					updateEditor(slot, 6, session.transform(slot, 5).copy());
				} else {
					Transform[] transforms = FashionDisplaySession.transforms(preset == 1 ? ItemDisplaySettings.compactFashion() : ItemDisplaySettings.vanillaBlock());
					for (int i = 0; i < transforms.length; i++) updateEditor(slot, i, transforms[i]);
				}
				presets.setSelectedIndex(0); select();
				changeListener.run();
			});
			previewHost.add(presets, BorderLayout.SOUTH);
		}
		fields.removeAll(); fields.add(editor, BorderLayout.CENTER);
		selecting = false;
		revalidate(); repaint();
	}
	private void updateEditor(String slot, int view, Transform transform) {
		session.update(slot, view, transform);
		var editor = editors.get(session.key(slot, view));
		if (editor != null) editor.load(transform);
	}
	void applyTo(Fashion target) { session.applyTo(target); }
	Fashion snapshot() { return session.snapshot(); }
	void setChangeListener(Runnable listener) { changeListener = listener; }
	void selectView(int index) { views.setSelectedIndex(index); }
	void selectSlot(int index) { slots.setSelectedIndex(index); }
	void selectSide(int index) { side.setSelectedIndex(index); }
	void setSidesLinked(boolean value) { if (linked.isSelected() != value) linked.doClick(); }
	void setSideVisible(boolean left, boolean value) {
		JCheckBox box = left ? leftVisible : rightVisible;
		if (box.isSelected() != value) box.doClick();
	}
	ItemDisplaySettingsDialog.TransformEditor activeEditor() { return active; }
	private static String slotLabel(String slot) {
		return L10N.t("elementgui.fashion.slot." + switch (slot) {
			case "helmet" -> "head"; case "body" -> "body"; case "leggings" -> "legs"; default -> "feet";
		});
	}
	private static void addAction(JToolBar tools, Icon icon, String text, Runnable action) {
		JButton button = new JButton(icon); button.setToolTipText(text);
		button.addActionListener(_ -> action.run()); tools.add(button);
	}
}
