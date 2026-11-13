package net.mcreator.ui.dialogs;

import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.integration.IntegrationTestSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
class ItemDisplaySettingsEditorTest {
	@Test void typedScaleCommitsAndKeepsRatios() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = new ItemDisplaySettingsDialog.TransformEditor(new Transform(0, 0, 0, 0, 0, 0, 1, 2, 3));
			List<JSpinner> spinners = spinners(editor);
			((JSpinner.DefaultEditor) spinners.get(6).getEditor()).getTextField().setText("0.5");
			assertEquals(0.5, editor.snapshot().scaleX);
			assertEquals(1, editor.snapshot().scaleY);
			assertEquals(1.5, editor.snapshot().scaleZ);
			editor.undo();
			assertEquals(1, editor.snapshot().scaleX);
			assertEquals(2, editor.snapshot().scaleY);
			editor.redo();
			assertEquals(1.5, editor.snapshot().scaleZ);
		});
	}

	@Test void dragIsOneUndoAndDoesNotMutateOriginal() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Transform original = new Transform();
			var editor = new ItemDisplaySettingsDialog.TransformEditor(original);
			editor.beginGesture();
			for (int i = 1; i <= 10; i++) {
				Transform next = editor.snapshot();
				next.translationX = i;
				editor.load(next);
			}
			editor.endGesture();
			assertEquals(10, editor.snapshot().translationX);
			editor.undo();
			assertEquals(0, editor.snapshot().translationX);
			assertEquals(0, original.translationX);
			editor.redo();
			assertEquals(10, editor.snapshot().translationX);
		});
	}

	@Test void pastedNonUniformScaleStaysExactAndContextsAreIndependent() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var right = new ItemDisplaySettingsDialog.TransformEditor(new Transform());
			var left = new ItemDisplaySettingsDialog.TransformEditor(new Transform());
			right.load(new Transform(15, 25, 35, 1, 2, 3, .2, .3, .4));
			left.load(right.snapshot());
			right.load(new Transform());
			assertEquals(.3, left.snapshot().scaleY);
			assertEquals(35, left.snapshot().rotationZ);
			assertEquals(1, right.snapshot().scaleY);
		});
	}

	private static List<JSpinner> spinners(Container root) {
		List<JSpinner> result = new ArrayList<>();
		for (Component child : root.getComponents()) {
			if (child instanceof JSpinner spinner) result.add(spinner);
			else if (child instanceof Container container) result.addAll(spinners(container));
		}
		return result;
	}
}
