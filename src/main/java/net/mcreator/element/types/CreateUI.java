/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.element.types;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.parts.Sound;
import net.mcreator.element.parts.GridSettings;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.IGUI;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.ui.wysiwyg.WYSIWYG;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.references.ModElementReference;
import net.mcreator.workspace.references.TextureReference;
import net.mcreator.util.FilenameUtilsPatched;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused") public class CreateUI extends GeneratableElement implements IGUI {
	public static final int CURRENT_REMARK_SCHEMA = 2;

	public static final String ACTION_NONE = "NONE";
	public static final String ACTION_SINGLEPLAYER = "SINGLEPLAYER";
	public static final String ACTION_MULTIPLAYER = "MULTIPLAYER";
	public static final String ACTION_OPTIONS = "OPTIONS";
	public static final String ACTION_QUIT = "QUIT";
	public static final String ACTION_JOIN_SERVER = "JOIN_SERVER";
	public static final String ACTION_OPEN_URL = "OPEN_URL";
	public static final String ACTION_RESUME_GAME = "RESUME_GAME";
	public static final String ACTION_ADVANCEMENTS = "ADVANCEMENTS";
	public static final String ACTION_STATISTICS = "STATISTICS";
	public static final String ACTION_DISCONNECT = "DISCONNECT";
	public static final String ACTION_VANILLA_PAUSE_MENU = "VANILLA_PAUSE_MENU";
	public static final String TARGET_MAIN_MENU = "Main menu";
	public static final String TARGET_PAUSE_MENU = "Pause menu";

	public static final String BACKGROUND_STATIC = "STATIC";
	public static final String BACKGROUND_CUT = "CUT";
	public static final String BACKGROUND_FADE = "FADE";
	public static final String RESPONSIVE_CENTER = "CENTER";
	public static final String RESPONSIVE_FIT = "FIT";
	public static final String RESPONSIVE_FIT_DOWN = "FIT_DOWN";

	public int remarkSchemaVersion = CURRENT_REMARK_SCHEMA;

	@LimitedOptions({ TARGET_MAIN_MENU, TARGET_PAUSE_MENU }) public String targetScreen = TARGET_MAIN_MENU;

	public boolean replaceVanillaMenu = true;
	public boolean responsiveLayout = true;
	public String responsiveMode = RESPONSIVE_FIT_DOWN;
	public int safeMargin = 8;

	public String titleText = "Remake Studios";
	public String subtitleText = "Custom Minecraft menu";

	public String titleColor = "FFD76A";
	public String subtitleColor = "FFFFFF";
	public String accentColor = "E91E79";

	public boolean showSingleplayer = true;
	public boolean showMultiplayer = true;
	public boolean showOptions = true;
	public boolean showQuit = true;

	@TextureReference(TextureType.SCREEN) public List<String> backgroundImages;
	public String backgroundMode = BACKGROUND_FADE;
	public int backgroundDurationTicks = 120;
	public int backgroundFadeTicks = 20;
	public boolean backgroundSlowZoom;
	public int backgroundZoomPercent = 5;
	public int backgroundZoomDurationTicks = 400;
	public String backgroundOverlayColor = "000000";
	public int backgroundOverlayOpacity = 60;
	public boolean backgroundOverlayGradient = true;

	public Sound menuMusic;
	public boolean loopMenuMusic = true;
	public int menuMusicVolume = 100;

	public boolean serverStatusEnabled;
	public String serverStatusAddress = "";
	public int serverStatusRefreshTicks = 200;
	public int serverStatusX = 12;
	public int serverStatusY = 216;
	public boolean serverStatusShowMOTD = true;
	public boolean serverStatusShowPlayers = true;
	public boolean serverStatusShowPing = true;
	public boolean livePreviewEnabled = true;

	@ModElementReference @TextureReference(TextureType.SCREEN) public List<GUIComponent> components;
	public List<CreateUIButtonAction> buttonActions;
	public GridSettings gridSettings;

	public final transient int W;
	public final transient int H;

	private CreateUI() {
		this(null);
	}

	public CreateUI(ModElement element) {
		super(element);
		this.W = WYSIWYG.W;
		this.H = WYSIWYG.H;
		this.backgroundImages = new ArrayList<>();
		this.components = new ArrayList<>();
		this.buttonActions = new ArrayList<>();
		this.gridSettings = new GridSettings();
	}

	public void normalizeRemarkSchema() {
		backgroundImages = backgroundImages == null ? new ArrayList<>() : new ArrayList<>(backgroundImages);
		components = components == null ? new ArrayList<>() : new ArrayList<>(components);
		buttonActions = buttonActions == null ? new ArrayList<>() : new ArrayList<>(buttonActions);
		buttonActions.removeIf(action -> action == null || action.buttonName == null || action.buttonName.isBlank());
		for (CreateUIButtonAction action : buttonActions) {
			action.actionType = action.actionType == null ? ACTION_NONE : action.actionType;
			action.value = action.value == null ? "" : action.value;
			action.sourceUrl = action.sourceUrl == null ? "" : action.sourceUrl;
		}
		gridSettings = gridSettings == null ? new GridSettings() : gridSettings;
		targetScreen = TARGET_PAUSE_MENU.equals(targetScreen) ? TARGET_PAUSE_MENU : TARGET_MAIN_MENU;
		responsiveMode = List.of(RESPONSIVE_CENTER, RESPONSIVE_FIT, RESPONSIVE_FIT_DOWN).contains(responsiveMode) ?
				responsiveMode : RESPONSIVE_FIT_DOWN;
		backgroundMode = List.of(BACKGROUND_STATIC, BACKGROUND_CUT, BACKGROUND_FADE).contains(backgroundMode) ?
				backgroundMode : BACKGROUND_FADE;
		titleText = titleText == null ? "Remake Studios" : titleText;
		subtitleText = subtitleText == null ? "" : subtitleText;
		titleColor = titleColor == null ? "FFD76A" : titleColor;
		subtitleColor = subtitleColor == null ? "FFFFFF" : subtitleColor;
		accentColor = accentColor == null ? "E91E79" : accentColor;
		backgroundOverlayColor = backgroundOverlayColor == null ? "000000" : backgroundOverlayColor;
		serverStatusAddress = serverStatusAddress == null ? "" : serverStatusAddress;
		safeMargin = Math.max(0, safeMargin);
		backgroundDurationTicks = Math.max(1, backgroundDurationTicks);
		backgroundFadeTicks = Math.max(0, backgroundFadeTicks);
		backgroundZoomDurationTicks = Math.max(1, backgroundZoomDurationTicks);
		serverStatusRefreshTicks = Math.max(20, serverStatusRefreshTicks);
		remarkSchemaVersion = CURRENT_REMARK_SCHEMA;
	}

	public String getTitleColorARGB() {
		return toARGB(titleColor, "FFD76A");
	}

	public boolean isPauseMenu() {
		return TARGET_PAUSE_MENU.equals(targetScreen);
	}

	public String getSubtitleColorARGB() {
		return toARGB(subtitleColor, "FFFFFF");
	}

	public String getAccentColorARGB() {
		return toARGB(accentColor, "E91E79");
	}

	public String getTitleTextJava() {
		return escapeJava(titleText);
	}

	public String getSubtitleTextJava() {
		return escapeJava(subtitleText);
	}

	@Override public List<GUIComponent> getComponents() {
		return components;
	}

	public CreateUIButtonAction getActionForButton(String buttonName) {
		if (buttonActions == null)
			return new CreateUIButtonAction(buttonName, ACTION_NONE, "");
		CreateUIButtonAction action = buttonActions.stream().filter(entry -> buttonName.equals(entry.buttonName)).findFirst()
				.orElseGet(() -> new CreateUIButtonAction(buttonName, ACTION_NONE, ""));
		if (action.actionType == null)
			action.actionType = ACTION_NONE;
		if (action.value == null)
			action.value = "";
		if (action.sourceUrl == null)
			action.sourceUrl = "";
		return action;
	}

	public String getButtonActionType(String buttonName) {
		return getActionForButton(buttonName).actionType;
	}

	public String getButtonActionValueJava(String buttonName) {
		return escapeJava(getActionForButton(buttonName).value);
	}

	public String getButtonActionSourceURLJava(String buttonName) {
		return escapeJava(getActionForButton(buttonName).sourceUrl);
	}

	public String getServerStatusAddressJava() {
		return escapeJava(serverStatusAddress);
	}

	public boolean hasServerStatus() {
		return serverStatusEnabled && serverStatusAddress != null && !serverStatusAddress.isBlank();
	}

	public String getButtonNormalColor(String buttonName) {
		return buttonColor(buttonName, "normal");
	}

	public String getButtonHoverColor(String buttonName) {
		return buttonColor(buttonName, "hover");
	}

	public String getButtonPressedColor(String buttonName) {
		return buttonColor(buttonName, "pressed");
	}

	public String getButtonDisabledColor(String buttonName) {
		return buttonColor(buttonName, "disabled");
	}

	private String buttonColor(String buttonName, String state) {
		Button button = components == null ? null : components.stream().filter(Button.class::isInstance)
				.map(Button.class::cast).filter(component -> buttonName.equals(component.getName())).findFirst().orElse(null);
		String value = button == null ? "FFFFFF" : switch (state) {
			case "hover" -> button.hoverTextColor;
			case "pressed" -> button.pressedTextColor;
			case "disabled" -> button.disabledTextColor;
			default -> button.normalTextColor;
		};
		return "0xFF" + normalizeRGB(value, "FFFFFF");
	}

	public String getButtonTexture(String buttonName, String state) {
		Button button = components == null ? null : components.stream().filter(Button.class::isInstance)
				.map(Button.class::cast).filter(component -> buttonName.equals(component.getName())).findFirst().orElse(null);
		if (button == null)
			return "";
		String value = switch (state) {
			case "hover" -> button.hoverTexture;
			case "pressed" -> button.pressedTexture;
			case "disabled" -> button.disabledTexture;
			default -> button.normalTexture;
		};
		return value == null ? "" : value;
	}

	public boolean hasLayoutComponents() {
		return components != null && !components.isEmpty();
	}

	public boolean hasBackgroundImages() {
		return backgroundImages != null && !backgroundImages.isEmpty();
	}

	public boolean hasMenuMusic() {
		return menuMusic != null && !menuMusic.isEmpty();
	}

	public String getMenuMusicJava() {
		return hasMenuMusic() ? escapeJava(menuMusic.toString()) : "";
	}

	public String getMenuMusicVolumeFactor() {
		return String.format(Locale.ROOT, "%.2fF", Math.max(0, Math.min(100, menuMusicVolume)) / 100.0F);
	}

	public String getBackgroundOverlayARGB() {
		String normalized = normalizeRGB(backgroundOverlayColor, "000000");
		int rgb = Integer.parseInt(normalized, 16);
		int alpha = Math.round(Math.max(0, Math.min(100, backgroundOverlayOpacity)) * 255.0F / 100.0F);
		return String.format(Locale.ROOT, "0x%08X", (alpha << 24) | rgb);
	}

	public String getBackgroundOverlayTransparentARGB() {
		return "0x00" + normalizeRGB(backgroundOverlayColor, "000000");
	}

	public String getBackgroundOverlayRGB() {
		return "0x00" + normalizeRGB(backgroundOverlayColor, "000000");
	}

	public int getBackgroundImageWidth(String image) {
		return getBackgroundImageSize(image, true);
	}

	public int getBackgroundImageHeight(String image) {
		return getBackgroundImageSize(image, false);
	}

	private int getBackgroundImageSize(String image, boolean width) {
		try {
			File texture = getModElement().getFolderManager()
					.getTextureFile(FilenameUtilsPatched.removeExtension(image), TextureType.SCREEN);
			var bufferedImage = ImageIO.read(texture);
			if (bufferedImage != null)
				return width ? bufferedImage.getWidth() : bufferedImage.getHeight();
		} catch (Exception ignored) {
		}
		return width ? W : H;
	}

	private static String toARGB(String color, String fallback) {
		String normalized = color == null ? "" : color.trim().toUpperCase(Locale.ROOT);
		if (normalized.startsWith("#"))
			normalized = normalized.substring(1);
		if (normalized.startsWith("0X"))
			normalized = normalized.substring(2);
		if (!normalized.matches("[0-9A-F]{6}([0-9A-F]{2})?"))
			normalized = fallback;
		if (normalized.length() == 6)
			return "0xFF" + normalized;
		return "0x" + normalized;
	}

	private static String normalizeRGB(String color, String fallback) {
		String normalized = color == null ? "" : color.trim().toUpperCase(Locale.ROOT);
		if (normalized.startsWith("#"))
			normalized = normalized.substring(1);
		if (normalized.startsWith("0X"))
			normalized = normalized.substring(2);
		if (!normalized.matches("[0-9A-F]{6}"))
			normalized = fallback;
		return normalized;
	}

	private static String escapeJava(String text) {
		if (text == null)
			return "";
		return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	public static class CreateUIButtonAction {
		public String buttonName;
		public String actionType;
		public String value;
		public String sourceUrl;

		private CreateUIButtonAction() {
			this("", ACTION_NONE, "", "");
		}

		public CreateUIButtonAction(String buttonName, String actionType, String value) {
			this(buttonName, actionType, value, "");
		}

		public CreateUIButtonAction(String buttonName, String actionType, String value, String sourceUrl) {
			this.buttonName = buttonName;
			this.actionType = actionType;
			this.value = value;
			this.sourceUrl = sourceUrl;
		}
	}

}
