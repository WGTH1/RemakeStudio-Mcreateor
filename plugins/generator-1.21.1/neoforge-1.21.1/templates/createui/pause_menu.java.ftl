<#if data.isPauseMenu()>
	private static java.lang.ref.WeakReference<net.minecraft.client.gui.screens.Screen> vanillaMenuBypass = new java.lang.ref.WeakReference<>(null);
	private net.minecraft.client.gui.screens.Screen originalPauseMenu;
	<#if remakeUiVersion == "1.21.1">
	@Override public void renderBackground(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderRemarkBackground(guiGraphics);
	}
	<#elseif remakeUiVersion == "26.1.2">
	@Override public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
		renderRemarkBackground(guiGraphics);
		this.minecraft.gui.extractDeferredSubtitles();
	}
	</#if>

	private static boolean matchesPauseMenu(net.minecraft.client.gui.screens.Screen screen) {
		// PauseScreen(false) is also used for hidden/loading transitions, not the Esc menu.
		return net.minecraft.client.Minecraft.getInstance().level != null
			&& screen != vanillaMenuBypass.get()
			&& screen instanceof net.minecraft.client.gui.screens.PauseScreen
			&& screen.getTitle().equals(net.minecraft.network.chat.Component.translatable("menu.game"));
	}

	private void addPauseMenuButtons() {
		int y = 65;
		addPauseButton(net.minecraft.network.chat.Component.translatable("menu.returnToGame"), "RESUME_GAME", y); y += 24;
		addPauseButton(net.minecraft.network.chat.Component.translatable("gui.advancements"), "ADVANCEMENTS", y); y += 24;
		addPauseButton(net.minecraft.network.chat.Component.translatable("gui.stats"), "STATISTICS", y); y += 24;
		<#if data.showOptions>
		addPauseButton(net.minecraft.network.chat.Component.translatable("menu.options"), "OPTIONS", y); y += 24;
		</#if>
		addPauseButton(net.minecraft.network.chat.Component.literal("Original menu"), "VANILLA_PAUSE_MENU", y); y += 24;
		addPauseButton(net.minecraft.network.chat.Component.translatable(this.minecraft.isLocalServer() ? "menu.returnToMenu" : "menu.disconnect"), "DISCONNECT", y);
	}

	private void addPauseButton(net.minecraft.network.chat.Component text, String action, int y) {
		this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(text,
			button -> handleButtonAction(action, "", ""))
			.bounds(layoutX(114), layoutY(y), layoutSize(200), layoutSize(20)).build());
	}

	private boolean handlePauseAction(String action) {
		switch (action) {
		case "RESUME_GAME" -> resumeGame();
		case "ADVANCEMENTS" -> {
			if (this.minecraft.player != null)
				this.minecraft.setScreen(new net.minecraft.client.gui.screens.advancements.AdvancementsScreen(
					this.minecraft.player.connection.getAdvancements()<#if remakeUiVersion != "1.20.1">, this</#if>));
		}
		case "STATISTICS" -> {
			if (this.minecraft.player != null)
				this.minecraft.setScreen(new net.minecraft.client.gui.screens.achievement.StatsScreen(this, this.minecraft.player.getStats()));
		}
		case "DISCONNECT" -> {
			if (this.minecraft.level != null)
				this.minecraft.getReportingContext().draftReportHandled(this.minecraft, this, this::disconnectFromWorld, true);
		}
		case "VANILLA_PAUSE_MENU" -> {
			if (this.minecraft.level != null) {
				var original = originalPauseMenu != null ? originalPauseMenu : new net.minecraft.client.gui.screens.PauseScreen(true);
				vanillaMenuBypass = new java.lang.ref.WeakReference<>(original);
				this.minecraft.setScreen(original);
			}
		}
		default -> { return false; }
		}
		return true;
	}

	private void resumeGame() {
		if (this.minecraft == null || this.minecraft.level == null)
			return;
		stopMenuMusic();
		this.minecraft.setScreen(null);
		this.minecraft.mouseHandler.grabMouse();
	}

	private void disconnectFromWorld() {
		if (this.minecraft == null || this.minecraft.level == null)
			return;
		stopMenuMusic();
		<#if remakeUiVersion == "26.1.2">
		this.minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
		<#else>
		boolean local = this.minecraft.isLocalServer();
		<#if remakeUiVersion == "1.20.1">
		boolean realms = this.minecraft.isConnectedToRealms();
		<#else>
		var server = this.minecraft.getCurrentServer();
		boolean realms = server != null && server.isRealm();
		</#if>
		this.minecraft.level.disconnect();
		<#if remakeUiVersion == "1.20.1">
		if (local)
			this.minecraft.clearLevel(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(net.minecraft.network.chat.Component.translatable("menu.savingLevel")));
		else
			this.minecraft.clearLevel();
		<#else>
		if (local)
			this.minecraft.disconnect(new net.minecraft.client.gui.screens.GenericMessageScreen(net.minecraft.network.chat.Component.translatable("menu.savingLevel")));
		else
			this.minecraft.disconnect();
		</#if>
		var title = new net.minecraft.client.gui.screens.TitleScreen();
		if (local)
			this.minecraft.setScreen(title);
		else if (realms)
			this.minecraft.setScreen(new com.mojang.realmsclient.RealmsMainScreen(title));
		else
			this.minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(title));
		</#if>
	}

	@Override public void onClose() {
		if (this.minecraft != null && this.minecraft.level != null)
			resumeGame();
		else
			super.onClose();
	}
</#if>
