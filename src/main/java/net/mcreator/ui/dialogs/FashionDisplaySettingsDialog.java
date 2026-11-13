package net.mcreator.ui.dialogs;

import net.mcreator.element.types.Fashion;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.remark.DisplayLivePreviewSession;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class FashionDisplaySettingsDialog {
	private FashionDisplaySettingsDialog() {}
	public static boolean open(MCreator owner, Fashion draft, String slot, boolean worn) {
		FashionDisplayEditorPanel panel = new FashionDisplayEditorPanel(draft,
				owner.getGenerator().getGeneratorName(), slot, worn);
		String apply = L10N.t("dialog.item_display.save");
		JCheckBox live = new JCheckBox("Live preview in game");
		JLabel status = new JLabel("Disconnected");
		JPanel footer = new JPanel(new BorderLayout(10, 0)); footer.add(live, BorderLayout.WEST); footer.add(status, BorderLayout.CENTER);
		JPanel content = new JPanel(new BorderLayout(8, 8)); content.add(panel, BorderLayout.CENTER); content.add(footer, BorderLayout.SOUTH);
		AtomicReference<DisplayLivePreviewSession> session = new AtomicReference<>();
		var worker = Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, "Display live publisher"); thread.setDaemon(true); return thread; });
		Timer send = new Timer(500, _ -> {
			if (!live.isSelected()) return;
			Fashion snapshot = panel.snapshot();
			worker.submit(() -> {
				try {
					if (session.get() == null) session.set(new DisplayLivePreviewSession(owner, snapshot));
					else session.get().publish(snapshot);
				} catch (Exception e) { SwingUtilities.invokeLater(() -> { status.setText("Preview error"); status.setToolTipText(e.getMessage()); }); }
			});
		});
		send.setRepeats(false);
		panel.setChangeListener(() -> { if (live.isSelected()) send.restart(); });
		live.addActionListener(_ -> {
			if (live.isSelected()) { status.setText("Waiting for game..."); send.restart(); }
			else { send.stop(); worker.submit(() -> { var previous = session.getAndSet(null); if (previous != null) previous.close(); }); status.setText("Disconnected"); }
		});
		Timer check = new Timer(1000, _ -> {
			var current = session.get();
			if (current != null && live.isSelected()) { status.setText(current.getState().toString()); status.setToolTipText(current.getStatus()); }
		});
		check.start();
		JOptionPane pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
				null, new String[] { apply, L10N.t("dialog.item_display.cancel") });
		JDialog dialog = pane.createDialog(owner, L10N.t("dialog.model_display.title"));
		dialog.setResizable(true); dialog.setMinimumSize(new Dimension(760, 420));
		Rectangle bounds = owner.getGraphicsConfiguration().getBounds();
		dialog.setSize(Math.min(dialog.getWidth(), bounds.width - 60), Math.min(dialog.getHeight(), bounds.height - 80));
		dialog.setLocationRelativeTo(owner);
		try { dialog.setVisible(true); } finally {
			dialog.dispose(); send.stop(); check.stop();
			worker.submit(() -> { var previous = session.getAndSet(null); if (previous != null) previous.close(); });
			worker.shutdown();
		}
		if (!apply.equals(pane.getValue())) return false;
		panel.applyTo(draft);
		return true;
	}
}
