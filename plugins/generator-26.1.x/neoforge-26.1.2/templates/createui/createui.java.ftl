<#-- @formatter:off -->
package ${package}.client.gui;

@net.neoforged.fml.common.EventBusSubscriber(net.neoforged.api.distmarker.Dist.CLIENT)
public class ${name}CreateUIScreen extends net.minecraft.client.gui.screens.Screen {
	<#assign remakeUiVersion = "26.1.2">
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
	private static final Identifier IMAGE_${component?index} = Identifier.parse("${modid}:textures/screens/${component.image}");
	</#list>
	<#if data.hasBackgroundImages()>
	<#list data.backgroundImages as background>
	private static final Identifier BACKGROUND_${background?index} = Identifier.parse("${modid}:textures/screens/${background}");
	</#list>
	private static final Identifier[] BACKGROUNDS = new Identifier[] {
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
		super(Component.literal("${data.titleTextJava}"));
	}

	@net.neoforged.bus.api.SubscribeEvent
	public static void onScreenOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
		if (!${data.replaceVanillaMenu?c} || replacing)
			return;

		net.minecraft.client.gui.screens.Screen next = event.getNewScreen();
		if (<#if data.isPauseMenu()>matchesPauseMenu(next)<#else>next instanceof net.minecraft.client.gui.screens.TitleScreen && !(next instanceof ${name}CreateUIScreen)</#if>) {
			replacing = true;
			try {
				var replacement = new ${name}CreateUIScreen();
				<#if data.isPauseMenu()>replacement.originalPauseMenu = next;</#if>
				event.setNewScreen(replacement);
			} finally { replacing = false; }
		}
	}

	<#if data.hasMenuMusic()>
	@net.neoforged.bus.api.SubscribeEvent
	public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
		if (net.minecraft.client.Minecraft.getInstance().level != null
				&& !(net.minecraft.client.Minecraft.getInstance().screen instanceof ${name}CreateUIScreen))
			stopMenuMusic();
	}
	</#if>

	@Override protected void init() {
		openedAtMillis = net.minecraft.util.Util.getMillis();
		loadLivePreview(true);
		<#if data.hasLayoutComponents()>
		<#list data.getComponentsOfType("Button") as component>
		remarkButton${component?index} = new RemarkButton(layoutX(${component.x}), layoutY(${component.y}),
			layoutSize(${component.width}), layoutSize(${component.height}),
			Component.translatable("gui.${modid}.${registryname}.${component.getName()}"),
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
		this.addRenderableWidget(Button.builder(Component.literal("Singleplayer"),
			button -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this))).bounds(x, y, 200, 20).build());
		y += 24;
		</#if>
		<#if data.showMultiplayer>
		this.addRenderableWidget(Button.builder(Component.literal("Multiplayer"),
			button -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this))).bounds(x, y, 200, 20).build());
		y += 24;
		</#if>
		<#if data.showOptions>
		this.addRenderableWidget(Button.builder(Component.literal("Options"),
			button -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(this, this.minecraft.options, ${data.isPauseMenu()?c}))).bounds(x, y, 200, 20).build());
		y += 24;
		</#if>
		<#if data.showQuit>
		this.addRenderableWidget(Button.builder(Component.literal("Quit Game"),
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

	@Override public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		<#if !data.isPauseMenu() || remakeUiVersion == "1.20.1">
		renderRemarkBackground(guiGraphics);
		</#if>
		applyWidgetLayout();
		super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
		<#if data.hasLayoutComponents()>
		float layoutScale = layoutScale();
		<#list data.getComponentsOfType("Image") as component>
		LiveComponent liveImage${component?index} = live("${component.getName()}");
		if (liveVisible(liveImage${component?index}, ${component.visible?c})) {
		int imageWidth${component?index} = ${component.getWidth(w.getWorkspace())};
		int imageHeight${component?index} = ${component.getHeight(w.getWorkspace())};
		<#if component.pulseAnimation>
		float imagePulse${component?index} = (float) Math.sin((net.minecraft.util.Util.getMillis() / 50.0D)
			* Math.PI * 2.0D / Math.max(1, ${component.pulsePeriodTicks})) * Math.max(0, ${component.pulseAmount});
		int imageDrawWidth${component?index} = Math.max(1, Math.round(imageWidth${component?index} + imagePulse${component?index}));
		int imageDrawHeight${component?index} = Math.max(1, Math.round(imageHeight${component?index} + imagePulse${component?index}));
		<#else>
		int imageDrawWidth${component?index} = imageWidth${component?index};
		int imageDrawHeight${component?index} = imageHeight${component?index};
		</#if>
		String imageAnimationType${component?index} = liveAnimation(liveImage${component?index}, "${component.animationType}");
		int imageAnimationDuration${component?index} = liveDuration(liveImage${component?index}, ${component.animationDurationTicks});
		float imageAnimation${component?index} = animationProgress(imageAnimationType${component?index},
			liveDelay(liveImage${component?index}, ${component.animationDelayTicks}), imageAnimationDuration${component?index},
			liveLoop(liveImage${component?index}, ${component.animationLoop?c}));
		float imageScale${component?index} = layoutScale * animationScale(imageAnimationType${component?index},
			imageAnimation${component?index}, imageAnimationDuration${component?index});
		int imageScaledWidth${component?index} = Math.max(1, Math.round(imageDrawWidth${component?index} * imageScale${component?index}));
		int imageScaledHeight${component?index} = Math.max(1, Math.round(imageDrawHeight${component?index} * imageScale${component?index}));
		int imageX${component?index} = layoutX(liveX(liveImage${component?index}, ${component.x})
			+ animationOffsetX(imageAnimationType${component?index}, imageAnimation${component?index}))
			+ (Math.round(imageWidth${component?index} * layoutScale) - imageScaledWidth${component?index}) / 2;
		int imageY${component?index} = layoutY(liveY(liveImage${component?index}, ${component.y})
			+ animationOffsetY(imageAnimationType${component?index}, imageAnimation${component?index}))
			+ (Math.round(imageHeight${component?index} * layoutScale) - imageScaledHeight${component?index}) / 2;
		int imageTint${component?index} = ((int) (("FADE".equals(imageAnimationType${component?index})
			? imageAnimation${component?index} : 1.0F) * 255.0F) << 24) | 0x00FFFFFF;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, IMAGE_${component?index}, imageX${component?index}, imageY${component?index},
			0, 0, imageScaledWidth${component?index}, imageScaledHeight${component?index},
			${component.getTextureWidth(w.getWorkspace())}, ${component.getTextureHeight(w.getWorkspace())},
			${component.getTextureWidth(w.getWorkspace())}, ${component.getTextureHeight(w.getWorkspace())}, imageTint${component?index});
		}
		</#list>
		<#list data.getComponentsOfType("Label") as component>
		LiveComponent liveLabel${component?index} = live("${component.getName()}");
		if (liveVisible(liveLabel${component?index}, ${component.visible?c})) {
		String labelAnimationType${component?index} = liveAnimation(liveLabel${component?index}, "${component.animationType}");
		float labelAnimation${component?index} = animationProgress(labelAnimationType${component?index},
			liveDelay(liveLabel${component?index}, ${component.animationDelayTicks}),
			liveDuration(liveLabel${component?index}, ${component.animationDurationTicks}),
			liveLoop(liveLabel${component?index}, ${component.animationLoop?c}));
		int labelColor${component?index} = (${component.color.getRGB()} & 0x00FFFFFF) |
			((int) (("FADE".equals(labelAnimationType${component?index}) ? labelAnimation${component?index} : 1.0F) * 255.0F) << 24);
		guiGraphics.text(this.font, liveText(liveLabel${component?index}, Component.translatable("gui.${modid}.${registryname}.${component.getName()}")),
			layoutX(liveX(liveLabel${component?index}, ${component.x}) + animationOffsetX(labelAnimationType${component?index}, labelAnimation${component?index})),
			layoutY(liveY(liveLabel${component?index}, ${component.y}) + animationOffsetY(labelAnimationType${component?index}, labelAnimation${component?index})),
			labelColor${component?index}, ${component.hasShadow?c});
		}
		</#list>
		<#if data.hasServerStatus()>
		renderServerStatus(guiGraphics);
		</#if>
		<#else>
		drawCentered(guiGraphics, "${data.titleTextJava}", 38, ${data.getTitleColorARGB()});
		<#if data.subtitleText?has_content>
		drawCentered(guiGraphics, "${data.subtitleTextJava}", 58, ${data.getSubtitleColorARGB()});
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

	private int layoutX(int x) { float scale = layoutScale(); return Math.round((this.width - ${data.W} * scale) / 2.0F + x * scale); }
	private int layoutY(int y) { float scale = layoutScale(); return Math.round((this.height - ${data.H} * scale) / 2.0F + y * scale); }
	private int layoutSize(int size) { return Math.max(1, Math.round(size * layoutScale())); }

	private void applyWidgetLayout() {
		<#list data.getComponentsOfType("Button") as component>
		if (remarkButton${component?index} != null) {
			LiveComponent liveButton${component?index} = live("${component.getName()}");
			String animationType${component?index} = liveAnimation(liveButton${component?index}, "${component.animationType}");
			int duration${component?index} = liveDuration(liveButton${component?index}, ${component.animationDurationTicks});
			float animation${component?index} = animationProgress(animationType${component?index},
				liveDelay(liveButton${component?index}, ${component.animationDelayTicks}), duration${component?index},
				liveLoop(liveButton${component?index}, ${component.animationLoop?c}));
			remarkButton${component?index}.setX(layoutX(liveX(liveButton${component?index}, ${component.x}) + animationOffsetX(animationType${component?index}, animation${component?index})));
			remarkButton${component?index}.setY(layoutY(liveY(liveButton${component?index}, ${component.y}) + animationOffsetY(animationType${component?index}, animation${component?index})));
			if (liveButton${component?index} != null && liveButton${component?index}.text != null)
				remarkButton${component?index}.setMessage(Component.literal(liveButton${component?index}.text));
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
			remarkButton${component?index}.setAnimationScale(animationScale(animationType${component?index}, animation${component?index}, duration${component?index}));
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
		long elapsed = Math.max(0L, net.minecraft.util.Util.getMillis() - openedAtMillis) / 50L;
		delay = Math.max(0, delay);
		duration = Math.max(1, duration);
		if (loop) {
			long phase = elapsed % (delay + duration);
			return phase < delay ? 0.0F : Math.min(1.0F, (phase - delay) / (float) duration);
		}
		return Math.max(0.0F, Math.min(1.0F, (elapsed - delay) / (float) duration));
	}

	private int animationOffsetX(String type, float progress) { return "SLIDE_LEFT".equals(type) ? Math.round((1.0F - progress) * 40.0F) : 0; }
	private int animationOffsetY(String type, float progress) { return "SLIDE_UP".equals(type) ? Math.round((1.0F - progress) * 30.0F) : 0; }
	private float animationScale(String type, float progress, int duration) {
		if ("SCALE".equals(type))
			return 0.7F + 0.3F * progress;
		if ("PULSE".equals(type)) {
			double elapsed = Math.max(0L, net.minecraft.util.Util.getMillis() - openedAtMillis) / 50.0D;
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
	private static Component liveText(LiveComponent live, Component fallback) { return live != null && live.text != null ? Component.literal(live.text) : fallback; }
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
		case "OPTIONS" -> this.minecraft.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(this, this.minecraft.options, ${data.isPauseMenu()?c}));
		case "QUIT" -> { stopMenuMusic(); this.minecraft.stop(); }
		case "JOIN_SERVER" -> joinServer(value, sourceUrl);
		case "OPEN_URL" -> net.minecraft.util.Util.getPlatform().openUri(value);
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
				"Direct Server", address, net.minecraft.client.multiplayer.ServerData.Type.OTHER);
		net.minecraft.client.gui.screens.ConnectScreen.startConnecting(this, this.minecraft,
				net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), serverData, false, null);
	}

	private void renderRemarkBackground(GuiGraphicsExtractor guiGraphics) {
		<#if data.hasBackgroundImages()>
		String currentBackgroundMode = livePayload != null && livePayload.backgroundMode != null ? livePayload.backgroundMode : "${data.backgroundMode}";
		if (BACKGROUNDS.length == 1 || currentBackgroundMode.equals("STATIC")) {
			drawCoverBackground(guiGraphics, 0);
		} else {
			int duration = Math.max(1, livePayload != null && livePayload.backgroundDurationTicks > 0 ? livePayload.backgroundDurationTicks : ${data.backgroundDurationTicks});
			long tick = net.minecraft.util.Util.getMillis() / 50L;
			int index = (int) ((tick / duration) % BACKGROUNDS.length);
			if (currentBackgroundMode.equals("FADE")) {
				int fade = Math.min(Math.max(0, livePayload != null ? livePayload.backgroundFadeTicks : ${data.backgroundFadeTicks}), duration);
				int phase = (int) (tick % duration);
				if (fade > 0 && phase >= duration - fade) {
					float alpha = (phase - (duration - fade)) / (float) fade;
					drawCoverBackground(guiGraphics, index);
					drawCoverBackground(guiGraphics, (index + 1) % BACKGROUNDS.length, alpha);
				} else {
					drawCoverBackground(guiGraphics, index);
				}
			} else {
				drawCoverBackground(guiGraphics, index);
			}
		}
		<#elseif !data.isPauseMenu()>
		guiGraphics.fill(0, 0, this.width, this.height, 0xFF120814);
		guiGraphics.fill(0, 0, this.width, Math.max(36, this.height / 6), ${data.getAccentColorARGB()});
		</#if>
		applyBackgroundOverlay(guiGraphics);
	}

	<#if data.hasBackgroundImages()>
	private void drawCoverBackground(GuiGraphicsExtractor guiGraphics, int index) {
		drawCoverBackground(guiGraphics, index, 1.0F);
	}

	private void drawCoverBackground(GuiGraphicsExtractor guiGraphics, int index, float alpha) {
		int textureWidth = Math.max(1, BACKGROUND_WIDTHS[index]);
		int textureHeight = Math.max(1, BACKGROUND_HEIGHTS[index]);
		float scale = Math.max(this.width / (float) textureWidth, this.height / (float) textureHeight);
		<#if data.backgroundSlowZoom>
		int zoomDuration = Math.max(1, ${data.backgroundZoomDurationTicks});
		double zoomPhase = ((net.minecraft.util.Util.getMillis() / 50.0D) % zoomDuration) / zoomDuration;
		scale *= (float) (1.0D + (${data.backgroundZoomPercent} / 100.0D)
				* (0.5D - 0.5D * Math.cos(zoomPhase * Math.PI * 2.0D)));
		</#if>
		int drawWidth = (int) Math.ceil(textureWidth * scale);
		int drawHeight = (int) Math.ceil(textureHeight * scale);
		int x = (this.width - drawWidth) / 2;
		int y = (this.height - drawHeight) / 2;
		int tint = (Math.max(0, Math.min(255, Math.round(alpha * 255.0F))) << 24) | 0x00FFFFFF;
		guiGraphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUNDS[index], x, y, 0, 0, drawWidth, drawHeight,
			textureWidth, textureHeight, textureWidth, textureHeight, tint);
	}
	</#if>

	private void applyBackgroundOverlay(GuiGraphicsExtractor guiGraphics) {
		int opacity = Math.max(0, Math.min(100, livePayload != null ? livePayload.overlayOpacity : ${data.backgroundOverlayOpacity}));
		if (opacity <= 0)
			return;
		int overlayRgb = parseRGB(livePayload != null ? livePayload.overlayColor : "${data.backgroundOverlayColor}", ${data.getBackgroundOverlayRGB()});
		int overlayAlpha = (opacity * 255) / 100;
		<#if data.backgroundOverlayGradient>
		for (int band = 0; band < 16; band++) {
			int top = this.height * band / 16;
			int bottom = this.height * (band + 1) / 16;
			int alpha = overlayAlpha * (band + 1) / 16;
			guiGraphics.fill(0, top, this.width, bottom, (alpha << 24) | overlayRgb);
		}
		<#else>
		guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24) | overlayRgb);
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
		serverStatusData = new net.minecraft.client.multiplayer.ServerData("Remake Server", "${data.getServerStatusAddressJava()}",
			net.minecraft.client.multiplayer.ServerData.Type.OTHER);
		serverStatusData.ping = -2L;
		try {
			serverStatusPinger.pingServer(serverStatusData, () -> {}, () -> {},
				net.minecraft.server.network.EventLoopGroupHolder.remote(false));
		} catch (Exception e) {
			serverStatusError = "Offline";
		}
		serverStatusRefreshCountdown = Math.max(20, ${data.serverStatusRefreshTicks});
	}

	private void renderServerStatus(GuiGraphicsExtractor guiGraphics) {
		int x = layoutX(${data.serverStatusX});
		int y = layoutY(${data.serverStatusY});
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
		guiGraphics.text(this.font, Component.literal(status), x + 9, y, 0xFFFFFFFF, true);
		<#if data.serverStatusShowMOTD>
		if (serverStatusData != null && serverStatusData.motd != null)
			guiGraphics.text(this.font, serverStatusData.motd, x + 9, y + 11, 0xFFCCCCCC, false);
		</#if>
	}
	</#if>

	private static Identifier screenTexture(String path) {
		return path == null || path.isBlank() ? null : Identifier.parse("${modid}:textures/screens/" + path);
	}

	private static final class RemarkButton extends Button {
		private static final Identifier NORMAL_SPRITE = Identifier.withDefaultNamespace("widget/button");
		private static final Identifier HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("widget/button_highlighted");
		private static final Identifier DISABLED_SPRITE = Identifier.withDefaultNamespace("widget/button_disabled");
		private final boolean textOnly;
		private Identifier normalTexture;
		private Identifier hoverTexture;
		private Identifier pressedTexture;
		private Identifier disabledTexture;
		private int normalColor;
		private int hoverColor;
		private int pressedColor;
		private int disabledColor;
		private float hoverScale;
		private boolean pressed;
		private float animationScale = 1.0F;

		private RemarkButton(int x, int y, int width, int height, Component message, Button.OnPress onPress,
				boolean textOnly, Identifier normalTexture, Identifier hoverTexture, Identifier pressedTexture,
				Identifier disabledTexture, int normalColor, int hoverColor, int pressedColor, int disabledColor,
				float hoverScale) {
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

		private void setAnimationScale(float scale) {
			this.animationScale = Math.max(0.1F, scale);
		}

		private void setRemarkSize(int width, int height) {
			this.width = Math.max(1, width);
			this.height = Math.max(1, height);
		}

		private void setLiveStyle(int normalColor, int hoverColor, int pressedColor, int disabledColor,
				float hoverScale, Identifier normalTexture, Identifier hoverTexture, Identifier pressedTexture,
				Identifier disabledTexture) {
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

		@Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				float partialTick) {
			boolean highlighted = this.isHoveredOrFocused();
			float scale = animationScale * (highlighted && this.active ? hoverScale : 1.0F);
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.getX() + this.getWidth() / 2.0F, this.getY() + this.getHeight() / 2.0F);
			graphics.pose().scale(scale, scale);
			graphics.pose().translate(-(this.getX() + this.getWidth() / 2.0F), -(this.getY() + this.getHeight() / 2.0F));

			Identifier texture = !this.active ? disabledTexture : pressed ? pressedTexture : highlighted ? hoverTexture : normalTexture;
			if (texture == null)
				texture = normalTexture;
			int alpha = Math.max(0, Math.min(255, Math.round(this.alpha * 255.0F)));
			int tint = (alpha << 24) | 0x00FFFFFF;
			if (texture != null) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, texture, this.getX(), this.getY(), 0, 0,
						this.getWidth(), this.getHeight(), this.getWidth(), this.getHeight(), this.getWidth(),
						this.getHeight(), tint);
			} else if (!textOnly) {
				Identifier sprite = !this.active ? DISABLED_SPRITE : highlighted ? HIGHLIGHTED_SPRITE : NORMAL_SPRITE;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.getWidth(),
						this.getHeight(), tint);
			}

			int color = !this.active ? disabledColor : pressed ? pressedColor : highlighted ? hoverColor : normalColor;
			color = (color & 0x00FFFFFF) | (alpha << 24);
			net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
			String[] lines = this.getMessage().getString().split("\\R", -1);
			int textY = this.getY() + (this.getHeight() - font.lineHeight * lines.length) / 2;
			for (String line : lines) {
				graphics.centeredText(font, Component.literal(line), this.getX() + this.getWidth() / 2, textY, color);
				textY += font.lineHeight;
			}
			graphics.pose().popMatrix();
		}

		@Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
			pressed = true;
			super.onClick(event, doubleClick);
		}

		@Override public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
			pressed = false;
			super.onRelease(event);
		}
	}

	<#if data.hasMenuMusic()>
	private void startMenuMusic() {
		if (this.minecraft == null)
			return;
		if (menuMusicInstance != null && this.minecraft.getSoundManager().isActive(menuMusicInstance))
			return;
		this.minecraft.getMusicManager().stopPlaying();
		menuMusicInstance = new net.minecraft.client.resources.sounds.SimpleSoundInstance(
				Identifier.parse("${data.getMenuMusicJava()}"), net.minecraft.sounds.SoundSource.MUSIC,
				${data.getMenuMusicVolumeFactor()}, 1.0F, net.minecraft.util.RandomSource.create(), ${data.loopMenuMusic?c}, 0,
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

	private void drawCentered(GuiGraphicsExtractor guiGraphics, String text, int y, int color) {
		guiGraphics.text(this.font, Component.literal(text), (this.width - this.font.width(text)) / 2, y, color, true);
	}

	@Override public boolean isPauseScreen() {
		return ${data.isPauseMenu()?c};
	}
}
<#-- @formatter:on -->
