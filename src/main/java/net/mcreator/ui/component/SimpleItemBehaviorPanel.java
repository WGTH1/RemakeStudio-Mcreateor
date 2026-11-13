/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.ui.component;

import net.mcreator.element.parts.SimpleItemBehavior;
import net.mcreator.generator.behavior.SimpleItemBehaviorCompiler;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.validation.AggregatedValidationResult;

import javax.swing.*;
import java.awt.*;
import java.text.ParseException;

public final class SimpleItemBehaviorPanel extends JPanel {
	private final JCheckBox enabled = L10N.checkbox("remark.behavior.enable");
	private final JSpinner duration = new JSpinner(new SpinnerNumberModel(5, 1, 600, 1));
	private final JSpinner cooldown = new JSpinner(new SpinnerNumberModel(10, 1, 3600, 1));
	private final JSpinner level = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
	private final JCheckBox particles = L10N.checkbox("remark.behavior.particles");
	private final JButton reset = L10N.button("remark.behavior.reset");
	private final JLabel status = new JLabel();
	private final String target;
	private int schemaVersion = 1;
	private JSpinner invalidNumber;

	public SimpleItemBehaviorPanel(String target) {
		super(new GridBagLayout());
		this.target = target;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
		addRow(0, "remark.behavior.when", L10N.label("remark.behavior.right_click"));
		addRow(1, "remark.behavior.target", L10N.label("remark.behavior.user"));
		addRow(2, "remark.behavior.action", enabled);
		addRow(3, "remark.behavior.duration", duration);
		addRow(4, "remark.behavior.level", level);
		addRow(5, "remark.behavior.cooldown", cooldown);
		addRow(6, "remark.behavior.visuals", particles);
		addRow(7, "remark.behavior.profile", status);
		addRow(8, "remark.behavior.defaults", reset);
		GridBagConstraints filler = new GridBagConstraints();
		filler.gridy = 9;
		filler.weighty = 1;
		add(Box.createVerticalGlue(), filler);
		enabled.setName("simpleBehavior.enabled");
		duration.setName("simpleBehavior.duration");
		cooldown.setName("simpleBehavior.cooldown");
		level.setName("simpleBehavior.level");
		particles.setName("simpleBehavior.particles");
		reset.setName("simpleBehavior.reset");
		enabled.addActionListener(_ -> updateEnabled());
		reset.addActionListener(_ -> {
			SimpleItemBehavior defaults = new SimpleItemBehavior();
			defaults.enabled = enabled.isSelected();
			load(defaults);
		});
		load(new SimpleItemBehavior());
	}

	private void addRow(int row, String key, JComponent control) {
		JLabel label = L10N.label(key);
		label.setLabelFor(control);
		GridBagConstraints left = new GridBagConstraints();
		left.gridx = 0;
		left.gridy = row;
		left.anchor = GridBagConstraints.LINE_START;
		left.insets = new Insets(6, 0, 6, 18);
		add(label, left);
		GridBagConstraints right = new GridBagConstraints();
		right.gridx = 1;
		right.gridy = row;
		right.fill = GridBagConstraints.HORIZONTAL;
		right.weightx = 1;
		right.insets = new Insets(6, 0, 6, 0);
		add(control, right);
	}

	public void load(SimpleItemBehavior value) {
		schemaVersion = value.schemaVersion;
		enabled.setSelected(value.enabled);
		duration.setValue(value.durationSeconds);
		cooldown.setValue(value.cooldownSeconds);
		level.setValue(value.effectLevel);
		particles.setSelected(value.particles);
		updateEnabled();
	}

	public SimpleItemBehavior read() {
		SimpleItemBehavior value = new SimpleItemBehavior();
		value.schemaVersion = schemaVersion;
		value.enabled = enabled.isSelected();
		value.durationSeconds = ((Number) duration.getValue()).intValue();
		value.cooldownSeconds = ((Number) cooldown.getValue()).intValue();
		value.effectLevel = ((Number) level.getValue()).intValue();
		value.particles = particles.isSelected();
		return value;
	}

	public AggregatedValidationResult validateInput() {
		if (!enabled.isSelected())
			return new AggregatedValidationResult.PASS();
		try {
			for (JSpinner spinner : new JSpinner[] { duration, cooldown, level }) {
				invalidNumber = spinner; spinner.commitEdit();
			}
			invalidNumber = null;
			return new AggregatedValidationResult.PASS();
		} catch (ParseException e) {
			return new AggregatedValidationResult.FAIL(L10N.t("remark.behavior.error.number"));
		}
	}

	public void focusProblem(String message) {
		JComponent control = enabled;
		if (message.equals(L10N.t("remark.behavior.error.duration"))) control = duration;
		else if (message.equals(L10N.t("remark.behavior.error.cooldown"))) control = cooldown;
		else if (message.equals(L10N.t("remark.behavior.error.level"))) control = level;
		else if (message.equals(L10N.t("remark.behavior.error.number")) && invalidNumber != null) control = invalidNumber;
		if (control instanceof JSpinner spinner && spinner.getEditor() instanceof JSpinner.DefaultEditor editor)
			control = editor.getTextField();
		control.scrollRectToVisible(new Rectangle(0, 0, control.getWidth(), control.getHeight())); control.requestFocusInWindow();
	}

	private void updateEnabled() {
		boolean supported = SimpleItemBehaviorCompiler.supports(target);
		boolean known = schemaVersion == 1;
		enabled.setEnabled(known && (supported || enabled.isSelected()));
		for (JComponent component : new JComponent[] { duration, cooldown, level, particles, reset })
			component.setEnabled(known && supported && enabled.isSelected());
		status.setText(L10N.t(!known ? "remark.behavior.error.schema" :
				supported ? "remark.behavior.experimental" : "remark.behavior.error.target"));
	}
}
