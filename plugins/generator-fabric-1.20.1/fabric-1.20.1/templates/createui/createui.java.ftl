<#-- @formatter:off -->
package ${package}.client.gui;

public class ${name}CreateUIScreen extends net.minecraft.client.gui.screens.Screen {
	<#assign remakeUiVersion = "1.20.1">
	<#include "pause_menu.java.ftl">

	private static boolean replacing;
	private long openedAtMillis;
	private long lastLivePreviewModified;
	private LivePayload livePayload;
	private final java.util.Map<String, LiveComponent> liveComponents = new java.util.HashMap<>();
	<#list data.getComponentsOfType("Button") as component>
	private RemarkButton remarkButton${component?index};
	</#list>
	<#if data.hasServerStatus()>
	private final net.minecraft.client.multiplayer.ServerStatusPinger serverStatusPinger = new net.minecraft.client.multiplayer.ServerStatusPinger();
	private net.minecraft.client.multiplayer.ServerData serverStatusData;
	private int serverStatusRefreshCountdown;
	private String serverStatusError = "";
	</#if>
	<#if data.hasMenuMusic()>
	private static net.minecraft.client.resources.sounds.SimpleSoundInstance menuMusicInstance;
	</#if>

	<#list data.getComponentsOfType("Image") as component>
	private static final net.minecraft.resources.ResourceLocation IMAGE_${component?index} = new net.minecraft.resources.ResourceLocation("${modid}:textures/screens/${component.image}");
	</#list>
	<#if data.hasBackgroundImages()>
	<#list data.backgroundImages as background>
	private static final net.minecraft.resources.ResourceLocation BACKGROUND_${background?index} = new net.minecraft.resources.ResourceLocation("${modid}:textures/screens/${background}");
	</#list>
	private static final net.minecraft.resources.ResourceLocation[] BACKGROUNDS = new net.minecraft.resources.ResourceLocation[] {
		<#list data.backgroundImages as background>BACKGROUND_${background?index}<#sep>, </#list>
	};
	private static final int[] BACKGROUND_WIDTHS = new int[] {
		<#list data.backgroundImages as background>${data.getBackgroundImageWidth(background)}<#sep>, </#list>
	};
	private static final int[] BACKGROUND_HEIGHTS = new int[] {
		<#list data.backgroundImages as background>${data.getBackgroundImageHeight(background)}<#sep>, </#list>
	};
	</#if>

	public ${name}CreateUIScreen() {
		super(net.minecraft.network.chat.Component.literal("${data.titleTextJava}"));
	}

	public static void clientLoad() {
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
			<#if data.hasMenuMusic()>
			if (client.level != null && !(client.screen instanceof ${name}CreateUIScreen)) {
				stopMenuMusic();
			}
			</#if>
			if (!${data.replaceVanillaMenu?c} || replacing)
				return;
			if (<#if data.isPauseMenu()>matchesPauseMenu(client.screen)<#else>client.screen instanceof net.minecraft.client.gui.screens.TitleScreen && !(client.screen instanceof ${name}CreateUIScreen)</#if>) {
				replacing = true;
				try {
					var replacement = new ${name}CreateUIScreen();
					<#if data.isPauseMenu()>replacement.originalPauseMenu = client.screen;</#if>
					client.setScreen(replacement);
				} finally { replacing = false; }
			}
		});
	}

	@Override protected void init() {
		openedAtMillis = net.minecraft.Util.getMillis();
		loadLivePreview(true);
		<#if data.hasLayoutComponents()>
		<#list data.getComponentsOfType("Button") as component>
		remarkButton${component?index} = new RemarkButton(layoutX(${component.x}), layoutY(${component.y}),
			layoutSize(${component.width}), layoutSize(${component.height}),
			net.minecraft.network.chat.Component.translatable("gui.${modid}.${registryname}.${component.getName()}"),
			button -> handleButtonAction("${data.getButtonActionType(component.getName())}", "${data.getButtonActionValueJava(component.getName())}",
				"${data.getButtonActionSourceURLJava(component.getName())}"), ${component.isUndecorated?c},
			screenTexture("${data.getButtonTexture(component.getName(), "normal")}"),
			screenTexture("${data.getButtonTexture(component.getName(), "hover")}"),
			screenTexture("${data.getButtonTexture(component.getName(), "pressed")}"),
			screenTexture("${data.getButtonTexture(component.getName(), "disabled")}"),
			${data.getButtonNormalColor(component.getName())}, ${data.getButtonHoverColor(component.getName())},
			${data.getButtonPressedColor(component.getName())}, ${data.getButtonDisabledColor(component.getName())},
			${component.hoverScalePercent} / 100.0F);
		remarkButton${component?index}.active = ${component.enabled?c};
		remarkButton${component?index}.visible = ${component.visible?c};
		this.addRenderableWidget(remarkButton${component?index});
		</#list>
		<#elseif data.isPauseMenu()>
		addPauseMenuButtons();
		<#else>
		int x = this.width / 2 - 100;
		int y = this.height / 4 + 54;
		<#if data.showSingleplayer>
		this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Singleplayer"),
			button -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this))).bounds(x, y, 200, 20).build());
		y += 24;
		</#if>
		<#if data.showMultiplayer>
		this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Multiplayer"),
			button -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this))).bounds(x, y, 200, 20).build());
		y += 24;
		</#if>
		<#if data.showOptions>
		this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Options"),
			button -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.OptionsScreen(this, this.minecraft.options))).bounds(x, y, 200, 20).build());
		y += 24;
		</#if>
		<#if data.showQuit>
		this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Quit Game"),
			button -> { stopMenuMusic(); this.minecraft.stop(); }).bounds(x, y, 200, 20).build());
		</#if>
		</#if>
		<#if data.hasMenuMusic()>
		startMenuMusic();
		</#if>
		<#if data.hasServerStatus()>
		requestServerStatus();
		</#if>
		applyWidgetLayout();
	}

	@Override public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		<#if !data.isPauseMenu() || remakeUiVersion == "1.20.1">
		renderRemarkBackground(guiGraphics);
		</#if>
		applyWidgetLayout();
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		<#if data.hasLayoutComponents()>
		float layoutScale = layoutScale();
		int baseX = Math.round((this.width - ${data.W} * layoutScale) / 2.0F);
		int baseY = Math.round((this.height - ${data.H} * layoutScale) / 2.0F);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(baseX, baseY, 0);
		guiGraphics.pose().scale(layoutScale, layoutScale, 1.0F);
		<#list data.getComponentsOfType("Image") as component>
		LiveComponent liveImage${component?index} = live("${component.getName()}");
		if (liveVisible(liveImage${component?index}, ${component.visible?c})) {
		int imageWidth${component?index} = ${component.getWidth(w.getWorkspace())};
		int imageHeight${component?index} = ${component.getHeight(w.getWorkspace())};
		<#if component.pulseAnimation>
		float imagePulse${component?index} = (float) Math.sin((net.minecraft.Util.getMillis() / 50.0D)
			* Math.PI * 2.0D / Math.max(1, ${component.pulsePeriodTicks})) * Math.max(0, ${component.pulseAmount});
		int imageDrawWidth${component?index} = Math.max(1, Math.round(imageWidth${component?index} + imagePulse${component?index}));
		int imageDrawHeight${component?index} = Math.max(1, Math.round(imageHeight${component?index} + imagePulse${component?index}));
		<#else>
		int imageDrawWidth${component?index} = imageWidth${component?index};
		int imageDrawHeight${component?index} = imageHeight${component?index};
		</#if>
		int imageBaseX${component?index} = liveX(liveImage${component?index}, ${component.x});
		int imageBaseY${component?index} = liveY(liveImage${component?index}, ${component.y});
		float animation${component?index} = animationProgress(liveAnimation(liveImage${component?index}, "${component.animationType}"),
			liveDelay(liveImage${component?index}, ${component.animationDelayTicks}),
			liveDuration(liveImage${component?index}, ${component.animationDurationTicks}),
			liveLoop(liveImage${component?index}, ${component.animationLoop?c}));
		String animationType${component?index} = liveAnimation(liveImage${component?index}, "${component.animationType}");
		int imageX${component?index} = imageBaseX${component?index} + animationOffsetX(animationType${component?index}, animation${component?index})
			+ (imageWidth${component?index} - imageDrawWidth${component?index}) / 2;
		int imageY${component?index} = imageBaseY${component?index} + animationOffsetY(animationType${component?index}, animation${component?index})
			+ (imageHeight${component?index} - imageDrawHeight${component?index}) / 2;
		float imageAnimationScale${component?index} = animationScale(animationType${component?index}, animation${component?index},
			liveDuration(liveImage${component?index}, ${component.animationDurationTicks}));
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(imageBaseX${component?index} + imageWidth${component?index} / 2.0F,
			imageBaseY${component?index} + imageHeight${component?index} / 2.0F, 0);
		guiGraphics.pose().scale(imageAnimationScale${component?index}, imageAnimationScale${component?index}, 1.0F);
		guiGraphics.pose().translate(-(imageBaseX${component?index} + imageWidth${component?index} / 2.0F),
			-(imageBaseY${component?index} + imageHeight${component?index} / 2.0F), 0);
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F,
			"FADE".equals(animationType${component?index}) ? animation${component?index} : 1.0F);
		guiGraphics.blit(IMAGE_${component?index}, imageX${component?index}, imageY${component?index},
			imageDrawWidth${component?index}, imageDrawHeight${component?index}, 0, 0,
			${component.getTextureWidth(w.getWorkspace())}, ${component.getTextureHeight(w.getWorkspace())},
			${component.getTextureWidth(w.getWorkspace())}, ${component.getTextureHeight(w.getWorkspace())});
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		guiGraphics.pose().popPose();
		}
		</#list>
		<#list data.getComponentsOfType("Label") as component>
		LiveComponent liveLabel${component?index} = live("${component.getName()}");
		if (liveVisible(liveLabel${component?index}, ${component.visible?c})) {
		float labelAnimation${component?index} = animationProgress(liveAnimation(liveLabel${component?index}, "${component.animationType}"),
			liveDelay(liveLabel${component?index}, ${component.animationDelayTicks}),
			liveDuration(liveLabel${component?index}, ${component.animationDurationTicks}),
			liveLoop(liveLabel${component?index}, ${component.animationLoop?c}));
		String labelAnimationType${component?index} = liveAnimation(liveLabel${component?index}, "${component.animationType}");
		int labelColor${component?index} = (${component.color.getRGB()} & 0x00FFFFFF) |
			((int) (("FADE".equals(labelAnimationType${component?index}) ? labelAnimation${component?index} : 1.0F) * 255.0F) << 24);
		guiGraphics.drawString(this.font, liveText(liveLabel${component?index},
			net.minecraft.network.chat.Component.translatable("gui.${modid}.${registryname}.${component.getName()}")),
			liveX(liveLabel${component?index}, ${component.x}) + animationOffsetX(labelAnimationType${component?index}, labelAnimation${component?index}),
			liveY(liveLabel${component?index}, ${component.y}) + animationOffsetY(labelAnimationType${component?index}, labelAnimation${component?index}),
			labelColor${component?index}, ${component.hasShadow?c});
		}
		</#list>
		<#if data.hasServerStatus()>
		renderServerStatus(guiGraphics);
		</#if>
		guiGraphics.pose().popPose();
		<#else>
		guiGraphics.drawCenteredString(this.font, net.minecraft.network.chat.Component.literal("${data.titleTextJava}"), this.width / 2, 38, ${data.getTitleColorARGB()});
		<#if data.subtitleText?has_content>
		guiGraphics.drawCenteredString(this.font, net.minecraft.network.chat.Component.literal("${data.subtitleTextJava}"), this.width / 2, 58, ${data.getSubtitleColorARGB()});
		</#if>
		</#if>
	}

	private float layoutScale() {
		boolean enabled = livePayload != null ? livePayload.responsiveLayout : ${data.responsiveLayout?c};
		if (!enabled)
			return 1.0F;
		String mode = livePayload != null && livePayload.responsiveMode != null ? livePayload.responsiveMode : "${data.responsiveMode}";
		float fit = Math.min((this.width - ${data.safeMargin * 2}) / (float) ${data.W},
			(this.height - ${data.safeMargin * 2}) / (float) ${data.H});
		if ("FIT".equals(mode))
			return Math.max(0.1F, fit);
		if ("FIT_DOWN".equals(mode))
			return Math.max(0.1F, Math.min(1.0F, fit));
		return 1.0F;
	}

	private int layoutX(int x) {
		float scale = layoutScale();
		return Math.round((this.width - ${data.W} * scale) / 2.0F + x * scale);
	}

	private int layoutY(int y) {
		float scale = layoutScale();
		return Math.round((this.height - ${data.H} * scale) / 2.0F + y * scale);
	}

	private int layoutSize(int size) {
		return Math.max(1, Math.round(size * layoutScale()));
	}

	private void applyWidgetLayout() {
		<#list data.getComponentsOfType("Button") as component>
		if (remarkButton${component?index} != null) {
			LiveComponent liveButton${component?index} = live("${component.getName()}");
			String animationType${component?index} = liveAnimation(liveButton${component?index}, "${component.animationType}");
			int animationDuration${component?index} = liveDuration(liveButton${component?index}, ${component.animationDurationTicks});
			float animation${component?index} = animationProgress(animationType${component?index},
				liveDelay(liveButton${component?index}, ${component.animationDelayTicks}), animationDuration${component?index},
				liveLoop(liveButton${component?index}, ${component.animationLoop?c}));
			remarkButton${component?index}.setX(layoutX(liveX(liveButton${component?index}, ${component.x})
				+ animationOffsetX(animationType${component?index}, animation${component?index})));
			remarkButton${component?index}.setY(layoutY(liveY(liveButton${component?index}, ${component.y})
				+ animationOffsetY(animationType${component?index}, animation${component?index})));
			if (liveButton${component?index} != null && liveButton${component?index}.text != null)
				remarkButton${component?index}.setMessage(net.minecraft.network.chat.Component.literal(liveButton${component?index}.text));
			int targetWidth${component?index} = liveWidth(liveButton${component?index}, ${component.width});
			int targetHeight${component?index} = liveHeight(liveButton${component?index}, ${component.height});
			if (${component.autoSize?c}) {
				targetWidth${component?index} = autoButtonWidth(remarkButton${component?index}, targetWidth${component?index},
					${component.autoSizePaddingX}, ${component.autoSizeMinWidth}, ${component.autoSizeMaxWidth});
				targetHeight${component?index} = autoButtonHeight(remarkButton${component?index}, targetHeight${component?index},
					${component.autoSizePaddingY}, ${component.autoSizeMinHeight}, ${component.autoSizeMaxHeight});
			}
			remarkButton${component?index}.setRemarkSize(layoutSize(targetWidth${component?index}),
				layoutSize(targetHeight${component?index}));
			remarkButton${component?index}.visible = liveVisible(liveButton${component?index}, ${component.visible?c});
			remarkButton${component?index}.active = liveEnabled(liveButton${component?index}, ${component.enabled?c});
			remarkButton${component?index}.setAlpha("FADE".equals(animationType${component?index}) ? animation${component?index} : 1.0F);
			remarkButton${component?index}.setAnimationScale(animationScale(animationType${component?index},
				animation${component?index}, animationDuration${component?index}));
			remarkButton${component?index}.setLiveStyle(
				liveColor(liveButton${component?index} != null ? liveButton${component?index}.normalTextColor : null, ${data.getButtonNormalColor(component.getName())}),
				liveColor(liveButton${component?index} != null ? liveButton${component?index}.hoverTextColor : null, ${data.getButtonHoverColor(component.getName())}),
				liveColor(liveButton${component?index} != null ? liveButton${component?index}.pressedTextColor : null, ${data.getButtonPressedColor(component.getName())}),
				liveColor(liveButton${component?index} != null ? liveButton${component?index}.disabledTextColor : null, ${data.getButtonDisabledColor(component.getName())}),
				liveButton${component?index} != null && liveButton${component?index}.hoverScalePercent > 0
					? liveButton${component?index}.hoverScalePercent / 100.0F : ${component.hoverScalePercent} / 100.0F,
				screenTexture(liveTexture(liveButton${component?index} != null ? liveButton${component?index}.normalTexture : null, "${data.getButtonTexture(component.getName(), "normal")}")),
				screenTexture(liveTexture(liveButton${component?index} != null ? liveButton${component?index}.hoverTexture : null, "${data.getButtonTexture(component.getName(), "hover")}")),
				screenTexture(liveTexture(liveButton${component?index} != null ? liveButton${component?index}.pressedTexture : null, "${data.getButtonTexture(component.getName(), "pressed")}")),
				screenTexture(liveTexture(liveButton${component?index} != null ? liveButton${component?index}.disabledTexture : null, "${data.getButtonTexture(component.getName(), "disabled")}")));
		}
		</#list>
	}

	private float animationProgress(String type, int delay, int duration, boolean loop) {
		if (type == null || "NONE".equals(type))
			return 1.0F;
		long elapsed = Math.max(0L, net.minecraft.Util.getMillis() - openedAtMillis) / 50L;
		delay = Math.max(0, delay);
		duration = Math.max(1, duration);
		if (loop) {
			long phase = elapsed % (delay + duration);
			return phase < delay ? 0.0F : Math.min(1.0F, (phase - delay) / (float) duration);
		}
		return Math.max(0.0F, Math.min(1.0F, (elapsed - delay) / (float) duration));
	}

	private int animationOffsetX(String type, float progress) {
		return "SLIDE_LEFT".equals(type) ? Math.round((1.0F - progress) * 40.0F) : 0;
	}

	private int animationOffsetY(String type, float progress) {
		return "SLIDE_UP".equals(type) ? Math.round((1.0F - progress) * 30.0F) : 0;
	}

	private float animationScale(String type, float progress, int duration) {
		if ("SCALE".equals(type))
			return 0.7F + 0.3F * progress;
		if ("PULSE".equals(type)) {
			double elapsed = Math.max(0L, net.minecraft.Util.getMillis() - openedAtMillis) / 50.0D;
			return 1.0F + (float) Math.sin(elapsed * Math.PI * 2.0D / Math.max(1, duration)) * 0.04F;
		}
		return 1.0F;
	}

	private void loadLivePreview(boolean force) {
		<#if data.livePreviewEnabled>
		try {
			java.io.File file = new java.io.File("remarkstudio/live/${registryname}.json");
			if (!file.isFile())
				return;
			long modified = file.lastModified();
			if (!force && modified == lastLivePreviewModified)
				return;
			LivePayload loaded = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(file.toPath()), LivePayload.class);
			if (loaded == null || loaded.formatVersion != 1)
				return;
			livePayload = loaded;
			liveComponents.clear();
			if (loaded.components != null)
				for (LiveComponent component : loaded.components)
					if (component != null && component.name != null)
						liveComponents.put(component.name, component);
			lastLivePreviewModified = modified;
		} catch (Exception ignored) {
		}
		</#if>
	}

	private LiveComponent live(String name) { return liveComponents.get(name); }
	private static int liveX(LiveComponent live, int fallback) { return live != null ? live.x : fallback; }
	private static int liveY(LiveComponent live, int fallback) { return live != null ? live.y : fallback; }
	private static int liveWidth(LiveComponent live, int fallback) { return live != null && live.width > 0 ? live.width : fallback; }
	private static int liveHeight(LiveComponent live, int fallback) { return live != null && live.height > 0 ? live.height : fallback; }
	private int autoButtonWidth(RemarkButton button, int fallback, int padding, int minimum, int maximum) {
		int width = 0;
		for (String line : button.getMessage().getString().split("\\R", -1))
			width = Math.max(width, this.font.width(line));
		return clampAutoSize(width + Math.max(0, padding) * 2, minimum, maximum, fallback);
	}
	private int autoButtonHeight(RemarkButton button, int fallback, int padding, int minimum, int maximum) {
		int lines = button.getMessage().getString().split("\\R", -1).length;
		return clampAutoSize(this.font.lineHeight * Math.max(1, lines) + Math.max(0, padding) * 2,
			minimum, maximum, fallback);
	}
	private static int clampAutoSize(int value, int minimum, int maximum, int fallback) {
		int result = Math.max(Math.max(1, minimum), value > 0 ? value : fallback);
		return maximum > 0 ? Math.min(maximum, result) : result;
	}
	private static boolean liveVisible(LiveComponent live, boolean fallback) { return live != null ? live.visible : fallback; }
	private static boolean liveEnabled(LiveComponent live, boolean fallback) { return live != null ? live.enabled : fallback; }
	private static String liveAnimation(LiveComponent live, String fallback) { return live != null && live.animationType != null ? live.animationType : fallback; }
	private static int liveDelay(LiveComponent live, int fallback) { return live != null ? live.animationDelayTicks : fallback; }
	private static int liveDuration(LiveComponent live, int fallback) { return live != null && live.animationDurationTicks > 0 ? live.animationDurationTicks : Math.max(1, fallback); }
	private static boolean liveLoop(LiveComponent live, boolean fallback) { return live != null ? live.animationLoop : fallback; }
	private static net.minecraft.network.chat.Component liveText(LiveComponent live, net.minecraft.network.chat.Component fallback) {
		return live != null && live.text != null ? net.minecraft.network.chat.Component.literal(live.text) : fallback;
	}
	private static String liveTexture(String value, String fallback) { return value != null ? value : fallback; }
	private static int liveColor(String value, int fallback) {
		try {
			String normalized = value == null ? "" : value.trim().replace("#", "").replace("0x", "").replace("0X", "");
			if (normalized.matches("[0-9A-Fa-f]{6}"))
				return 0xFF000000 | Integer.parseInt(normalized, 16);
			if (normalized.matches("[0-9A-Fa-f]{8}"))
				return (int) Long.parseLong(normalized, 16);
		} catch (Exception ignored) {
		}
		return fallback;
	}

	private static final class LivePayload {
		int formatVersion;
		boolean responsiveLayout;
		String responsiveMode;
		String overlayColor;
		int overlayOpacity;
		String backgroundMode;
		int backgroundDurationTicks;
		int backgroundFadeTicks;
		java.util.List<LiveComponent> components;
	}

	private static final class LiveComponent {
		String name;
		String text;
		int x;
		int y;
		int width;
		int height;
		boolean visible;
		boolean enabled;
		String animationType;
		int animationDelayTicks;
		int animationDurationTicks;
		boolean animationLoop;
		String normalTextColor;
		String hoverTextColor;
		String pressedTextColor;
		String disabledTextColor;
		String normalTexture;
		String hoverTexture;
		String pressedTexture;
		String disabledTexture;
		int hoverScalePercent;
	}

	private void handleButtonAction(String action, String value, String sourceUrl) {
		if (this.minecraft == null)
			return;
		<#if data.isPauseMenu()>
		if (handlePauseAction(action)) return;
		if ("SINGLEPLAYER".equals(action) || "MULTIPLAYER".equals(action) || "JOIN_SERVER".equals(action)) return;
		</#if>
		switch (action) {
		case "SINGLEPLAYER" -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this));
		case "MULTIPLAYER" -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this));
		case "OPTIONS" -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.OptionsScreen(this, this.minecraft.options));
		case "QUIT" -> { stopMenuMusic(); this.minecraft.stop(); }
		case "JOIN_SERVER" -> joinServer(value, sourceUrl);
		case "OPEN_URL" -> net.minecraft.Util.getPlatform().openUri(value);
		default -> {
		}
		}
	}

	private void joinServer(String fallbackAddress, String sourceUrl) {
		if (sourceUrl == null || sourceUrl.isBlank()) {
			connectToServer(fallbackAddress);
			return;
		}

		net.minecraft.client.Minecraft client = this.minecraft;
		java.util.concurrent.CompletableFuture.supplyAsync(() -> fetchServerAddress(sourceUrl))
				.whenComplete((address, error) -> client.execute(() -> connectToServer(
						error == null && isServerAddress(address) ? address : fallbackAddress)));
	}

	private static String fetchServerAddress(String sourceUrl) {
		try {
			java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
					.connectTimeout(java.time.Duration.ofSeconds(5)).build();
			java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(sourceUrl))
					.timeout(java.time.Duration.ofSeconds(8)).header("Accept", "application/json, text/plain").GET().build();
			java.net.http.HttpResponse<String> response = client.send(request,
					java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
			return response.statusCode() >= 200 && response.statusCode() < 300 ? decodeServerAddress(response.body()) : "";
		} catch (Exception ignored) {
			return "";
		}
	}

	private static String decodeServerAddress(String body) {
		if (body == null)
			return "";
		String candidate = body.trim();
		try {
			com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(candidate);
			if (json.isJsonPrimitive()) {
				candidate = json.getAsString().trim();
			} else if (json.isJsonObject()) {
				for (String key : new String[] { "ip", "address", "server", "serverAddress" }) {
					com.google.gson.JsonElement value = json.getAsJsonObject().get(key);
					if (value != null && value.isJsonPrimitive()) {
						candidate = value.getAsString().trim();
						break;
					}
				}
			}
		} catch (Exception ignored) {
		}

		try {
			String decoded = new String(java.util.Base64.getDecoder().decode(candidate),
					java.nio.charset.StandardCharsets.UTF_8).trim();
			if (isServerAddress(decoded))
				return decoded;
		} catch (Exception ignored) {
		}
		return isServerAddress(candidate) ? candidate : "";
	}

	private static boolean isServerAddress(String address) {
		return address != null && !address.isBlank() && address.length() <= 253
				&& address.matches("[A-Za-z0-9._\\-\\[\\]:]+");
	}

	private void connectToServer(String address) {
		if (!isServerAddress(address)) {
			this.minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this));
			return;
		}
		stopMenuMusic();
		net.minecraft.client.multiplayer.ServerData serverData = new net.minecraft.client.multiplayer.ServerData(
				"Direct Server", address, false);
		net.minecraft.client.gui.screens.ConnectScreen.startConnecting(this, this.minecraft,
				net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), serverData, false);
	}

	private void renderRemarkBackground(net.minecraft.client.gui.GuiGraphics guiGraphics) {
		<#if data.hasBackgroundImages()>
		String currentBackgroundMode = livePayload != null && livePayload.backgroundMode != null ? livePayload.backgroundMode : "${data.backgroundMode}";
		if (BACKGROUNDS.length == 1 || currentBackgroundMode.equals("STATIC")) {
			drawCoverBackground(guiGraphics, 0, 1.0F);
		} else {
			int duration = Math.max(1, livePayload != null && livePayload.backgroundDurationTicks > 0 ? livePayload.backgroundDurationTicks : ${data.backgroundDurationTicks});
			long tick = net.minecraft.Util.getMillis() / 50L;
			int index = (int) ((tick / duration) % BACKGROUNDS.length);
			drawCoverBackground(guiGraphics, index, 1.0F);
			if (currentBackgroundMode.equals("FADE")) {
				int fade = Math.min(Math.max(0, livePayload != null ? livePayload.backgroundFadeTicks : ${data.backgroundFadeTicks}), duration);
				int phase = (int) (tick % duration);
				if (fade > 0 && phase >= duration - fade) {
					float alpha = (phase - (duration - fade)) / (float) fade;
					drawCoverBackground(guiGraphics, (index + 1) % BACKGROUNDS.length, alpha);
				}
			}
		}
		<#elseif !data.isPauseMenu()>
		guiGraphics.fillGradient(0, 0, this.width, this.height, 0xFF120814, 0xFF040405);
		guiGraphics.fillGradient(0, 0, this.width, Math.max(36, this.height / 6), ${data.getAccentColorARGB()}, 0x00120814);
		</#if>
		applyBackgroundOverlay(guiGraphics);
	}

	<#if data.hasBackgroundImages()>
	private void drawCoverBackground(net.minecraft.client.gui.GuiGraphics guiGraphics, int index, float alpha) {
		if (alpha <= 0.0F)
			return;
		int textureWidth = Math.max(1, BACKGROUND_WIDTHS[index]);
		int textureHeight = Math.max(1, BACKGROUND_HEIGHTS[index]);
		float scale = Math.max(this.width / (float) textureWidth, this.height / (float) textureHeight);
		<#if data.backgroundSlowZoom>
		int zoomDuration = Math.max(1, ${data.backgroundZoomDurationTicks});
		double zoomPhase = ((net.minecraft.Util.getMillis() / 50.0D) % zoomDuration) / zoomDuration;
		scale *= (float) (1.0D + (${data.backgroundZoomPercent} / 100.0D)
				* (0.5D - 0.5D * Math.cos(zoomPhase * Math.PI * 2.0D)));
		</#if>
		int drawWidth = (int) Math.ceil(textureWidth * scale);
		int drawHeight = (int) Math.ceil(textureHeight * scale);
		int x = (this.width - drawWidth) / 2;
		int y = (this.height - drawHeight) / 2;

		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(x, y, 0);
		guiGraphics.pose().scale(scale, scale, 1.0F);
		guiGraphics.blit(BACKGROUNDS[index], 0, 0, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
		guiGraphics.pose().popPose();
		com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}
	</#if>

	private void applyBackgroundOverlay(net.minecraft.client.gui.GuiGraphics guiGraphics) {
		int opacity = Math.max(0, Math.min(100, livePayload != null ? livePayload.overlayOpacity : ${data.backgroundOverlayOpacity}));
		if (opacity <= 0)
			return;
		int rgb = parseRGB(livePayload != null ? livePayload.overlayColor : "${data.backgroundOverlayColor}", ${data.getBackgroundOverlayRGB()});
		int alpha = Math.round(opacity * 255.0F / 100.0F);
		<#if data.backgroundOverlayGradient>
		guiGraphics.fillGradient(0, 0, this.width, this.height, rgb, (alpha << 24) | rgb);
		<#else>
		guiGraphics.fill(0, 0, this.width, this.height, (alpha << 24) | rgb);
		</#if>
	}

	private static int parseRGB(String value, int fallback) {
		try {
			String normalized = value == null ? "" : value.trim().replace("#", "");
			return normalized.matches("[0-9A-Fa-f]{6}") ? Integer.parseInt(normalized, 16) : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	<#if data.hasServerStatus()>
	private void requestServerStatus() {
		serverStatusPinger.removeAll();
		serverStatusError = "";
		serverStatusData = new net.minecraft.client.multiplayer.ServerData("Remake Server", "${data.getServerStatusAddressJava()}", false);
		serverStatusData.ping = -2L;
		try {
			serverStatusPinger.pingServer(serverStatusData, () -> {});
		} catch (Exception e) {
			serverStatusError = "Offline";
		}
		serverStatusRefreshCountdown = Math.max(20, ${data.serverStatusRefreshTicks});
	}

	private void renderServerStatus(net.minecraft.client.gui.GuiGraphics guiGraphics) {
		int x = ${data.serverStatusX};
		int y = ${data.serverStatusY};
		int color = 0xFFDDDDDD;
		String status;
		if (!serverStatusError.isBlank()) {
			status = serverStatusError;
			color = 0xFFFF6666;
		} else if (serverStatusData == null || serverStatusData.ping < 0L) {
			status = "Connecting...";
			color = 0xFFFFD76A;
		} else {
			status = "Online";
			color = 0xFF5CDC78;
			<#if data.serverStatusShowPlayers>
			if (serverStatusData.players != null)
				status += "  " + serverStatusData.players.online() + "/" + serverStatusData.players.max();
			</#if>
			<#if data.serverStatusShowPing>
			status += "  " + serverStatusData.ping + "ms";
			</#if>
		}
		guiGraphics.fill(x, y + 2, x + 5, y + 7, color);
		guiGraphics.drawString(this.font, status, x + 9, y, 0xFFFFFFFF, true);
		<#if data.serverStatusShowMOTD>
		if (serverStatusData != null && serverStatusData.motd != null)
			guiGraphics.drawString(this.font, serverStatusData.motd, x + 9, y + 11, 0xFFCCCCCC, false);
		</#if>
	}
	</#if>

	private static net.minecraft.resources.ResourceLocation screenTexture(String path) {
		return path == null || path.isBlank() ? null : net.minecraft.resources.ResourceLocation.tryParse("${modid}:textures/screens/" + path);
	}

	private static final class RemarkButton extends net.minecraft.client.gui.components.Button {
		private final boolean textOnly;
		private net.minecraft.resources.ResourceLocation normalTexture;
		private net.minecraft.resources.ResourceLocation hoverTexture;
		private net.minecraft.resources.ResourceLocation pressedTexture;
		private net.minecraft.resources.ResourceLocation disabledTexture;
		private int normalColor;
		private int hoverColor;
		private int pressedColor;
		private int disabledColor;
		private float hoverScale;
		private boolean pressed;
		private float animationScale = 1.0F;

		private RemarkButton(int x, int y, int width, int height, net.minecraft.network.chat.Component message,
				net.minecraft.client.gui.components.Button.OnPress onPress, boolean textOnly,
				net.minecraft.resources.ResourceLocation normalTexture, net.minecraft.resources.ResourceLocation hoverTexture,
				net.minecraft.resources.ResourceLocation pressedTexture, net.minecraft.resources.ResourceLocation disabledTexture,
				int normalColor, int hoverColor, int pressedColor, int disabledColor, float hoverScale) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
			this.textOnly = textOnly;
			this.normalTexture = normalTexture;
			this.hoverTexture = hoverTexture;
			this.pressedTexture = pressedTexture;
			this.disabledTexture = disabledTexture;
			this.normalColor = normalColor;
			this.hoverColor = hoverColor;
			this.pressedColor = pressedColor;
			this.disabledColor = disabledColor;
			this.hoverScale = Math.max(0.5F, hoverScale);
		}

		private void setAnimationScale(float scale) { this.animationScale = Math.max(0.1F, scale); }
		private void setRemarkSize(int width, int height) {
			this.width = Math.max(1, width);
			this.height = Math.max(1, height);
		}

		private void setLiveStyle(int normalColor, int hoverColor, int pressedColor, int disabledColor,
				float hoverScale, net.minecraft.resources.ResourceLocation normalTexture,
				net.minecraft.resources.ResourceLocation hoverTexture,
				net.minecraft.resources.ResourceLocation pressedTexture,
				net.minecraft.resources.ResourceLocation disabledTexture) {
			this.normalColor = normalColor;
			this.hoverColor = hoverColor;
			this.pressedColor = pressedColor;
			this.disabledColor = disabledColor;
			this.hoverScale = Math.max(0.5F, hoverScale);
			this.normalTexture = normalTexture;
			this.hoverTexture = hoverTexture;
			this.pressedTexture = pressedTexture;
			this.disabledTexture = disabledTexture;
		}

		@Override protected void renderWidget(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
			boolean highlighted = this.isHoveredOrFocused();
			float scale = animationScale * (highlighted && this.active ? hoverScale : 1.0F);
			guiGraphics.pose().pushPose();
			guiGraphics.pose().translate(this.getX() + this.getWidth() / 2.0F, this.getY() + this.getHeight() / 2.0F, 0);
			guiGraphics.pose().scale(scale, scale, 1.0F);
			guiGraphics.pose().translate(-(this.getX() + this.getWidth() / 2.0F), -(this.getY() + this.getHeight() / 2.0F), 0);
			net.minecraft.resources.ResourceLocation texture = !this.active ? disabledTexture : pressed ? pressedTexture : highlighted ? hoverTexture : normalTexture;
			if (texture == null)
				texture = normalTexture;
			guiGraphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			if (texture != null)
				guiGraphics.blit(texture, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0, 0,
					this.getWidth(), this.getHeight(), this.getWidth(), this.getHeight());
			else if (!textOnly) {
				int state = !this.active ? 0 : highlighted ? 2 : 1;
				guiGraphics.blitNineSliced(WIDGETS_LOCATION, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 20, 4, 200, 20, 0, 46 + state * 20);
			}
			guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
			int color = !this.active ? disabledColor : pressed ? pressedColor : highlighted ? hoverColor : normalColor;
			color = (color & 0x00FFFFFF) | (net.minecraft.util.Mth.ceil(this.alpha * 255.0F) << 24);
			net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
			String[] lines = this.getMessage().getString().split("\\R", -1);
			int textY = this.getY() + (this.getHeight() - font.lineHeight * lines.length) / 2;
			for (String line : lines) {
				guiGraphics.drawCenteredString(font, net.minecraft.network.chat.Component.literal(line),
					this.getX() + this.getWidth() / 2, textY, color);
				textY += font.lineHeight;
			}
			guiGraphics.pose().popPose();
		}

		@Override public void onClick(double mouseX, double mouseY) { pressed = true; super.onClick(mouseX, mouseY); }
		@Override public void onRelease(double mouseX, double mouseY) { pressed = false; super.onRelease(mouseX, mouseY); }
	}

	<#if data.hasMenuMusic()>
	private void startMenuMusic() {
		if (this.minecraft == null)
			return;
		if (menuMusicInstance != null && this.minecraft.getSoundManager().isActive(menuMusicInstance))
			return;
		this.minecraft.getMusicManager().stopPlaying();
		menuMusicInstance = new net.minecraft.client.resources.sounds.SimpleSoundInstance(
				new net.minecraft.resources.ResourceLocation("${data.getMenuMusicJava()}"),
				net.minecraft.sounds.SoundSource.MUSIC, ${data.getMenuMusicVolumeFactor()}, 1.0F,
				net.minecraft.util.RandomSource.create(), ${data.loopMenuMusic?c}, 0,
				net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true);
		this.minecraft.getSoundManager().play(menuMusicInstance);
	}

	</#if>

	@Override public void tick() {
		super.tick();
		<#if data.hasMenuMusic()>
		if (this.minecraft != null)
			this.minecraft.getMusicManager().stopPlaying();
		</#if>
		loadLivePreview(false);
		applyWidgetLayout();
		<#if data.hasServerStatus()>
		serverStatusPinger.tick();
		if (--serverStatusRefreshCountdown <= 0)
			requestServerStatus();
		</#if>
	}

	@Override public void removed() {
		<#if data.isPauseMenu()>stopMenuMusic();</#if>
		<#if data.hasServerStatus()>
		serverStatusPinger.removeAll();
		</#if>
		super.removed();
	}

	private static void stopMenuMusic() {
		<#if data.hasMenuMusic()>
		if (menuMusicInstance != null) {
			net.minecraft.client.Minecraft.getInstance().getSoundManager().stop(menuMusicInstance);
			menuMusicInstance = null;
		}
		</#if>
	}

	@Override public boolean isPauseScreen() {
		return ${data.isPauseMenu()?c};
	}
}
<#-- @formatter:on -->
