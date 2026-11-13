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

package net.mcreator.ui.wysiwyg;

import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.parts.gui.SizedComponent;
import net.mcreator.minecraft.MinecraftImageGenerator;
import net.mcreator.ui.component.zoompane.IZoomable;
import net.mcreator.ui.component.zoompane.JZoomPane;
import net.mcreator.ui.component.zoompane.JZoomport;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.laf.themes.Theme;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WYSIWYG extends JComponent implements MouseMotionListener, MouseListener, IZoomable {

	public final static int W = 427;
	public final static int H = 240;

	public static final FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, false);

	public static Font fontMC;

	boolean isNotOverlayType = true;

	private boolean positioningModeSettingWidth = false;
	private boolean positioningModeSettingHeight = false;

	private boolean componentMoveMode;
	private boolean componentDragMode;

	public boolean showGrid = false;

	@Nullable private GUIComponent selected;
	private final List<GUIComponent> selectedComponents = new ArrayList<>();
	private final Map<GUIComponent, Point> moveOrigins = new LinkedHashMap<>();

	private int ox, oy;
	private int ow, oh;
	private int dragOffsetX, dragOffsetY;

	private final WYSIWYGEditor wysiwygEditor;

	private final Image background = UIRES.get("guieditor").getImage();
	private final Image inventorySlots = MinecraftImageGenerator.generateInventorySlots();

	private JZoomport owner;

	int grid_x_spacing = 18;
	int grid_y_spacing = 18;

	int grid_x_offset = 11;
	int grid_y_offset = 15;

	WYSIWYG(WYSIWYGEditor wysiwygEditor) {
		if (fontMC == null)
			fontMC = createEditorFont();

		this.wysiwygEditor = wysiwygEditor;

		addMouseListener(this);
		addMouseMotionListener(this);
	}

	private static Font createEditorFont() {
		Font themed = Theme.current().getConsoleFont().deriveFont(8f).deriveFont(Font.BOLD);
		if (themed.canDisplayUpTo("ภาษาไทย") == -1)
			return themed;

		for (String family : List.of("Leelawadee UI", "Tahoma", "Noto Sans Thai", Font.SANS_SERIF, Font.DIALOG)) {
			Font font = new Font(family, Font.BOLD, 8);
			if (font.canDisplayUpTo("ภาษาไทย") == -1)
				return font;
		}
		return themed;
	}

	@Override public void setZoomPane(JZoomPane jZoomPane) {
		this.owner = jZoomPane.getZoomport();
	}

	@Override public Dimension getPreferredSize() {
		return new Dimension(W * 2, H * 2);
	}

	@Override public int getWidth() {
		return W * 2;
	}

	@Override public int getHeight() {
		return H * 2;
	}

	void setSelectedComponent(GUIComponent selected) {
		this.selected = selected;
		selectedComponents.clear();
		if (selected != null)
			selectedComponents.add(selected);
		repaint();
	}

	void setSelectedComponents(List<GUIComponent> components) {
		selectedComponents.clear();
		selectedComponents.addAll(components);
		this.selected = selectedComponents.isEmpty() ? null : selectedComponents.get(selectedComponents.size() - 1);
		repaint();
	}

	public void moveMode() {
		if (selected == null || selectedComponents.stream().noneMatch(component -> !component.locked))
			return;

		owner.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
		componentMoveMode = true;
		moveOrigins.clear();
		for (GUIComponent component : selectedComponents)
			moveOrigins.put(component, new Point(component.x, component.y));
		ox = selected.x;
		oy = selected.y;

		if (selected instanceof SizedComponent) {
			this.ow = ((SizedComponent) selected).width;
			this.oh = ((SizedComponent) selected).height;
		}
	}

	void removeMode() {
		wysiwygEditor.components.removeAll(new ArrayList<>(selectedComponents));
		selectedComponents.clear();
		selected = null;
		repaint();
	}

	public void addComponent(GUIComponent component) {
		wysiwygEditor.components.add(component);
		repaint();
	}

	@Override public void paint(Graphics gx) {
		Graphics2D g = (Graphics2D) gx;

		g.drawImage(background, 0, 0, null);

		g.setColor(Theme.current().getInterfaceAccentColor());
		g.drawRect(0, 0, getWidth(), getHeight());

		// draw wysiwyg
		BufferedImage bi = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D wg = bi.createGraphics();
		drawWYSIWYG(wg);
		wg.dispose();
		g.drawImage(bi, 0, 0, this);

		// draw selection boxes
		for (GUIComponent component : selectedComponents) {
			if (!component.visible)
				continue;
			int cx = component.getX(), cy = component.getY();
			if (componentMoveMode) {
				Point origin = moveOrigins.get(component);
				Point selectedOrigin = moveOrigins.get(selected);
				if (origin != null && selectedOrigin != null) {
					cx = origin.x + ox - selectedOrigin.x;
					cy = origin.y + oy - selectedOrigin.y;
				}
			}

			int cw = component.getWidth(wysiwygEditor.mcreator.getWorkspace());
			int ch = component.getHeight(wysiwygEditor.mcreator.getWorkspace());
			g.setColor(new Color(255, 255, 255, component == selected ? 100 : 55));
			g.fillRect(cx * 2, cy * 2, cw * 2, ch * 2);

			Stroke original = g.getStroke();
			g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 3 }, 0));
			g.setColor(component == selected ? Color.white : new Color(255, 215, 106));
			if (component.isSizeKnown()) {
				g.drawRect(cx * 2, cy * 2, cw * 2, ch * 2);
			} else {
				g.drawLine(cx * 2, cy * 2 + ch * 2, cx * 2 + 20, cy * 2 + ch * 2);
				g.drawLine(cx * 2, cy * 2, cx * 2, cy * 2 + ch * 2);
			}
			g.setStroke(original);
		}

		if (componentMoveMode) {
			float[] dash = { 2.0f };
			g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
			g.setColor(new Color(255, 255, 255, 150));

			g.drawLine(0, oy * 2, getWidth(), oy * 2);
			g.drawLine(ox * 2, 0, ox * 2, getHeight());

			if (positioningModeSettingWidth || positioningModeSettingHeight) {
				if ((ow != 0 || oh != 0) && (selected == null || selected.isSizeKnown())) {
					g.drawLine(0, oy * 2 + oh * 2, getWidth(), oy * 2 + oh * 2);
					g.drawLine(ox * 2 + ow * 2, 0, ox * 2 + ow * 2, getHeight());

					g.setStroke(
							new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[] { 3 }, 0));
					g.setColor(Color.white);
					g.drawRect(ox * 2, oy * 2, ow * 2, oh * 2);
				}
			} else if (selected != null && selected.isSizeKnown()) {
				g.drawLine(0, oy * 2 + selected.getHeight(wysiwygEditor.mcreator.getWorkspace()) * 2, getWidth(),
						oy * 2 + selected.getHeight(wysiwygEditor.mcreator.getWorkspace()) * 2);
				g.drawLine(ox * 2 + selected.getWidth(wysiwygEditor.mcreator.getWorkspace()) * 2, 0,
						ox * 2 + selected.getWidth(wysiwygEditor.mcreator.getWorkspace()) * 2, getHeight());
			}

			if (!isNotOverlayType && selected != null) {
				GUIComponent.AnchorPoint anchorPoint = selected.getAnchorPoint();
				if (anchorPoint != null) {
					Point anchor = anchorPoint.getAnchorPoint(W, H);

					g.setColor(new Color(255, 153, 250, 230));
					g.fillRect(anchor.x * 2 - 4, anchor.y * 2 - 4, 8, 8);
					g.drawLine(anchor.x * 2, anchor.y * 2, ox * 2, anchor.y * 2);
					g.drawLine(anchor.x * 2, anchor.y * 2, anchor.x * 2, oy * 2);
				}
			}
		}

		if (showGrid)
			drawGrid(g);

		if (this.owner != null)
			this.owner.repaint();
	}

	private void drawWYSIWYG(Graphics2D g) {
		g.scale(2, 2);

		g.setFont(fontMC);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

		g.setColor(Color.gray.brighter().brighter());

		int gw = (Integer) wysiwygEditor.spa1.getValue();
		int gh = (Integer) wysiwygEditor.spa2.getValue();

		if (isNotOverlayType) {
			if (wysiwygEditor.renderBgLayer.isSelected()) {
				if (wysiwygEditor.getGUITypeSelector().getSelectedIndex() == 0) {
					g.drawImage(MinecraftImageGenerator.generateBackground(gw, gh), (int) Math.ceil(W / 2.0 - gw / 2.0),
							(int) Math.ceil(H / 2.0 - gh / 2.0), gw, gh, this);
				} else if (wysiwygEditor.getGUITypeSelector().getSelectedIndex() == 1) {
					g.drawImage(MinecraftImageGenerator.generateBackground(gw, gh), (int) Math.ceil(W / 2.0 - gw / 2.0),
							(int) Math.ceil(H / 2.0 - gh / 2.0), gw, gh, this);
					g.drawImage(inventorySlots,
							(int) Math.ceil(W / 2.0 - 176 / 2.0) + (int) wysiwygEditor.invOffX.getValue(),
							(int) Math.ceil(H / 2.0 - 166 / 2.0) + (int) wysiwygEditor.invOffY.getValue(), 176, 166,
							this);
				}
			} else {
				g.setColor(Color.white);
				g.drawRect((int) Math.ceil(W / 2.0 - gw / 2.0), (int) Math.ceil(H / 2.0 - gh / 2.0), gw, gh);
			}
		} else {
			if (wysiwygEditor.overlayBaseTexture.hasTexture()) {
				g.drawImage(wysiwygEditor.overlayBaseTexture.getTexture()
						.getTextureIcon(wysiwygEditor.mcreator.getWorkspace()).getImage(), 0, 0, W, H, this);
			}
		}

		List<GUIComponent> toDraw = new ArrayList<>(wysiwygEditor.components);
		if (selected != null && !toDraw.contains(selected))
			toDraw.add(selected);

		toDraw.stream().filter(component -> component.visible).sorted().forEach(component -> {
			g.setColor(Color.gray.brighter().brighter());
			Font originalFont = g.getFont();
			Stroke originalStroke = g.getStroke();

			int cx = component.getX(), cy = component.getY();
			if (componentMoveMode && selectedComponents.contains(component)) {
				Point origin = moveOrigins.get(component);
				Point selectedOrigin = moveOrigins.get(selected);
				if (origin != null && selectedOrigin != null) {
					cx = origin.x + ox - selectedOrigin.x;
					cy = origin.y + oy - selectedOrigin.y;
				}
			}

			// paint actual component
			component.paintComponent(cx, cy, wysiwygEditor, g);

			g.setFont(originalFont);
			g.setStroke(originalStroke);
		});
	}

	private void drawGrid(Graphics2D g) {
		float[] dash = { 2.0f };
		g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));

		// vertical lines
		for (int i = 0; i < getWidth(); i = i + grid_x_spacing) {
			if (i % (grid_x_spacing * 2) == 0)
				g.setColor(new Color(255, 255, 255, 80));
			else
				g.setColor(new Color(255, 255, 255, 30));
			g.drawLine(i + grid_x_offset, 0, i + grid_x_offset, getHeight());
		}

		// horizontal lines
		for (int i = 0; i < getHeight(); i = i + grid_y_spacing) {
			if (i % (grid_y_spacing * 2) == 0)
				g.setColor(new Color(255, 255, 255, 80));
			else
				g.setColor(new Color(255, 255, 255, 30));
			g.drawLine(0, i + grid_y_offset, getWidth(), i + grid_y_offset);
		}
	}

	@Override public void mouseMoved(MouseEvent e) {
		int ex = e.getX();
		int ey = e.getY();

		if (showGrid) {
			ex -= grid_x_offset;
			ey -= grid_y_offset;
			ex = Math.round(ex / (float) grid_x_spacing) * grid_x_spacing;
			ey = Math.round(ey / (float) grid_y_spacing) * grid_y_spacing;
			ex += grid_x_offset + 1;
			ey += grid_y_offset + 1;
		}

		// scale to coordinate system of the Minecraft
		ex = (int) Math.round(ex / 2.0);
		ey = (int) Math.round(ey / 2.0);

		if (positioningModeSettingWidth) {
			ow = Math.abs(ox - ex);
			if (positioningModeSettingHeight)
				oh = Math.abs(oy - ey);
		} else if (componentMoveMode) {
			ox = ex;
			oy = ey;
		}

		repaint();
	}

	@Override public void mouseClicked(MouseEvent e) {
		int ex = e.getX();
		int ey = e.getY();

		// scale to coordinate system of the Minecraft
		ex = (int) Math.round(ex / 2.0);
		ey = (int) Math.round(ey / 2.0);

		if (componentMoveMode) {
			if (e.getButton() == 1 || !(selected instanceof SizedComponent component) || positioningModeSettingWidth) {
				finishGUIComponentMove();
			} else {
				positioningModeSettingWidth = true;
				if (component.canChangeHeight())
					positioningModeSettingHeight = true;
			}
		} else { // "click-on-component" mode
			GUIComponent component = getGUIComponentAt(ex, ey);
			if (component != null) {
				if (e.getClickCount() > 1) {
					wysiwygEditor.editCurrentlySelectedComponent();
				} else {
					if (e.isControlDown()) {
						int index = wysiwygEditor.components.indexOf(component);
						if (wysiwygEditor.list.isSelectedIndex(index))
							wysiwygEditor.list.removeSelectionInterval(index, index);
						else
							wysiwygEditor.list.addSelectionInterval(index, index);
					} else if (!wysiwygEditor.list.getSelectedValuesList().contains(component)) {
						selectComponentAndGroup(component);
					}
					wysiwygEditor.list.requestFocus();
				}
			} else {
				wysiwygEditor.list.clearSelection();
				this.selected = null;
				repaint();
			}
		}
	}

	@Override public void mouseDragged(MouseEvent e) {
		int ex = e.getX();
		int ey = e.getY();

		if (showGrid && componentDragMode) {
			ex -= grid_x_offset;
			ey -= grid_y_offset;
			ex = Math.round(ex / (float) grid_x_spacing) * grid_x_spacing;
			ey = Math.round(ey / (float) grid_y_spacing) * grid_y_spacing;
			ex += grid_x_offset + 1;
			ey += grid_y_offset + 1;
		}

		// scale to coordinate system of the Minecraft
		ex = (int) Math.round(ex / 2.0);
		ey = (int) Math.round(ey / 2.0);

		if (componentDragMode) {
			ox = ex + (showGrid ? 0 : dragOffsetX);
			oy = ey + (showGrid ? 0 : dragOffsetY);
		} else if (componentMoveMode) {
			if (selected instanceof SizedComponent component && !positioningModeSettingWidth) {
				positioningModeSettingWidth = true;
				if (component.canChangeHeight())
					positioningModeSettingHeight = true;
			}
			mouseMoved(e); // if in move mode, we consider dragging as moving the mouse
		} else {
			GUIComponent component = getGUIComponentAt(ex, ey);
			if (component != null) {
				if (!wysiwygEditor.list.getSelectedValuesList().contains(component))
					selectComponentAndGroup(component);

				dragOffsetX = component.getX() - ex;
				dragOffsetY = component.getY() - ey;

				ox = ex + (showGrid ? 0 : dragOffsetX);
				oy = ey + (showGrid ? 0 : dragOffsetY);

				componentDragMode = true;
				moveMode();
			} else {
				wysiwygEditor.list.clearSelection();
				this.selected = null;
				repaint();
			}
		}
	}

	@Override public void mouseEntered(MouseEvent e) {

	}

	@Override public void mouseExited(MouseEvent e) {

	}

	@Override public void mousePressed(MouseEvent e) {

	}

	@Override public void mouseReleased(MouseEvent e) {
		if (componentDragMode) {
			componentDragMode = false;
			finishGUIComponentMove();
		}
	}

	@Override public void paintPreZoom(Graphics g, Dimension d) {

	}

	@Override public void paintPostZoom(Graphics g, Dimension d) {

	}

	@Nullable private GUIComponent getGUIComponentAt(int ex, int ey) {
		List<GUIComponent> guiComponentList = new ArrayList<>(wysiwygEditor.components);
		guiComponentList.sort(Collections.reverseOrder());

		for (GUIComponent component : guiComponentList) {
			if (!component.visible)
				continue;
			if (ex >= component.getX() && ex <= component.getX() + component.getWidth(
					wysiwygEditor.mcreator.getWorkspace())) {
				if (ey >= component.getY() && ey <= component.getY() + component.getHeight(
						wysiwygEditor.mcreator.getWorkspace())) {
					return component;
				}
			}
		}
		return null;
	}

	public void cancelMoveMode() {
		componentMoveMode = false;
		positioningModeSettingWidth = false;
		positioningModeSettingHeight = false;
		moveOrigins.clear();
		owner.setCursor(Cursor.getDefaultCursor());
		repaint();
	}

	private void selectComponentAndGroup(GUIComponent component) {
		if (component.groupName == null || component.groupName.isBlank()) {
			wysiwygEditor.list.setSelectedValue(component, true);
			return;
		}
		List<Integer> indices = new ArrayList<>();
		for (int i = 0; i < wysiwygEditor.components.size(); i++) {
			GUIComponent candidate = wysiwygEditor.components.get(i);
			if (component.groupName.equals(candidate.groupName))
				indices.add(i);
		}
		wysiwygEditor.list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
	}

	private void finishGUIComponentMove() {
		Point selectedOrigin = moveOrigins.get(selected);
		int deltaX = selectedOrigin == null ? 0 : ox - selectedOrigin.x;
		int deltaY = selectedOrigin == null ? 0 : oy - selectedOrigin.y;
		for (GUIComponent component : selectedComponents) {
			Point origin = moveOrigins.get(component);
			if (!component.locked && origin != null) {
				component.x = origin.x + deltaX;
				component.y = origin.y + deltaY;
				if (component.equals(selected) && positioningModeSettingWidth && component instanceof SizedComponent sized) {
					sized.width = ow;
					sized.height = oh;
				}
			}
		}
		componentMoveMode = false;
		positioningModeSettingWidth = false;
		positioningModeSettingHeight = false;
		moveOrigins.clear();

		owner.setCursor(Cursor.getDefaultCursor());

		repaint();
	}

}
