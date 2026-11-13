/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.dialogs;

import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.ui.component.ItemDisplayPreview;
import net.mcreator.ui.component.ScrollablePanel;
import net.mcreator.ui.component.ItemDisplayPreview.ToolMode;
import net.mcreator.ui.component.ItemDisplayPreview.ViewContext;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.workspace.resources.Model;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

public final class ItemDisplaySettingsDialog {

	private ItemDisplaySettingsDialog() {
	}

	@Nullable public static ItemDisplaySettings open(MCreator mcreator, ItemDisplaySettings current) {
		return open(mcreator, current, null);
	}

	@Nullable public static ItemDisplaySettings open(MCreator mcreator, ItemDisplaySettings current,
			@Nullable Model model) {
		ItemDisplaySettings working = current != null ?
				current.copy() :
				ItemDisplaySettings.compactFashion();

		JCheckBox enabled = L10N.checkbox("dialog.item_display.enabled");
		enabled.setSelected(true);
		enabled.setOpaque(false);

		Map<String, TransformEditor> editors = createEditors(working);
		CardLayout cards = new CardLayout();
		JPanel editorCards = new JPanel(cards);
		editorCards.setOpaque(false);
		editors.forEach(editorCards::add);
		JComboBox<String> context = new JComboBox<>(editors.keySet().toArray(String[]::new));
		JPanel tabs = new JPanel(new BorderLayout(8, 8));
		tabs.setOpaque(false);
		tabs.add(context, BorderLayout.NORTH);
		tabs.add(editorCards, BorderLayout.CENTER);
		TransformEditor[] editorValues = editors.values().toArray(TransformEditor[]::new);
		AtomicReference<TransformEditor> activeEditor = new AtomicReference<>(editorValues[0]);
		ItemDisplayPreview preview = new ItemDisplayPreview(model, () -> activeEditor.get().snapshot(),
				transform -> activeEditor.get().load(transform));
		preview.addPropertyChangeListener("transformGesture", event -> {
			if (Boolean.TRUE.equals(event.getNewValue())) activeEditor.get().beginGesture();
			else activeEditor.get().endGesture();
		});
		for (TransformEditor editor : editorValues)
			editor.setChangeListener(preview::repaint);
		context.addActionListener(_ -> {
			int index = context.getSelectedIndex();
			cards.show(editorCards, (String) context.getSelectedItem());
			activeEditor.set(editorValues[index]);
			preview.setViewContext(ViewContext.values()[index]);
		});
		JPanel previewPanel = new JPanel(new BorderLayout());
		previewPanel.setOpaque(false);
		JToolBar toolbar = createPreviewToolbar(preview);
		AtomicReference<Transform> clipboard = new AtomicReference<>();
		JButton undo = new JButton(UIRES.get("img_editor.undo"));
		undo.setToolTipText("Undo display change");
		undo.addActionListener(_ -> activeEditor.get().undo());
		JButton redo = new JButton(UIRES.get("img_editor.redo"));
		redo.setToolTipText("Redo display change");
		redo.addActionListener(_ -> activeEditor.get().redo());
		JButton copy = new JButton(L10N.t("dialog.item_display.copy"));
		copy.addActionListener(_ -> clipboard.set(activeEditor.get().snapshot()));
		JButton paste = new JButton(L10N.t("dialog.item_display.paste"));
		paste.addActionListener(_ -> { if (clipboard.get() != null) activeEditor.get().load(clipboard.get()); });
		JButton reset = new JButton(UIRES.get("16px.reset"));
		reset.setToolTipText(L10N.t("dialog.item_display.reset_transform"));
		reset.addActionListener(_ -> activeEditor.get().load(new Transform()));
		JToolBar editTools = new JToolBar(); editTools.setFloatable(false);
		editTools.add(undo);
		editTools.add(redo);
		editTools.add(copy);
		editTools.add(paste);
		editTools.add(reset);
		JButton fit = new JButton(UIRES.get("img_editor.resizecanvas"));
		fit.setToolTipText("Fit model to reference (starting size)");
		fit.setEnabled(preview.getFaceCount() > 0);
		fit.addActionListener(_ -> { Transform value = preview.fitToReference(); if (value != null) activeEditor.get().load(value); });
		editTools.add(fit);
		JPanel toolbarRows = new JPanel(new GridLayout(2, 1, 0, 2));
		toolbarRows.add(toolbar); toolbarRows.add(editTools);
		previewPanel.add(toolbarRows, BorderLayout.NORTH);
		previewPanel.add(preview, BorderLayout.CENTER);

		JButton compactPreset = L10N.button("dialog.item_display.preset_compact");
		compactPreset.addActionListener(_ -> loadEditors(editors, ItemDisplaySettings.compactFashion()));
		JButton vanillaPreset = L10N.button("dialog.item_display.preset_vanilla");
		vanillaPreset.addActionListener(_ -> loadEditors(editors, ItemDisplaySettings.vanillaBlock()));
		JButton mirrorHands = L10N.button("dialog.item_display.mirror_hands");
		mirrorHands.addActionListener(_ -> mirrorHands(editors));

		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setOpaque(false);
		content.add(enabled, BorderLayout.NORTH);
		JPanel inspectorContent = new ScrollablePanel();
		inspectorContent.setLayout(new BorderLayout(8, 8));
		inspectorContent.add(tabs, BorderLayout.CENTER);
		inspectorContent.add(new DisplayPresetControls(content, () -> activeEditor.get().snapshot(),
				value -> activeEditor.get().load(value)), BorderLayout.SOUTH);
		JScrollPane inspector = new JScrollPane(inspectorContent);
		inspector.setMinimumSize(new Dimension(370, 250));
		JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewPanel, inspector);
		editorSplit.setResizeWeight(0.65);
		editorSplit.setDividerLocation(580);
		editorSplit.setBorder(null);
		editorSplit.setOpaque(false);
		content.add(editorSplit, BorderLayout.CENTER);
		content.add(PanelUtils.join(FlowLayout.LEFT, compactPreset, vanillaPreset, mirrorHands), BorderLayout.SOUTH);
		content.setPreferredSize(new Dimension(1120, 580));
		JOptionPane optionPane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
				null, new String[] { L10N.t("dialog.item_display.save"), L10N.t("dialog.item_display.cancel") });
		JDialog dialog = optionPane.createDialog(mcreator, L10N.t("dialog.item_display.title"));
		dialog.setResizable(true);
		dialog.setMinimumSize(new Dimension(760, 420));
		Rectangle screen = mcreator.getGraphicsConfiguration().getBounds();
		dialog.setSize(Math.min(dialog.getWidth(), screen.width - 60), Math.min(dialog.getHeight(), screen.height - 80));
		dialog.setLocationRelativeTo(mcreator);
		dialog.setVisible(true);
		dialog.dispose();
		if (!L10N.t("dialog.item_display.save").equals(optionPane.getValue()))
			return null;

		working.enabled = enabled.isSelected();
		readEditors(editors, working);
		return working;
	}

	static JToolBar createPreviewToolbar(ItemDisplayPreview preview) {
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);
		toolbar.setOpaque(false);
		toolbar.setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));

		JToggleButton move = toolButton(UIRES.get("18px.move"), null,
				L10N.t("dialog.item_display.tool_move"));
		JToggleButton rotate = toolButton(UIRES.get("16px.reset"), null, L10N.t("dialog.item_display.tool_rotate"));
		JToggleButton scale = toolButton(UIRES.get("img_editor.resize"), null, L10N.t("dialog.item_display.tool_scale"));
		ButtonGroup tools = new ButtonGroup();
		tools.add(move);
		tools.add(rotate);
		tools.add(scale);
		move.setSelected(true);
		move.addActionListener(_ -> preview.setToolMode(ToolMode.MOVE));
		rotate.addActionListener(_ -> preview.setToolMode(ToolMode.ROTATE));
		scale.addActionListener(_ -> preview.setToolMode(ToolMode.SCALE));
		preview.addPropertyChangeListener("toolMode", event -> {
			switch ((ToolMode) event.getNewValue()) {
			case MOVE -> move.setSelected(true);
			case ROTATE -> rotate.setSelected(true);
			case SCALE -> scale.setSelected(true);
			}
		});

		JToggleButton snap = new JToggleButton(L10N.t("dialog.item_display.tool_snap_short"));
		snap.setToolTipText(L10N.t("dialog.item_display.tool_snap"));
		snap.addActionListener(_ -> preview.setSnapEnabled(snap.isSelected()));
		snap.setFocusable(false);
		snap.setPreferredSize(new Dimension(58, 30));

		JButton resetView = new JButton(UIRES.get("16px.reset"));
		resetView.setToolTipText(L10N.t("dialog.item_display.reset_view"));
		resetView.setFocusable(false);
		resetView.setPreferredSize(new Dimension(34, 30));
		resetView.addActionListener(_ -> preview.resetCamera());

		toolbar.add(move);
		toolbar.add(rotate);
		toolbar.add(scale);
		toolbar.addSeparator(new Dimension(8, 0));
		toolbar.add(snap);
		toolbar.add(Box.createHorizontalGlue());
		toolbar.add(resetView);
		return toolbar;
	}

	private static JToggleButton toolButton(@Nullable Icon icon, @Nullable String text, String tooltip) {
		JToggleButton button = icon != null ? new JToggleButton(icon) : new JToggleButton(text);
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setPreferredSize(new Dimension(34, 30));
		return button;
	}

	private static Map<String, TransformEditor> createEditors(ItemDisplaySettings settings) {
		Map<String, TransformEditor> editors = new LinkedHashMap<>();
		editors.put(L10N.t("dialog.item_display.gui"), new TransformEditor(settings.getGui()));
		editors.put(L10N.t("dialog.item_display.ground"), new TransformEditor(settings.getGround()));
		editors.put(L10N.t("dialog.item_display.fixed"), new TransformEditor(settings.getFixed()));
		editors.put(L10N.t("dialog.item_display.third_right"),
				new TransformEditor(settings.getThirdPersonRightHand()));
		editors.put(L10N.t("dialog.item_display.third_left"), new TransformEditor(settings.getThirdPersonLeftHand()));
		editors.put(L10N.t("dialog.item_display.first_right"),
				new TransformEditor(settings.getFirstPersonRightHand()));
		editors.put(L10N.t("dialog.item_display.first_left"), new TransformEditor(settings.getFirstPersonLeftHand()));
		return editors;
	}

	private static void loadEditors(Map<String, TransformEditor> editors, ItemDisplaySettings settings) {
		int index = 0;
		Transform[] values = transforms(settings);
		for (TransformEditor editor : editors.values())
			editor.load(values[index++]);
	}

	private static void readEditors(Map<String, TransformEditor> editors, ItemDisplaySettings settings) {
		int index = 0;
		Transform[] values = transforms(settings);
		for (TransformEditor editor : editors.values())
			editor.save(values[index++]);
	}

	private static Transform[] transforms(ItemDisplaySettings settings) {
		return new Transform[] { settings.getGui(), settings.getGround(), settings.getFixed(),
				settings.getThirdPersonRightHand(), settings.getThirdPersonLeftHand(),
				settings.getFirstPersonRightHand(), settings.getFirstPersonLeftHand() };
	}

	private static void mirrorHands(Map<String, TransformEditor> editors) {
		TransformEditor[] values = editors.values().toArray(TransformEditor[]::new);
		mirror(values[3], values[4]);
		mirror(values[5], values[6]);
	}

	private static void mirror(TransformEditor right, TransformEditor left) {
		Transform transform = new Transform();
		right.save(transform);
		// Minecraft mirrors X translation and Y/Z rotation when rendering the left hand.
		left.load(transform);
	}

	private static double normalizeDegrees(double degrees) {
		double result = degrees % 360;
		return result < -180 ? result + 360 : result > 180 ? result - 360 : result;
	}

	static final class TransformEditor extends JPanel {

		private final JSpinner[] rotation = vector(-360, 360, 1);
		private final JSpinner[] translation = vector(-80, 80, 0.1);
		private final JSpinner[] scale = vector(-4, 4, 0.01);
		private final JCheckBox uniform = new JCheckBox(L10N.t("dialog.item_display.uniform"), true);
		private final UndoManager history = new UndoManager();
		private CompoundEdit gesture;
		private boolean loading;
		private Transform previous;
		private Runnable changeListener = () -> {
		};

		TransformEditor(Transform transform) {
			super(new BorderLayout(8, 12));
			setOpaque(false);
			JPanel fields = new JPanel(new GridLayout(4, 4, 6, 8));
			fields.setOpaque(false);
			fields.add(new JLabel(""));
			for (String axis : new String[] { "X", "Y", "Z" }) fields.add(new JLabel(axis, SwingConstants.CENTER));
			addRow(fields, L10N.t("dialog.item_display.rotation"), rotation);
			addRow(fields, L10N.t("dialog.item_display.translation"), translation);
			addRow(fields, L10N.t("dialog.item_display.scale"), scale);
			JPanel top = new JPanel(new BorderLayout(8, 12));
			top.setOpaque(false);
			top.add(fields, BorderLayout.NORTH);
			uniform.setOpaque(false);
			top.add(uniform, BorderLayout.SOUTH);
			add(top, BorderLayout.NORTH);
			for (JSpinner spinner : allSpinners())
				spinner.addChangeListener(_ -> changed(spinner));
			restore(transform);
			setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
		}

		private void addRow(JPanel panel, String label, JSpinner[] vector) {
			panel.add(new JLabel(label));
			for (JSpinner spinner : vector)
				panel.add(spinner);
		}

		private void changed(JSpinner spinner) {
			if (loading) return;
			Transform before = previous.copy();
			if (uniform.isSelected()) {
				double[] old = { before.scaleX, before.scaleY, before.scaleZ };
				for (int axis = 0; axis < 3; axis++) if (spinner == scale[axis]) {
					loading = true;
					double factor = old[axis] == 0 ? 1 : value(spinner) / old[axis];
					for (int other = 0; other < 3; other++) if (other != axis)
						setClamped(scale[other], old[axis] == 0 ? value(spinner) : old[other] * factor);
					loading = false;
				}
			}
			record(before, snapshot());
		}

		void beginGesture() { gesture = new CompoundEdit(); }
		void endGesture() {
			if (gesture != null) { gesture.end(); history.addEdit(gesture); gesture = null; }
		}
		void undo() { if (history.canUndo()) history.undo(); }
		void redo() { if (history.canRedo()) history.redo(); }
		private void record(Transform before, Transform after) {
			AbstractUndoableEdit edit = new AbstractUndoableEdit() {
				@Override public void undo() { super.undo(); restore(before); }
				@Override public void redo() { super.redo(); restore(after); }
			};
			if (gesture != null) gesture.addEdit(edit); else history.addEdit(edit);
			previous = after.copy();
			changeListener.run();
		}
		void load(Transform transform) {
			Transform before = snapshot();
			restore(transform);
			record(before, snapshot());
		}
		private void restore(Transform transform) {
			loading = true;
			set(rotation, transform.rotationX, transform.rotationY, transform.rotationZ);
			set(translation, transform.translationX, transform.translationY, transform.translationZ);
			set(scale, transform.scaleX, transform.scaleY, transform.scaleZ);
			loading = false;
			previous = snapshot();
			changeListener.run();
		}

		Transform snapshot() {
			Transform transform = new Transform();
			save(transform);
			return transform;
		}

		void setChangeListener(Runnable changeListener) {
			this.changeListener = changeListener;
		}

		private void save(Transform transform) {
			transform.rotationX = value(rotation[0]);
			transform.rotationY = value(rotation[1]);
			transform.rotationZ = value(rotation[2]);
			transform.translationX = value(translation[0]);
			transform.translationY = value(translation[1]);
			transform.translationZ = value(translation[2]);
			transform.scaleX = value(scale[0]);
			transform.scaleY = value(scale[1]);
			transform.scaleZ = value(scale[2]);
		}

		private static JSpinner[] vector(double min, double max, double step) {
			JSpinner[] result = new JSpinner[3];
			for (int i = 0; i < result.length; i++) {
				result[i] = new JSpinner(new SpinnerNumberModel(0.0, min, max, step));
				JSpinner.NumberEditor editor = new JSpinner.NumberEditor(result[i], "0.##");
				editor.getTextField().setColumns(4);
				((javax.swing.text.DefaultFormatter) editor.getTextField().getFormatter()).setCommitsOnValidEdit(true);
				result[i].setEditor(editor);
			}
			return result;
		}

		private static void set(JSpinner[] spinners, double x, double y, double z) {
			setClamped(spinners[0], x);
			setClamped(spinners[1], y);
			setClamped(spinners[2], z);
		}

		private static void setClamped(JSpinner spinner, double value) {
			SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
			double minimum = ((Number) model.getMinimum()).doubleValue();
			double maximum = ((Number) model.getMaximum()).doubleValue();
			spinner.setValue(Math.max(minimum, Math.min(maximum, value)));
		}

		private static double value(JSpinner spinner) {
			return ((Number) spinner.getValue()).doubleValue();
		}

		private JSpinner[] allSpinners() {
			JSpinner[] spinners = new JSpinner[9];
			System.arraycopy(rotation, 0, spinners, 0, 3);
			System.arraycopy(translation, 0, spinners, 3, 3);
			System.arraycopy(scale, 0, spinners, 6, 3);
			return spinners;
		}
	}
}
