/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.element.parts.gui;

import net.mcreator.element.parts.procedure.Procedure;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.ui.wysiwyg.WYSIWYGEditor;
import net.mcreator.util.FilenameUtilsPatched;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.references.TextureReference;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.image.ImageObserver;

public class Image extends GUIComponent {

	@Nonnull @TextureReference(TextureType.SCREEN) public String image;
	public boolean use1Xscale;
	public int customWidth;
	public int customHeight;
	public boolean pulseAnimation;
	public int pulseAmount = 4;
	public int pulsePeriodTicks = 40;

	public Procedure displayCondition;

	public Image(int x, int y, @Nonnull String image, boolean use1Xscale, Procedure displayCondition) {
		super(x, y);
		this.image = image;
		this.use1Xscale = use1Xscale;
		this.displayCondition = displayCondition;
	}

	public Image(int x, int y, String image, boolean use1Xscale, Procedure displayCondition, AnchorPoint anchorPoint) {
		this(x, y, image, use1Xscale, displayCondition);
		this.anchorPoint = anchorPoint;
	}

	@Override public String getName() {
		return resolveComponentName("image_" + FilenameUtilsPatched.removeExtension(image));
	}

	@Override public int getWidth(Workspace workspace) {
		if (customWidth > 0)
			return customWidth;
		if (use1Xscale)
			return getImage(workspace).getWidth(null) / 2;
		else
			return getImage(workspace).getWidth(null);
	}

	@Override public int getHeight(Workspace workspace) {
		if (customHeight > 0)
			return customHeight;
		if (use1Xscale)
			return getImage(workspace).getHeight(null) / 2;
		else
			return getImage(workspace).getHeight(null);
	}

	public java.awt.Image getImage(Workspace workspace) {
		return new ImageIcon(workspace.getFolderManager()
				.getTextureFile(FilenameUtilsPatched.removeExtension(image), TextureType.SCREEN)
				.getAbsolutePath()).getImage();
	}

	public int getTextureWidth(Workspace workspace) {
		return Math.max(1, getImage(workspace).getWidth(null));
	}

	public int getTextureHeight(Workspace workspace) {
		return Math.max(1, getImage(workspace).getHeight(null));
	}

	@Override public int getWeight() {
		return 40;
	}

	@Override public void paintComponent(int cx, int cy, WYSIWYGEditor wysiwygEditor, Graphics2D g) {
		java.awt.Image actualImage = this.getImage(wysiwygEditor.mcreator.getWorkspace());
		int cw = getWidth(wysiwygEditor.mcreator.getWorkspace());
		int ch = getHeight(wysiwygEditor.mcreator.getWorkspace());
		drawCrisp(g, actualImage, cx, cy, cw, ch, wysiwygEditor);
	}

	public static void drawCrisp(Graphics2D graphics, java.awt.Image image, int x, int y, int width, int height,
			ImageObserver observer) {
		Graphics2D imageGraphics = (Graphics2D) graphics.create();
		try {
			// Large UI artwork needs quality downsampling, while pixel art should stay sharp when enlarged.
			double deviceScaleX = Math.hypot(imageGraphics.getTransform().getScaleX(),
					imageGraphics.getTransform().getShearY());
			double deviceScaleY = Math.hypot(imageGraphics.getTransform().getShearX(),
					imageGraphics.getTransform().getScaleY());
			int deviceWidth = Math.max(1, (int) Math.round(Math.abs(width) * deviceScaleX));
			int deviceHeight = Math.max(1, (int) Math.round(Math.abs(height) * deviceScaleY));
			boolean downscaling = image.getWidth(observer) > deviceWidth || image.getHeight(observer) > deviceHeight;

			imageGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					downscaling ? RenderingHints.VALUE_INTERPOLATION_BICUBIC :
							RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			imageGraphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			imageGraphics.drawImage(image, x, y, width, height, observer);
		} finally {
			imageGraphics.dispose();
		}
	}

}
