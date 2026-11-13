/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.component;

import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.element.parts.WornDisplaySettings;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.workspace.resources.Model;
import net.mcreator.workspace.resources.TexturedModel;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Lightweight OBJ preview for item display transforms. It intentionally uses the same OBJ vertices, UVs and selected
 * texture mapping as the workspace model, while keeping the editor independent from a running Minecraft client.
 */
public final class ItemDisplayPreview extends JPanel {

	public enum ViewContext {
		GUI, GROUND, FIXED, THIRD_RIGHT, THIRD_LEFT, FIRST_RIGHT, FIRST_LEFT
	}

	public enum ToolMode {
		MOVE, ROTATE, SCALE
	}

	private enum Axis {
		NONE, X, Y, Z, CENTER
	}

	private static final int MAX_FACES = 100_000;
	private static final int GUI_SLOT_SIZE = 150;
	private static final int ITEM_FRAME_SIZE = 170;
	private static final int PLAYER_ARM_WIDTH = 44;
	private static final int GIZMO_LENGTH = 62;
	private static final Vec3 MODEL_PIVOT = new Vec3(0.5, 0.5, 0.5);
	private static final Color PANEL_BACKGROUND = new Color(21, 18, 22);
	private static final Color GUIDE = new Color(130, 120, 132, 115);
	private static final Map<Axis, Color> AXIS_COLORS = Map.of(Axis.X, new Color(238, 78, 86),
			Axis.Y, new Color(78, 202, 118), Axis.Z, new Color(75, 145, 245));

	private final Mesh mesh;
	private final Supplier<Transform> transformReader;
	private final Consumer<Transform> transformWriter;

	private ViewContext viewContext = ViewContext.GUI;
	private ToolMode toolMode = ToolMode.MOVE;
	private boolean snapEnabled;
	private double cameraYaw;
	private double cameraPitch;
	private double cameraZoom = 1;
	private double cameraPanX;
	private double cameraPanY;
	private double dragCameraYaw;
	private double dragCameraPitch;
	private double dragCameraPanX;
	private double dragCameraPanY;
	private int dragButton;
	private Axis activeAxis = Axis.NONE;
	private Axis hoverAxis = Axis.NONE;
	private GizmoGeometry gizmoGeometry;
	private double lastModelUnitScale = GUI_SLOT_SIZE;
	private Point dragOrigin;
	private Transform dragTransform;
	private String wornSlot;
	private Transform wornBase = new Transform();
	public enum Pose { STANDING, WALKING, CROUCHING, HEAD_TURN }
	private Pose pose = Pose.STANDING;
	private WornDisplaySettings wornSides;
	private boolean editingLeft = true, renderingLeft = true;
	public void setWornSides(WornDisplaySettings settings, boolean left) { wornSides = settings; editingLeft = left; repaint(); }
	public void setPose(Pose pose) { this.pose = Objects.requireNonNull(pose); repaint(); }

	@Nullable public Transform fitToReference() {
		if (mesh.vertices.isEmpty()) return null;
		Transform result = transformReader.get().copy();
		boolean leftHand = isLeftHand();
		double[] min = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };
		double[] max = { -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE };
		for (Vec3 point : mesh.vertices) {
			Vec3 vertex = point.subtract(MODEL_PIVOT);
			if (wornSlot != null) {
				vertex = rotate(new Vec3(vertex.x * wornBase.scaleX, vertex.y * wornBase.scaleY, vertex.z * wornBase.scaleZ),
						wornBase.rotationX, wornBase.rotationY, wornBase.rotationZ);
				vertex = vertex.add(new Vec3(wornBase.translationX / 16, wornBase.translationY / 16, wornBase.translationZ / 16));
			}
			vertex = rotate(vertex, result.rotationX, leftHand ? -result.rotationY : result.rotationY,
					leftHand ? -result.rotationZ : result.rotationZ);
			double[] values = { vertex.x, vertex.y, vertex.z };
			for (int i = 0; i < 3; i++) { min[i] = Math.min(min[i], values[i]); max[i] = Math.max(max[i], values[i]); }
		}
		double[] target = wornSlot == null ? new double[] { .8, .8, .8 } : switch (wornSlot) {
			case "helmet" -> new double[] { .8, .8, .8 };
			case "body" -> new double[] { .5/.72, .75/.72, .25/.72 };
			case "leggings" -> new double[] { .25/.48, .75/.48, .25/.48 };
			default -> new double[] { .25/.42, .25/.42, .25/.42 };
		};
		double scale = 4;
		for (int i = 0; i < 3; i++) if (max[i] - min[i] > 1e-9) scale = Math.min(scale, target[i] / (max[i] - min[i]));
		if (!Double.isFinite(scale) || scale <= 0) return null;
		result.scaleX = result.scaleY = result.scaleZ = scale;
		result.translationX = clamp((leftHand ? 1 : -1) * (min[0] + max[0]) * .5 * scale * 16, -80, 80);
		result.translationY = clamp(-(min[1] + max[1]) * .5 * scale * 16, -80, 80);
		result.translationZ = clamp(-(min[2] + max[2]) * .5 * scale * 16, -80, 80);
		return result;
	}

	public void setWornContext(String slot, Transform base) {
		wornSlot = slot;
		wornBase = base.copy();
		resetCamera();
	}

	public ItemDisplayPreview(@Nullable Model model, Supplier<Transform> transformReader,
			Consumer<Transform> transformWriter) {
		this.mesh = Mesh.load(model);
		this.transformReader = transformReader;
		this.transformWriter = transformWriter;
		setOpaque(true);
		setBackground(PANEL_BACKGROUND);
		setPreferredSize(new Dimension(410, 340));
		setMinimumSize(new Dimension(300, 270));
		setFocusable(true);
		setToolTipText(L10N.t("dialog.item_display.preview_tooltip"));
		installMouseControls();
		installKeyboardControls();
	}

	public void setViewContext(ViewContext viewContext) {
		wornSlot = null;
		wornSides = null;
		this.viewContext = viewContext;
		resetCamera();
		repaint();
	}

	public void setToolMode(ToolMode toolMode) {
		ToolMode previous = this.toolMode;
		this.toolMode = Objects.requireNonNull(toolMode);
		activeAxis = Axis.NONE;
		firePropertyChange("toolMode", previous, toolMode);
		repaint();
	}

	public ToolMode getToolMode() {
		return toolMode;
	}

	public void setSnapEnabled(boolean snapEnabled) {
		this.snapEnabled = snapEnabled;
	}

	public boolean isSnapEnabled() {
		return snapEnabled;
	}

	public void resetCamera() {
		cameraYaw = 0;
		cameraPitch = 0;
		cameraZoom = 1;
		cameraPanX = 0;
		cameraPanY = 0;
		repaint();
	}

	public double getCameraZoom() {
		return cameraZoom;
	}

	public int getFaceCount() {
		return mesh.faces.size();
	}

	@Override protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		int width = getWidth();
		int height = getHeight();
		int guideSize = Math.max(96, Math.min(width - 44, height - 60));
		int centerX = width / 2;
		int centerY = height / 2 + 8;
		if (wornSlot != null) {
			double anchorY = switch (wornSlot) { case "helmet" -> 1.75; case "body" -> 1.15; case "leggings" -> .45; default -> .05; };
			centerY += (int) ((1 - anchorY) * modelUnitScale(guideSize));
			if ("leggings".equals(wornSlot) || "boots".equals(wornSlot))
				centerX += (int) (.125 * modelUnitScale(guideSize));
		}

		paintGrid(g, width, height);
		Graphics2D guideGraphics = (Graphics2D) g.create();
		guideGraphics.translate(centerX + cameraPanX, centerY + cameraPanY);
		guideGraphics.scale(cameraZoom, cameraZoom);
		guideGraphics.translate(-centerX, -centerY);
		paintGuide(guideGraphics, centerX, centerY, guideSize);
		guideGraphics.dispose();
		if (mesh.faces.isEmpty()) {
			gizmoGeometry = null;
			paintUnavailable(g, width, height);
		} else {
			Transform transform = transformReader.get();
			lastModelUnitScale = modelUnitScale(guideSize);
			for (PaintFace face : buildPaintFaces(transform, centerX, centerY, lastModelUnitScale)) paintFace(g, face);
			renderingLeft = editingLeft;
			Vec3 offset = wornOffset();
			paintGizmo(g, transform, centerX + (int) (offset.x * lastModelUnitScale * cameraZoom),
					centerY - (int) (offset.y * lastModelUnitScale * cameraZoom), lastModelUnitScale);
		}
		g.dispose();
	}

	private void paintGrid(Graphics2D g, int width, int height) {
		g.setColor(new Color(255, 255, 255, 8));
		for (int x = 0; x < width; x += 20)
			g.drawLine(x, 0, x, height);
		for (int y = 0; y < height; y += 20)
			g.drawLine(0, y, width, y);
	}

	private void paintGuide(Graphics2D g, int centerX, int centerY, int size) {
		if (wornSlot != null) {
			paintWornGuide(g, centerX, centerY, modelUnitScale(size));
			return;
		}
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(GUIDE);
		switch (viewContext) {
		case GUI -> {
			int slot = Math.min(GUI_SLOT_SIZE, size);
			int x = centerX - slot / 2;
			int y = centerY - slot / 2;
			g.fillRoundRect(x, y, slot, slot, 5, 5);
			g.setColor(new Color(225, 220, 226, 150));
			g.drawRoundRect(x, y, slot, slot, 5, 5);
		}
		case GROUND -> {
			int y = centerY + size / 3;
			Polygon plane = new Polygon(new int[] { centerX - size / 2, centerX + size / 2,
					centerX + size / 3, centerX - size / 3 },
					new int[] { y, y, y + size / 5, y + size / 5 }, 4);
			g.fillPolygon(plane);
			g.setColor(new Color(225, 220, 226, 150));
			g.drawPolygon(plane);
		}
		case FIXED -> {
			int frame = Math.min(ITEM_FRAME_SIZE, size);
			g.setColor(new Color(84, 62, 45, 165));
			g.fillRect(centerX - frame / 2, centerY - frame / 2, frame, frame);
			g.setColor(new Color(199, 169, 121, 170));
			g.drawRect(centerX - frame / 2, centerY - frame / 2, frame, frame);
		}
		case THIRD_RIGHT, THIRD_LEFT -> paintThirdPersonArm(g, centerX, centerY);
		case FIRST_RIGHT -> paintArmGuide(g, centerX + size / 3, centerY + size / 3, false);
		case FIRST_LEFT -> paintArmGuide(g, centerX - size / 3, centerY + size / 3, true);
		}
	}

	private void paintWornGuide(Graphics2D g, int centerX, int centerY, double units) {
		Vec3 anchor = switch (wornSlot) {
			case "helmet" -> new Vec3(0, 1.75, 0);
			case "body" -> new Vec3(0, 1.15, 0.2);
			case "leggings" -> new Vec3(0.125, 0.45, 0.08);
			default -> new Vec3(0.125, 0.05, 0.08);
		};
		double[][] boxes = { {-.25,1.5,-.25,.25,2,.25}, {-.25,.75,-.125,.25,1.5,.125},
				{-.5,.75,-.125,-.25,1.5,.125}, {.25,.75,-.125,.5,1.5,.125},
				{-.25,0,-.125,0,.75,.125}, {0,0,-.125,.25,.75,.125} };
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(new Color(150, 184, 192, 190));
		for (int part = 0; part < boxes.length; part++) {
			double[] box = boxes[part];
			Vec3[] points = new Vec3[8];
			for (int i = 0; i < 8; i++) {
				Vec3 world = new Vec3(box[(i & 1) == 0 ? 0 : 3], box[(i & 2) == 0 ? 1 : 4], box[(i & 4) == 0 ? 2 : 5]);
				Vec3 v = rotate(posePoint(world, part).subtract(anchor), cameraPitch, cameraYaw, 0);
				points[i] = new Vec3(centerX + v.x * units, centerY - v.y * units, v.z);
			}
			for (int i = 0; i < 8; i++) for (int bit : new int[] { 1, 2, 4 }) if ((i & bit) == 0)
				g.draw(new Line2D.Double(points[i].x, points[i].y, points[i | bit].x, points[i | bit].y));
		}
	}

	private void paintThirdPersonArm(Graphics2D g, int centerX, int centerY) {
		// Vanilla standing arm: 4 x 12 x 4 pixels, relative to the ItemInHandLayer attachment.
		Vec3[] points = new Vec3[8];
		for (int i = 0; i < 8; i++) {
			Vec3 p = rotate(new Vec3((i & 1) == 0 ? -0.125 : 0.125,
					(i & 2) == 0 ? 0 : 0.75, (i & 4) == 0 ? -0.25 : 0), cameraPitch, cameraYaw, 0);
			points[i] = new Vec3(centerX + p.x * PLAYER_ARM_WIDTH * 4,
					centerY - p.y * PLAYER_ARM_WIDTH * 4, p.z);
		}
		g.setColor(new Color(202, 154, 120, 180));
		for (int i = 0; i < 8; i++) for (int bit : new int[] { 1, 2, 4 })
			if ((i & bit) == 0) g.draw(new Line2D.Double(points[i].x, points[i].y,
					points[i | bit].x, points[i | bit].y));
	}

	private Vec3 displayToView(Vec3 vertex) {
		if (wornSlot != null) {
			double scale = switch (wornSlot) {
				case "helmet" -> 0.625;
				case "body" -> 0.72;
				case "leggings" -> 0.48;
				default -> 0.42;
			};
			Vec3 posed = poseDirection(new Vec3(-vertex.x * scale, vertex.y * scale, -vertex.z * scale), wornPart());
			return rotate(posed, cameraPitch, cameraYaw, 0);
		}
		if (viewContext == ViewContext.THIRD_RIGHT || viewContext == ViewContext.THIRD_LEFT) {
			// ItemInHandLayer: X -90, Y 180; convert downward player-model Y to upright preview space.
			vertex = rotate(vertex, -90, 180, 0);
			vertex = new Vec3(vertex.x, -vertex.y, -vertex.z);
		}
		return rotate(vertex, cameraPitch, cameraYaw, 0);
	}

	private int wornPart() {
		return "helmet".equals(wornSlot) ? 0 : "body".equals(wornSlot) ? 1 : renderingLeft ? 5 : 4;
	}
	private Vec3 poseDirection(Vec3 value, int part) {
		double x = 0, y = 0;
		if (pose == Pose.WALKING) x = part == 4 || part == 3 ? -25 : part == 5 || part == 2 ? 25 : 0;
		if (pose == Pose.CROUCHING) x = part >= 4 ? -15 : part == 0 ? 0 : 25;
		if (pose == Pose.HEAD_TURN && part == 0) y = 40;
		return rotate(value, x, y, 0);
	}
	private Vec3 posePoint(Vec3 world, int part) {
		Vec3 pivot = switch (part) {
			case 0, 1 -> new Vec3(0, 1.5, 0);
			case 2 -> new Vec3(-.375, 1.5, 0);
			case 3 -> new Vec3(.375, 1.5, 0);
			case 4 -> new Vec3(-.125, .75, 0);
			default -> new Vec3(.125, .75, 0);
		};
		Vec3 result = poseDirection(world.subtract(pivot), part).add(pivot);
		return pose == Pose.CROUCHING ? result.add(new Vec3(0, -.15, .10)) : result;
	}
	private Vec3 wornOffset() {
		if (wornSlot == null) return new Vec3(0, 0, 0);
		Vec3 anchor = switch (wornSlot) {
			case "helmet" -> new Vec3(0, 1.75, 0);
			case "body" -> new Vec3(0, 1.15, .2);
			case "leggings" -> new Vec3(.125, .45, .08);
			default -> new Vec3(.125, .05, .08);
		};
		Vec3 origin = renderingLeft || wornPart() < 4 ? anchor : anchor.add(new Vec3(-.25, 0, 0));
		return rotate(posePoint(origin, wornPart()).subtract(anchor), cameraPitch, cameraYaw, 0);
	}
	private List<PaintFace> buildPaintFaces(Transform transform, int centerX, int centerY, double units) {
		List<PaintFace> faces = new ArrayList<>(mesh.faces.size() * 2);
		renderingLeft = true;
		if (wornSlot == null || wornSides == null || wornSides.isSideEnabled(wornSlot, true))
			collectPaintFaces(faces, wornSlot == null || wornSides == null ? transform : wornSides.getForSide(wornSlot, true), centerX, centerY, units);
		if ("leggings".equals(wornSlot) || "boots".equals(wornSlot)) {
			renderingLeft = false;
			if (wornSides == null || wornSides.isSideEnabled(wornSlot, false))
				collectPaintFaces(faces, wornSides == null ? transform : wornSides.getForSide(wornSlot, false), centerX, centerY, units);
		}
		renderingLeft = editingLeft;
		faces.sort(Comparator.comparingDouble(PaintFace::depth));
		return faces;
	}

	private double modelUnitScale(int guideSize) {
		if (wornSlot != null) return Math.min(150, guideSize / 2.5);
		return switch (viewContext) {
			case GUI -> Math.min(GUI_SLOT_SIZE, guideSize);
			case GROUND -> guideSize;
			case FIXED -> Math.min(ITEM_FRAME_SIZE, guideSize) * 0.5;
			case THIRD_RIGHT, THIRD_LEFT, FIRST_RIGHT, FIRST_LEFT -> PLAYER_ARM_WIDTH * 4.0;
		};
	}

	private static void paintArmGuide(Graphics2D g, int x, int y, boolean left) {
		AffineTransform old = g.getTransform();
		g.rotate(Math.toRadians(left ? 24 : -24), x, y);
		g.setColor(new Color(202, 154, 120, 125));
		g.fillRoundRect(x - 22, y - 82, 44, 150, 12, 12);
		g.setColor(new Color(235, 201, 177, 150));
		g.drawRoundRect(x - 22, y - 82, 44, 150, 12, 12);
		g.setTransform(old);
	}

	private void paintUnavailable(Graphics2D g, int width, int height) {
		String text = L10N.t("dialog.item_display.preview_unavailable");
		FontMetrics metrics = g.getFontMetrics();
		g.setColor(new Color(230, 225, 230, 190));
		g.drawString(text, Math.max(16, (width - metrics.stringWidth(text)) / 2), height / 2);
	}

	private boolean isLeftHand() {
		return wornSlot == null && (viewContext == ViewContext.THIRD_LEFT || viewContext == ViewContext.FIRST_LEFT);
	}

	private void collectPaintFaces(List<PaintFace> paintFaces, @Nullable Transform transform, int centerX, int centerY,
			double modelUnitScale) {
		if (transform == null)
			return;

		boolean leftHand = isLeftHand();
		Vec3 offset = wornOffset();
		double rotationY = leftHand ? -transform.rotationY : transform.rotationY;
		double rotationZ = leftHand ? -transform.rotationZ : transform.rotationZ;
		double translationX = leftHand ? -transform.translationX : transform.translationX;
		Vec3[] projected = new Vec3[mesh.vertices.size()];
		for (int i = 0; i < mesh.vertices.size(); i++) {
			// Minecraft applies display translation/rotation/scale, then offsets baked model coordinates by -0.5.
			Vec3 vertex = mesh.vertices.get(i).subtract(MODEL_PIVOT);
			if (wornSlot != null) {
				vertex = rotate(new Vec3(vertex.x * wornBase.scaleX, vertex.y * wornBase.scaleY,
						vertex.z * wornBase.scaleZ), wornBase.rotationX, wornBase.rotationY, wornBase.rotationZ);
				vertex = vertex.add(new Vec3(wornBase.translationX / 16, wornBase.translationY / 16, wornBase.translationZ / 16));
			}
			vertex = new Vec3(vertex.x * transform.scaleX, vertex.y * transform.scaleY,
					vertex.z * transform.scaleZ);
			vertex = rotate(vertex, transform.rotationX, rotationY, rotationZ);
			vertex = vertex.add(new Vec3(translationX / 16.0, transform.translationY / 16.0,
					transform.translationZ / 16.0));
			vertex = displayToView(vertex).add(offset);
			projected[i] = new Vec3(
					centerX + cameraPanX + vertex.x * modelUnitScale * cameraZoom,
					centerY + cameraPanY - vertex.y * modelUnitScale * cameraZoom,
					vertex.z * modelUnitScale * cameraZoom);
		}

		for (Face face : mesh.faces) {
			Vec3 first = projected[face.vertexA];
			Vec3 second = projected[face.vertexB];
			Vec3 third = projected[face.vertexC];
			paintFaces.add(new PaintFace(face, first, second, third, (first.z + second.z + third.z) / 3));
		}
	}

	private void paintGizmo(Graphics2D g, @Nullable Transform transform, int centerX, int centerY,
			double modelUnitScale) {
		if (transform == null) {
			gizmoGeometry = null;
			return;
		}

		boolean leftHand = isLeftHand();
		Vec3 translation = new Vec3((leftHand ? -transform.translationX : transform.translationX) / 16.0,
				transform.translationY / 16.0, transform.translationZ / 16.0);
		Vec3 projectedOrigin = displayToView(translation);
		Point2D.Double origin = new Point2D.Double(
				centerX + cameraPanX + projectedOrigin.x * modelUnitScale * cameraZoom,
				centerY + cameraPanY - projectedOrigin.y * modelUnitScale * cameraZoom);
		Map<Axis, Point2D.Double> endpoints = new EnumMap<>(Axis.class);
		endpoints.put(Axis.X, axisEndpoint(origin, cameraAxisDirection(Axis.X, leftHand)));
		endpoints.put(Axis.Y, axisEndpoint(origin, cameraAxisDirection(Axis.Y, false)));
		endpoints.put(Axis.Z, axisEndpoint(origin, cameraAxisDirection(Axis.Z, false)));
		gizmoGeometry = new GizmoGeometry(origin, endpoints);

		Stroke previousStroke = g.getStroke();
		Font previousFont = g.getFont();
		g.setFont(previousFont.deriveFont(Font.BOLD, Math.max(10f, previousFont.getSize2D() - 1)));
		for (Axis axis : List.of(Axis.X, Axis.Y, Axis.Z)) {
			Point2D.Double endpoint = endpoints.get(axis);
			if (endpoint.distance(origin) < 1) continue;
			boolean highlighted = activeAxis == axis || hoverAxis == axis;
			g.setColor(highlighted ? Color.WHITE : AXIS_COLORS.get(axis));
			g.setStroke(new BasicStroke(highlighted ? 3.5f : 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(new Line2D.Double(origin, endpoint));
			paintGizmoHandle(g, origin, endpoint, axis);
		}

		boolean centerHighlighted = activeAxis == Axis.CENTER || hoverAxis == Axis.CENTER;
		g.setColor(centerHighlighted ? Color.WHITE : new Color(235, 230, 238));
		if (toolMode == ToolMode.ROTATE) {
			g.setStroke(new BasicStroke(centerHighlighted ? 3f : 2f));
			g.drawOval((int) origin.x - 12, (int) origin.y - 12, 24, 24);
		} else {
			g.fillRect((int) origin.x - 4, (int) origin.y - 4, 8, 8);
		}
		g.setFont(previousFont);
		g.setStroke(previousStroke);
	}

	private Point2D.Double cameraAxisDirection(Axis axis, boolean reverseX) {
		Vec3 source = switch (axis) {
			case X -> new Vec3(reverseX ? -1 : 1, 0, 0);
			case Y -> new Vec3(0, 1, 0);
			case Z -> new Vec3(0, 0, 1);
			default -> new Vec3(0, 0, 0);
		};
		Vec3 direction = displayToView(source);
		double screenX = direction.x;
		double screenY = -direction.y;
		double length = Math.hypot(screenX, screenY);
		if (length < 0.2) {
			if (toolMode == ToolMode.MOVE) return new Point2D.Double();
			screenX = -0.68;
			screenY = 0.68;
			length = Math.hypot(screenX, screenY);
		}
		return new Point2D.Double(screenX / length, screenY / length);
	}

	private static Point2D.Double axisEndpoint(Point2D.Double origin, Point2D.Double direction) {
		return new Point2D.Double(origin.x + direction.x * GIZMO_LENGTH, origin.y + direction.y * GIZMO_LENGTH);
	}

	private void paintGizmoHandle(Graphics2D g, Point2D.Double origin, Point2D.Double endpoint, Axis axis) {
		double dx = endpoint.x - origin.x;
		double dy = endpoint.y - origin.y;
		double length = Math.max(1, Math.hypot(dx, dy));
		double unitX = dx / length;
		double unitY = dy / length;
		switch (toolMode) {
		case MOVE -> {
			double sideX = -unitY * 5;
			double sideY = unitX * 5;
			Polygon arrow = new Polygon(
					new int[] { (int) endpoint.x, (int) (endpoint.x - unitX * 12 + sideX),
							(int) (endpoint.x - unitX * 12 - sideX) },
					new int[] { (int) endpoint.y, (int) (endpoint.y - unitY * 12 + sideY),
							(int) (endpoint.y - unitY * 12 - sideY) }, 3);
			g.fillPolygon(arrow);
		}
		case ROTATE -> {
			g.setStroke(new BasicStroke(2.5f));
			g.drawOval((int) endpoint.x - 6, (int) endpoint.y - 6, 12, 12);
		}
		case SCALE -> g.fillRect((int) endpoint.x - 5, (int) endpoint.y - 5, 10, 10);
		}
		g.drawString(axis.name(), (int) (endpoint.x + unitX * 8 - 4), (int) (endpoint.y + unitY * 8 + 4));
	}

	private void paintFace(Graphics2D g, PaintFace paintFace) {
		Face face = paintFace.face;
		Vec3 a = paintFace.a;
		Vec3 b = paintFace.b;
		Vec3 c = paintFace.c;
		Path2D path = new Path2D.Double();
		path.moveTo(a.x, a.y);
		path.lineTo(b.x, b.y);
		path.lineTo(c.x, c.y);
		path.closePath();

		double cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
		double shade = 0.70 + 0.30 * Math.min(1, Math.abs(cross) / 1800.0);
		BufferedImage texture = mesh.textures.get(face.material);
		if (texture != null && face.textureA >= 0 && face.textureB >= 0 && face.textureC >= 0) {
			Vec2 uvA = mesh.textureVertices.get(face.textureA);
			Vec2 uvB = mesh.textureVertices.get(face.textureB);
			Vec2 uvC = mesh.textureVertices.get(face.textureC);
			paintTextureTriangle(g, path, texture, uvA, uvB, uvC, a, b, c);
			if (shade < 0.99) {
				g.setColor(new Color(0, 0, 0, (int) ((1 - shade) * 155)));
				g.fill(path);
			}
		} else {
			Color base = fallbackColor(face.material);
			g.setColor(new Color((int) (base.getRed() * shade), (int) (base.getGreen() * shade),
					(int) (base.getBlue() * shade), 235));
			g.fill(path);
		}
		g.setColor(new Color(10, 8, 12, 80));
		g.draw(path);
	}

	private static void paintTextureTriangle(Graphics2D g, Path2D path, BufferedImage texture, Vec2 uvA, Vec2 uvB,
			Vec2 uvC, Vec3 a, Vec3 b, Vec3 c) {
		double sourceAX = uvA.x * texture.getWidth();
		double sourceAY = (1 - uvA.y) * texture.getHeight();
		double sourceBX = uvB.x * texture.getWidth();
		double sourceBY = (1 - uvB.y) * texture.getHeight();
		double sourceCX = uvC.x * texture.getWidth();
		double sourceCY = (1 - uvC.y) * texture.getHeight();
		double determinant = sourceAX * (sourceBY - sourceCY) + sourceBX * (sourceCY - sourceAY)
				+ sourceCX * (sourceAY - sourceBY);
		if (Math.abs(determinant) < 0.00001)
			return;

		double m00 = (a.x * (sourceBY - sourceCY) + b.x * (sourceCY - sourceAY)
				+ c.x * (sourceAY - sourceBY)) / determinant;
		double m01 = (a.x * (sourceCX - sourceBX) + b.x * (sourceAX - sourceCX)
				+ c.x * (sourceBX - sourceAX)) / determinant;
		double m02 = (a.x * (sourceBX * sourceCY - sourceCX * sourceBY)
				+ b.x * (sourceCX * sourceAY - sourceAX * sourceCY)
				+ c.x * (sourceAX * sourceBY - sourceBX * sourceAY)) / determinant;
		double m10 = (a.y * (sourceBY - sourceCY) + b.y * (sourceCY - sourceAY)
				+ c.y * (sourceAY - sourceBY)) / determinant;
		double m11 = (a.y * (sourceCX - sourceBX) + b.y * (sourceAX - sourceCX)
				+ c.y * (sourceBX - sourceAX)) / determinant;
		double m12 = (a.y * (sourceBX * sourceCY - sourceCX * sourceBY)
				+ b.y * (sourceCX * sourceAY - sourceAX * sourceCY)
				+ c.y * (sourceAX * sourceBY - sourceBX * sourceAY)) / determinant;

		Shape oldClip = g.getClip();
		g.clip(path);
		g.drawImage(texture, new AffineTransform(m00, m10, m01, m11, m02, m12), null);
		g.setClip(oldClip);
	}

	private void installMouseControls() {
		MouseAdapter controls = new MouseAdapter() {
			@Override public void mousePressed(MouseEvent event) {
				requestFocusInWindow();
				dragOrigin = event.getPoint();
				dragButton = event.getButton();
				Transform current = transformReader.get();
				dragTransform = current != null ? current.copy() : null;
				dragCameraYaw = cameraYaw;
				dragCameraPitch = cameraPitch;
				dragCameraPanX = cameraPanX;
				dragCameraPanY = cameraPanY;
				activeAxis = dragButton == MouseEvent.BUTTON1 ? hitTest(event.getPoint()) : Axis.NONE;
				if (dragButton == MouseEvent.BUTTON1 && activeAxis != Axis.NONE)
					firePropertyChange("transformGesture", false, true);
				repaint();
			}

			@Override public void mouseReleased(MouseEvent event) {
				if (dragButton == MouseEvent.BUTTON1 && activeAxis != Axis.NONE)
					firePropertyChange("transformGesture", true, false);
				dragOrigin = null;
				dragTransform = null;
				dragButton = MouseEvent.NOBUTTON;
				activeAxis = Axis.NONE;
				repaint();
			}

			@Override public void mouseDragged(MouseEvent event) {
				if (dragOrigin == null)
					return;
				int deltaX = event.getX() - dragOrigin.x;
				int deltaY = event.getY() - dragOrigin.y;
				if (dragButton == MouseEvent.BUTTON3) {
					cameraYaw = normalizeDegrees(dragCameraYaw + deltaX * 0.6);
					cameraPitch = clamp(dragCameraPitch + deltaY * 0.6, -89, 89);
					repaint();
					return;
				}
				if (dragButton == MouseEvent.BUTTON2) {
					cameraPanX = dragCameraPanX + deltaX;
					cameraPanY = dragCameraPanY + deltaY;
					repaint();
					return;
				}
				if (dragTransform == null || dragButton != MouseEvent.BUTTON1 || activeAxis == Axis.NONE)
					return;
				Transform updated = switch (toolMode) {
					case MOVE -> moveTransform(dragTransform, deltaX, deltaY, activeAxis);
					case ROTATE -> rotateTransform(dragTransform, deltaX, deltaY, activeAxis);
					case SCALE -> scaleTransform(dragTransform, deltaX, deltaY, activeAxis);
				};
				transformWriter.accept(updated);
				repaint();
			}

			@Override public void mouseMoved(MouseEvent event) {
				hoverAxis = hitTest(event.getPoint());
				setCursor(Cursor.getPredefinedCursor(
						hoverAxis == Axis.NONE ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
				repaint();
			}

			@Override public void mouseExited(MouseEvent event) {
				hoverAxis = Axis.NONE;
				setCursor(Cursor.getDefaultCursor());
				repaint();
			}

			@Override public void mouseWheelMoved(MouseWheelEvent event) {
				double factor = Math.pow(1.08, -event.getPreciseWheelRotation());
				if ((event.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
					Transform current = transformReader.get();
					if (current == null)
						return;
					Transform updated = current.copy();
					updated.scaleX = snapScale(clampScale(updated.scaleX * factor));
					updated.scaleY = snapScale(clampScale(updated.scaleY * factor));
					updated.scaleZ = snapScale(clampScale(updated.scaleZ * factor));
					transformWriter.accept(updated);
				} else {
					cameraZoom = clamp(cameraZoom * factor, 0.25, 4);
				}
				repaint();
			}
		};
		addMouseListener(controls);
		addMouseMotionListener(controls);
		addMouseWheelListener(controls);
	}

	private void installKeyboardControls() {
		InputMap inputMap = getInputMap(WHEN_FOCUSED);
		ActionMap actionMap = getActionMap();
		registerToolKey(inputMap, actionMap, KeyEvent.VK_W, "tool.move", ToolMode.MOVE);
		registerToolKey(inputMap, actionMap, KeyEvent.VK_E, "tool.rotate", ToolMode.ROTATE);
		registerToolKey(inputMap, actionMap, KeyEvent.VK_R, "tool.scale", ToolMode.SCALE);
		inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "camera.reset");
		actionMap.put("camera.reset", new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent event) {
				resetCamera();
			}
		});
	}

	private void registerToolKey(InputMap inputMap, ActionMap actionMap, int keyCode, String actionName,
			ToolMode mode) {
		inputMap.put(KeyStroke.getKeyStroke(keyCode, 0), actionName);
		actionMap.put(actionName, new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent event) {
				setToolMode(mode);
			}
		});
	}

	private Transform moveTransform(Transform original, int deltaX, int deltaY, Axis axis) {
		Transform updated = original.copy();
		double unitsPerPixel = 16.0 / Math.max(1, lastModelUnitScale * cameraZoom);
		double axisDelta = projectedDelta(deltaX, deltaY, axis) * unitsPerPixel;
		boolean leftHand = isLeftHand();
		Vec3 direction = displayToView(switch (axis) {
			case X -> new Vec3(leftHand ? -1 : 1, 0, 0);
			case Y -> new Vec3(0, 1, 0);
			case Z -> new Vec3(0, 0, 1);
			default -> new Vec3(0, 0, 0);
		});
		double projectedLengthSquared = direction.x * direction.x + direction.y * direction.y;
		if (axis != Axis.CENTER && axis != Axis.NONE)
			axisDelta = projectedLengthSquared < 0.04 ? 0 :
					(deltaX * direction.x - deltaY * direction.y) / projectedLengthSquared * unitsPerPixel;
		switch (axis) {
		case X -> updated.translationX += axisDelta;
		case Y -> updated.translationY += axisDelta;
		case Z -> updated.translationZ += axisDelta;
		case CENTER, NONE -> {
			Vec3 x = displayToView(new Vec3(leftHand ? -1 : 1, 0, 0));
			Vec3 y = displayToView(new Vec3(0, 1, 0));
			Vec3 z = displayToView(new Vec3(0, 0, 1));
			updated.translationX += (deltaX * x.x - deltaY * x.y) * unitsPerPixel / (x.x*x.x + x.y*x.y + x.z*x.z);
			updated.translationY += (deltaX * y.x - deltaY * y.y) * unitsPerPixel / (y.x*y.x + y.y*y.y + y.z*y.z);
			updated.translationZ += (deltaX * z.x - deltaY * z.y) * unitsPerPixel / (z.x*z.x + z.y*z.y + z.z*z.z);
		}
		}
		updated.translationX = snapPosition(updated.translationX);
		updated.translationY = snapPosition(updated.translationY);
		updated.translationZ = snapPosition(updated.translationZ);
		return updated;
	}

	private Transform rotateTransform(Transform original, int deltaX, int deltaY, Axis axis) {
		Transform updated = original.copy();
		double axisDelta = projectedDelta(deltaX, deltaY, axis) * 0.9;
		switch (axis) {
		case X -> updated.rotationX = normalizeDegrees(original.rotationX + axisDelta);
		case Y -> updated.rotationY = normalizeDegrees(original.rotationY + axisDelta);
		case Z -> updated.rotationZ = normalizeDegrees(original.rotationZ + axisDelta);
		case CENTER, NONE -> {
			updated.rotationY = normalizeDegrees(original.rotationY + deltaX * 0.75);
			updated.rotationX = normalizeDegrees(original.rotationX + deltaY * 0.75);
		}
		}
		updated.rotationX = snapRotation(updated.rotationX);
		updated.rotationY = snapRotation(updated.rotationY);
		updated.rotationZ = snapRotation(updated.rotationZ);
		return updated;
	}

	private Transform scaleTransform(Transform original, int deltaX, int deltaY, Axis axis) {
		Transform updated = original.copy();
		double projected = axis == Axis.CENTER || axis == Axis.NONE ?
				deltaX * 0.35 - deltaY :
				projectedDelta(deltaX, deltaY, axis);
		double factor = Math.pow(1.01, projected);
		switch (axis) {
		case X -> updated.scaleX = snapScale(clampScale(original.scaleX * factor));
		case Y -> updated.scaleY = snapScale(clampScale(original.scaleY * factor));
		case Z -> updated.scaleZ = snapScale(clampScale(original.scaleZ * factor));
		case CENTER, NONE -> {
			updated.scaleX = snapScale(clampScale(original.scaleX * factor));
			updated.scaleY = snapScale(clampScale(original.scaleY * factor));
			updated.scaleZ = snapScale(clampScale(original.scaleZ * factor));
		}
		}
		return updated;
	}

	private double projectedDelta(int deltaX, int deltaY, Axis axis) {
		if (gizmoGeometry == null || axis == Axis.NONE || axis == Axis.CENTER)
			return deltaX - deltaY;
		Point2D.Double origin = gizmoGeometry.origin;
		Point2D.Double endpoint = gizmoGeometry.endpoints.get(axis);
		double axisX = endpoint.x - origin.x;
		double axisY = endpoint.y - origin.y;
		double length = Math.max(1, Math.hypot(axisX, axisY));
		return deltaX * axisX / length + deltaY * axisY / length;
	}

	private Axis hitTest(Point point) {
		if (gizmoGeometry == null)
			return Axis.NONE;
		if (point.distance(gizmoGeometry.origin) <= 12)
			return Axis.CENTER;
		Axis closestAxis = Axis.NONE;
		double closestDistance = 9;
		for (Axis axis : List.of(Axis.X, Axis.Y, Axis.Z)) {
			double distance = Line2D.ptSegDist(gizmoGeometry.origin.x, gizmoGeometry.origin.y,
					gizmoGeometry.endpoints.get(axis).x, gizmoGeometry.endpoints.get(axis).y, point.x, point.y);
			if (distance < closestDistance) {
				closestDistance = distance;
				closestAxis = axis;
			}
		}
		return closestAxis;
	}

	private double snapPosition(double value) {
		return snapEnabled ? snap(value, 0.25) : value;
	}

	private double snapRotation(double value) {
		return snapEnabled ? normalizeDegrees(snap(value, 15)) : value;
	}

	private double snapScale(double value) {
		return snapEnabled ? clampScale(snap(value, 0.05)) : value;
	}

	private static double snap(double value, double step) {
		return Math.round(value / step) * step;
	}

	private static double clampScale(double value) {
		double clamped = clamp(value, -4, 4);
		if (Math.abs(clamped) >= 0.01)
			return clamped;
		return clamped < 0 ? -0.01 : 0.01;
	}

	private static Vec3 rotate(Vec3 vertex, double degreesX, double degreesY, double degreesZ) {
		double x = Math.toRadians(degreesX) * 0.5;
		double y = Math.toRadians(degreesY) * 0.5;
		double z = Math.toRadians(degreesZ) * 0.5;
		double sinX = Math.sin(x);
		double cosX = Math.cos(x);
		double sinY = Math.sin(y);
		double cosY = Math.cos(y);
		double sinZ = Math.sin(z);
		double cosZ = Math.cos(z);

		// Equivalent to JOML Quaternionf.rotationXYZ used by Minecraft's ItemTransform.
		double quaternionW = cosX * cosY * cosZ - sinX * sinY * sinZ;
		double quaternionX = sinX * cosY * cosZ + cosX * sinY * sinZ;
		double quaternionY = cosX * sinY * cosZ - sinX * cosY * sinZ;
		double quaternionZ = cosX * cosY * sinZ + sinX * sinY * cosZ;
		double crossX = 2 * (quaternionY * vertex.z - quaternionZ * vertex.y);
		double crossY = 2 * (quaternionZ * vertex.x - quaternionX * vertex.z);
		double crossZ = 2 * (quaternionX * vertex.y - quaternionY * vertex.x);
		return new Vec3(vertex.x + quaternionW * crossX + quaternionY * crossZ - quaternionZ * crossY,
				vertex.y + quaternionW * crossY + quaternionZ * crossX - quaternionX * crossZ,
				vertex.z + quaternionW * crossZ + quaternionX * crossY - quaternionY * crossX);
	}

	private static double normalizeDegrees(double degrees) {
		double result = degrees % 360;
		return result < -180 ? result + 360 : result > 180 ? result - 360 : result;
	}

	private static double clamp(double value, double minimum, double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static Color fallbackColor(String material) {
		int hash = material == null ? 0 : material.hashCode();
		float hue = (hash & 0xffff) / 65535f;
		return Color.getHSBColor(hue, 0.42f, 0.88f);
	}

	private record Vec2(double x, double y) {
	}

	private record Vec3(double x, double y, double z) {
		private Vec3 subtract(Vec3 other) {
			return new Vec3(x - other.x, y - other.y, z - other.z);
		}

		private Vec3 add(Vec3 other) {
			return new Vec3(x + other.x, y + other.y, z + other.z);
		}
	}

	private record GizmoGeometry(Point2D.Double origin, Map<Axis, Point2D.Double> endpoints) {
	}

	private record Face(int vertexA, int vertexB, int vertexC, int textureA, int textureB, int textureC,
						String material) {
	}

	private record PaintFace(Face face, Vec3 a, Vec3 b, Vec3 c, double depth) {
	}

	private record FacePoint(int vertex, int texture) {
	}

	private static final class Mesh {

		private final List<Vec3> vertices;
		private final List<Vec2> textureVertices;
		private final List<Face> faces;
		private final Map<String, BufferedImage> textures;

		private Mesh(List<Vec3> vertices, List<Vec2> textureVertices, List<Face> faces,
				Map<String, BufferedImage> textures) {
			this.vertices = vertices;
			this.textureVertices = textureVertices;
			this.faces = faces;
			this.textures = textures;
		}

		private static Mesh load(@Nullable Model model) {
			List<Vec3> vertices = new ArrayList<>();
			List<Vec2> textureVertices = new ArrayList<>();
			List<Face> faces = new ArrayList<>();
			if (model == null || model.getType() != Model.Type.OBJ || model.getFile() == null)
				return new Mesh(vertices, textureVertices, faces, Map.of());

			String material = "";
			try (BufferedReader reader = Files.newBufferedReader(model.getFile().toPath(), StandardCharsets.UTF_8)) {
				String line;
				while ((line = reader.readLine()) != null && faces.size() < MAX_FACES) {
					String value = line.trim();
					if (value.startsWith("v "))
						vertices.add(parseVertex(value));
					else if (value.startsWith("vt "))
						textureVertices.add(parseTextureVertex(value));
					else if (value.startsWith("usemtl "))
						material = value.substring(7).trim();
					else if (value.startsWith("f "))
						addFace(value.substring(2).trim(), material, vertices.size(), textureVertices.size(), faces);
				}
			} catch (IOException | NumberFormatException ignored) {
				return new Mesh(List.of(), List.of(), List.of(), Map.of());
			}
			return new Mesh(vertices, textureVertices, faces, loadTextures(model));
		}

		private static Vec3 parseVertex(String line) {
			String[] values = line.substring(2).trim().split("\\s+");
			return new Vec3(Double.parseDouble(values[0]), Double.parseDouble(values[1]),
					Double.parseDouble(values[2]));
		}

		private static Vec2 parseTextureVertex(String line) {
			String[] values = line.substring(3).trim().split("\\s+");
			return new Vec2(Double.parseDouble(values[0]), Double.parseDouble(values[1]));
		}

		private static void addFace(String line, String material, int vertexCount, int textureCount,
				List<Face> faces) {
			String[] tokens = line.split("\\s+");
			if (tokens.length < 3)
				return;
			FacePoint first = parseFacePoint(tokens[0], vertexCount, textureCount);
			for (int i = 1; i < tokens.length - 1; i++) {
				FacePoint second = parseFacePoint(tokens[i], vertexCount, textureCount);
				FacePoint third = parseFacePoint(tokens[i + 1], vertexCount, textureCount);
				if (first.vertex >= 0 && second.vertex >= 0 && third.vertex >= 0)
					faces.add(new Face(first.vertex, second.vertex, third.vertex, first.texture, second.texture,
							third.texture, material));
			}
		}

		private static FacePoint parseFacePoint(String token, int vertexCount, int textureCount) {
			String[] parts = token.split("/", -1);
			int vertex = parseIndex(parts[0], vertexCount);
			int texture = parts.length > 1 && !parts[1].isBlank() ? parseIndex(parts[1], textureCount) : -1;
			return new FacePoint(vertex, texture);
		}

		private static int parseIndex(String value, int size) {
			int index = Integer.parseInt(value);
			return index < 0 ? size + index : index - 1;
		}

		private static Map<String, BufferedImage> loadTextures(Model model) {
			if (!(model instanceof TexturedModel texturedModel) || texturedModel.getTextureMapping() == null)
				return Map.of();
			Map<String, BufferedImage> result = new HashMap<>();
			for (Map.Entry<String, TextureHolder> entry :
					texturedModel.getTextureMapping().getTextureMap().entrySet()) {
				ImageIcon icon = entry.getValue().getImageIcon(TextureType.BLOCK);
				int width = icon.getIconWidth();
				int height = icon.getIconHeight();
				if (width <= 0 || height <= 0)
					continue;
				BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				Graphics2D graphics = image.createGraphics();
				icon.paintIcon(null, graphics, 0, 0);
				graphics.dispose();
				result.put(entry.getKey(), image);
			}
			return result;
		}
	}
}
