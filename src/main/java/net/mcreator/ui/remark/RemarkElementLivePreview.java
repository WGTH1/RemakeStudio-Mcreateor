/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.Fashion;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.workspace.resources.TextureType;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public final class RemarkElementLivePreview {
	private static final Color BACKGROUND = new Color(22, 20, 22);
	private static final Color GRID = new Color(64, 59, 61);
	private static final Color GOLD = new Color(225, 180, 73);
	private static final Color PINK = new Color(226, 39, 126);
	private static final Color TEXT = new Color(238, 235, 231);

	private RemarkElementLivePreview() {
	}

	@Nullable public static JComponent create(GeneratableElement element) {
		if (element instanceof AAAParticle particle)
			return new AAAPreview(particle);
		if (element instanceof Fashion fashion)
			return new FashionPreview(fashion);
		return null;
	}

	private abstract static class AnimatedCanvas extends JPanel {
		protected int tick;
		private final Timer timer = new Timer(33, _ -> {
			tick++;
			repaint();
		});

		private AnimatedCanvas() {
			setOpaque(true);
			setBackground(BACKGROUND);
			setPreferredSize(new Dimension(660, 370));
		}

		@Override public void addNotify() {
			super.addNotify();
			timer.start();
		}

		@Override public void removeNotify() {
			timer.stop();
			super.removeNotify();
		}

		protected void prepare(Graphics2D graphics) {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		}

		protected void drawGrid(Graphics2D graphics) {
			graphics.setColor(GRID);
			for (int x = 0; x < getWidth(); x += 32)
				graphics.drawLine(x, 0, x, getHeight());
			for (int y = 0; y < getHeight(); y += 32)
				graphics.drawLine(0, y, getWidth(), y);
		}
	}

	private static final class AAAPreview extends AnimatedCanvas {
		private final AAAParticle particle;

		private AAAPreview(AAAParticle particle) {
			this.particle = particle;
			setToolTipText(L10N.t("remark.tools.live_preview.aaa_tip"));
		}

		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			drawGrid(g);

			int sourceX = Math.max(90, getWidth() / 2 - 100);
			int sourceY = Math.max(180, getHeight() * 2 / 3);
			double facing = switch (particle.getSpawnModeSafe()) {
				case "source_facing" -> -Math.PI / 7;
				case "source_follow_view" -> -Math.PI / 7 + Math.sin(tick * 0.035) * 0.75;
				default -> 0;
			};

			drawSource(g, sourceX, sourceY, facing);
			double offsetScale = 18;
			double localX = particle.offsetX * offsetScale;
			double localZ = particle.offsetZ * offsetScale;
			double offsetX = localX;
			double offsetDepth = localZ;
			if (!"world".equals(particle.getSpawnModeSafe())) {
				offsetX = localX * Math.cos(facing) - localZ * Math.sin(facing);
				offsetDepth = localX * Math.sin(facing) + localZ * Math.cos(facing);
			}
			double originX = sourceX + offsetX + offsetDepth * 0.35;
			double originY = sourceY - particle.offsetY * offsetScale - offsetDepth * 0.25;
			double effectDirection = ("world".equals(particle.getSpawnModeSafe()) ? 0 : facing)
					+ Math.toRadians(particle.rotationY);

			g.setColor(new Color(255, 255, 255, 115));
			g.drawLine(sourceX, sourceY, (int) originX, (int) originY);
			g.setColor(GOLD);
			g.fill(new Ellipse2D.Double(originX - 5, originY - 5, 10, 10));

			double averageScale = Math.max(0.15,
					(Math.abs(particle.scaleX) + Math.abs(particle.scaleY) + Math.abs(particle.scaleZ)) / 3.0);
			double speed = Math.max(0.15, Math.abs(particle.speed));
			for (int index = 0; index < 34; index++) {
				double life = ((tick * 0.018 * speed + index / 34.0) % 1.0);
				double distance = life * Math.min(210, 72 * speed);
				double spread = Math.sin(index * 2.17 + tick * 0.05) * (8 + life * 30) * averageScale;
				double x = originX + Math.cos(effectDirection) * distance - Math.sin(effectDirection) * spread;
				double y = originY + Math.sin(effectDirection) * distance * 0.42
						- Math.sin(life * Math.PI) * 72 * Math.max(0.25, Math.abs(particle.scaleY));
				double radius = Math.max(2, Math.min(14, (1.0 - life) * 7 * averageScale));
				int alpha = (int) Math.max(20, 220 * (1.0 - life));
				g.setColor(index % 3 == 0 ? new Color(255, 215, 95, alpha) : new Color(238, 43, 143, alpha));
				g.fill(new Ellipse2D.Double(x - radius, y - radius, radius * 2, radius * 2));
			}

			drawAAALabels(g);
			g.dispose();
		}

		private void drawSource(Graphics2D g, int x, int y, double facing) {
			g.setStroke(new BasicStroke(2f));
			g.setColor(new Color(205, 202, 205));
			g.drawOval(x - 9, y - 38, 18, 18);
			g.drawLine(x, y - 20, x, y + 8);
			g.drawLine(x, y - 10, x - 12, y);
			g.drawLine(x, y - 10, x + 12, y);
			g.drawLine(x, y + 8, x - 10, y + 25);
			g.drawLine(x, y + 8, x + 10, y + 25);
			g.setColor(PINK);
			int endX = (int) (x + Math.cos(facing) * 60);
			int endY = (int) (y - 28 + Math.sin(facing) * 28);
			g.drawLine(x, y - 28, endX, endY);
			g.fillOval(endX - 3, endY - 3, 6, 6);
		}

		private void drawAAALabels(Graphics2D g) {
			g.setColor(TEXT);
			g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
			String name = particle.sourceName == null || particle.sourceName.isBlank() ?
					particle.getModElement().getName() : particle.sourceName;
			g.drawString(name, 16, 24);
			g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
			g.drawString(L10N.t("remark.tools.live_preview.mode") + ": "
					+ L10N.t("remark.tools.live_preview.mode." + particle.getSpawnModeSafe()), 16, 44);
			g.drawString(String.format(java.util.Locale.ROOT, "Offset %.1f, %.1f, %.1f", particle.offsetX,
					particle.offsetY, particle.offsetZ), 16, getHeight() - 34);
			g.drawString(String.format(java.util.Locale.ROOT, "Rotation %.0f, %.0f, %.0f | Scale %.2f, %.2f, %.2f | Speed %.2f",
					particle.rotationX, particle.rotationY, particle.rotationZ, particle.scaleX, particle.scaleY,
					particle.scaleZ, particle.speed), 16, getHeight() - 16);
		}
	}

	private static final class FashionPreview extends JPanel {
		private final Fashion fashion;
		private final FashionCanvas canvas;

		private FashionPreview(Fashion fashion) {
			super(new BorderLayout(6, 6));
			this.fashion = fashion;
			this.canvas = new FashionCanvas(fashion);
			add(createSlotBar(), BorderLayout.NORTH);
			add(canvas, BorderLayout.CENTER);
		}

		private JComponent createSlotBar() {
			JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
			bar.add(new JLabel(L10N.t("remark.tools.live_preview.worn_slot")));
			ButtonGroup group = new ButtonGroup();
			addSlotButton(bar, group, "head", fashion.enableHelmet);
			addSlotButton(bar, group, "body", fashion.enableBody);
			addSlotButton(bar, group, "legs", fashion.enableLeggings);
			addSlotButton(bar, group, "feet", fashion.enableBoots);
			if (group.getSelection() == null)
				canvas.slot = "none";
			return bar;
		}

		private void addSlotButton(JPanel bar, ButtonGroup group, String slot, boolean enabled) {
			JToggleButton button = new JToggleButton(L10N.t("remark.tools.live_preview.slot." + slot));
			button.setEnabled(enabled);
			button.addActionListener(_ -> {
				canvas.slot = slot;
				canvas.repaint();
			});
			group.add(button);
			bar.add(button);
			if (enabled && group.getSelection() == null) {
				button.setSelected(true);
				canvas.slot = slot;
			}
		}
	}

	private static final class FashionCanvas extends AnimatedCanvas {
		private final Fashion fashion;
		private String slot = "none";

		private FashionCanvas(Fashion fashion) {
			this.fashion = fashion;
			setToolTipText(L10N.t("remark.tools.live_preview.fashion_tip"));
		}

		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			prepare(g);
			drawGrid(g);
			int cx = getWidth() / 2;
			int top = 48;
			double turn = 0.72 + Math.cos(tick * 0.025) * 0.22;
			int bodyWidth = (int) (92 * turn);

			drawMannequin(g, cx, top, bodyWidth);
			drawFashionPart(g, cx, top, bodyWidth);
			drawFashionLabels(g);
			drawItemTexture(g);
			g.dispose();
		}

		private void drawMannequin(Graphics2D g, int cx, int top, int width) {
			g.setColor(new Color(112, 107, 110));
			g.fill(new Ellipse2D.Double(cx - width * 0.27, top, width * 0.54, 52));
			g.fill(new RoundRectangle2D.Double(cx - width / 2.0, top + 58, width, 105, 18, 18));
			g.fill(new RoundRectangle2D.Double(cx - width * 0.44, top + 168, width * 0.38, 105, 14, 14));
			g.fill(new RoundRectangle2D.Double(cx + width * 0.06, top + 168, width * 0.38, 105, 14, 14));
			g.setColor(new Color(81, 77, 80));
			g.fillRoundRect(cx - width / 2, top + 272, width * 2 / 5, 28, 9, 9);
			g.fillRoundRect(cx + width / 10, top + 272, width * 2 / 5, 28, 9, 9);
		}

		private void drawFashionPart(Graphics2D g, int cx, int top, int width) {
			g.setColor(new Color(PINK.getRed(), PINK.getGreen(), PINK.getBlue(), 190));
			g.setStroke(new BasicStroke(3f));
			switch (slot) {
				case "head" -> {
					g.fill(new Ellipse2D.Double(cx - width * 0.34, top - 5, width * 0.68, 61));
					g.setColor(GOLD);
					g.draw(new Ellipse2D.Double(cx - width * 0.34, top - 5, width * 0.68, 61));
				}
				case "body" -> {
					g.fill(new RoundRectangle2D.Double(cx - width * 0.57, top + 53, width * 1.14, 116, 18, 18));
					g.setColor(GOLD);
					g.draw(new RoundRectangle2D.Double(cx - width * 0.57, top + 53, width * 1.14, 116, 18, 18));
				}
				case "legs" -> {
					g.fill(new RoundRectangle2D.Double(cx - width * 0.49, top + 162, width * 0.44, 116, 14, 14));
					g.fill(new RoundRectangle2D.Double(cx + width * 0.05, top + 162, width * 0.44, 116, 14, 14));
					g.setColor(GOLD);
					g.draw(new RoundRectangle2D.Double(cx - width * 0.49, top + 162, width * 0.44, 116, 14, 14));
					g.draw(new RoundRectangle2D.Double(cx + width * 0.05, top + 162, width * 0.44, 116, 14, 14));
				}
				case "feet" -> {
					g.fillRoundRect(cx - width / 2, top + 268, width * 2 / 5, 34, 9, 9);
					g.fillRoundRect(cx + width / 10, top + 268, width * 2 / 5, 34, 9, 9);
					g.setColor(GOLD);
					g.drawRoundRect(cx - width / 2, top + 268, width * 2 / 5, 34, 9, 9);
					g.drawRoundRect(cx + width / 10, top + 268, width * 2 / 5, 34, 9, 9);
				}
				default -> {
				}
			}
		}

		private void drawFashionLabels(Graphics2D g) {
			g.setColor(TEXT);
			g.setFont(g.getFont().deriveFont(Font.BOLD, 14f));
			g.drawString(fashion.getModElement().getName(), 16, 24);
			g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
			g.drawString(L10N.t("remark.tools.live_preview.slot") + ": "
					+ L10N.t("remark.tools.live_preview.slot." + slot), 16, 44);
			g.drawString(L10N.t("remark.tools.live_preview.model") + ": " + safe(modelName()), 16,
					getHeight() - 35);
			g.drawString(L10N.t("remark.tools.live_preview.part") + ": " + safe(modelPart()), 16,
					getHeight() - 17);
		}

		private void drawItemTexture(Graphics2D g) {
			TextureHolder texture = texture();
			if (texture == null)
				return;
			try {
				Image image = texture.getImage(TextureType.ITEM);
				if (image == null)
					return;
				int size = 72;
				int x = getWidth() - size - 20;
				int y = 20;
				g.setColor(new Color(0, 0, 0, 110));
				g.fillRoundRect(x - 7, y - 7, size + 14, size + 14, 8, 8);
				g.drawImage(image, x, y, size, size, null);
				g.setColor(TEXT);
				g.drawString(L10N.t("remark.tools.live_preview.item"), x, y + size + 18);
			} catch (Exception ignored) {
			}
		}

		@Nullable private TextureHolder texture() {
			return switch (slot) {
				case "head" -> fashion.textureHelmet;
				case "body" -> fashion.textureBody;
				case "legs" -> fashion.textureLeggings;
				case "feet" -> fashion.textureBoots;
				default -> null;
			};
		}

		private String modelName() {
			return switch (slot) {
				case "head" -> fashion.helmetModelName;
				case "body" -> fashion.bodyModelName;
				case "legs" -> fashion.leggingsModelName;
				case "feet" -> fashion.bootsModelName;
				default -> "";
			};
		}

		private String modelPart() {
			return switch (slot) {
				case "head" -> fashion.helmetModelPart;
				case "body" -> fashion.bodyModelPart;
				case "legs" -> String.join(" + ", safe(fashion.leggingsModelPartL), safe(fashion.leggingsModelPartR));
				case "feet" -> String.join(" + ", safe(fashion.bootsModelPartL), safe(fashion.bootsModelPartR));
				default -> "";
			};
		}
	}

	private static String safe(@Nullable String value) {
		return value == null || value.isBlank() ? L10N.t("remark.tools.live_preview.default") : value;
	}
}
