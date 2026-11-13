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
import net.mcreator.minecraft.MinecraftImageGenerator;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.ui.wysiwyg.WYSIWYG;
import net.mcreator.ui.wysiwyg.WYSIWYGEditor;
import net.mcreator.workspace.references.TextureReference;

import javax.annotation.Nonnull;
import java.awt.*;

public class Button extends SizedComponent {

	@Nonnull public String name;
	public String text;
	public final boolean isUndecorated;
	public final Procedure onClick;
	public final Procedure displayCondition;
	public boolean enabled = true;
	public String normalTextColor = "FFFFFF";
	public String hoverTextColor = "FFD76A";
	public String pressedTextColor = "E91E79";
	public String disabledTextColor = "777777";
	@TextureReference(TextureType.SCREEN) public String normalTexture = "";
	@TextureReference(TextureType.SCREEN) public String hoverTexture = "";
	@TextureReference(TextureType.SCREEN) public String pressedTexture = "";
	@TextureReference(TextureType.SCREEN) public String disabledTexture = "";
	public int hoverScalePercent = 100;
	public boolean autoSize = false;
	public int autoSizePaddingX = 8;
	public int autoSizePaddingY = 4;
	public int autoSizeMinWidth = 20;
	public int autoSizeMinHeight = 20;
	public int autoSizeMaxWidth = 0;
	public int autoSizeMaxHeight = 0;

	public Button(@Nonnull String name, int x, int y, String text, int width, int height, boolean isUndecorated,
			Procedure onClick, Procedure displayCondition) {
		super(x, y, width, height);
		this.text = text;
		this.onClick = onClick;
		this.displayCondition = displayCondition;
		this.name = name;
		this.isUndecorated = isUndecorated;
	}

	@Override public int getWeight() {
		return 30;
	}

	@Override public String getName() {
		return resolveComponentName(name);
	}

	@Override public void paintComponent(int cx, int cy, WYSIWYGEditor wysiwygEditor, Graphics2D g) {
		g.setColor(parseColor(enabled ? normalTextColor : disabledTextColor, enabled ? Color.WHITE : Color.GRAY));
		if (!isUndecorated) {
			g.drawImage(MinecraftImageGenerator.generateButton(this.width, this.height), cx, cy, this.width,
					this.height, wysiwygEditor);
		}

		String[] lines = this.text.split("\\R", -1);
		int lineHeight = Math.max(1,
				(int) Math.ceil(WYSIWYG.fontMC.getStringBounds("Ag", WYSIWYG.frc).getHeight()));
		int textBlockHeight = lineHeight * lines.length;
		int baseline = cy + Math.max(lineHeight, (this.height - textBlockHeight) / 2 + lineHeight) - 2;
		for (String line : lines) {
			int lineWidth = (int) Math.ceil(WYSIWYG.fontMC.getStringBounds(line, WYSIWYG.frc).getWidth());
			g.drawString(line, cx + (this.width - lineWidth) / 2, baseline);
			baseline += lineHeight;
		}
	}

	private static Color parseColor(String value, Color fallback) {
		try {
			String normalized = value == null ? "" : value.trim().replace("#", "");
			return normalized.matches("[0-9A-Fa-f]{6}") ? new Color(Integer.parseInt(normalized, 16)) : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

}
