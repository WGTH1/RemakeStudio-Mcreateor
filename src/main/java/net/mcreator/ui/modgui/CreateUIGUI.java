/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.modgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.gui.Button;
import net.mcreator.element.parts.gui.Checkbox;
import net.mcreator.element.parts.gui.GUIComponent;
import net.mcreator.element.parts.gui.Label;
import net.mcreator.element.parts.gui.SizedComponent;
import net.mcreator.element.parts.gui.TextField;
import net.mcreator.element.parts.gui.Tooltip;
import net.mcreator.element.parts.procedure.StringProcedure;
import net.mcreator.element.types.CreateUI;
import net.mcreator.io.FileIO;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.SoundElementDialog;
import net.mcreator.ui.dialogs.file.FileDialogs;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.ui.minecraft.SoundSelector;
import net.mcreator.ui.minecraft.TextureComboBox;
import net.mcreator.ui.remark.RemarkStudioPreferences;
import net.mcreator.ui.validation.ValidationGroup;
import net.mcreator.ui.validation.ValidationResult;
import net.mcreator.ui.validation.component.VTextField;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.ui.wysiwyg.WYSIWYG;
import net.mcreator.ui.wysiwyg.WYSIWYGComponentRegistration;
import net.mcreator.ui.wysiwyg.WYSIWYGEditor;
import net.mcreator.util.FilenameUtilsPatched;
import net.mcreator.util.GSONClone;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.elements.SoundElement;

import javax.imageio.ImageIO;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CreateUIGUI extends ModElementGUI<CreateUI> {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private WYSIWYGEditor editor;

	private final JComboBox<String> targetScreen = new JComboBox<>(new String[] {
			CreateUI.TARGET_MAIN_MENU, CreateUI.TARGET_PAUSE_MENU });
	private final JCheckBox replaceVanillaMenu = L10N.checkbox("elementgui.createui.replace_vanilla_menu");
	private final JCheckBox responsiveLayout = L10N.checkbox("elementgui.createui.responsive_layout");
	private final JComboBox<String> responsiveMode = new JComboBox<>(new String[] { CreateUI.RESPONSIVE_CENTER,
			CreateUI.RESPONSIVE_FIT, CreateUI.RESPONSIVE_FIT_DOWN });
	private final JSpinner safeMargin = new JSpinner(new SpinnerNumberModel(8, 0, 128, 1));
	private final JComboBox<String> previewResolution = new JComboBox<>(new String[] { "854 x 480", "960 x 540",
			"1024 x 768", "1280 x 720", "1366 x 768", "1600 x 900", "1920 x 1080", "2560 x 1440" });
	private final JComboBox<Integer> previewGuiScale = new JComboBox<>(new Integer[] { 2, 3 });
	private final JLabel previewLogicalSize = new JLabel();

	private final VTextField titleText = new VTextField(32).requireValue("elementgui.createui.error_title");
	private final VTextField subtitleText = new VTextField(32);

	private final VTextField titleColor = colorField("FFD76A");
	private final VTextField subtitleColor = colorField("FFFFFF");
	private final VTextField accentColor = colorField("E91E79");

	private final JCheckBox showSingleplayer = L10N.checkbox("elementgui.createui.show_singleplayer");
	private final JCheckBox showMultiplayer = L10N.checkbox("elementgui.createui.show_multiplayer");
	private final JCheckBox showOptions = L10N.checkbox("elementgui.createui.show_options");
	private final JCheckBox showQuit = L10N.checkbox("elementgui.createui.show_quit");

	private final JComboBox<String> presetSelector = new JComboBox<>(
			new String[] { "Vanilla menu", "Pause menu", "Wizard lobby", "Join server", "Open web link" });
	private final JComboBox<String> actionButton = new JComboBox<>();
	private final JComboBox<String> actionType = new JComboBox<>(new String[] { CreateUI.ACTION_NONE,
			CreateUI.ACTION_SINGLEPLAYER, CreateUI.ACTION_MULTIPLAYER, CreateUI.ACTION_OPTIONS, CreateUI.ACTION_QUIT,
			CreateUI.ACTION_JOIN_SERVER, CreateUI.ACTION_OPEN_URL, CreateUI.ACTION_RESUME_GAME,
			CreateUI.ACTION_ADVANCEMENTS, CreateUI.ACTION_STATISTICS, CreateUI.ACTION_DISCONNECT,
			CreateUI.ACTION_VANILLA_PAUSE_MENU });
	private final VTextField actionValue = new VTextField(28);
	private final VTextField actionSourceUrl = new VTextField(28);
	private final List<CreateUI.CreateUIButtonAction> buttonActions = new ArrayList<>();
	private boolean loadingActionEditor;

	private final DefaultListModel<String> backgroundImages = new DefaultListModel<>();
	private final JList<String> backgroundImageList = new JList<>(backgroundImages);
	private TextureComboBox backgroundTexture;
	private final JComboBox<String> backgroundMode = new JComboBox<>(
			new String[] { CreateUI.BACKGROUND_STATIC, CreateUI.BACKGROUND_CUT, CreateUI.BACKGROUND_FADE });
	private final JSpinner backgroundDurationTicks = new JSpinner(new SpinnerNumberModel(120, 20, 72000, 5));
	private final JSpinner backgroundFadeTicks = new JSpinner(new SpinnerNumberModel(20, 0, 72000, 5));
	private final JCheckBox backgroundSlowZoom = L10N.checkbox("elementgui.createui.background_slow_zoom");
	private final JSpinner backgroundZoomPercent = new JSpinner(new SpinnerNumberModel(5, 0, 50, 1));
	private final JSpinner backgroundZoomDurationTicks = new JSpinner(new SpinnerNumberModel(400, 20, 72000, 20));
	private final VTextField backgroundOverlayColor = colorField("000000");
	private final JSpinner backgroundOverlayOpacity = new JSpinner(new SpinnerNumberModel(60, 0, 100, 5));
	private final JCheckBox backgroundOverlayGradient = L10N.checkbox(
			"elementgui.createui.background_overlay_gradient");
	private final SoundSelector menuMusic = new SoundSelector(mcreator);
	private final JCheckBox loopMenuMusic = L10N.checkbox("elementgui.createui.menu_music_loop");
	private final JSpinner menuMusicVolume = new JSpinner(new SpinnerNumberModel(100, 0, 100, 5));
	private final JCheckBox serverStatusEnabled = L10N.checkbox("elementgui.createui.server_status_enabled");
	private final VTextField serverStatusAddress = new VTextField(24);
	private final JSpinner serverStatusRefreshTicks = new JSpinner(new SpinnerNumberModel(200, 20, 72000, 20));
	private final JSpinner serverStatusX = new JSpinner(new SpinnerNumberModel(12, -4096, 4096, 1));
	private final JSpinner serverStatusY = new JSpinner(new SpinnerNumberModel(216, -4096, 4096, 1));
	private final JCheckBox serverStatusShowMOTD = L10N.checkbox("elementgui.createui.server_status_motd");
	private final JCheckBox serverStatusShowPlayers = L10N.checkbox("elementgui.createui.server_status_players");
	private final JCheckBox serverStatusShowPing = L10N.checkbox("elementgui.createui.server_status_ping");
	private final JCheckBox livePreviewEnabled = L10N.checkbox("elementgui.createui.live_preview_enabled");
	private final DefaultListModel<String> issueModel = new DefaultListModel<>();
	private final JList<String> issueList = new JList<>(issueModel);
	private final JLabel issueSummary = new JLabel();
	private final JLabel autosaveStatus = new JLabel();

	private final JSpinner inspectorX = new JSpinner(new SpinnerNumberModel(0, -4096, 4096, 1));
	private final JSpinner inspectorY = new JSpinner(new SpinnerNumberModel(0, -4096, 4096, 1));
	private final JSpinner inspectorLayer = new JSpinner(new SpinnerNumberModel(0, -999, 999, 1));
	private final JComboBox<Object> inspectorAnchor = new JComboBox<>();
	private final JCheckBox inspectorVisible = L10N.checkbox("elementgui.createui.visible");
	private final JCheckBox inspectorLocked = L10N.checkbox("elementgui.createui.locked");
	private boolean updatingInspector;
	private final JLabel previewSelectionName = new JLabel();
	private final JTextField previewNameEditor = new JTextField(20);
	private final JTextArea previewTextEditor = new JTextArea(4, 20);
	private final VTextField previewColor = colorField("FFFFFF");
	private final JSpinner previewX = new JSpinner(new SpinnerNumberModel(0, -4096, 4096, 1));
	private final JSpinner previewY = new JSpinner(new SpinnerNumberModel(0, -4096, 4096, 1));
	private final JSpinner previewWidth = new JSpinner(new SpinnerNumberModel(1, 1, 4096, 1));
	private final JSpinner previewHeight = new JSpinner(new SpinnerNumberModel(1, 1, 4096, 1));
	private final JSpinner previewLayer = new JSpinner(new SpinnerNumberModel(0, -999, 999, 1));
	private final JComboBox<Object> previewAnchor = new JComboBox<>();
	private final JCheckBox previewVisible = L10N.checkbox("elementgui.createui.visible");
	private final JCheckBox previewLocked = L10N.checkbox("elementgui.createui.locked");
	private final JCheckBox previewAutoSize = L10N.checkbox("elementgui.createui.auto_size_enabled");
	private boolean updatingPreviewInspector;
	private JList<GUIComponent> previewLayerList;
	private JTabbedPane previewTools;
	private JSplitPane previewWorkspaceSplit;

	private final JComboBox<GUIComponent> timelineComponent = new JComboBox<>();
	private final JComboBox<String> animationType = new JComboBox<>(
			new String[] { "NONE", "FADE", "SLIDE_UP", "SLIDE_LEFT", "SCALE", "PULSE" });
	private final JSpinner animationDelay = new JSpinner(new SpinnerNumberModel(0, 0, 72000, 1));
	private final JSpinner animationDuration = new JSpinner(new SpinnerNumberModel(20, 1, 72000, 1));
	private final JCheckBox animationLoop = L10N.checkbox("elementgui.createui.animation_loop");

	private final Deque<String> undoHistory = new ArrayDeque<>();
	private final Deque<String> redoHistory = new ArrayDeque<>();
	private final JButton undoButton = L10N.button("elementgui.createui.undo");
	private final JButton redoButton = L10N.button("elementgui.createui.redo");
	private String lastHistoryJSON;
	private boolean restoringHistory;
	private javax.swing.Timer historyTimer;
	private javax.swing.Timer autosaveTimer;
	private javax.swing.Timer timelineTimer;
	private boolean updatingTimelineComponents;
	private CreateUIPreviewPanel previewPanel;

	private static final Pattern SERVER_ADDRESS_PATTERN = Pattern.compile(
			"serverAddress\\s*=\\s*\\\"([^\\\"]+)\\\"");
	private static final Pattern SERVER_SOURCE_URL_PATTERN = Pattern.compile(
			"(?:IP_API_URL|serverAddressUrl|serverUrl)\\s*=\\s*\\\"([^\\\"]+)\\\"",
			Pattern.CASE_INSENSITIVE);

	private final ValidationGroup validation = new ValidationGroup();

	public CreateUIGUI(MCreator mcreator, ModElement modElement, boolean editingMode) {
		super(mcreator, modElement, editingMode);
		this.initGUI();
		super.finalizeGUI();
		startStudioTimers();
	}

	@Override protected void initGUI() {
		editor = new WYSIWYGEditor(mcreator, true);
		installFriendlyOptionRenderers();
		if (editor.getGUITypeSelector().getSelectedIndex() != 0)
			editor.getGUITypeSelector().setSelectedIndex(0);
		editor.setSlotComponentsEnabled(false);
		editor.spa1.setValue(427);
		editor.spa2.setValue(240);
		editor.renderBgLayer.setSelected(false);
		editor.doesPauseGame.setSelected(false);
		inspectorAnchor.addItem(L10N.t("elementgui.createui.anchor_auto"));
		previewAnchor.addItem(L10N.t("elementgui.createui.anchor_auto"));
		for (GUIComponent.AnchorPoint anchorPoint : GUIComponent.AnchorPoint.values())
			inspectorAnchor.addItem(anchorPoint);
		for (GUIComponent.AnchorPoint anchorPoint : GUIComponent.AnchorPoint.values())
			previewAnchor.addItem(anchorPoint);
		editor.list.addListSelectionListener(_ -> {
			updateSelectionInspector();
			updatePreviewInspector();
			refreshTimelineComponents();
			if (previewPanel != null)
				previewPanel.selectionChanged();
			if (previewPanel != null)
				previewPanel.repaint();
			if (previewLayerList != null)
				previewLayerList.repaint();
		});
		editor.components.addListDataListener(new javax.swing.event.ListDataListener() {
			@Override public void intervalAdded(javax.swing.event.ListDataEvent event) {
				componentListChanged();
			}

			@Override public void intervalRemoved(javax.swing.event.ListDataEvent event) {
				componentListChanged();
			}

			@Override public void contentsChanged(javax.swing.event.ListDataEvent event) {
				componentListChanged();
			}
		});
		installLayoutContextMenu();
		installImageDropTarget();
		previewPanel = new CreateUIPreviewPanel();
		installPreviewImageDropTarget();
		configurePreviewInspectorListeners();
		backgroundTexture = new TextureComboBox(mcreator, TextureType.SCREEN, true);
		backgroundImageList.setVisibleRowCount(8);
		backgroundImageList.setPrototypeCellValue("remark_background_preview_image.png");
		installBackgroundDropTarget(backgroundImageList);

		for (JComponent component : new JComponent[] { titleText, subtitleText, titleColor, subtitleColor, accentColor,
				targetScreen, presetSelector, actionButton, actionType, actionValue, actionSourceUrl, backgroundMode,
				backgroundTexture, backgroundOverlayColor, menuMusic })
			ComponentUtils.deriveFont(component, 16);

		backgroundMode.addActionListener(_ -> previewPanel.repaint());
		backgroundDurationTicks.addChangeListener(_ -> previewPanel.repaint());
		backgroundFadeTicks.addChangeListener(_ -> previewPanel.repaint());
		backgroundSlowZoom.addActionListener(_ -> previewPanel.repaint());
		backgroundZoomPercent.addChangeListener(_ -> previewPanel.repaint());
		backgroundZoomDurationTicks.addChangeListener(_ -> previewPanel.repaint());
		backgroundOverlayOpacity.addChangeListener(_ -> previewPanel.repaint());
		backgroundOverlayGradient.addActionListener(_ -> previewPanel.repaint());
		DocumentListener previewRepaintListener = new DocumentListener() {
			private void repaintPreview() {
				previewPanel.repaint();
			}

			@Override public void insertUpdate(DocumentEvent event) {
				repaintPreview();
			}

			@Override public void removeUpdate(DocumentEvent event) {
				repaintPreview();
			}

			@Override public void changedUpdate(DocumentEvent event) {
				repaintPreview();
			}
		};
		for (JTextField field : new JTextField[] { titleText, subtitleText, titleColor, subtitleColor, accentColor,
				backgroundOverlayColor, serverStatusAddress })
			field.getDocument().addDocumentListener(previewRepaintListener);
		previewResolution.addActionListener(_ -> refreshPreviewScale());
		previewGuiScale.addActionListener(_ -> refreshPreviewScale());
		responsiveLayout.addActionListener(_ -> previewPanel.repaint());
		responsiveMode.addActionListener(_ -> previewPanel.repaint());
		safeMargin.addChangeListener(_ -> previewPanel.repaint());
		serverStatusEnabled.addActionListener(_ -> previewPanel.repaint());
		targetScreen.addActionListener(_ -> {
			boolean pause = CreateUI.TARGET_PAUSE_MENU.equals(targetScreen.getSelectedItem());
			showSingleplayer.setVisible(!pause);
			showMultiplayer.setVisible(!pause);
			showQuit.setVisible(!pause);
			if (pause && "Vanilla menu".equals(presetSelector.getSelectedItem()))
				presetSelector.setSelectedItem("Pause menu");
			else if (!pause && "Pause menu".equals(presetSelector.getSelectedItem()))
				presetSelector.setSelectedItem("Vanilla menu");
			previewPanel.repaint();
		});

		replaceVanillaMenu.setSelected(true);
		responsiveLayout.setSelected(true);
		responsiveMode.setSelectedItem(CreateUI.RESPONSIVE_FIT_DOWN);
		showSingleplayer.setSelected(true);
		showMultiplayer.setSelected(true);
		showOptions.setSelected(true);
		showQuit.setSelected(true);
		backgroundOverlayGradient.setSelected(true);
		loopMenuMusic.setSelected(true);
		serverStatusShowMOTD.setSelected(true);
		serverStatusShowPlayers.setSelected(true);
		serverStatusShowPing.setSelected(true);
		livePreviewEnabled.setSelected(true);
		inspectorVisible.setSelected(true);
		previewResolution.setSelectedItem("1280 x 720");
		previewGuiScale.setSelectedItem(3);
		refreshPreviewScale();

		JPanel basicGrid = new JPanel(new GridLayout(9, 2, 8, 6));
		basicGrid.setOpaque(false);
		basicGrid.add(L10N.label("elementgui.createui.target_screen"));
		basicGrid.add(targetScreen);
		basicGrid.add(L10N.label("elementgui.createui.title_text"));
		basicGrid.add(titleText);
		basicGrid.add(L10N.label("elementgui.createui.subtitle_text"));
		basicGrid.add(subtitleText);
		basicGrid.add(L10N.label("elementgui.createui.title_color"));
		basicGrid.add(createColorPicker(titleColor, "elementgui.createui.title_color"));
		basicGrid.add(L10N.label("elementgui.createui.subtitle_color"));
		basicGrid.add(createColorPicker(subtitleColor, "elementgui.createui.subtitle_color"));
		basicGrid.add(L10N.label("elementgui.createui.accent_color"));
		basicGrid.add(createColorPicker(accentColor, "elementgui.createui.accent_color"));
		basicGrid.add(L10N.label("elementgui.createui.responsive_layout"));
		basicGrid.add(responsiveLayout);
		basicGrid.add(L10N.label("elementgui.createui.responsive_mode"));
		basicGrid.add(responsiveMode);
		basicGrid.add(L10N.label("elementgui.createui.safe_margin"));
		basicGrid.add(safeMargin);

		JPanel fallbackButtonsPanel = PanelUtils.column(4, showSingleplayer, showMultiplayer, showOptions, showQuit);
		JPanel settingsPanel = PanelUtils.column(10, replaceVanillaMenu, basicGrid,
				PanelUtils.join(FlowLayout.LEFT, L10N.label("elementgui.createui.buttons"), fallbackButtonsPanel));

		JButton applyPreset = L10N.button("elementgui.createui.apply_preset");
		applyPreset.addActionListener(_ -> applySelectedPreset());

		JButton importLobbyProject = L10N.button("elementgui.createui.import_lobby_project");
		importLobbyProject.addActionListener(_ -> importLobbyProject());

		JButton refreshButtons = L10N.button("elementgui.createui.refresh_buttons");
		refreshButtons.addActionListener(_ -> refreshActionButtonList());

		JButton saveAction = L10N.button("elementgui.createui.save_action");
		saveAction.addActionListener(_ -> saveCurrentAction());

		JButton clearAction = L10N.button("elementgui.createui.clear_action");
		clearAction.addActionListener(_ -> clearCurrentAction());

		JPanel actionGrid = new JPanel(new GridLayout(5, 2, 8, 6));
		actionGrid.setOpaque(false);
		actionGrid.add(L10N.label("elementgui.createui.preset"));
		actionGrid.add(PanelUtils.join(FlowLayout.LEFT, presetSelector, applyPreset, importLobbyProject));
		actionGrid.add(L10N.label("elementgui.createui.action_button"));
		actionGrid.add(PanelUtils.join(FlowLayout.LEFT, actionButton, refreshButtons));
		actionGrid.add(L10N.label("elementgui.createui.action_type"));
		actionGrid.add(actionType);
		actionGrid.add(L10N.label("elementgui.createui.action_value"));
		actionGrid.add(PanelUtils.join(FlowLayout.LEFT, actionValue, saveAction, clearAction));
		actionGrid.add(L10N.label("elementgui.createui.action_source_url"));
		actionGrid.add(actionSourceUrl);

		actionButton.addActionListener(_ -> loadSelectedAction());
		actionType.addActionListener(_ -> saveCurrentAction());
		DocumentListener liveActionListener = new DocumentListener() {
			private void save() {
				saveCurrentAction();
			}

			@Override public void insertUpdate(DocumentEvent event) {
				save();
			}

			@Override public void removeUpdate(DocumentEvent event) {
				save();
			}

			@Override public void changedUpdate(DocumentEvent event) {
				save();
			}
		};
		actionValue.getDocument().addDocumentListener(liveActionListener);
		actionSourceUrl.getDocument().addDocumentListener(liveActionListener);

		JPanel actionsPanel = PanelUtils.totalCenterInPanel(PanelUtils.column(14, settingsPanel, actionGrid));

		JButton addBackgroundFiles = L10N.button("elementgui.createui.background_add_files");
		addBackgroundFiles.addActionListener(_ -> addBackgroundFiles());

		JButton addBackgroundURL = L10N.button("elementgui.createui.background_add_url");
		addBackgroundURL.addActionListener(_ -> addBackgroundFromURL());

		JButton addBackgroundTexture = L10N.button("elementgui.createui.background_add_selected");
		addBackgroundTexture.addActionListener(_ -> addBackgroundFromTexturePicker());

		JButton removeBackground = L10N.button("elementgui.createui.background_remove");
		removeBackground.addActionListener(_ -> removeSelectedBackground());

		JButton moveBackgroundUp = L10N.button("elementgui.createui.background_move_up");
		moveBackgroundUp.addActionListener(_ -> moveSelectedBackground(-1));

		JButton moveBackgroundDown = L10N.button("elementgui.createui.background_move_down");
		moveBackgroundDown.addActionListener(_ -> moveSelectedBackground(1));

		JPanel backgroundTiming = new JPanel(new GridLayout(9, 2, 8, 6));
		backgroundTiming.setOpaque(false);
		backgroundTiming.add(L10N.label("elementgui.createui.background_mode"));
		backgroundTiming.add(backgroundMode);
		backgroundTiming.add(L10N.label("elementgui.createui.background_duration"));
		backgroundTiming.add(backgroundDurationTicks);
		backgroundTiming.add(L10N.label("elementgui.createui.background_fade"));
		backgroundTiming.add(backgroundFadeTicks);
		backgroundTiming.add(L10N.label("elementgui.createui.background_slow_zoom"));
		backgroundTiming.add(backgroundSlowZoom);
		backgroundTiming.add(L10N.label("elementgui.createui.background_zoom_amount"));
		backgroundTiming.add(backgroundZoomPercent);
		backgroundTiming.add(L10N.label("elementgui.createui.background_zoom_duration"));
		backgroundTiming.add(backgroundZoomDurationTicks);
		backgroundTiming.add(L10N.label("elementgui.createui.background_overlay_color"));
		backgroundTiming.add(createColorPicker(backgroundOverlayColor,
				"elementgui.createui.background_overlay_color"));
		backgroundTiming.add(L10N.label("elementgui.createui.background_overlay_opacity"));
		backgroundTiming.add(backgroundOverlayOpacity);
		backgroundTiming.add(L10N.label("elementgui.createui.background_overlay_gradient"));
		backgroundTiming.add(backgroundOverlayGradient);

		JPanel musicSettings = new JPanel(new GridLayout(3, 2, 8, 6));
		musicSettings.setOpaque(false);
		musicSettings.add(L10N.label("elementgui.createui.menu_music"));
		musicSettings.add(menuMusic);
		musicSettings.add(L10N.label("elementgui.createui.menu_music_volume"));
		musicSettings.add(menuMusicVolume);
		musicSettings.add(L10N.label("elementgui.createui.menu_music_loop"));
		musicSettings.add(loopMenuMusic);

		JPanel backgroundControls = PanelUtils.column(8, L10N.label("elementgui.createui.background_existing"),
				backgroundTexture, addBackgroundTexture, PanelUtils.join(FlowLayout.LEFT, addBackgroundFiles, addBackgroundURL),
				new JScrollPane(backgroundImageList), PanelUtils.join(FlowLayout.LEFT, moveBackgroundUp,
						moveBackgroundDown, removeBackground), backgroundTiming, musicSettings);
		JScrollPane backgroundScroll = createPreviewToolScroll(backgroundControls);

		JPanel previewPage = new JPanel(new BorderLayout(10, 0));
		previewPage.setOpaque(false);
		previewPage.add("North", createPreviewToolbar());
		previewPage.add("West", createPreviewElementsPanel());
		previewTools = new JTabbedPane();
		previewTools.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		JScrollPane selectionScroll = createPreviewToolScroll(createPreviewSelectionPanel());
		previewTools.addTab(L10N.t("elementgui.createui.preview_properties"), selectionScroll);
		previewTools.addTab(L10N.t("elementgui.createui.preview_layers"), createPreviewLayersPanel());
		previewTools.addTab(L10N.t("elementgui.createui.preview_background"), backgroundScroll);
		JScrollPane screenScroll = createPreviewToolScroll(actionsPanel);
		previewTools.addTab(L10N.t("elementgui.createui.preview_screen"), screenScroll);
		previewTools.setMinimumSize(new Dimension(300, 0));
		previewTools.setPreferredSize(new Dimension(420, 10));

		previewPanel.setMinimumSize(new Dimension(360, 240));
		previewWorkspaceSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewPanel, previewTools);
		previewWorkspaceSplit.setBorder(BorderFactory.createEmptyBorder());
		previewWorkspaceSplit.setContinuousLayout(true);
		previewWorkspaceSplit.setOneTouchExpandable(true);
		previewWorkspaceSplit.setResizeWeight(1.0);
		previewWorkspaceSplit.setDividerSize(8);
		previewPage.add("Center", previewWorkspaceSplit);
		previewPage.add("South", new JLabel(L10N.t("elementgui.createui.preview_hint")));

		validation.addValidationElement(titleText);
		validation.addValidationElement(titleColor);
		validation.addValidationElement(subtitleColor);
		validation.addValidationElement(accentColor);
		validation.addValidationElement(backgroundOverlayColor);

		JPanel timelinePage = RemarkStudioPreferences.isAdvancedMode() ? createTimelinePage() : null;
		JPanel studioPage = createStudioToolsPage();

		addPage(L10N.t("elementgui.createui.page_preview"), previewPage).validate(validation);
		if (RemarkStudioPreferences.isAdvancedMode())
			addPage(L10N.t("elementgui.createui.page_timeline"), timelinePage, false);
		addPage(L10N.t("elementgui.createui.page_check_export"), studioPage, false);

		if (!isEditingMode()) {
			titleText.setText("Remake Studios");
			subtitleText.setText("Custom Minecraft menu");
			applyVanillaPreset();
		}
		SwingUtilities.invokeLater(() -> {
			int width = previewWorkspaceSplit.getWidth();
			if (width > 0)
				previewWorkspaceSplit.setDividerLocation(Math.max(360, width - 420));
		});
		SwingUtilities.invokeLater(this::resetHistoryBaseline);
	}

	private static JScrollPane createPreviewToolScroll(Component content) {
		JScrollPane scroll = new JScrollPane(content, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(18);
		scroll.getHorizontalScrollBar().setUnitIncrement(18);
		return scroll;
	}

	private JToolBar createPreviewToolbar() {
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);
		toolbar.setOpaque(false);

		undoButton.addActionListener(_ -> undoStudioChange());
		redoButton.addActionListener(_ -> redoStudioChange());
		undoButton.setEnabled(false);
		redoButton.setEnabled(false);
		toolbar.add(undoButton);
		toolbar.add(redoButton);
		toolbar.addSeparator();

		JButton add = L10N.button("elementgui.createui.preview_add");
		add.addActionListener(_ -> showPreviewAddMenu(add, 0, add.getHeight(), null));
		toolbar.add(add);
		toolbar.add(studioButton("elementgui.createui.context_copy", this::duplicatePreviewSelection));
		toolbar.add(studioButton("elementgui.createui.context_delete", this::deletePreviewSelection));
		toolbar.add(studioButton("elementgui.createui.auto_size", this::configureAutoSizeForSelection));
		JButton arrange = L10N.button("elementgui.createui.align");
		JPopupMenu arrangeMenu = new JPopupMenu();
		addPopupAction(arrangeMenu, "elementgui.createui.group", this::groupSelection);
		addPopupAction(arrangeMenu, "elementgui.createui.ungroup", this::ungroupSelection);
		arrangeMenu.addSeparator();
		addPopupAction(arrangeMenu, "elementgui.createui.align_left", () -> alignSelection("LEFT"));
		addPopupAction(arrangeMenu, "elementgui.createui.align_center", () -> alignSelection("CENTER"));
		addPopupAction(arrangeMenu, "elementgui.createui.align_right", () -> alignSelection("RIGHT"));
		addPopupAction(arrangeMenu, "elementgui.createui.align_top", () -> alignSelection("TOP"));
		addPopupAction(arrangeMenu, "elementgui.createui.align_middle", () -> alignSelection("MIDDLE"));
		addPopupAction(arrangeMenu, "elementgui.createui.align_bottom", () -> alignSelection("BOTTOM"));
		arrangeMenu.addSeparator();
		addPopupAction(arrangeMenu, "elementgui.createui.distribute_horizontal", () -> distributeSelection(true));
		addPopupAction(arrangeMenu, "elementgui.createui.distribute_vertical", () -> distributeSelection(false));
		arrange.addActionListener(_ -> arrangeMenu.show(arrange, 0, arrange.getHeight()));
		toolbar.add(arrange);
		toolbar.addSeparator();
		toolbar.add(editor.snapOnGrid);
		toolbar.add(Box.createHorizontalGlue());
		toolbar.add(L10N.label("elementgui.createui.preview_resolution"));
		toolbar.add(previewResolution);
		toolbar.addSeparator(new Dimension(8, 1));
		toolbar.add(L10N.label("elementgui.createui.preview_gui_scale"));
		toolbar.add(previewGuiScale);
		toolbar.addSeparator(new Dimension(8, 1));
		toolbar.add(previewLogicalSize);
		return toolbar;
	}

	private JPanel createPreviewElementsPanel() {
		JPanel entries = new JPanel();
		entries.setOpaque(false);
		entries.setLayout(new BoxLayout(entries, BoxLayout.Y_AXIS));
		List<JButton> buttons = new ArrayList<>();
		buttons.add(createPaletteButton("button", "addbutton", L10N.t("elementgui.gui.add_button")));
		buttons.add(createPaletteButton("text_button", "addbutton",
				L10N.t("elementgui.createui.preview_add_text_button")));
		buttons.add(createPaletteButton("text_label", "addlabel", L10N.t("elementgui.gui.add_text_label")));
		buttons.add(createPaletteButton("image", "addimage", L10N.t("elementgui.gui.add_image")));
		for (WYSIWYGComponentRegistration<?> registration : WYSIWYGEditor.COMPONENT_REGISTRY) {
			if (net.mcreator.element.parts.gui.Slot.class.isAssignableFrom(registration.component())
					|| Set.of("button", "text_label", "image").contains(registration.machineName()))
				continue;
			buttons.add(createPaletteButton(registration.machineName(), registration.icon(),
					L10N.t("elementgui.gui.add_" + registration.machineName())));
		}

		Runnable rebuild = () -> {
			String query = String.valueOf(entries.getClientProperty("query")).trim().toLowerCase(Locale.ROOT);
			entries.removeAll();
			for (JButton button : buttons) {
				String searchable = String.valueOf(button.getClientProperty("searchable"));
				if (query.isEmpty() || searchable.contains(query)) {
					button.setAlignmentX(Component.LEFT_ALIGNMENT);
					entries.add(button);
					entries.add(Box.createVerticalStrut(4));
				}
			}
			entries.revalidate();
			entries.repaint();
		};

		JTextField search = new JTextField();
		search.putClientProperty("JTextField.placeholderText", L10N.t("elementgui.createui.preview_search_elements"));
		search.getDocument().addDocumentListener(new DocumentListener() {
			private void refresh() {
				entries.putClientProperty("query", search.getText());
				rebuild.run();
			}

			@Override public void insertUpdate(DocumentEvent event) {
				refresh();
			}

			@Override public void removeUpdate(DocumentEvent event) {
				refresh();
			}

			@Override public void changedUpdate(DocumentEvent event) {
				refresh();
			}
		});
		entries.putClientProperty("query", "");
		rebuild.run();

		JScrollPane scroll = new JScrollPane(entries);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		JButton library = studioButton("elementgui.createui.component_insert", this::insertFromComponentLibrary);
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
		panel.add("North", PanelUtils.column(5, L10N.label("elementgui.createui.preview_elements"), search));
		panel.add("Center", scroll);
		panel.add("South", library);
		panel.setPreferredSize(new Dimension(210, 10));
		return panel;
	}

	private JButton createPaletteButton(String machineName, String iconName, String label) {
		JButton button = new JButton(label, UIRES.get("wysiwyg_editor." + iconName));
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		button.setPreferredSize(new Dimension(190, 38));
		button.putClientProperty("searchable", (label + " " + machineName).toLowerCase(Locale.ROOT));
		button.addActionListener(_ -> addPreviewElement(machineName, defaultPreviewInsertionPoint()));
		button.setTransferHandler(new PaletteTransferHandler(machineName));
		MouseAdapter dragStarter = new MouseAdapter() {
			private boolean dragging;

			@Override public void mousePressed(MouseEvent event) {
				dragging = false;
			}

			@Override public void mouseDragged(MouseEvent event) {
				if (!dragging) {
					dragging = true;
					button.getTransferHandler().exportAsDrag(button, event, TransferHandler.COPY);
				}
			}

			@Override public void mouseReleased(MouseEvent event) {
				dragging = false;
			}
		};
		button.addMouseListener(dragStarter);
		button.addMouseMotionListener(dragStarter);
		return button;
	}

	private JPanel createPreviewLayersPanel() {
		previewLayerList = new JList<>(editor.components);
		previewLayerList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		previewLayerList.setSelectionModel(editor.list.getSelectionModel());
		previewLayerList.setCellRenderer((list, value, index, selected, focused) -> {
			JLabel label = new JLabel((value.visible ? "" : "[hidden] ") + value.getName()
					+ (value.locked ? " [locked]" : ""));
			label.setOpaque(true);
			label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
			label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
			label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
			return label;
		});
		JScrollPane scroll = new JScrollPane(previewLayerList);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(18);
		scroll.getHorizontalScrollBar().setUnitIncrement(18);

		JToolBar controls = new JToolBar();
		controls.setFloatable(false);
		controls.setOpaque(false);
		controls.add(studioButton("elementgui.createui.front", () -> changeSelectionLayer(true)));
		controls.add(studioButton("elementgui.createui.back", () -> changeSelectionLayer(false)));
		controls.add(studioButton("elementgui.createui.show_hide", this::toggleSelectionVisibility));
		controls.add(studioButton("elementgui.createui.locked", this::toggleSelectionLock));
		controls.add(studioButton("elementgui.createui.context_delete", this::deletePreviewSelection));
		JScrollPane controlsScroll = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		controlsScroll.setName("createui.layers.controls.scroll");
		controlsScroll.setBorder(BorderFactory.createEmptyBorder());
		controlsScroll.getHorizontalScrollBar().setUnitIncrement(18);
		controlsScroll.setPreferredSize(new Dimension(10,
				controls.getPreferredSize().height + controlsScroll.getHorizontalScrollBar().getPreferredSize().height + 3));

		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		panel.add("North", controlsScroll);
		panel.add("Center", scroll);
		return panel;
	}

	private Point defaultPreviewInsertionPoint() {
		return new Point(WYSIWYG.W / 2 - 40, WYSIWYG.H / 2 - 10);
	}

	private boolean addPreviewElement(String machineName, Point position) {
		Set<String> usedNames = editor.getComponentList().stream().map(GUIComponent::getName)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		switch (machineName) {
		case "button" -> addPreviewButton(false, position);
		case "text_button" -> addPreviewButton(true, position);
		case "text_label" -> addPreviewLabel(position);
		case "image" -> {
			return addPreviewImages(position);
		}
		case "checkbox" -> addPreviewComponent(new Checkbox(uniqueComponentName("checkbox", usedNames), position.x,
				position.y, "Checkbox", null));
		case "text_input" -> addPreviewComponent(new TextField(uniqueComponentName("text_input", usedNames),
				position.x, position.y, 120, 20, "Text"));
		case "tooltip" -> addPreviewComponent(new Tooltip(uniqueComponentName("tooltip", usedNames), position.x,
				position.y, 90, 24, new StringProcedure(null, "Tooltip"), null));
		default -> {
			return addPreviewComponentWithDialog(machineName, position);
		}
		}
		return true;
	}

	private boolean addPreviewComponentWithDialog(String machineName, Point position) {
		WYSIWYGComponentRegistration<?> registration = WYSIWYGEditor.COMPONENT_REGISTRY.stream()
				.filter(candidate -> machineName.equals(candidate.machineName())).findFirst().orElse(null);
		if (registration == null || registration.editor() == null)
			return false;
		Set<UUID> before = editor.getComponentList().stream().map(component -> component.uuid)
				.collect(java.util.stream.Collectors.toSet());
		recordHistoryCheckpoint();
		try {
			registration.editor().getConstructor(WYSIWYGEditor.class, registration.component()).newInstance(editor, null);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		} finally {
			editor.editor.cancelMoveMode();
		}
		List<GUIComponent> added = editor.getComponentList().stream().filter(component -> !before.contains(component.uuid))
				.toList();
		if (added.isEmpty())
			return false;
		for (int index = 0; index < added.size(); index++) {
			added.get(index).x = position.x + index * 8;
			added.get(index).y = position.y + index * 8;
		}
		selectPreviewComponents(added);
		refreshStudioCanvas();
		recordHistoryCheckpoint();
		return true;
	}

	private void toggleSelectionLock() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty())
			return;
		recordHistoryCheckpoint();
		boolean locked = selected.stream().anyMatch(component -> !component.locked);
		selected.forEach(component -> component.locked = locked);
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private static final class PaletteTransferHandler extends TransferHandler {
		private final String machineName;

		private PaletteTransferHandler(String machineName) {
			this.machineName = machineName;
		}

		@Override protected Transferable createTransferable(JComponent component) {
			return new StringSelection(machineName);
		}

		@Override public int getSourceActions(JComponent component) {
			return COPY;
		}
	}

	private JToolBar createStudioToolbar() {
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);
		toolbar.setOpaque(false);

		undoButton.addActionListener(_ -> undoStudioChange());
		redoButton.addActionListener(_ -> redoStudioChange());
		undoButton.setEnabled(false);
		redoButton.setEnabled(false);
		toolbar.add(undoButton);
		toolbar.add(redoButton);
		toolbar.addSeparator();
		toolbar.add(studioButton("elementgui.createui.auto_size", this::configureAutoSizeForSelection));
		toolbar.add(studioButton("elementgui.createui.component_save", this::saveSelectionToComponentLibrary));
		toolbar.add(studioButton("elementgui.createui.component_insert", this::insertFromComponentLibrary));

		if (RemarkStudioPreferences.isAdvancedMode()) {
			toolbar.addSeparator();
			toolbar.add(studioButton("elementgui.createui.group", this::groupSelection));
			toolbar.add(studioButton("elementgui.createui.ungroup", this::ungroupSelection));

			JButton align = L10N.button("elementgui.createui.align");
			JPopupMenu alignMenu = new JPopupMenu();
			addPopupAction(alignMenu, "elementgui.createui.align_left", () -> alignSelection("LEFT"));
			addPopupAction(alignMenu, "elementgui.createui.align_center", () -> alignSelection("CENTER"));
			addPopupAction(alignMenu, "elementgui.createui.align_right", () -> alignSelection("RIGHT"));
			alignMenu.addSeparator();
			addPopupAction(alignMenu, "elementgui.createui.align_top", () -> alignSelection("TOP"));
			addPopupAction(alignMenu, "elementgui.createui.align_middle", () -> alignSelection("MIDDLE"));
			addPopupAction(alignMenu, "elementgui.createui.align_bottom", () -> alignSelection("BOTTOM"));
			alignMenu.addSeparator();
			addPopupAction(alignMenu, "elementgui.createui.distribute_horizontal", () -> distributeSelection(true));
			addPopupAction(alignMenu, "elementgui.createui.distribute_vertical", () -> distributeSelection(false));
			align.addActionListener(_ -> alignMenu.show(align, 0, align.getHeight()));
			toolbar.add(align);

			toolbar.add(studioButton("elementgui.createui.front", () -> changeSelectionLayer(true)));
			toolbar.add(studioButton("elementgui.createui.back", () -> changeSelectionLayer(false)));
			toolbar.add(studioButton("elementgui.createui.show_hide", this::toggleSelectionVisibility));
			toolbar.addSeparator();
			toolbar.add(studioButton("elementgui.createui.animation", this::configureAnimationForSelection));
			toolbar.add(studioButton("elementgui.createui.button_style", this::configureSelectedButtonStyle));
		}
		return toolbar;
	}

	private JButton studioButton(String key, Runnable action) {
		JButton button = L10N.button(key);
		button.addActionListener(_ -> action.run());
		return button;
	}

	private void addPopupAction(JPopupMenu menu, String key, Runnable action) {
		JMenuItem item = new JMenuItem(L10N.t(key));
		item.addActionListener(_ -> action.run());
		menu.add(item);
	}

	private void addPopupAction(JMenu menu, String key, Runnable action) {
		JMenuItem item = new JMenuItem(L10N.t(key));
		item.addActionListener(_ -> action.run());
		menu.add(item);
	}

	private JPanel createSelectionInspector() {
		JPanel inspector = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
		inspector.setOpaque(false);
		inspector.add(new JLabel(L10N.t("elementgui.createui.selection_properties")));
		inspector.add(L10N.label("elementgui.createui.position_x"));
		inspector.add(inspectorX);
		inspector.add(L10N.label("elementgui.createui.position_y"));
		inspector.add(inspectorY);
		inspector.add(L10N.label("elementgui.createui.layer"));
		inspector.add(inspectorLayer);
		inspector.add(L10N.label("elementgui.createui.anchor"));
		inspector.add(inspectorAnchor);
		inspector.add(inspectorVisible);
		inspector.add(inspectorLocked);
		inspector.add(studioButton("elementgui.createui.apply_properties", this::applySelectionInspector));
		return inspector;
	}

	private JPanel createPreviewSelectionPanel() {
		previewSelectionName.setFont(previewSelectionName.getFont().deriveFont(Font.BOLD));
		previewTextEditor.setLineWrap(true);
		previewTextEditor.setWrapStyleWord(true);
		JScrollPane textScroll = new JScrollPane(previewTextEditor);
		textScroll.setPreferredSize(new Dimension(290, 92));

		JPanel geometry = new JPanel(new GridLayout(7, 2, 8, 6));
		geometry.setOpaque(false);
		geometry.add(L10N.label("elementgui.createui.preview_name"));
		geometry.add(previewNameEditor);
		geometry.add(L10N.label("elementgui.createui.position_x"));
		geometry.add(previewX);
		geometry.add(L10N.label("elementgui.createui.position_y"));
		geometry.add(previewY);
		geometry.add(L10N.label("elementgui.createui.resize_width"));
		geometry.add(previewWidth);
		geometry.add(L10N.label("elementgui.createui.resize_height"));
		geometry.add(previewHeight);
		geometry.add(L10N.label("elementgui.createui.layer"));
		geometry.add(previewLayer);
		geometry.add(L10N.label("elementgui.createui.anchor"));
		geometry.add(previewAnchor);

		JButton add = studioButton("elementgui.createui.preview_add",
				() -> showPreviewAddMenu(previewPanel, 12, 12, null));
		JButton edit = studioButton("elementgui.createui.context_edit", this::editSelectedComponent);
		JButton style = studioButton("elementgui.createui.button_style", this::configureSelectedButtonStyle);
		JButton duplicate = studioButton("elementgui.createui.context_copy", this::duplicatePreviewSelection);
		JButton delete = studioButton("elementgui.createui.context_delete", this::deletePreviewSelection);

		JPanel commands = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		commands.setOpaque(false);
		commands.add(add);
		commands.add(edit);
		commands.add(style);
		commands.add(duplicate);
		commands.add(delete);

		JPanel panel = new JPanel();
		panel.setOpaque(false);
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JPanel states = PanelUtils.join(FlowLayout.LEFT, previewVisible, previewLocked, previewAutoSize);
		for (JComponent component : new JComponent[] { previewSelectionName, geometry,
				L10N.label("elementgui.createui.preview_text"), textScroll,
				PanelUtils.join(FlowLayout.LEFT, L10N.label("elementgui.createui.preview_color"),
						createColorPicker(previewColor, "elementgui.createui.preview_color")), states, commands }) {
			component.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(component);
			panel.add(Box.createVerticalStrut(8));
		}
		updatePreviewInspector();
		return panel;
	}

	private void configurePreviewInspectorListeners() {
		previewNameEditor.addActionListener(_ -> applyPreviewNameChange());
		previewNameEditor.addFocusListener(new FocusAdapter() {
			@Override public void focusLost(FocusEvent event) {
				applyPreviewNameChange();
			}
		});
		previewTextEditor.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent event) {
				applyPreviewTextChange();
			}

			@Override public void removeUpdate(DocumentEvent event) {
				applyPreviewTextChange();
			}

			@Override public void changedUpdate(DocumentEvent event) {
				applyPreviewTextChange();
			}
		});
		previewColor.getDocument().addDocumentListener(new DocumentListener() {
			@Override public void insertUpdate(DocumentEvent event) {
				applyPreviewColorChange();
			}

			@Override public void removeUpdate(DocumentEvent event) {
				applyPreviewColorChange();
			}

			@Override public void changedUpdate(DocumentEvent event) {
				applyPreviewColorChange();
			}
		});
		previewTextEditor.addFocusListener(new FocusAdapter() {
			@Override public void focusGained(FocusEvent event) {
				recordHistoryCheckpoint();
			}

			@Override public void focusLost(FocusEvent event) {
				recordHistoryCheckpoint();
			}
		});
		for (JSpinner spinner : new JSpinner[] { previewX, previewY, previewWidth, previewHeight })
			spinner.addChangeListener(_ -> applyPreviewGeometryChange());
		previewLayer.addChangeListener(_ -> applyPreviewMetadataChange());
		previewAnchor.addActionListener(_ -> applyPreviewMetadataChange());
		previewVisible.addActionListener(_ -> applyPreviewMetadataChange());
		previewLocked.addActionListener(_ -> applyPreviewMetadataChange());
		previewAutoSize.addActionListener(_ -> applyPreviewAutoSize());
	}

	private void updatePreviewInspector() {
		if (updatingPreviewInspector || editor == null)
			return;
		updatingPreviewInspector = true;
		try {
			List<GUIComponent> selected = selectedComponents();
			boolean single = selected.size() == 1;
			GUIComponent component = single ? selected.get(0) : null;
			previewSelectionName.setText(selected.isEmpty() ? L10N.t("elementgui.createui.preview_no_selection") :
					selected.size() == 1 ? L10N.t("elementgui.createui.preview_selected", component.getName()) :
					L10N.t("elementgui.createui.preview_selected_count", selected.size()));

			previewNameEditor.setEnabled(single && !component.locked);
			previewNameEditor.setText(single ? component.getName() : "");
			boolean editableText = component != null && !component.locked && previewText(component) != null;
			previewTextEditor.setEnabled(editableText);
			previewTextEditor.setText(editableText ? previewText(component) : "");
			boolean colorEditable = component instanceof Button || component instanceof Label;
			previewColor.setEnabled(single && colorEditable && !component.locked);
			if (component instanceof Button button)
				previewColor.setText(button.normalTextColor);
			else if (component instanceof Label label)
				previewColor.setText(colorToHex(label.color));
			else
				previewColor.setText("");
			previewX.setEnabled(single && !component.locked);
			previewY.setEnabled(single && !component.locked);
			if (single) {
				previewX.setValue(component.x);
				previewY.setValue(component.y);
			}

			boolean resizable = component instanceof SizedComponent || component instanceof net.mcreator.element.parts.gui.Image;
			previewWidth.setEnabled(single && resizable && !component.locked);
			previewHeight.setEnabled(single && resizable && !component.locked);
			if (single) {
				previewWidth.setValue(Math.max(1, component.getWidth(mcreator.getWorkspace())));
				previewHeight.setValue(Math.max(1, component.getHeight(mcreator.getWorkspace())));
			}
			previewLayer.setEnabled(single && !component.locked);
			previewAnchor.setEnabled(single && !component.locked);
			previewVisible.setEnabled(single);
			previewLocked.setEnabled(single);
			if (single) {
				previewLayer.setValue(component.layer);
				previewAnchor.setSelectedItem(component.anchorPoint != null ? component.anchorPoint : previewAnchor.getItemAt(0));
				previewVisible.setSelected(component.visible);
				previewLocked.setSelected(component.locked);
				if (component instanceof Button)
					actionButton.setSelectedItem(component.getName());
			}
			previewAutoSize.setEnabled(component instanceof Button && !component.locked);
			previewAutoSize.setSelected(component instanceof Button button && button.autoSize);
		} finally {
			updatingPreviewInspector = false;
		}
	}

	private void applyPreviewNameChange() {
		if (updatingPreviewInspector)
			return;
		List<GUIComponent> selected = selectedComponents();
		if (selected.size() != 1 || selected.get(0).locked)
			return;
		GUIComponent component = selected.get(0);
		String oldName = component.getName();
		String requested = RegistryNameFixer.fix(previewNameEditor.getText());
		if (requested == null || requested.isBlank()) {
			updatePreviewInspector();
			return;
		}
		Set<String> used = editor.getComponentList().stream().filter(candidate -> candidate != component)
				.map(GUIComponent::getName).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		String newName = uniqueComponentName(requested, used);
		if (oldName.equals(newName))
			return;
		recordHistoryCheckpoint();
		if (component instanceof Button button) {
			button.name = newName;
			button.remarkNameOverride = null;
		} else if (component instanceof Label label) {
			label.name = newName;
			label.remarkNameOverride = null;
		} else {
			component.remarkNameOverride = newName;
		}
		buttonActions.stream().filter(action -> oldName.equals(action.buttonName))
				.forEach(action -> action.buttonName = newName);
		refreshActionButtonList();
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private void applyPreviewMetadataChange() {
		if (updatingPreviewInspector)
			return;
		List<GUIComponent> selected = selectedComponents();
		if (selected.size() != 1)
			return;
		recordHistoryCheckpoint();
		GUIComponent component = selected.get(0);
		component.layer = ((Number) previewLayer.getValue()).intValue();
		component.anchorPoint = previewAnchor.getSelectedItem() instanceof GUIComponent.AnchorPoint anchor ? anchor : null;
		component.visible = previewVisible.isSelected();
		component.locked = previewLocked.isSelected();
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	@Nullable private static String previewText(GUIComponent component) {
		if (component instanceof Button button)
			return button.text;
		if (component instanceof Label label)
			return label.text.getName() == null ? label.text.getFixedValue() : null;
		if (component instanceof Checkbox checkbox)
			return checkbox.text;
		if (component instanceof TextField textField)
			return textField.placeholder;
		if (component instanceof Tooltip tooltip)
			return tooltip.text.getName() == null ? tooltip.text.getFixedValue() : null;
		return null;
	}

	private void applyPreviewTextChange() {
		if (updatingPreviewInspector)
			return;
		List<GUIComponent> selected = selectedComponents();
		if (selected.size() != 1)
			return;
		GUIComponent component = selected.get(0);
		String text = previewTextEditor.getText();
		if (!setPreviewComponentText(component, text))
			return;
		if (component instanceof Button button && button.autoSize) {
			updatingPreviewInspector = true;
			previewWidth.setValue(Math.max(1, button.width));
			previewHeight.setValue(Math.max(1, button.height));
			updatingPreviewInspector = false;
		}
		editor.list.repaint();
		editor.editor.repaint();
		previewPanel.repaint();
		if (previewLayerList != null)
			previewLayerList.repaint();
	}

	private boolean setPreviewComponentText(GUIComponent component, String text) {
		if (component instanceof Button button) {
			button.text = text;
			if (button.autoSize) {
				applyButtonAutoSize(button);
			}
		} else if (component instanceof Label label && label.text.getName() == null) {
			label.text = new StringProcedure(null, text);
		} else if (component instanceof Checkbox checkbox) {
			checkbox.text = text;
		} else if (component instanceof TextField textField) {
			textField.placeholder = text;
		} else if (component instanceof Tooltip tooltip && tooltip.text.getName() == null) {
			tooltip.text = new StringProcedure(null, text);
		} else {
			return false;
		}
		return true;
	}

	private void applyPreviewColorChange() {
		if (updatingPreviewInspector || !isColor(previewColor.getText()))
			return;
		List<GUIComponent> selected = selectedComponents();
		if (selected.size() != 1 || selected.get(0).locked)
			return;
		GUIComponent component = selected.get(0);
		String color = normalizeColor(previewColor.getText());
		if (component instanceof Button button)
			button.normalTextColor = color;
		else if (component instanceof Label label)
			label.color = parseColor(color, label.color);
		else
			return;
		editor.editor.repaint();
		previewPanel.repaint();
	}

	private void applyPreviewGeometryChange() {
		if (updatingPreviewInspector)
			return;
		List<GUIComponent> selected = selectedComponents();
		if (selected.size() != 1 || selected.get(0).locked)
			return;
		recordHistoryCheckpoint();
		GUIComponent component = selected.get(0);
		component.x = ((Number) previewX.getValue()).intValue();
		component.y = ((Number) previewY.getValue()).intValue();
		int width = ((Number) previewWidth.getValue()).intValue();
		int height = ((Number) previewHeight.getValue()).intValue();
		if (component instanceof SizedComponent sized) {
			sized.width = width;
			sized.height = height;
			if (sized instanceof Button button && button.autoSize) {
				button.autoSize = false;
				previewAutoSize.setSelected(false);
			}
		} else if (component instanceof net.mcreator.element.parts.gui.Image image) {
			image.customWidth = width;
			image.customHeight = height;
			image.use1Xscale = false;
		}
		editor.list.repaint();
		editor.editor.repaint();
		previewPanel.repaint();
		updateSelectionInspector();
		recordHistoryCheckpoint();
	}

	private void applyPreviewAutoSize() {
		if (updatingPreviewInspector)
			return;
		List<GUIComponent> selected = selectedComponents();
		if (selected.size() != 1 || !(selected.get(0) instanceof Button button) || button.locked)
			return;
		button.autoSize = previewAutoSize.isSelected();
		if (button.autoSize)
			applyButtonAutoSize(button);
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private Dimension selectedPreviewResolution() {
		String value = String.valueOf(previewResolution.getSelectedItem());
		Matcher matcher = Pattern.compile("(\\d+)\\s*x\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(value);
		if (matcher.find())
			return new Dimension(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
		return new Dimension(WYSIWYG.W, WYSIWYG.H);
	}

	private Dimension selectedPreviewLogicalResolution() {
		return logicalPreviewResolution(selectedPreviewResolution(), selectedPreviewGuiScale());
	}

	private int selectedPreviewGuiScale() {
		return previewGuiScale.getSelectedItem() instanceof Integer scale ? scale : 3;
	}

	static Dimension logicalPreviewResolution(Dimension physical, int guiScale) {
		int scale = Math.max(1, guiScale);
		return new Dimension((physical.width + scale - 1) / scale, (physical.height + scale - 1) / scale);
	}

	private void refreshPreviewScale() {
		Dimension logical = selectedPreviewLogicalResolution();
		previewLogicalSize.setText(L10N.t("elementgui.createui.preview_logical_size", logical.width, logical.height));
		if (previewPanel != null)
			previewPanel.repaint();
	}

	private void updateSelectionInspector() {
		if (updatingInspector)
			return;
		List<GUIComponent> selected = selectedComponents();
		boolean hasSelection = !selected.isEmpty();
		for (JComponent component : new JComponent[] { inspectorX, inspectorY, inspectorLayer, inspectorAnchor,
				inspectorVisible, inspectorLocked })
			component.setEnabled(hasSelection);
		if (!hasSelection)
			return;

		updatingInspector = true;
		GUIComponent primary = selected.get(selected.size() - 1);
		inspectorX.setValue(primary.x);
		inspectorY.setValue(primary.y);
		inspectorX.setEnabled(selected.size() == 1);
		inspectorY.setEnabled(selected.size() == 1);
		inspectorLayer.setValue(primary.layer);
		inspectorAnchor.setSelectedItem(primary.anchorPoint != null ? primary.anchorPoint : inspectorAnchor.getItemAt(0));
		inspectorVisible.setSelected(selected.stream().allMatch(component -> component.visible));
		inspectorLocked.setSelected(selected.stream().allMatch(component -> component.locked));
		updatingInspector = false;
	}

	private void applySelectionInspector() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty())
			return;
		if (selected.size() == 1) {
			selected.get(0).x = ((Number) inspectorX.getValue()).intValue();
			selected.get(0).y = ((Number) inspectorY.getValue()).intValue();
		}
		for (GUIComponent component : selected) {
			component.layer = ((Number) inspectorLayer.getValue()).intValue();
			component.anchorPoint = inspectorAnchor.getSelectedItem() instanceof GUIComponent.AnchorPoint anchor ? anchor : null;
			component.visible = inspectorVisible.isSelected();
			component.locked = inspectorLocked.isSelected();
		}
		refreshStudioCanvas();
	}

	private List<GUIComponent> selectedComponents() {
		return new ArrayList<>(editor.list.getSelectedValuesList());
	}

	private void groupSelection() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.size() < 2) {
			showSelectionRequired(2);
			return;
		}
		String suggested = "group_" + (editor.getComponentList().stream().map(component -> component.groupName)
				.filter(name -> name != null && !name.isBlank()).distinct().count() + 1);
		String group = JOptionPane.showInputDialog(mcreator, L10N.t("elementgui.createui.group_name"), suggested);
		if (group == null || group.isBlank())
			return;
		String normalized = RegistryNameFixer.fix(group);
		selected.forEach(component -> component.groupName = normalized != null && !normalized.isBlank() ? normalized : suggested);
		refreshStudioCanvas();
	}

	private void ungroupSelection() {
		selectedComponents().forEach(component -> component.groupName = "");
		refreshStudioCanvas();
	}

	private void alignSelection(String alignment) {
		List<GUIComponent> selected = selectedComponents().stream().filter(component -> !component.locked).toList();
		if (selected.size() < 2) {
			showSelectionRequired(2);
			return;
		}
		int left = selected.stream().mapToInt(component -> component.x).min().orElse(0);
		int top = selected.stream().mapToInt(component -> component.y).min().orElse(0);
		int right = selected.stream().mapToInt(component -> component.x + component.getWidth(mcreator.getWorkspace()))
				.max().orElse(0);
		int bottom = selected.stream().mapToInt(component -> component.y + component.getHeight(mcreator.getWorkspace()))
				.max().orElse(0);
		for (GUIComponent component : selected) {
			switch (alignment) {
			case "LEFT" -> component.x = left;
			case "CENTER" -> component.x = (left + right - component.getWidth(mcreator.getWorkspace())) / 2;
			case "RIGHT" -> component.x = right - component.getWidth(mcreator.getWorkspace());
			case "TOP" -> component.y = top;
			case "MIDDLE" -> component.y = (top + bottom - component.getHeight(mcreator.getWorkspace())) / 2;
			case "BOTTOM" -> component.y = bottom - component.getHeight(mcreator.getWorkspace());
			default -> {
			}
			}
		}
		refreshStudioCanvas();
	}

	private void distributeSelection(boolean horizontal) {
		List<GUIComponent> selected = selectedComponents().stream().filter(component -> !component.locked)
				.sorted(Comparator.comparingInt(component -> horizontal ? component.x : component.y)).toList();
		if (selected.size() < 3) {
			showSelectionRequired(3);
			return;
		}
		GUIComponent first = selected.get(0);
		GUIComponent last = selected.get(selected.size() - 1);
		int start = horizontal ? first.x : first.y;
		int end = horizontal ? last.x : last.y;
		for (int i = 1; i < selected.size() - 1; i++) {
			int position = Math.round(start + (end - start) * (i / (float) (selected.size() - 1)));
			if (horizontal)
				selected.get(i).x = position;
			else
				selected.get(i).y = position;
		}
		refreshStudioCanvas();
	}

	private void changeSelectionLayer(boolean front) {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty()) {
			showSelectionRequired(1);
			return;
		}
		int target = front ? editor.getComponentList().stream().mapToInt(component -> component.layer).max().orElse(0) + 1 :
				editor.getComponentList().stream().mapToInt(component -> component.layer).min().orElse(0) - 1;
		selected.forEach(component -> component.layer = target);
		refreshStudioCanvas();
	}

	private void toggleSelectionVisibility() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty()) {
			showSelectionRequired(1);
			return;
		}
		boolean visible = selected.stream().anyMatch(component -> !component.visible);
		selected.forEach(component -> component.visible = visible);
		refreshStudioCanvas();
	}

	private void showSelectionRequired(int count) {
		JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.select_components", count),
				L10N.t("elementgui.createui.selection_properties"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void refreshStudioCanvas() {
		editor.list.repaint();
		editor.editor.repaint();
		previewPanel.repaint();
		if (previewLayerList != null)
			previewLayerList.repaint();
		updateSelectionInspector();
		updatePreviewInspector();
		refreshIssues();
	}

	private JPanel createTimelinePage() {
		JPanel controls = new JPanel(new GridLayout(6, 2, 8, 7));
		controls.setOpaque(false);
		controls.add(L10N.label("elementgui.createui.timeline_component"));
		controls.add(timelineComponent);
		controls.add(L10N.label("elementgui.createui.animation_type"));
		controls.add(animationType);
		controls.add(L10N.label("elementgui.createui.animation_delay"));
		controls.add(animationDelay);
		controls.add(L10N.label("elementgui.createui.animation_duration"));
		controls.add(animationDuration);
		controls.add(L10N.label("elementgui.createui.animation_loop"));
		controls.add(animationLoop);
		JButton refresh = studioButton("elementgui.createui.refresh_components", this::refreshTimelineComponents);
		JButton apply = studioButton("elementgui.createui.apply_animation", this::applyTimelineAnimation);
		controls.add(refresh);
		controls.add(apply);

		timelineComponent.addActionListener(_ -> loadTimelineAnimation());
		JPanel timeline = new JPanel() {
			@Override protected void paintComponent(Graphics graphics) {
				super.paintComponent(graphics);
				Graphics2D g = (Graphics2D) graphics.create();
				int left = 32;
				int y = getHeight() / 2;
				int width = Math.max(1, getWidth() - 64);
				g.setColor(new Color(255, 255, 255, 60));
				g.drawLine(left, y, left + width, y);
				int delay = ((Number) animationDelay.getValue()).intValue();
				int duration = ((Number) animationDuration.getValue()).intValue();
				int total = Math.max(1, delay + duration);
				int start = left + Math.round(width * delay / (float) total);
				g.setColor(new Color(233, 30, 121, 190));
				g.fillRect(start, y - 12, Math.max(4, left + width - start), 24);
				g.setColor(Color.WHITE);
				g.drawString("0", left, y + 32);
				g.drawString(total + " ticks", left + width - 48, y + 32);
				g.dispose();
			}
		};
		timeline.setOpaque(false);
		timelineTimer = new javax.swing.Timer(120, _ -> timeline.repaint());
		timelineTimer.start();

		JPanel page = new JPanel(new BorderLayout(18, 0));
		page.setOpaque(false);
		JPanel side = PanelUtils.column(10, controls);
		side.setPreferredSize(new Dimension(390, 10));
		page.add("West", side);
		page.add("Center", timeline);
		refreshTimelineComponents();
		return page;
	}

	private void refreshTimelineComponents() {
		if (updatingTimelineComponents)
			return;
		updatingTimelineComponents = true;
		try {
			GUIComponent selected = (GUIComponent) timelineComponent.getSelectedItem();
			UUID selectedUUID = selected != null ? selected.uuid : null;
			if (timelineComponent.isPopupVisible())
				timelineComponent.setPopupVisible(false);
			GUIComponent[] components = editor.getComponentList().stream().toArray(GUIComponent[]::new);
			timelineComponent.setModel(new DefaultComboBoxModel<>(components));
			if (selectedUUID != null) {
				for (int i = 0; i < timelineComponent.getItemCount(); i++) {
					if (selectedUUID.equals(timelineComponent.getItemAt(i).uuid)) {
						timelineComponent.setSelectedIndex(i);
						break;
					}
				}
			}
			int selectedIndex = editor.components.indexOf(editor.list.getSelectedValue());
			if (selectedIndex >= 0 && selectedIndex < timelineComponent.getItemCount())
				timelineComponent.setSelectedIndex(selectedIndex);
		} finally {
			updatingTimelineComponents = false;
		}
		loadTimelineAnimation();
	}

	private void loadTimelineAnimation() {
		if (updatingTimelineComponents)
			return;
		GUIComponent component = (GUIComponent) timelineComponent.getSelectedItem();
		if (component == null)
			return;
		animationType.setSelectedItem(component.animationType != null ? component.animationType : "NONE");
		animationDelay.setValue(Math.max(0, component.animationDelayTicks));
		animationDuration.setValue(Math.max(1, component.animationDurationTicks));
		animationLoop.setSelected(component.animationLoop);
	}

	private void applyTimelineAnimation() {
		GUIComponent component = (GUIComponent) timelineComponent.getSelectedItem();
		if (component == null)
			return;
		applyAnimation(component);
		refreshStudioCanvas();
	}

	private void configureAnimationForSelection() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty()) {
			showSelectionRequired(1);
			return;
		}
		GUIComponent primary = selected.get(selected.size() - 1);
		timelineComponent.setSelectedItem(primary);
		loadTimelineAnimation();
		int result = JOptionPane.showConfirmDialog(mcreator,
				PanelUtils.column(8, PanelUtils.join(FlowLayout.LEFT, L10N.label("elementgui.createui.animation_type"), animationType),
						PanelUtils.join(FlowLayout.LEFT, L10N.label("elementgui.createui.animation_delay"), animationDelay),
						PanelUtils.join(FlowLayout.LEFT, L10N.label("elementgui.createui.animation_duration"), animationDuration),
						animationLoop), L10N.t("elementgui.createui.animation"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
			return;
		for (GUIComponent component : selected)
			applyAnimation(component);
		refreshStudioCanvas();
	}

	private void configureAutoSizeForSelection() {
		List<Button> buttons = selectedComponents().stream().filter(Button.class::isInstance).map(Button.class::cast)
				.toList();
		if (buttons.isEmpty()) {
			showSelectionRequired(1);
			return;
		}

		Button source = buttons.get(buttons.size() - 1);
		JCheckBox enabled = L10N.checkbox("elementgui.createui.auto_size_enabled");
		enabled.setSelected(source.autoSize);
		JSpinner paddingX = new JSpinner(new SpinnerNumberModel(Math.max(0, source.autoSizePaddingX), 0, 256, 1));
		JSpinner paddingY = new JSpinner(new SpinnerNumberModel(Math.max(0, source.autoSizePaddingY), 0, 256, 1));
		JSpinner minWidth = new JSpinner(new SpinnerNumberModel(Math.max(1, source.autoSizeMinWidth), 1, 4096, 1));
		JSpinner minHeight = new JSpinner(new SpinnerNumberModel(Math.max(1, source.autoSizeMinHeight), 1, 4096, 1));
		JSpinner maxWidth = new JSpinner(new SpinnerNumberModel(Math.max(0, source.autoSizeMaxWidth), 0, 4096, 1));
		JSpinner maxHeight = new JSpinner(new SpinnerNumberModel(Math.max(0, source.autoSizeMaxHeight), 0, 4096, 1));

		JPanel fields = new JPanel(new GridLayout(7, 2, 8, 6));
		fields.add(L10N.label("elementgui.createui.auto_size_enabled"));
		fields.add(enabled);
		fields.add(L10N.label("elementgui.createui.auto_size_padding_x"));
		fields.add(paddingX);
		fields.add(L10N.label("elementgui.createui.auto_size_padding_y"));
		fields.add(paddingY);
		fields.add(L10N.label("elementgui.createui.auto_size_min_width"));
		fields.add(minWidth);
		fields.add(L10N.label("elementgui.createui.auto_size_min_height"));
		fields.add(minHeight);
		fields.add(L10N.label("elementgui.createui.auto_size_max_width"));
		fields.add(maxWidth);
		fields.add(L10N.label("elementgui.createui.auto_size_max_height"));
		fields.add(maxHeight);

		int result = JOptionPane.showConfirmDialog(mcreator, fields, L10N.t("elementgui.createui.auto_size"),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
			return;

		for (Button button : buttons) {
			button.autoSize = enabled.isSelected();
			button.autoSizePaddingX = ((Number) paddingX.getValue()).intValue();
			button.autoSizePaddingY = ((Number) paddingY.getValue()).intValue();
			button.autoSizeMinWidth = ((Number) minWidth.getValue()).intValue();
			button.autoSizeMinHeight = ((Number) minHeight.getValue()).intValue();
			button.autoSizeMaxWidth = ((Number) maxWidth.getValue()).intValue();
			button.autoSizeMaxHeight = ((Number) maxHeight.getValue()).intValue();
			if (button.autoSize)
				applyButtonAutoSize(button);
		}
		refreshStudioCanvas();
	}

	private static void applyButtonAutoSize(Button button) {
		String[] lines = button.text.split("\\R", -1);
		int textWidth = 0;
		for (String line : lines)
			textWidth = Math.max(textWidth,
					(int) Math.ceil(WYSIWYG.fontMC.getStringBounds(line, WYSIWYG.frc).getWidth()));
		int lineHeight = Math.max(1,
				(int) Math.ceil(WYSIWYG.fontMC.getStringBounds("Ag", WYSIWYG.frc).getHeight()));
		int desiredWidth = textWidth + Math.max(0, button.autoSizePaddingX) * 2;
		int desiredHeight = lineHeight * lines.length + Math.max(0, button.autoSizePaddingY) * 2;
		button.width = clampAutoSize(desiredWidth, Math.max(1, button.autoSizeMinWidth), button.autoSizeMaxWidth);
		button.height = clampAutoSize(desiredHeight, Math.max(1, button.autoSizeMinHeight), button.autoSizeMaxHeight);
	}

	private static int clampAutoSize(int value, int minimum, int maximum) {
		int result = Math.max(minimum, value);
		return maximum > 0 ? Math.min(maximum, result) : result;
	}

	private void applyAnimation(GUIComponent component) {
		component.animationType = (String) animationType.getSelectedItem();
		component.animationDelayTicks = ((Number) animationDelay.getValue()).intValue();
		component.animationDurationTicks = ((Number) animationDuration.getValue()).intValue();
		component.animationLoop = animationLoop.isSelected();
	}

	private void configureSelectedButtonStyle() {
		List<Button> buttons = selectedComponents().stream().filter(Button.class::isInstance).map(Button.class::cast)
				.toList();
		if (buttons.isEmpty()) {
			showSelectionRequired(1);
			return;
		}
		Button source = buttons.get(buttons.size() - 1);
		JCheckBox enabled = L10N.checkbox("elementgui.createui.button_enabled");
		enabled.setSelected(source.enabled);
		VTextField normalColor = colorField(source.normalTextColor);
		VTextField hoverColor = colorField(source.hoverTextColor);
		VTextField pressedColor = colorField(source.pressedTextColor);
		VTextField disabledColor = colorField(source.disabledTextColor);
		JSpinner hoverScale = new JSpinner(new SpinnerNumberModel(Math.max(50, source.hoverScalePercent), 50, 200, 1));
		TextureComboBox normalTexture = new TextureComboBox(mcreator, TextureType.SCREEN, true);
		TextureComboBox hoverTexture = new TextureComboBox(mcreator, TextureType.SCREEN, true);
		TextureComboBox pressedTexture = new TextureComboBox(mcreator, TextureType.SCREEN, true);
		TextureComboBox disabledTexture = new TextureComboBox(mcreator, TextureType.SCREEN, true);
		normalTexture.setTextureFromTextureName(source.normalTexture);
		hoverTexture.setTextureFromTextureName(source.hoverTexture);
		pressedTexture.setTextureFromTextureName(source.pressedTexture);
		disabledTexture.setTextureFromTextureName(source.disabledTexture);

		JPanel fields = new JPanel(new GridLayout(10, 2, 8, 6));
		fields.add(L10N.label("elementgui.createui.button_enabled"));
		fields.add(enabled);
		fields.add(L10N.label("elementgui.createui.button_normal_color"));
		fields.add(createColorPicker(normalColor, "elementgui.createui.button_normal_color"));
		fields.add(L10N.label("elementgui.createui.button_hover_color"));
		fields.add(createColorPicker(hoverColor, "elementgui.createui.button_hover_color"));
		fields.add(L10N.label("elementgui.createui.button_pressed_color"));
		fields.add(createColorPicker(pressedColor, "elementgui.createui.button_pressed_color"));
		fields.add(L10N.label("elementgui.createui.button_disabled_color"));
		fields.add(createColorPicker(disabledColor, "elementgui.createui.button_disabled_color"));
		fields.add(L10N.label("elementgui.createui.button_hover_scale"));
		fields.add(hoverScale);
		fields.add(L10N.label("elementgui.createui.button_normal_texture"));
		fields.add(normalTexture);
		fields.add(L10N.label("elementgui.createui.button_hover_texture"));
		fields.add(hoverTexture);
		fields.add(L10N.label("elementgui.createui.button_pressed_texture"));
		fields.add(pressedTexture);
		fields.add(L10N.label("elementgui.createui.button_disabled_texture"));
		fields.add(disabledTexture);

		JScrollPane scroll = new JScrollPane(fields);
		scroll.setPreferredSize(new Dimension(650, 470));
		int result = JOptionPane.showConfirmDialog(mcreator, scroll, L10N.t("elementgui.createui.button_style"),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
			return;
		for (Button button : buttons) {
			button.enabled = enabled.isSelected();
			button.normalTextColor = normalColor.getText();
			button.hoverTextColor = hoverColor.getText();
			button.pressedTextColor = pressedColor.getText();
			button.disabledTextColor = disabledColor.getText();
			button.hoverScalePercent = ((Number) hoverScale.getValue()).intValue();
			button.normalTexture = normalTexture.getTextureName();
			button.hoverTexture = hoverTexture.getTextureName();
			button.pressedTexture = pressedTexture.getTextureName();
			button.disabledTexture = disabledTexture.getTextureName();
		}
		refreshStudioCanvas();
	}

	private JPanel createStudioToolsPage() {
		JPanel serverGrid = new JPanel(new GridLayout(8, 2, 8, 6));
		serverGrid.setOpaque(false);
		serverGrid.setBorder(BorderFactory.createTitledBorder(L10N.t("elementgui.createui.section_server_status")));
		serverGrid.add(L10N.label("elementgui.createui.server_status_enabled"));
		serverGrid.add(serverStatusEnabled);
		serverGrid.add(L10N.label("elementgui.createui.server_status_address"));
		serverGrid.add(serverStatusAddress);
		serverGrid.add(L10N.label("elementgui.createui.server_status_refresh"));
		serverGrid.add(serverStatusRefreshTicks);
		serverGrid.add(L10N.label("elementgui.createui.server_status_position"));
		serverGrid.add(PanelUtils.join(FlowLayout.LEFT, L10N.label("elementgui.createui.position_x"), serverStatusX,
				L10N.label("elementgui.createui.position_y"), serverStatusY));
		serverGrid.add(L10N.label("elementgui.createui.server_status_motd"));
		serverGrid.add(serverStatusShowMOTD);
		serverGrid.add(L10N.label("elementgui.createui.server_status_players"));
		serverGrid.add(serverStatusShowPlayers);
		serverGrid.add(L10N.label("elementgui.createui.server_status_ping"));
		serverGrid.add(serverStatusShowPing);
		serverGrid.add(L10N.label("elementgui.createui.live_preview_enabled"));
		serverGrid.add(livePreviewEnabled);

		JButton validate = studioButton("elementgui.createui.validate_now", this::refreshIssues);
		JButton savePack = studioButton("elementgui.createui.export_template_pack", this::exportTemplatePack);
		JButton loadPack = studioButton("elementgui.createui.import_template_pack", this::importTemplatePack);
		JButton optimize = studioButton("elementgui.createui.optimize_assets", this::showAssetOptimizationReport);
		JButton sync = studioButton("elementgui.createui.sync_live_preview", this::syncLivePreview);
		JButton run = studioButton("elementgui.createui.run_game", () -> mcreator.getActionRegistry().runClient.doAction());
		JButton restore = studioButton("elementgui.createui.restore_autosave", this::restoreAutosave);

		JPanel tools = new JPanel(new GridLayout(4, 2, 8, 6));
		tools.setOpaque(false);
		tools.setBorder(BorderFactory.createTitledBorder(L10N.t("elementgui.createui.section_tools")));
		tools.add(validate);
		tools.add(sync);
		tools.add(savePack);
		tools.add(loadPack);
		tools.add(optimize);
		tools.add(restore);
		tools.add(run);
		tools.add(autosaveStatus);

		JPanel left = PanelUtils.column(14, serverGrid, tools);
		left.setPreferredSize(new Dimension(470, 10));

		issueList.setVisibleRowCount(16);
		JPanel issues = new JPanel(new BorderLayout(0, 6));
		issues.setOpaque(false);
		issues.setBorder(BorderFactory.createTitledBorder(L10N.t("elementgui.createui.section_issues")));
		issues.add("North", issueSummary);
		issues.add("Center", new JScrollPane(issueList));

		JPanel page = new JPanel(new BorderLayout(18, 0));
		page.setOpaque(false);
		page.add("West", left);
		page.add("Center", issues);
		refreshIssues();
		return page;
	}

	private void installFriendlyOptionRenderers() {
		setFriendlyRenderer(targetScreen, value -> switch (value) {
			case CreateUI.TARGET_MAIN_MENU -> L10N.t("elementgui.createui.option.main_menu");
			case CreateUI.TARGET_PAUSE_MENU -> L10N.t("elementgui.createui.option.pause_menu");
			default -> value;
		});
		setFriendlyRenderer(presetSelector, value -> switch (value) {
			case "Vanilla menu" -> L10N.t("elementgui.createui.option.preset_vanilla");
			case "Pause menu" -> L10N.t("elementgui.createui.option.preset_pause");
			case "Wizard lobby" -> L10N.t("elementgui.createui.option.preset_lobby");
			case "Join server" -> L10N.t("elementgui.createui.option.preset_join");
			case "Open web link" -> L10N.t("elementgui.createui.option.preset_web");
			default -> value;
		});
		setFriendlyRenderer(responsiveMode, value -> switch (value) {
			case CreateUI.RESPONSIVE_CENTER -> L10N.t("elementgui.createui.option.responsive_center");
			case CreateUI.RESPONSIVE_FIT -> L10N.t("elementgui.createui.option.responsive_fit");
			case CreateUI.RESPONSIVE_FIT_DOWN -> L10N.t("elementgui.createui.option.responsive_fit_down");
			default -> value;
		});
		setFriendlyRenderer(backgroundMode, value -> switch (value) {
			case CreateUI.BACKGROUND_STATIC -> L10N.t("elementgui.createui.option.background_static");
			case CreateUI.BACKGROUND_CUT -> L10N.t("elementgui.createui.option.background_cut");
			case CreateUI.BACKGROUND_FADE -> L10N.t("elementgui.createui.option.background_fade");
			default -> value;
		});
		setFriendlyRenderer(actionType, value -> switch (value) {
			case CreateUI.ACTION_NONE -> L10N.t("elementgui.createui.option.action_none");
			case CreateUI.ACTION_SINGLEPLAYER -> L10N.t("elementgui.createui.option.action_singleplayer");
			case CreateUI.ACTION_MULTIPLAYER -> L10N.t("elementgui.createui.option.action_multiplayer");
			case CreateUI.ACTION_OPTIONS -> L10N.t("elementgui.createui.option.action_options");
			case CreateUI.ACTION_QUIT -> L10N.t("elementgui.createui.option.action_quit");
			case CreateUI.ACTION_JOIN_SERVER -> L10N.t("elementgui.createui.option.action_join_server");
			case CreateUI.ACTION_OPEN_URL -> L10N.t("elementgui.createui.option.action_open_url");
			case CreateUI.ACTION_RESUME_GAME -> L10N.t("elementgui.createui.option.action_resume");
			case CreateUI.ACTION_ADVANCEMENTS -> L10N.t("elementgui.createui.option.action_advancements");
			case CreateUI.ACTION_STATISTICS -> L10N.t("elementgui.createui.option.action_statistics");
			case CreateUI.ACTION_DISCONNECT -> L10N.t("elementgui.createui.option.action_disconnect");
			case CreateUI.ACTION_VANILLA_PAUSE_MENU -> L10N.t("elementgui.createui.option.action_vanilla_pause");
			default -> value;
		});
		setFriendlyRenderer(animationType, value -> switch (value) {
			case "NONE" -> L10N.t("elementgui.createui.option.animation_none");
			case "FADE" -> L10N.t("elementgui.createui.option.animation_fade");
			case "SLIDE_UP" -> L10N.t("elementgui.createui.option.animation_slide_up");
			case "SLIDE_LEFT" -> L10N.t("elementgui.createui.option.animation_slide_left");
			case "SCALE" -> L10N.t("elementgui.createui.option.animation_scale");
			case "PULSE" -> L10N.t("elementgui.createui.option.animation_pulse");
			default -> value;
		});
	}

	private static void setFriendlyRenderer(JComboBox<String> comboBox,
			java.util.function.Function<String, String> labelProvider) {
		comboBox.setRenderer(new DefaultListCellRenderer() {
			@Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
					boolean isSelected, boolean cellHasFocus) {
				Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setText(value == null ? "" : labelProvider.apply(value.toString()));
				return component;
			}
		});
	}

	private void applySelectedPreset() {
		recordHistoryCheckpoint();
		switch ((String) presetSelector.getSelectedItem()) {
		case "Pause menu" -> applyPauseMenuPreset();
		case "Wizard lobby" -> applyWizardPreset();
		case "Join server" -> applyJoinServerPreset();
		case "Open web link" -> applyWebPreset();
		default -> applyVanillaPreset();
		}
		recordHistoryCheckpoint();
	}

	private void applyWizardPreset() {
		targetScreen.setSelectedItem(CreateUI.TARGET_MAIN_MENU);
		titleText.setText("L O B B Y");
		subtitleText.setText("");
		backgroundMode.setSelectedItem(CreateUI.BACKGROUND_FADE);
		backgroundDurationTicks.setValue(120);
		backgroundFadeTicks.setValue(24);
		backgroundSlowZoom.setSelected(true);
		backgroundZoomPercent.setValue(5);
		backgroundZoomDurationTicks.setValue(400);
		backgroundOverlayColor.setText("000000");
		backgroundOverlayOpacity.setValue(60);
		backgroundOverlayGradient.setSelected(true);
		loopMenuMusic.setSelected(true);
		menuMusicVolume.setValue(100);

		List<GUIComponent> components = new ArrayList<>();
		components.add(label("lobby_title", 181, 112, titleText.getText(), Color.WHITE, true));
		components.add(textButton("join_server", 123, 130, "JOIN SERVER"));
		components.add(textButton("singleplayer", 123, 155, "Singleplayer"));
		components.add(textButton("options", 123, 180, "Settings"));
		components.add(textButton("quit", 123, 205, "Quit Game"));
		setPreset(components, List.of(action("join_server", CreateUI.ACTION_JOIN_SERVER, "play.example.com", ""),
				action("singleplayer", CreateUI.ACTION_SINGLEPLAYER, ""),
				action("options", CreateUI.ACTION_OPTIONS, ""), action("quit", CreateUI.ACTION_QUIT, "")));
	}

	private void applyVanillaPreset() {
		targetScreen.setSelectedItem(CreateUI.TARGET_MAIN_MENU);
		titleText.setText("Remake Studios");
		subtitleText.setText("Custom Minecraft menu");
		List<GUIComponent> components = new ArrayList<>();
		components.add(label("title", 151, 38, titleText.getText(), new Color(255, 215, 106), true));
		components.add(label("subtitle", 153, 58, subtitleText.getText(), Color.WHITE, true));
		components.add(button("singleplayer", 114, 94, "Singleplayer"));
		components.add(button("multiplayer", 114, 118, "Multiplayer"));
		components.add(button("options", 114, 142, "Options"));
		components.add(button("quit", 114, 166, "Quit Game"));
		setPreset(components, List.of(action("singleplayer", CreateUI.ACTION_SINGLEPLAYER, ""),
				action("multiplayer", CreateUI.ACTION_MULTIPLAYER, ""), action("options", CreateUI.ACTION_OPTIONS, ""),
				action("quit", CreateUI.ACTION_QUIT, "")));
	}

	private void applyJoinServerPreset() {
		targetScreen.setSelectedItem(CreateUI.TARGET_MAIN_MENU);
		titleText.setText("Join Server");
		subtitleText.setText("Paste the copied IP in Multiplayer");
		List<GUIComponent> components = new ArrayList<>();
		components.add(label("title", 176, 48, titleText.getText(), new Color(255, 215, 106), true));
		components.add(label("subtitle", 119, 68, subtitleText.getText(), Color.WHITE, true));
		components.add(button("join_server", 114, 112, "Join Server"));
		components.add(button("options", 114, 140, "Options"));
		setPreset(components, List.of(action("join_server", CreateUI.ACTION_JOIN_SERVER, "play.example.com"),
				action("options", CreateUI.ACTION_OPTIONS, "")));
	}

	private void applyWebPreset() {
		targetScreen.setSelectedItem(CreateUI.TARGET_MAIN_MENU);
		titleText.setText("Remake Studios");
		subtitleText.setText("Open our website");
		List<GUIComponent> components = new ArrayList<>();
		components.add(label("title", 151, 50, titleText.getText(), new Color(255, 215, 106), true));
		components.add(label("subtitle", 160, 70, subtitleText.getText(), Color.WHITE, true));
		components.add(button("open_website", 114, 116, "Open Website"));
		components.add(button("quit", 114, 144, "Quit Game"));
		setPreset(components, List.of(action("open_website", CreateUI.ACTION_OPEN_URL, "https://example.com"),
				action("quit", CreateUI.ACTION_QUIT, "")));
	}

	private void applyPauseMenuPreset() {
		targetScreen.setSelectedItem(CreateUI.TARGET_PAUSE_MENU);
		titleText.setText(L10N.t("elementgui.createui.pause_title"));
		subtitleText.setText("");
		List<GUIComponent> components = new ArrayList<>();
		Label title = label("title", 0, 25, titleText.getText(), Color.WHITE, true);
		title.x = (WYSIWYG.W - title.getWidth(mcreator.getWorkspace())) / 2;
		components.add(title);
		String[] actions = { CreateUI.ACTION_RESUME_GAME, CreateUI.ACTION_ADVANCEMENTS,
				CreateUI.ACTION_STATISTICS, CreateUI.ACTION_OPTIONS, CreateUI.ACTION_VANILLA_PAUSE_MENU,
				CreateUI.ACTION_DISCONNECT };
		List<CreateUI.CreateUIButtonAction> bindings = new ArrayList<>();
		for (int i = 0; i < actions.length; i++) {
			String name = defaultActionButtonName(actions[i]);
			Button button = button(name, 114, 65 + i * 24, defaultActionButtonText(actions[i]));
			button.autoSize = false;
			button.width = 200;
			components.add(button);
			bindings.add(action(name, actions[i], ""));
		}
		setPreset(components, bindings);
	}

	private void setPreset(List<GUIComponent> components, List<CreateUI.CreateUIButtonAction> actions) {
		editor.setComponentList(components);
		buttonActions.clear();
		buttonActions.addAll(actions);
		refreshActionButtonList();
		previewPanel.repaint();
	}

	private void importLobbyProject() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(L10N.t("elementgui.createui.import_lobby_project"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showOpenDialog(mcreator) != JFileChooser.APPROVE_OPTION)
			return;

		File projectRoot = chooser.getSelectedFile();
		File resourcesRoot = new File(projectRoot, "src/main/resources");
		if (!resourcesRoot.isDirectory() && new File(projectRoot, "assets").isDirectory())
			resourcesRoot = projectRoot;
		if (!resourcesRoot.isDirectory()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.import_lobby_missing_resources"),
					L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			List<File> resourceFiles;
			try (var paths = Files.walk(resourcesRoot.toPath())) {
				resourceFiles = paths.filter(Files::isRegularFile).map(java.nio.file.Path::toFile)
						.sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)).toList();
			}

			List<File> images = resourceFiles.stream().filter(CreateUIGUI::isImageFile).toList();
			File logo = images.stream().filter(file -> file.getName().toLowerCase(Locale.ROOT).contains("logo"))
					.findFirst().orElse(null);
			List<File> backgrounds = images.stream().filter(file -> file != logo).filter(file -> {
				String name = file.getName().toLowerCase(Locale.ROOT);
				return name.contains("background") || name.startsWith("bg") || name.contains("_bg");
			}).toList();
			if (backgrounds.isEmpty())
				backgrounds = images.stream().filter(file -> file != logo).toList();

			applyWizardPreset();
			backgroundImages.clear();
			importBackgroundImages(backgrounds.toArray(File[]::new));

			if (logo != null) {
				ImportedScreenImage importedLogo = importDroppedImage(logo);
				if (importedLogo != null) {
					double scale = Math.min(120.0 / importedLogo.width(), 100.0 / importedLogo.height());
					int width = Math.max(1, (int) Math.round(importedLogo.width() * scale));
					int height = Math.max(1, (int) Math.round(importedLogo.height() * scale));
					net.mcreator.element.parts.gui.Image logoComponent = new net.mcreator.element.parts.gui.Image(
							(net.mcreator.ui.wysiwyg.WYSIWYG.W - width) / 2, 6, importedLogo.file().getName(), false,
							null);
					logoComponent.customWidth = width;
					logoComponent.customHeight = height;
					logoComponent.pulseAnimation = true;
					logoComponent.pulseAmount = 4;
					logoComponent.pulsePeriodTicks = 40;
					List<GUIComponent> components = new ArrayList<>(editor.getComponentList());
					components.add(logoComponent);
					editor.setComponentList(components);
				}
			}

			File[] sounds = resourceFiles.stream()
					.filter(file -> file.getName().toLowerCase(Locale.ROOT).endsWith(".ogg")).toArray(File[]::new);
			Set<String> existingSounds = new HashSet<>();
			mcreator.getWorkspace().getSoundElements().forEach(sound -> existingSounds.add(sound.getName()));
			int soundCount = SoundElementDialog.importSoundsBatch(mcreator, sounds);
			mcreator.getWorkspace().getSoundElements().stream().filter(sound -> !existingSounds.contains(sound.getName()))
					.findFirst().ifPresent(sound -> menuMusic.setSound("CUSTOM:" + sound.getName()));

			LobbyConnectionSettings connection = readLobbyConnectionSettings(projectRoot);
			String address = connection.address().isBlank() ? "play.example.com" : connection.address();
			buttonActions.removeIf(action -> "join_server".equals(action.buttonName));
			buttonActions.add(new CreateUI.CreateUIButtonAction("join_server", CreateUI.ACTION_JOIN_SERVER, address,
					connection.sourceUrl()));
			refreshActionButtonList();
			previewPanel.clearCache();
			previewPanel.repaint();
			mcreator.reloadWorkspaceTabContents();

			JOptionPane.showMessageDialog(mcreator,
					L10N.t("elementgui.createui.import_lobby_complete", backgrounds.size(), logo != null ? 1 : 0,
							soundCount), L10N.t("elementgui.createui.import_lobby_project"), JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"),
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private static boolean isImageFile(File file) {
		String name = file.getName().toLowerCase(Locale.ROOT);
		return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
	}

	private static LobbyConnectionSettings readLobbyConnectionSettings(File projectRoot) {
		String address = "";
		String sourceUrl = "";
		File javaRoot = new File(projectRoot, "src/main/java");
		if (!javaRoot.isDirectory())
			javaRoot = projectRoot;

		try (var paths = Files.walk(javaRoot.toPath())) {
			for (File file : paths.filter(Files::isRegularFile).map(java.nio.file.Path::toFile)
					.filter(file -> file.getName().endsWith(".java")).toList()) {
				String source = Files.readString(file.toPath(), StandardCharsets.UTF_8);
				if (address.isBlank()) {
					Matcher matcher = SERVER_ADDRESS_PATTERN.matcher(source);
					if (matcher.find())
						address = matcher.group(1).trim();
				}
				if (sourceUrl.isBlank()) {
					Matcher matcher = SERVER_SOURCE_URL_PATTERN.matcher(source);
					if (matcher.find())
						sourceUrl = matcher.group(1).trim();
				}
				if (!address.isBlank() && !sourceUrl.isBlank())
					break;
			}
		} catch (Exception ignored) {
		}
		return new LobbyConnectionSettings(address, sourceUrl);
	}

	private record LobbyConnectionSettings(String address, String sourceUrl) {}

	private void addBackgroundFiles() {
		File[] files = FileDialogs.getMultiOpenDialog(mcreator, new String[] { ".png", ".jpg", ".jpeg" });
		if (files != null)
			importBackgroundImages(files);
	}

	private void addBackgroundFromURL() {
		String value = JOptionPane.showInputDialog(mcreator, L10N.t("elementgui.createui.background_url_prompt"),
				"https://");
		if (value == null || value.isBlank())
			return;
		URI uri;
		try {
			uri = URI.create(value.trim());
			if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())))
				throw new IllegalArgumentException(L10N.t("elementgui.createui.invalid_url"));
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.invalid_url"),
					L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
			return;
		}
		if ("http".equalsIgnoreCase(uri.getScheme()) && JOptionPane.showConfirmDialog(mcreator,
				L10N.t("elementgui.createui.http_warning"), L10N.t("common.warning"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
			return;

		JDialog progress = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
				L10N.t("elementgui.createui.downloading_asset"), true);
		progress.add(new JProgressBar() {{
			setIndeterminate(true);
		}});
		progress.setSize(320, 72);
		progress.setLocationRelativeTo(mcreator);
		new SwingWorker<RemoteImage, Void>() {
			@Override protected RemoteImage doInBackground() throws Exception {
				HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
						.followRedirects(HttpClient.Redirect.NORMAL).build();
				HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
						.header("Accept", "image/png,image/jpeg,image/*").GET().build();
				HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
				if (response.statusCode() < 200 || response.statusCode() >= 300)
					throw new IOException("HTTP " + response.statusCode());
				if (response.body().length > 16 * 1024 * 1024)
					throw new IOException(L10N.t("elementgui.createui.remote_asset_too_large"));
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
				if (image == null)
					throw new IOException(L10N.t("elementgui.createui.drop_image_error"));
				String path = uri.getPath();
				String fileName = path == null || path.isBlank() ? "remote_background" :
						FilenameUtilsPatched.removeExtension(new File(path).getName());
				String name = RegistryNameFixer.fix(fileName);
				if (name == null || name.isBlank())
					name = "remote_background";
				File target = uniqueScreenTexture(name);
				FileIO.writeImageToPNGFile(image, target);
				return new RemoteImage(target, image.getWidth(), image.getHeight());
			}

			@Override protected void done() {
				progress.dispose();
				try {
					RemoteImage imported = get();
					backgroundImages.addElement(imported.file().getName());
					backgroundTexture.reload();
					previewPanel.clearCache();
					previewPanel.repaint();
					mcreator.reloadWorkspaceTabContents();
				} catch (Exception e) {
					Throwable cause = e.getCause() != null ? e.getCause() : e;
					JOptionPane.showMessageDialog(mcreator, cause.getMessage(), L10N.t("common.error"),
							JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
		progress.setVisible(true);
	}

	private record RemoteImage(File file, int width, int height) {}

	private void importBackgroundImages(File[] files) {
		int imported = 0;
		for (File file : files) {
			try {
				BufferedImage image = ImageIO.read(file);
				if (image == null)
					continue;

				String name = RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(file.getName()));
				if (name == null || name.isBlank())
					name = "create_ui_background";
				File target = uniqueScreenTexture(name);
				FileIO.writeImageToPNGFile(image, target);
				backgroundImages.addElement(target.getName());
				imported++;
			} catch (Exception e) {
				JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("elementgui.createui.drop_image_error"),
						JOptionPane.ERROR_MESSAGE);
			}
		}

		if (imported > 0) {
			backgroundTexture.reload();
			previewPanel.clearCache();
			previewPanel.repaint();
			mcreator.reloadWorkspaceTabContents();
		}
	}

	private void addBackgroundFromTexturePicker() {
		String textureName = backgroundTexture.getTextureName();
		if (textureName == null || textureName.isBlank())
			return;

		backgroundImages.addElement(textureName);
		backgroundImageList.setSelectedIndex(backgroundImages.size() - 1);
		previewPanel.clearCache();
		previewPanel.repaint();
	}

	private void removeSelectedBackground() {
		int index = backgroundImageList.getSelectedIndex();
		if (index == -1)
			return;
		backgroundImages.remove(index);
		if (!backgroundImages.isEmpty())
			backgroundImageList.setSelectedIndex(Math.min(index, backgroundImages.size() - 1));
		previewPanel.repaint();
	}

	private void moveSelectedBackground(int direction) {
		int index = backgroundImageList.getSelectedIndex();
		int target = index + direction;
		if (index == -1 || target < 0 || target >= backgroundImages.size())
			return;

		String selected = backgroundImages.remove(index);
		backgroundImages.add(target, selected);
		backgroundImageList.setSelectedIndex(target);
		previewPanel.repaint();
	}

	private List<String> getBackgroundImageNames() {
		List<String> images = new ArrayList<>();
		for (int i = 0; i < backgroundImages.size(); i++)
			images.add(backgroundImages.get(i));
		return images;
	}

	private void installBackgroundDropTarget(Component target) {
		new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override public void drop(DropTargetDropEvent event) {
				try {
					if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						event.rejectDrop();
						return;
					}

					event.acceptDrop(DnDConstants.ACTION_COPY);
					List<?> dropped = (List<?>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					List<File> files = new ArrayList<>();
					for (Object object : dropped) {
						if (object instanceof File file && file.isFile())
							files.add(file);
					}
					importBackgroundImages(files.toArray(File[]::new));
					event.dropComplete(!files.isEmpty());
				} catch (Exception e) {
					event.dropComplete(false);
					JOptionPane.showMessageDialog(mcreator, e.getMessage(),
							L10N.t("elementgui.createui.drop_image_error"), JOptionPane.ERROR_MESSAGE);
				}
			}
		}, true);
	}

	private void showPreviewAddMenu(Component owner, int menuX, int menuY, @Nullable Point designPoint) {
		Point position = designPoint != null ? designPoint : new Point(WYSIWYG.W / 2 - 50, WYSIWYG.H / 2 - 10);
		JPopupMenu menu = new JPopupMenu();
		menu.add(createQuickActionButtonMenu(position));
		menu.addSeparator();
		addPopupAction(menu, "elementgui.createui.preview_add_button", () -> addPreviewButton(false, position));
		addPopupAction(menu, "elementgui.createui.preview_add_text_button", () -> addPreviewButton(true, position));
		addPopupAction(menu, "elementgui.createui.preview_add_label", () -> addPreviewLabel(position));
		addPopupAction(menu, "elementgui.createui.preview_add_image", () -> addPreviewImages(position));
		JMenu more = new JMenu(L10N.t("elementgui.createui.preview_more_elements"));
		for (WYSIWYGComponentRegistration<?> registration : WYSIWYGEditor.COMPONENT_REGISTRY) {
			if (net.mcreator.element.parts.gui.Slot.class.isAssignableFrom(registration.component())
					|| Set.of("button", "text_label", "image").contains(registration.machineName()))
				continue;
			JMenuItem item = new JMenuItem(L10N.t("elementgui.gui.add_" + registration.machineName()),
					UIRES.get("wysiwyg_editor." + registration.icon()));
			item.addActionListener(_ -> addPreviewElement(registration.machineName(), position));
			more.add(item);
		}
		menu.add(more);
		menu.addSeparator();
		addPopupAction(menu, "elementgui.createui.component_insert", this::insertFromComponentLibrary);
		menu.show(owner, Math.max(0, menuX), Math.max(0, menuY));
	}

	private JMenu createQuickActionButtonMenu(Point position) {
		JMenu menu = new JMenu(L10N.t("elementgui.createui.preview_add_action_button"));
		addPauseActions(menu, action -> addQuickActionButton(action, position));
		addPopupAction(menu, "elementgui.createui.option.action_singleplayer",
				() -> addQuickActionButton(CreateUI.ACTION_SINGLEPLAYER, position));
		addPopupAction(menu, "elementgui.createui.option.action_multiplayer",
				() -> addQuickActionButton(CreateUI.ACTION_MULTIPLAYER, position));
		addPopupAction(menu, "elementgui.createui.option.action_options",
				() -> addQuickActionButton(CreateUI.ACTION_OPTIONS, position));
		addPopupAction(menu, "elementgui.createui.option.action_join_server",
				() -> addQuickActionButton(CreateUI.ACTION_JOIN_SERVER, position));
		addPopupAction(menu, "elementgui.createui.option.action_open_url",
				() -> addQuickActionButton(CreateUI.ACTION_OPEN_URL, position));
		menu.addSeparator();
		addPopupAction(menu, "elementgui.createui.option.action_quit",
				() -> addQuickActionButton(CreateUI.ACTION_QUIT, position));
		return menu;
	}

	private JMenu createButtonActionMenu(Button button) {
		JMenu menu = new JMenu(L10N.t("elementgui.createui.context_button_action"));
		addPauseActions(menu, action -> configureButtonAction(button, action));
		addPopupAction(menu, "elementgui.createui.option.action_singleplayer",
				() -> configureButtonAction(button, CreateUI.ACTION_SINGLEPLAYER));
		addPopupAction(menu, "elementgui.createui.option.action_multiplayer",
				() -> configureButtonAction(button, CreateUI.ACTION_MULTIPLAYER));
		addPopupAction(menu, "elementgui.createui.option.action_options",
				() -> configureButtonAction(button, CreateUI.ACTION_OPTIONS));
		addPopupAction(menu, "elementgui.createui.option.action_join_server",
				() -> configureButtonAction(button, CreateUI.ACTION_JOIN_SERVER));
		addPopupAction(menu, "elementgui.createui.option.action_open_url",
				() -> configureButtonAction(button, CreateUI.ACTION_OPEN_URL));
		addPopupAction(menu, "elementgui.createui.option.action_quit",
				() -> configureButtonAction(button, CreateUI.ACTION_QUIT));
		menu.addSeparator();
		addPopupAction(menu, "elementgui.createui.context_button_action_clear",
				() -> assignButtonAction(button, CreateUI.ACTION_NONE, "", ""));
		return menu;
	}

	private void addPauseActions(JMenu menu, java.util.function.Consumer<String> handler) {
		for (String action : List.of(CreateUI.ACTION_RESUME_GAME, CreateUI.ACTION_ADVANCEMENTS,
				CreateUI.ACTION_STATISTICS, CreateUI.ACTION_DISCONNECT, CreateUI.ACTION_VANILLA_PAUSE_MENU)) {
			JMenuItem item = new JMenuItem(defaultActionButtonText(action));
			item.addActionListener(_ -> handler.accept(action));
			menu.add(item);
		}
		menu.addSeparator();
	}

	private void addQuickActionButton(String actionType, Point position) {
		QuickActionSettings settings = requestQuickActionSettings(actionType, null);
		if (settings != null)
			addActionButton(actionType, position, settings.value(), settings.sourceUrl());
	}

	private Button addActionButton(String actionType, Point position, String value, String sourceUrl) {
		Set<String> usedNames = editor.getComponentList().stream().map(GUIComponent::getName)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		String name = uniqueComponentName(defaultActionButtonName(actionType), usedNames);
		Button component = button(name, position.x, position.y, defaultActionButtonText(actionType));
		recordHistoryCheckpoint();
		editor.editor.addComponent(component);
		buttonActions.removeIf(action -> name.equals(action.buttonName));
		buttonActions.add(new CreateUI.CreateUIButtonAction(name, actionType, value, sourceUrl));
		editor.list.setSelectedValue(component, true);
		refreshActionButtonList();
		actionButton.setSelectedItem(name);
		loadSelectedAction();
		refreshStudioCanvas();
		recordHistoryCheckpoint();
		return component;
	}

	private void configureButtonAction(Button button, String actionType) {
		QuickActionSettings settings = requestQuickActionSettings(actionType, button);
		if (settings != null)
			assignButtonAction(button, actionType, settings.value(), settings.sourceUrl());
	}

	private void assignButtonAction(Button button, String actionType, String value, String sourceUrl) {
		recordHistoryCheckpoint();
		buttonActions.removeIf(action -> button.getName().equals(action.buttonName));
		if (!CreateUI.ACTION_NONE.equals(actionType))
			buttonActions.add(new CreateUI.CreateUIButtonAction(button.getName(), actionType, value, sourceUrl));
		refreshActionButtonList();
		actionButton.setSelectedItem(button.getName());
		loadSelectedAction();
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	@Nullable private QuickActionSettings requestQuickActionSettings(String actionType, @Nullable Button button) {
		CreateUI.CreateUIButtonAction current = button == null ? null : buttonActions.stream()
				.filter(action -> button.getName().equals(action.buttonName)).findFirst().orElse(null);
		String currentValue = current != null && current.value != null ? current.value : "";
		String currentSourceUrl = current != null && current.sourceUrl != null ? current.sourceUrl : "";
		if (CreateUI.ACTION_JOIN_SERVER.equals(actionType)) {
			JTextField address = new JTextField(currentValue.isBlank() ? suggestedServerAddress() : currentValue, 28);
			JTextField sourceUrl = new JTextField(currentSourceUrl, 28);
			JPanel fields = new JPanel(new GridLayout(2, 2, 8, 6));
			fields.add(L10N.label("elementgui.createui.server_status_address"));
			fields.add(address);
			fields.add(L10N.label("elementgui.createui.action_source_url"));
			fields.add(sourceUrl);
			while (true) {
				int result = JOptionPane.showConfirmDialog(mcreator, fields,
						L10N.t("elementgui.createui.option.action_join_server"), JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.PLAIN_MESSAGE);
				if (result != JOptionPane.OK_OPTION)
					return null;
				String addressValue = address.getText().trim();
				String sourceValue = sourceUrl.getText().trim();
				if (isServerAddress(addressValue) || isHTTPURL(sourceValue))
					return new QuickActionSettings(addressValue, sourceValue);
				JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.quick_action_invalid_server"),
						L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
			}
		}
		if (CreateUI.ACTION_OPEN_URL.equals(actionType)) {
			JTextField url = new JTextField(currentValue.isBlank() ? "https://" : currentValue, 28);
			while (true) {
				int result = JOptionPane.showConfirmDialog(mcreator,
						PanelUtils.join(FlowLayout.LEFT, L10N.label("elementgui.createui.quick_action_url"), url),
						L10N.t("elementgui.createui.option.action_open_url"), JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.PLAIN_MESSAGE);
				if (result != JOptionPane.OK_OPTION)
					return null;
				if (isHTTPURL(url.getText()))
					return new QuickActionSettings(url.getText().trim(), "");
				JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.quick_action_invalid_url"),
						L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
			}
		}
		return new QuickActionSettings("", "");
	}

	private String suggestedServerAddress() {
		String address = serverStatusAddress.getText().trim();
		return isServerAddress(address) ? address : "play.example.com";
	}

	private static String defaultActionButtonName(String actionType) {
		return switch (actionType) {
		case CreateUI.ACTION_SINGLEPLAYER -> "singleplayer";
		case CreateUI.ACTION_MULTIPLAYER -> "multiplayer";
		case CreateUI.ACTION_OPTIONS -> "options";
		case CreateUI.ACTION_QUIT -> "quit";
		case CreateUI.ACTION_JOIN_SERVER -> "join_server";
		case CreateUI.ACTION_OPEN_URL -> "open_website";
		case CreateUI.ACTION_RESUME_GAME -> "resume_game";
		case CreateUI.ACTION_ADVANCEMENTS -> "advancements";
		case CreateUI.ACTION_STATISTICS -> "statistics";
		case CreateUI.ACTION_DISCONNECT -> "disconnect";
		case CreateUI.ACTION_VANILLA_PAUSE_MENU -> "original_menu";
		default -> "action_button";
		};
	}

	private static String defaultActionButtonText(String actionType) {
		return switch (actionType) {
		case CreateUI.ACTION_SINGLEPLAYER -> L10N.t("elementgui.createui.action_button_singleplayer");
		case CreateUI.ACTION_MULTIPLAYER -> L10N.t("elementgui.createui.action_button_multiplayer");
		case CreateUI.ACTION_OPTIONS -> L10N.t("elementgui.createui.action_button_options");
		case CreateUI.ACTION_QUIT -> L10N.t("elementgui.createui.action_button_quit");
		case CreateUI.ACTION_JOIN_SERVER -> L10N.t("elementgui.createui.action_button_join_server");
		case CreateUI.ACTION_OPEN_URL -> L10N.t("elementgui.createui.action_button_open_url");
		case CreateUI.ACTION_RESUME_GAME -> L10N.t("elementgui.createui.option.action_resume");
		case CreateUI.ACTION_ADVANCEMENTS -> L10N.t("elementgui.createui.option.action_advancements");
		case CreateUI.ACTION_STATISTICS -> L10N.t("elementgui.createui.option.action_statistics");
		case CreateUI.ACTION_DISCONNECT -> L10N.t("elementgui.createui.option.action_disconnect");
		case CreateUI.ACTION_VANILLA_PAUSE_MENU -> L10N.t("elementgui.createui.option.action_vanilla_pause");
		default -> L10N.t("elementgui.createui.preview_add_button");
		};
	}

	private record QuickActionSettings(String value, String sourceUrl) {}

	private void addPreviewButton(boolean textOnly, Point position) {
		Set<String> usedNames = editor.getComponentList().stream().map(GUIComponent::getName)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		String name = uniqueComponentName(textOnly ? "text_button" : "button", usedNames);
		Button component = textOnly ? textButton(name, position.x, position.y, "Button") :
				button(name, position.x, position.y, "Button");
		addPreviewComponent(component);
	}

	private void addPreviewLabel(Point position) {
		Set<String> usedNames = editor.getComponentList().stream().map(GUIComponent::getName)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Label component = label(uniqueComponentName("text", usedNames), position.x, position.y, "Text", Color.WHITE,
				true);
		addPreviewComponent(component);
	}

	private boolean addPreviewImages(Point position) {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle(L10N.t("elementgui.createui.preview_add_image"));
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG / JPG", "png", "jpg", "jpeg"));
		if (chooser.showOpenDialog(mcreator) != JFileChooser.APPROVE_OPTION)
			return false;

		List<GUIComponent> added = new ArrayList<>();
		int offset = 0;
		File[] selectedFiles = chooser.getSelectedFiles();
		if (selectedFiles.length == 0 && chooser.getSelectedFile() != null)
			selectedFiles = new File[] { chooser.getSelectedFile() };
		for (File file : selectedFiles) {
			ImportedScreenImage texture = importDroppedImage(file);
			if (texture == null)
				continue;
			net.mcreator.element.parts.gui.Image component = createPreviewImageComponent(texture,
					new Point(position.x + offset, position.y + offset));
			editor.editor.addComponent(component);
			added.add(component);
			offset += 10;
		}
		if (!added.isEmpty()) {
			selectPreviewComponents(added);
			mcreator.reloadWorkspaceTabContents();
			refreshStudioCanvas();
			recordHistoryCheckpoint();
		}
		return !added.isEmpty();
	}

	private net.mcreator.element.parts.gui.Image createPreviewImageComponent(ImportedScreenImage texture,
			Point position) {
		net.mcreator.element.parts.gui.Image component = new net.mcreator.element.parts.gui.Image(position.x,
				position.y, texture.file().getName(), false, null);
		Dimension fitSize = fitImageSize(texture.width(), texture.height());
		if (fitSize != null) {
			component.customWidth = fitSize.width;
			component.customHeight = fitSize.height;
		}
		return component;
	}

	private void addPreviewComponent(GUIComponent component) {
		recordHistoryCheckpoint();
		editor.editor.addComponent(component);
		editor.list.setSelectedValue(component, true);
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private void selectPreviewComponents(List<GUIComponent> components) {
		int[] indices = components.stream().mapToInt(editor.components::indexOf).filter(index -> index >= 0).toArray();
		editor.list.setSelectedIndices(indices);
	}

	private void installPreviewImageDropTarget() {
		new DropTarget(previewPanel, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override public void drop(DropTargetDropEvent event) {
				try {
					Point position = previewPanel.toDesignPoint(event.getLocation());
					if (position == null)
						position = defaultPreviewInsertionPoint();
					if (event.isDataFlavorSupported(DataFlavor.stringFlavor)) {
						event.acceptDrop(DnDConstants.ACTION_COPY);
						String machineName = String.valueOf(
								event.getTransferable().getTransferData(DataFlavor.stringFlavor)).trim();
						event.dropComplete(addPreviewElement(machineName, position));
						return;
					}
					if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						event.rejectDrop();
						return;
					}
					event.acceptDrop(DnDConstants.ACTION_COPY);
					List<?> dropped = (List<?>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					List<GUIComponent> added = new ArrayList<>();
					int offset = 0;
					for (Object object : dropped) {
						if (!(object instanceof File file) || !file.isFile())
							continue;
						ImportedScreenImage texture = importDroppedImage(file);
						if (texture == null)
							continue;
						net.mcreator.element.parts.gui.Image component = createPreviewImageComponent(texture,
								new Point(position.x + offset, position.y + offset));
						editor.editor.addComponent(component);
						added.add(component);
						offset += 10;
					}
					if (!added.isEmpty()) {
						selectPreviewComponents(added);
						mcreator.reloadWorkspaceTabContents();
						refreshStudioCanvas();
						recordHistoryCheckpoint();
					}
					event.dropComplete(!added.isEmpty());
				} catch (Exception e) {
					event.dropComplete(false);
					JOptionPane.showMessageDialog(mcreator, e.getMessage(),
							L10N.t("elementgui.createui.drop_image_error"), JOptionPane.ERROR_MESSAGE);
				}
			}
		}, true);
	}

	private void installLayoutContextMenu() {
		editor.editor.addMouseListener(new MouseAdapter() {
			@Override public void mousePressed(MouseEvent event) {
				showContextMenu(event);
			}

			@Override public void mouseReleased(MouseEvent event) {
				showContextMenu(event);
			}
		});
	}

	private void showContextMenu(MouseEvent event) {
		if (!event.isPopupTrigger())
			return;

		GUIComponent component = findLayoutComponentAt(event.getX(), event.getY());
		if (component == null)
			return;

		if (!editor.list.getSelectedValuesList().contains(component))
			editor.list.setSelectedValue(component, true);
		JPopupMenu menu = new JPopupMenu();

		JMenuItem edit = new JMenuItem(L10N.t("elementgui.createui.context_edit"));
		edit.addActionListener(_ -> editSelectedComponent());
		menu.add(edit);

		if (component instanceof SizedComponent || component instanceof net.mcreator.element.parts.gui.Image) {
			JMenuItem resize = new JMenuItem(L10N.t("elementgui.createui.context_resize"));
			resize.addActionListener(_ -> resizeComponent(component));
			menu.add(resize);
		}

		if (component instanceof net.mcreator.element.parts.gui.Image image) {
			JMenuItem animation = new JMenuItem(L10N.t("elementgui.createui.context_image_animation"));
			animation.addActionListener(_ -> configureImageAnimation(image));
			menu.add(animation);
		}

		JMenuItem componentAnimation = new JMenuItem(L10N.t("elementgui.createui.animation"));
		componentAnimation.addActionListener(_ -> configureAnimationForSelection());
		menu.add(componentAnimation);

		if (component instanceof Button button) {
			JMenuItem autoSize = new JMenuItem(L10N.t("elementgui.createui.auto_size"));
			autoSize.addActionListener(_ -> configureAutoSizeForSelection());
			menu.add(autoSize);
			JMenuItem textOnly = new JMenuItem(L10N.t(button.isUndecorated ?
					"elementgui.createui.context_button_background" : "elementgui.createui.context_button_text_only"));
			textOnly.addActionListener(_ -> toggleButtonDecoration(button));
			menu.add(textOnly);
			JMenuItem style = new JMenuItem(L10N.t("elementgui.createui.button_style"));
			style.addActionListener(_ -> configureSelectedButtonStyle());
			menu.add(style);
		}

		menu.addSeparator();
		JMenuItem group = new JMenuItem(L10N.t("elementgui.createui.group"));
		group.addActionListener(_ -> groupSelection());
		menu.add(group);
		JMenuItem ungroup = new JMenuItem(L10N.t("elementgui.createui.ungroup"));
		ungroup.addActionListener(_ -> ungroupSelection());
		menu.add(ungroup);
		JMenuItem showHide = new JMenuItem(L10N.t("elementgui.createui.show_hide"));
		showHide.addActionListener(_ -> toggleSelectionVisibility());
		menu.add(showHide);
		menu.addSeparator();

		JMenuItem copy = new JMenuItem(L10N.t("elementgui.createui.context_copy"));
		copy.addActionListener(_ -> copyComponent(component));
		menu.add(copy);
		JMenuItem saveComponent = new JMenuItem(L10N.t("elementgui.createui.component_save"));
		saveComponent.addActionListener(_ -> saveSelectionToComponentLibrary());
		menu.add(saveComponent);
		JMenuItem insertComponent = new JMenuItem(L10N.t("elementgui.createui.component_insert"));
		insertComponent.addActionListener(_ -> insertFromComponentLibrary());
		menu.add(insertComponent);

		JMenuItem delete = new JMenuItem(L10N.t("elementgui.createui.context_delete"));
		delete.addActionListener(_ -> deleteComponent(component));
		menu.add(delete);

		menu.show(editor.editor, event.getX(), event.getY());
	}

	@Nullable private GUIComponent findLayoutComponentAt(int mouseX, int mouseY) {
		int x = (int) Math.round(mouseX / 2.0);
		int y = (int) Math.round(mouseY / 2.0);
		List<GUIComponent> guiComponents = new ArrayList<>(editor.getComponentList());
		guiComponents.sort(Collections.reverseOrder());
		for (GUIComponent component : guiComponents) {
			if (x >= component.getX() && x <= component.getX() + component.getWidth(mcreator.getWorkspace())
					&& y >= component.getY() && y <= component.getY() + component.getHeight(mcreator.getWorkspace()))
				return component;
		}
		return null;
	}

	private void resizeComponent(GUIComponent component) {
		JSpinner width = new JSpinner(
				new SpinnerNumberModel(Math.max(1, component.getWidth(mcreator.getWorkspace())), 1, 4096, 1));
		JSpinner height = new JSpinner(
				new SpinnerNumberModel(Math.max(1, component.getHeight(mcreator.getWorkspace())), 1, 4096, 1));

		JPanel panel = new JPanel(new GridLayout(2, 2, 8, 6));
		panel.add(L10N.label("elementgui.createui.resize_width"));
		panel.add(width);
		panel.add(L10N.label("elementgui.createui.resize_height"));
		panel.add(height);

		int result = JOptionPane.showConfirmDialog(mcreator, panel, L10N.t("elementgui.createui.context_resize"),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
			return;

		recordHistoryCheckpoint();
		int newWidth = ((Number) width.getValue()).intValue();
		int newHeight = ((Number) height.getValue()).intValue();
		if (component instanceof SizedComponent sizedComponent) {
			sizedComponent.width = newWidth;
			sizedComponent.height = newHeight;
			if (sizedComponent instanceof Button button)
				button.autoSize = false;
		} else if (component instanceof net.mcreator.element.parts.gui.Image image) {
			image.customWidth = newWidth;
			image.customHeight = newHeight;
			image.use1Xscale = false;
		}

		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private void configureImageAnimation(net.mcreator.element.parts.gui.Image image) {
		JCheckBox enabled = L10N.checkbox("elementgui.createui.image_pulse_enabled");
		enabled.setSelected(image.pulseAnimation);
		JSpinner amount = new JSpinner(new SpinnerNumberModel(Math.max(0, image.pulseAmount), 0, 128, 1));
		JSpinner period = new JSpinner(new SpinnerNumberModel(Math.max(1, image.pulsePeriodTicks), 1, 72000, 1));

		JPanel panel = new JPanel(new GridLayout(3, 2, 8, 6));
		panel.add(L10N.label("elementgui.createui.image_pulse_enabled"));
		panel.add(enabled);
		panel.add(L10N.label("elementgui.createui.image_pulse_amount"));
		panel.add(amount);
		panel.add(L10N.label("elementgui.createui.image_pulse_period"));
		panel.add(period);

		int result = JOptionPane.showConfirmDialog(mcreator, panel,
				L10N.t("elementgui.createui.context_image_animation"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
			return;

		image.pulseAnimation = enabled.isSelected();
		image.pulseAmount = ((Number) amount.getValue()).intValue();
		image.pulsePeriodTicks = ((Number) period.getValue()).intValue();
		editor.list.repaint();
		editor.editor.repaint();
		previewPanel.repaint();
	}

	private void resetImageSize(net.mcreator.element.parts.gui.Image image) {
		recordHistoryCheckpoint();
		image.customWidth = 0;
		image.customHeight = 0;
		image.use1Xscale = false;
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private void toggleButtonDecoration(Button button) {
		Button replacement = new Button(button.name, button.x, button.y, button.text, button.width, button.height,
				!button.isUndecorated, button.onClick, button.displayCondition);
		copyComponentMetadata(button, replacement);
		replacement.enabled = button.enabled;
		replacement.normalTextColor = button.normalTextColor;
		replacement.hoverTextColor = button.hoverTextColor;
		replacement.pressedTextColor = button.pressedTextColor;
		replacement.disabledTextColor = button.disabledTextColor;
		replacement.normalTexture = button.normalTexture;
		replacement.hoverTexture = button.hoverTexture;
		replacement.pressedTexture = button.pressedTexture;
		replacement.disabledTexture = button.disabledTexture;
		replacement.hoverScalePercent = button.hoverScalePercent;
		replacement.autoSize = button.autoSize;
		replacement.autoSizePaddingX = button.autoSizePaddingX;
		replacement.autoSizePaddingY = button.autoSizePaddingY;
		replacement.autoSizeMinWidth = button.autoSizeMinWidth;
		replacement.autoSizeMinHeight = button.autoSizeMinHeight;
		replacement.autoSizeMaxWidth = button.autoSizeMaxWidth;
		replacement.autoSizeMaxHeight = button.autoSizeMaxHeight;
		replacement.uuid = button.uuid;
		replaceComponent(button, replacement);
	}

	private static void copyComponentMetadata(GUIComponent source, GUIComponent target) {
		target.anchorPoint = source.anchorPoint;
		target.locked = source.locked;
		target.visible = source.visible;
		target.layer = source.layer;
		target.groupName = source.groupName;
		target.animationType = source.animationType;
		target.animationDelayTicks = source.animationDelayTicks;
		target.animationDurationTicks = source.animationDurationTicks;
		target.animationLoop = source.animationLoop;
		target.remarkNameOverride = source.remarkNameOverride;
	}

	private void replaceComponent(GUIComponent component, GUIComponent replacement) {
		int index = editor.components.indexOf(component);
		if (index == -1)
			return;

		editor.components.set(index, replacement);
		editor.list.setSelectedValue(replacement, true);
		editor.editor.repaint();
		refreshActionButtonList();
	}

	private void editSelectedComponent() {
		GUIComponent original = editor.list.getSelectedValue();
		if (original == null)
			return;
		recordHistoryCheckpoint();
		try {
			Method method = WYSIWYGEditor.class.getDeclaredMethod("editCurrentlySelectedComponent");
			method.setAccessible(true);
			method.invoke(editor);
			GUIComponent edited = editor.list.getSelectedValue();
			if (edited != null && edited != original) {
				copyComponentMetadata(original, edited);
				edited.uuid = original.uuid;
			}
			refreshStudioCanvas();
			recordHistoryCheckpoint();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"),
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void copyComponent(GUIComponent component) {
		duplicateComponents(List.of(component));
	}

	private void deleteComponent(GUIComponent component) {
		deleteComponents(List.of(component));
	}

	private void duplicatePreviewSelection() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty()) {
			showSelectionRequired(1);
			return;
		}
		duplicateComponents(selected);
	}

	private void duplicateComponents(List<GUIComponent> sourceComponents) {
		if (sourceComponents.isEmpty())
			return;
		recordHistoryCheckpoint();
		Set<String> usedNames = editor.getComponentList().stream().map(GUIComponent::getName)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		List<GUIComponent> copies = new ArrayList<>();
		List<CreateUI.CreateUIButtonAction> copiedActions = new ArrayList<>();
		for (GUIComponent component : sourceComponents) {
			String oldName = component.getName();
			String newName = uniqueComponentName(oldName, usedNames);
			GUIComponent copy = GSONClone.clone(component, component.getClass());
			copy.uuid = UUID.randomUUID();
			copy.x += 8;
			copy.y += 8;
			if (copy instanceof Button button) {
				button.name = newName;
				button.remarkNameOverride = null;
			} else if (copy instanceof Label label) {
				label.name = newName;
				label.remarkNameOverride = null;
			} else {
				copy.remarkNameOverride = newName;
			}
			usedNames.add(newName);
			editor.editor.addComponent(copy);
			copies.add(copy);
			for (CreateUI.CreateUIButtonAction action : buttonActions) {
				if (oldName.equals(action.buttonName))
					copiedActions.add(new CreateUI.CreateUIButtonAction(newName, action.actionType, action.value,
							action.sourceUrl));
			}
		}
		buttonActions.addAll(copiedActions);
		selectPreviewComponents(copies);
		refreshActionButtonList();
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private void deletePreviewSelection() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty()) {
			showSelectionRequired(1);
			return;
		}
		deleteComponents(selected);
	}

	private void deleteComponents(List<GUIComponent> components) {
		if (components.isEmpty())
			return;
		recordHistoryCheckpoint();
		Set<String> names = components.stream().filter(Button.class::isInstance).map(GUIComponent::getName)
				.collect(java.util.stream.Collectors.toSet());
		buttonActions.removeIf(action -> names.contains(action.buttonName));
		for (GUIComponent component : new ArrayList<>(components))
			editor.components.remove(component);
		editor.list.clearSelection();
		refreshActionButtonList();
		refreshStudioCanvas();
		recordHistoryCheckpoint();
	}

	private void saveSelectionToComponentLibrary() {
		List<GUIComponent> selected = selectedComponents();
		if (selected.isEmpty()) {
			showSelectionRequired(1);
			return;
		}
		String name = JOptionPane.showInputDialog(mcreator, L10N.t("elementgui.createui.component_name"),
				L10N.t("elementgui.createui.component_save"), JOptionPane.PLAIN_MESSAGE);
		if (name == null || name.isBlank())
			return;
		String safeName = RegistryNameFixer.fix(name);
		if (safeName == null || safeName.isBlank())
			safeName = "component";

		int minX = selected.stream().mapToInt(component -> component.x).min().orElse(0);
		int minY = selected.stream().mapToInt(component -> component.y).min().orElse(0);
		ComponentLibraryEntry entry = new ComponentLibraryEntry();
		Set<String> selectedNames = selected.stream().map(GUIComponent::getName).collect(
				java.util.stream.Collectors.toSet());
		for (GUIComponent component : selected) {
			GUIComponent copy = GSONClone.clone(component, component.getClass());
			copy.x -= minX;
			copy.y -= minY;
			copy.uuid = UUID.randomUUID();
			entry.components.add(copy);
		}
		for (CreateUI.CreateUIButtonAction action : buttonActions) {
			if (selectedNames.contains(action.buttonName))
				entry.buttonActions.add(new CreateUI.CreateUIButtonAction(action.buttonName, action.actionType,
						action.value, action.sourceUrl));
		}

		File folder = componentLibraryFolder();
		folder.mkdirs();
		File target = new File(folder, safeName + ".remarkcomponent");
		if (target.isFile() && JOptionPane.showConfirmDialog(mcreator,
				L10N.t("elementgui.createui.component_overwrite", target.getName()),
				L10N.t("elementgui.createui.component_save"), JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
			return;
		FileIO.writeStringToFile(GSON.toJson(entry), target);
		JOptionPane.showMessageDialog(mcreator,
				L10N.t("elementgui.createui.component_saved", target.getAbsolutePath()),
				L10N.t("elementgui.createui.component_save"), JOptionPane.INFORMATION_MESSAGE);
	}

	private void insertFromComponentLibrary() {
		JFileChooser chooser = new JFileChooser(componentLibraryFolder());
		chooser.setDialogTitle(L10N.t("elementgui.createui.component_insert"));
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Remake UI Component",
				"remarkcomponent"));
		if (chooser.showOpenDialog(mcreator) != JFileChooser.APPROVE_OPTION)
			return;
		try {
			ComponentLibraryEntry entry = GSON.fromJson(FileIO.readFileToString(chooser.getSelectedFile()),
					ComponentLibraryEntry.class);
			if (entry == null || entry.formatVersion != 1 || entry.components == null || entry.components.isEmpty())
				throw new IOException(L10N.t("elementgui.createui.component_invalid"));

			Set<String> usedNames = editor.getComponentList().stream().map(GUIComponent::getName)
					.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
			Map<String, String> renamedButtons = new LinkedHashMap<>();
			List<GUIComponent> inserted = new ArrayList<>();
			int offset = 16 + Math.min(80, editor.getComponentList().size() * 2);
			for (GUIComponent component : entry.components) {
				String oldName = component.getName();
				String uniqueName = uniqueComponentName(oldName, usedNames);
				if (component instanceof Button button) {
					button.name = uniqueName;
					button.remarkNameOverride = null;
					renamedButtons.put(oldName, uniqueName);
				} else if (component instanceof Label label) {
					label.name = uniqueName;
					label.remarkNameOverride = null;
				} else
					component.remarkNameOverride = uniqueName;
				usedNames.add(component.getName());
				component.x += offset;
				component.y += offset;
				component.uuid = UUID.randomUUID();
				if (component instanceof Button button && button.autoSize)
					applyButtonAutoSize(button);
				editor.editor.addComponent(component);
				inserted.add(component);
			}

			if (entry.buttonActions != null) {
				for (CreateUI.CreateUIButtonAction action : entry.buttonActions) {
					String buttonName = renamedButtons.getOrDefault(action.buttonName, action.buttonName);
					buttonActions.removeIf(existing -> buttonName.equals(existing.buttonName));
					buttonActions.add(new CreateUI.CreateUIButtonAction(buttonName, action.actionType, action.value,
							action.sourceUrl));
				}
			}
			int[] indices = inserted.stream().mapToInt(editor.components::indexOf).filter(index -> index >= 0).toArray();
			editor.list.setSelectedIndices(indices);
			refreshActionButtonList();
			refreshStudioCanvas();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator,
					L10N.t("elementgui.createui.component_load_failed", e.getMessage()),
					L10N.t("elementgui.createui.component_insert"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String uniqueComponentName(String requested, Set<String> usedNames) {
		if (!usedNames.contains(requested))
			return requested;
		int index = 2;
		String candidate;
		do {
			candidate = requested + "_" + index++;
		} while (usedNames.contains(candidate));
		return candidate;
	}

	private File componentLibraryFolder() {
		return new File(mcreator.getWorkspaceFolder(), ".remarkstudio" + File.separator + "components");
	}

	private void installImageDropTarget() {
		new DropTarget(editor.editor, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override public void drop(DropTargetDropEvent event) {
				try {
					if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						event.rejectDrop();
						return;
					}

					event.acceptDrop(DnDConstants.ACTION_COPY);
					List<?> dropped = (List<?>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					int x = (int) Math.round(event.getLocation().x / 2.0);
					int y = (int) Math.round(event.getLocation().y / 2.0);
					int imported = 0;

					for (Object object : dropped) {
						if (object instanceof File file && file.isFile()) {
							ImportedScreenImage texture = importDroppedImage(file);
							if (texture != null) {
								net.mcreator.element.parts.gui.Image component = new net.mcreator.element.parts.gui.Image(
										x + imported * 10, y + imported * 10, texture.file().getName(), false, null);
								Dimension fitSize = fitImageSize(texture.width(), texture.height());
								if (fitSize != null) {
									component.customWidth = fitSize.width;
									component.customHeight = fitSize.height;
								}
								editor.editor.addComponent(component);
								editor.list.setSelectedValue(component, true);
								imported++;
							}
						}
					}

					if (imported > 0) {
						mcreator.reloadWorkspaceTabContents();
						editor.editor.repaint();
						event.dropComplete(true);
					} else {
						event.dropComplete(false);
					}
				} catch (Exception e) {
					event.dropComplete(false);
					JOptionPane.showMessageDialog(mcreator, e.getMessage(),
							L10N.t("elementgui.createui.drop_image_error"), JOptionPane.ERROR_MESSAGE);
				}
			}
		}, true);
	}

	@Nullable private ImportedScreenImage importDroppedImage(File file) {
		try {
			BufferedImage image = ImageIO.read(file);
			if (image == null)
				return null;

			String name = RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(file.getName()));
			if (name == null || name.isBlank())
				name = "create_ui_image";
			File target = uniqueScreenTexture(name);
			FileIO.writeImageToPNGFile(image, target);
			return new ImportedScreenImage(target, image.getWidth(), image.getHeight());
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("elementgui.createui.drop_image_error"),
					JOptionPane.ERROR_MESSAGE);
			return null;
		}
	}

	@Nullable private static Dimension fitImageSize(int width, int height) {
		int maxWidth = 220;
		int maxHeight = 140;
		if (width <= maxWidth && height <= maxHeight)
			return null;

		double scale = Math.min(maxWidth / (double) width, maxHeight / (double) height);
		return new Dimension(Math.max(1, (int) Math.round(width * scale)),
				Math.max(1, (int) Math.round(height * scale)));
	}

	private File uniqueScreenTexture(String baseName) {
		String name = baseName;
		File target = mcreator.getFolderManager().getTextureFile(name, TextureType.SCREEN);
		int index = 2;
		while (target.isFile()) {
			name = baseName + "_" + index++;
			target = mcreator.getFolderManager().getTextureFile(name, TextureType.SCREEN);
		}
		return target;
	}

	private record ImportedScreenImage(File file, int width, int height) {}

	private static final class ComponentLibraryEntry {
		int formatVersion = 1;
		List<GUIComponent> components = new ArrayList<>();
		List<CreateUI.CreateUIButtonAction> buttonActions = new ArrayList<>();
	}

	private static Label label(String name, int x, int y, String text, Color color, boolean shadow) {
		return new Label(name, x, y, new StringProcedure(null, text), color, shadow, null);
	}

	private static Button button(String name, int x, int y, String text) {
		Button button = new Button(name, x, y, text, 200, 20, false, null, null);
		button.autoSize = true;
		applyButtonAutoSize(button);
		return button;
	}

	private static Button textButton(String name, int x, int y, String text) {
		Button button = new Button(name, x, y, text, 180, 20, true, null, null);
		button.autoSize = true;
		applyButtonAutoSize(button);
		return button;
	}

	private static CreateUI.CreateUIButtonAction action(String buttonName, String type, String value) {
		return new CreateUI.CreateUIButtonAction(buttonName, type, value);
	}

	private static CreateUI.CreateUIButtonAction action(String buttonName, String type, String value,
			String sourceUrl) {
		return new CreateUI.CreateUIButtonAction(buttonName, type, value, sourceUrl);
	}

	private void refreshActionButtonList() {
		String selected = (String) actionButton.getSelectedItem();
		actionButton.removeAllItems();
		editor.getComponentList().stream().filter(Button.class::isInstance).map(GUIComponent::getName).sorted()
				.forEach(actionButton::addItem);
		if (selected != null)
			actionButton.setSelectedItem(selected);
		loadSelectedAction();
	}

	private void componentListChanged() {
		SwingUtilities.invokeLater(() -> {
			if (editor == null)
				return;
			applyAutoSizeToAllButtons();
			Set<String> buttonNames = editor.getComponentList().stream().filter(Button.class::isInstance)
					.map(GUIComponent::getName).collect(java.util.stream.Collectors.toSet());
			buttonActions.removeIf(action -> action == null || !buttonNames.contains(action.buttonName));
			refreshActionButtonList();
			refreshTimelineComponents();
			updatePreviewInspector();
			refreshIssues();
		});
	}

	private void applyAutoSizeToAllButtons() {
		for (GUIComponent component : editor.getComponentList()) {
			if (component instanceof Button button && button.autoSize)
				applyButtonAutoSize(button);
		}
	}

	private void loadSelectedAction() {
		loadingActionEditor = true;
		try {
			String buttonName = (String) actionButton.getSelectedItem();
			if (buttonName == null) {
				actionType.setSelectedItem(CreateUI.ACTION_NONE);
				actionValue.setText("");
				actionSourceUrl.setText("");
				return;
			}

			CreateUI.CreateUIButtonAction action = buttonActions.stream()
					.filter(entry -> buttonName.equals(entry.buttonName)).findFirst()
					.orElse(new CreateUI.CreateUIButtonAction(buttonName, CreateUI.ACTION_NONE, ""));
			actionType.setSelectedItem(action.actionType);
			actionValue.setText(action.value);
			actionSourceUrl.setText(action.sourceUrl != null ? action.sourceUrl : "");
		} finally {
			loadingActionEditor = false;
		}
	}

	private void saveCurrentAction() {
		if (loadingActionEditor)
			return;
		String buttonName = (String) actionButton.getSelectedItem();
		if (buttonName == null)
			return;
		buttonActions.removeIf(action -> buttonName.equals(action.buttonName));
		String selectedAction = (String) actionType.getSelectedItem();
		if (!CreateUI.ACTION_NONE.equals(selectedAction))
			buttonActions.add(new CreateUI.CreateUIButtonAction(buttonName, selectedAction, actionValue.getText(),
					actionSourceUrl.getText()));
	}

	private void clearCurrentAction() {
		String buttonName = (String) actionButton.getSelectedItem();
		if (buttonName != null)
			buttonActions.removeIf(action -> buttonName.equals(action.buttonName));
		actionType.setSelectedItem(CreateUI.ACTION_NONE);
		actionValue.setText("");
		actionSourceUrl.setText("");
	}

	private void startStudioTimers() {
		historyTimer = new javax.swing.Timer(900, _ -> {
			recordHistoryCheckpoint();
			refreshIssues();
		});
		historyTimer.setInitialDelay(1400);
		historyTimer.start();
		autosaveTimer = new javax.swing.Timer(15000, _ -> writeAutosave());
		autosaveTimer.setInitialDelay(15000);
		autosaveTimer.start();
	}

	@Override public void onViewClosed() {
		writeAutosave();
		if (historyTimer != null)
			historyTimer.stop();
		if (autosaveTimer != null)
			autosaveTimer.stop();
		if (timelineTimer != null)
			timelineTimer.stop();
		if (previewPanel != null)
			previewPanel.stopAnimation();
		super.onViewClosed();
	}

	private String createStudioJSON() {
		return mcreator.getModElementManager().generatableElementToJSON(getElementFromGUI());
	}

	private void resetHistoryBaseline() {
		if (restoringHistory)
			return;
		undoHistory.clear();
		redoHistory.clear();
		lastHistoryJSON = createStudioJSON();
		updateHistoryButtons();
	}

	private void recordHistoryCheckpoint() {
		if (restoringHistory || editor == null)
			return;
		String current = createStudioJSON();
		if (lastHistoryJSON == null) {
			lastHistoryJSON = current;
			return;
		}
		if (current.equals(lastHistoryJSON))
			return;
		undoHistory.addLast(lastHistoryJSON);
		while (undoHistory.size() > 60)
			undoHistory.removeFirst();
		redoHistory.clear();
		lastHistoryJSON = current;
		updateHistoryButtons();
	}

	private void undoStudioChange() {
		if (undoHistory.isEmpty())
			return;
		String current = createStudioJSON();
		String previous = undoHistory.removeLast();
		redoHistory.addLast(current);
		restoreStudioJSON(previous);
		lastHistoryJSON = previous;
		updateHistoryButtons();
	}

	private void redoStudioChange() {
		if (redoHistory.isEmpty())
			return;
		String current = createStudioJSON();
		String next = redoHistory.removeLast();
		undoHistory.addLast(current);
		restoreStudioJSON(next);
		lastHistoryJSON = next;
		updateHistoryButtons();
	}

	private void restoreStudioJSON(String json) {
		GeneratableElement loaded = mcreator.getModElementManager().fromJSONtoGeneratableElementOrNull(json, modElement);
		if (!(loaded instanceof CreateUI createUI))
			return;
		restoringHistory = true;
		try {
			openInEditingMode(createUI);
		} finally {
			restoringHistory = false;
		}
		refreshStudioCanvas();
	}

	private void updateHistoryButtons() {
		undoButton.setEnabled(!undoHistory.isEmpty());
		redoButton.setEnabled(!redoHistory.isEmpty());
	}

	private File autosaveFile() {
		return new File(mcreator.getWorkspaceFolder(),
				".remark-autosave" + File.separator + "createui" + File.separator + modElement.getRegistryName() + ".json");
	}

	private void writeAutosave() {
		try {
			File target = autosaveFile();
			Files.createDirectories(target.toPath().getParent());
			Files.writeString(target.toPath(), createStudioJSON(), StandardCharsets.UTF_8);
			autosaveStatus.setText(L10N.t("elementgui.createui.autosaved_at",
					java.time.LocalTime.now().withSecond(0).withNano(0).toString()));
		} catch (Exception e) {
			autosaveStatus.setText(L10N.t("elementgui.createui.autosave_failed"));
		}
	}

	private void restoreAutosave() {
		File file = autosaveFile();
		if (!file.isFile()) {
			JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.no_autosave"),
					L10N.t("elementgui.createui.restore_autosave"), JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		if (JOptionPane.showConfirmDialog(mcreator, L10N.t("elementgui.createui.restore_autosave_confirm"),
				L10N.t("elementgui.createui.restore_autosave"), JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
			return;
		try {
			recordHistoryCheckpoint();
			restoreStudioJSON(Files.readString(file.toPath(), StandardCharsets.UTF_8));
			lastHistoryJSON = createStudioJSON();
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void refreshIssues() {
		if (editor == null || issueModel == null)
			return;
		List<String> errors = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		List<GUIComponent> components = new ArrayList<>(editor.getComponentList());

		Map<String, Long> names = components.stream().collect(java.util.stream.Collectors.groupingBy(
				GUIComponent::getName, LinkedHashMap::new, java.util.stream.Collectors.counting()));
		names.forEach((name, count) -> {
			if (name == null || name.isBlank())
				errors.add(L10N.t("elementgui.createui.issue_blank_name"));
			else if (count > 1)
				errors.add(L10N.t("elementgui.createui.issue_duplicate_name", name));
		});

		Set<String> componentNames = names.keySet();
		for (CreateUI.CreateUIButtonAction action : buttonActions) {
			if (action.buttonName == null || !componentNames.contains(action.buttonName))
				warnings.add(L10N.t("elementgui.createui.issue_orphan_action", action.buttonName));
			if (CreateUI.ACTION_OPEN_URL.equals(action.actionType) && !isHTTPURL(action.value))
				errors.add(L10N.t("elementgui.createui.issue_invalid_url", action.buttonName));
			if (CreateUI.ACTION_JOIN_SERVER.equals(action.actionType) && !isServerAddress(action.value)
					&& !isHTTPURL(action.sourceUrl))
				errors.add(L10N.t("elementgui.createui.issue_invalid_server", action.buttonName));
			if (action.sourceUrl != null && !action.sourceUrl.isBlank() && !isHTTPURL(action.sourceUrl))
				errors.add(L10N.t("elementgui.createui.issue_invalid_source", action.buttonName));
		}

		for (GUIComponent component : components) {
			if (!component.visible)
				continue;
			int width = Math.max(1, component.getWidth(mcreator.getWorkspace()));
			int height = Math.max(1, component.getHeight(mcreator.getWorkspace()));
			if (component.x + width < 0 || component.y + height < 0 || component.x > net.mcreator.ui.wysiwyg.WYSIWYG.W
					|| component.y > net.mcreator.ui.wysiwyg.WYSIWYG.H)
				warnings.add(L10N.t("elementgui.createui.issue_outside", component.getName()));
			if (component instanceof net.mcreator.element.parts.gui.Image image && !screenTextureExists(image.image))
				errors.add(L10N.t("elementgui.createui.issue_missing_texture", component.getName(), image.image));
			if (component instanceof Button button) {
				boolean hasAction = buttonActions.stream().anyMatch(action -> button.getName().equals(action.buttonName));
				if (!hasAction)
					warnings.add(L10N.t("elementgui.createui.issue_button_no_action", button.getName()));
				for (String texture : List.of(button.normalTexture, button.hoverTexture, button.pressedTexture,
						button.disabledTexture)) {
					if (texture != null && !texture.isBlank() && !screenTextureExists(texture))
						errors.add(L10N.t("elementgui.createui.issue_missing_texture", button.getName(), texture));
				}
			}
		}

		for (String background : getBackgroundImageNames()) {
			if (!screenTextureExists(background))
				errors.add(L10N.t("elementgui.createui.issue_missing_background", background));
		}
		if (CreateUI.BACKGROUND_FADE.equals(backgroundMode.getSelectedItem())
				&& ((Number) backgroundFadeTicks.getValue()).intValue() >= ((Number) backgroundDurationTicks.getValue()).intValue())
			warnings.add(L10N.t("elementgui.createui.issue_fade_timing"));
		if (serverStatusEnabled.isSelected() && !isServerAddress(serverStatusAddress.getText()))
			errors.add(L10N.t("elementgui.createui.issue_server_status_address"));
		long menuCount = 1 + mcreator.getWorkspace().getModElementsByType(ModElementType.CREATEUI).stream()
				.filter(element -> !element.getName().equals(modElement.getName()))
				.map(ModElement::getGeneratableElement).filter(CreateUI.class::isInstance).map(CreateUI.class::cast)
				.filter(ui -> ui.replaceVanillaMenu && Objects.equals(ui.targetScreen, targetScreen.getSelectedItem())).count();
		if (replaceVanillaMenu.isSelected() && menuCount > 1)
			warnings.add(L10N.t("elementgui.createui.issue_multiple_main_menus", menuCount));

		issueModel.clear();
		errors.forEach(issue -> issueModel.addElement("ERROR  " + issue));
		warnings.forEach(issue -> issueModel.addElement("WARN   " + issue));
		if (issueModel.isEmpty())
			issueModel.addElement(L10N.t("elementgui.createui.no_issues"));
		issueSummary.setText(L10N.t("elementgui.createui.issue_summary", errors.size(), warnings.size()));
	}

	private boolean screenTextureExists(String textureName) {
		if (textureName == null || textureName.isBlank())
			return false;
		return mcreator.getFolderManager().getTextureFile(FilenameUtilsPatched.removeExtension(textureName),
				TextureType.SCREEN).isFile();
	}

	private static boolean isHTTPURL(String value) {
		try {
			URI uri = URI.create(value == null ? "" : value.trim());
			return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
					&& uri.getHost() != null;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static boolean isServerAddress(String address) {
		return address != null && !address.isBlank() && address.length() <= 253
				&& address.matches("[A-Za-z0-9._\\-\\[\\]:]+") && !address.contains("..");
	}

	private void exportTemplatePack() {
		File target = FileDialogs.getSaveDialog(mcreator, modElement.getRegistryName() + ".remarkui",
				new String[] { ".remarkui" });
		if (target == null)
			return;
		if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".remarkui"))
			target = new File(target.getParentFile(), target.getName() + ".remarkui");

		CreateUI data = getElementFromGUI();
		JsonObject manifest = new JsonObject();
		manifest.addProperty("formatVersion", 1);
		manifest.addProperty("name", modElement.getName());
		JsonArray textureManifest = new JsonArray();
		JsonObject soundManifest = new JsonObject();
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(target), StandardCharsets.UTF_8)) {
			writeZipEntry(zip, "template.json", createStudioJSON().getBytes(StandardCharsets.UTF_8));
			for (String textureName : gatherReferencedTextures(data)) {
				File texture = mcreator.getFolderManager().getTextureFile(
						FilenameUtilsPatched.removeExtension(textureName), TextureType.SCREEN);
				if (!texture.isFile())
					continue;
				byte[] original = Files.readAllBytes(texture.toPath());
				byte[] optimized = optimizePNG(original);
				writeZipEntry(zip, "textures/" + texture.getName(), optimized.length < original.length ? optimized : original);
				textureManifest.add(texture.getName());
			}

			String customSound = customMenuSoundName(data);
			if (customSound != null) {
				mcreator.getWorkspace().getSoundElements().stream().filter(sound -> customSound.equals(sound.getName()))
						.findFirst().ifPresent(sound -> {
					JsonArray files = new JsonArray();
					for (String fileName : sound.getFiles()) {
						File soundFile = new File(mcreator.getFolderManager().getSoundsDir(), fileName + ".ogg");
						if (!soundFile.isFile())
							continue;
						String entryName = "sounds/" + customSound + "/" + soundFile.getName();
						try {
							writeZipEntry(zip, entryName, Files.readAllBytes(soundFile.toPath()));
							files.add(entryName);
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
					soundManifest.add(customSound, files);
				});
			}
			manifest.add("textures", textureManifest);
			manifest.add("sounds", soundManifest);
			writeZipEntry(zip, "manifest.json", new GsonBuilder().setPrettyPrinting().create().toJson(manifest)
					.getBytes(StandardCharsets.UTF_8));
			JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.template_pack_saved", target.getName()),
					L10N.t("elementgui.createui.export_template_pack"), JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private void importTemplatePack() {
		File source = FileDialogs.getOpenDialog(mcreator, new String[] { ".remarkui" });
		if (source == null)
			return;
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source.toPath()), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			long total = 0;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName().replace('\\', '/');
				if (entry.isDirectory())
					continue;
				if (name.startsWith("/") || name.contains("../") || entries.size() >= 512)
					throw new IOException(L10N.t("elementgui.createui.invalid_template_pack"));
				byte[] bytes = readLimited(zip, 32 * 1024 * 1024);
				total += bytes.length;
				if (total > 128L * 1024 * 1024)
					throw new IOException(L10N.t("elementgui.createui.invalid_template_pack"));
				entries.put(name, bytes);
			}
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
			return;
		}
		try {
			byte[] templateBytes = entries.get("template.json");
			byte[] manifestBytes = entries.get("manifest.json");
			if (templateBytes == null || manifestBytes == null)
				throw new IOException(L10N.t("elementgui.createui.invalid_template_pack"));
			String templateJSON = new String(templateBytes, StandardCharsets.UTF_8);
			JsonObject manifest = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!manifest.has("formatVersion") || manifest.get("formatVersion").getAsInt() != 1)
				throw new IOException(L10N.t("elementgui.createui.invalid_template_pack"));

			Map<String, String> replacements = new LinkedHashMap<>();
			JsonArray textures = manifest.has("textures") ? manifest.getAsJsonArray("textures") : new JsonArray();
			for (JsonElement textureElement : textures) {
				String oldName = new File(textureElement.getAsString()).getName();
				byte[] bytes = entries.get("textures/" + oldName);
				if (bytes == null)
					continue;
				BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
				if (image == null)
					continue;
				String base = RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(oldName));
				if (base == null || base.isBlank())
					base = "template_texture";
				File existing = mcreator.getFolderManager().getTextureFile(base, TextureType.SCREEN);
				File destination;
				if (existing.isFile() && MessageDigest.isEqual(sha256(Files.readAllBytes(existing.toPath())), sha256(bytes)))
					destination = existing;
				else
					destination = uniqueScreenTexture(base);
				if (!destination.isFile())
					FileIO.writeImageToPNGFile(image, destination);
				replacements.put(oldName, destination.getName());
			}

			if (manifest.has("sounds")) {
				for (Map.Entry<String, JsonElement> soundEntry : manifest.getAsJsonObject("sounds").entrySet()) {
					String oldName = soundEntry.getKey();
					String newName = importPackedSound(oldName, soundEntry.getValue().getAsJsonArray(), entries);
					if (!oldName.equals(newName))
						replacements.put("CUSTOM:" + oldName, "CUSTOM:" + newName);
				}
			}

			for (Map.Entry<String, String> replacement : replacements.entrySet())
				templateJSON = templateJSON.replace("\"" + replacement.getKey() + "\"",
						"\"" + replacement.getValue() + "\"");
			GeneratableElement loaded = mcreator.getModElementManager()
					.fromJSONtoGeneratableElementOrNull(templateJSON, modElement);
			if (!(loaded instanceof CreateUI createUI))
				throw new IOException(L10N.t("elementgui.createui.invalid_template_pack"));
			recordHistoryCheckpoint();
			openInEditingMode(createUI);
			mcreator.reloadWorkspaceTabContents();
			backgroundTexture.reload();
			JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.template_pack_loaded"),
					L10N.t("elementgui.createui.import_template_pack"), JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private String importPackedSound(String requestedName, JsonArray fileEntries, Map<String, byte[]> entries)
			throws IOException {
		SoundElement existing = mcreator.getWorkspace().getSoundElements().stream()
				.filter(sound -> requestedName.equals(sound.getName())).findFirst().orElse(null);
		if (existing != null)
			return requestedName;
		String registryName = requestedName;
		int index = 2;
		while (workspaceSoundExists(registryName))
			registryName = requestedName + "_" + index++;
		List<String> importedFiles = new ArrayList<>();
		for (JsonElement element : fileEntries) {
			String entryName = element.getAsString();
			byte[] bytes = entries.get(entryName);
			if (bytes == null)
				continue;
			String base = RegistryNameFixer.fix(FilenameUtilsPatched.removeExtension(new File(entryName).getName()));
			String fileName = base;
			File destination = new File(mcreator.getFolderManager().getSoundsDir(), fileName + ".ogg");
			int fileIndex = 2;
			while (destination.isFile()) {
				if (MessageDigest.isEqual(sha256(Files.readAllBytes(destination.toPath())), sha256(bytes)))
					break;
				fileName = base + "_" + fileIndex++;
				destination = new File(mcreator.getFolderManager().getSoundsDir(), fileName + ".ogg");
			}
			if (!destination.isFile())
				Files.write(destination.toPath(), bytes);
			importedFiles.add(fileName);
		}
		if (!importedFiles.isEmpty()) {
			mcreator.getWorkspace().addSoundElement(new SoundElement(registryName, importedFiles, "master", ""));
			mcreator.getWorkspace().setLocalization("subtitles." + registryName, "");
		}
		return registryName;
	}

	private boolean workspaceSoundExists(String name) {
		return mcreator.getWorkspace().getSoundElements().stream().anyMatch(sound -> sound.getName().equals(name));
	}

	private Set<String> gatherReferencedTextures(CreateUI data) {
		Set<String> names = new LinkedHashSet<>();
		if (data.backgroundImages != null)
			names.addAll(data.backgroundImages);
		if (data.components != null) {
			for (GUIComponent component : data.components) {
				if (component instanceof net.mcreator.element.parts.gui.Image image && image.image != null)
					names.add(image.image);
				if (component instanceof Button button) {
					for (String texture : List.of(button.normalTexture, button.hoverTexture, button.pressedTexture,
							button.disabledTexture)) {
						if (texture != null && !texture.isBlank())
							names.add(texture);
					}
				}
			}
		}
		names.removeIf(name -> name == null || name.isBlank());
		return names;
	}

	@Nullable private static String customMenuSoundName(CreateUI data) {
		if (data.menuMusic == null || data.menuMusic.isEmpty())
			return null;
		String value = data.menuMusic.getUnmappedValue();
		return value != null && value.startsWith("CUSTOM:") ? value.substring("CUSTOM:".length()) : null;
	}

	private static void writeZipEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		entry.setTime(0L);
		zip.putNextEntry(entry);
		zip.write(data);
		zip.closeEntry();
	}

	private static byte[] readLimited(InputStream stream, int limit) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int read;
		while ((read = stream.read(buffer)) != -1) {
			if (output.size() + read > limit)
				throw new IOException("Entry exceeds size limit");
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static byte[] optimizePNG(byte[] original) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
			if (image == null)
				return original;
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		} catch (Exception ignored) {
			return original;
		}
	}

	private static byte[] sha256(byte[] data) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(data);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void showAssetOptimizationReport() {
		CreateUI data = getElementFromGUI();
		long originalBytes = 0;
		long optimizedBytes = 0;
		long duplicateBytes = 0;
		int files = 0;
		Map<String, Long> hashes = new LinkedHashMap<>();
		for (String textureName : gatherReferencedTextures(data)) {
			try {
				File texture = mcreator.getFolderManager().getTextureFile(
						FilenameUtilsPatched.removeExtension(textureName), TextureType.SCREEN);
				if (!texture.isFile())
					continue;
				byte[] original = Files.readAllBytes(texture.toPath());
				byte[] optimized = optimizePNG(original);
				String hash = java.util.HexFormat.of().formatHex(sha256(original));
				if (hashes.containsKey(hash))
					duplicateBytes += original.length;
				else
					hashes.put(hash, (long) original.length);
				originalBytes += original.length;
				optimizedBytes += Math.min(original.length, optimized.length);
				files++;
			} catch (Exception ignored) {
			}
		}
		String report = L10N.t("elementgui.createui.optimize_report", files, formatBytes(originalBytes),
				formatBytes(optimizedBytes), formatBytes(Math.max(0, originalBytes - optimizedBytes)),
				formatBytes(duplicateBytes));
		JTextArea area = new JTextArea(report, 10, 52);
		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		JOptionPane.showMessageDialog(mcreator, new JScrollPane(area), L10N.t("elementgui.createui.optimize_assets"),
				JOptionPane.INFORMATION_MESSAGE);
	}

	private static String formatBytes(long bytes) {
		if (bytes < 1024)
			return bytes + " B";
		if (bytes < 1024 * 1024)
			return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
		return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
	}

	private void syncLivePreview() {
		try {
			JsonObject root = new JsonObject();
			root.addProperty("formatVersion", 1);
			root.addProperty("responsiveLayout", responsiveLayout.isSelected());
			root.addProperty("responsiveMode", String.valueOf(responsiveMode.getSelectedItem()));
			root.addProperty("overlayColor", backgroundOverlayColor.getText());
			root.addProperty("overlayOpacity", ((Number) backgroundOverlayOpacity.getValue()).intValue());
			root.addProperty("backgroundMode", String.valueOf(backgroundMode.getSelectedItem()));
			root.addProperty("backgroundDurationTicks", ((Number) backgroundDurationTicks.getValue()).intValue());
			root.addProperty("backgroundFadeTicks", ((Number) backgroundFadeTicks.getValue()).intValue());
			JsonArray components = new JsonArray();
			for (GUIComponent component : editor.getComponentList()) {
				JsonObject json = new JsonObject();
				json.addProperty("name", component.getName());
				json.addProperty("type", component.getClass().getSimpleName().toUpperCase(Locale.ROOT));
				json.addProperty("x", component.x);
				json.addProperty("y", component.y);
				json.addProperty("width", component.getWidth(mcreator.getWorkspace()));
				json.addProperty("height", component.getHeight(mcreator.getWorkspace()));
				json.addProperty("visible", component.visible);
				json.addProperty("animationType", component.animationType);
				json.addProperty("animationDelayTicks", component.animationDelayTicks);
				json.addProperty("animationDurationTicks", component.animationDurationTicks);
				json.addProperty("animationLoop", component.animationLoop);
				if (component instanceof Button button) {
					json.addProperty("text", button.text);
					json.addProperty("enabled", button.enabled);
					json.addProperty("normalTextColor", button.normalTextColor);
					json.addProperty("hoverTextColor", button.hoverTextColor);
					json.addProperty("pressedTextColor", button.pressedTextColor);
					json.addProperty("disabledTextColor", button.disabledTextColor);
					json.addProperty("normalTexture", button.normalTexture);
					json.addProperty("hoverTexture", button.hoverTexture);
					json.addProperty("pressedTexture", button.pressedTexture);
					json.addProperty("disabledTexture", button.disabledTexture);
					json.addProperty("hoverScalePercent", button.hoverScalePercent);
				} else if (component instanceof Label label) {
					json.addProperty("text", label.getRenderText());
				}
				components.add(json);
			}
			root.add("components", components);
			File liveFile = new File(mcreator.getFolderManager().getClientRunDir(),
					"remarkstudio" + File.separator + "live" + File.separator + modElement.getRegistryName() + ".json");
			Files.createDirectories(liveFile.toPath().getParent());
			Files.writeString(liveFile.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root),
					StandardCharsets.UTF_8);
			JOptionPane.showMessageDialog(mcreator, L10N.t("elementgui.createui.live_preview_synced"),
					L10N.t("elementgui.createui.sync_live_preview"), JOptionPane.INFORMATION_MESSAGE);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(mcreator, e.getMessage(), L10N.t("common.error"), JOptionPane.ERROR_MESSAGE);
		}
	}

	private JComponent createColorPicker(VTextField field, String titleKey) {
		JButton swatch = new JButton();
		swatch.setPreferredSize(new Dimension(38, 26));
		swatch.setMinimumSize(new Dimension(38, 26));
		swatch.setEnabled(field.isEnabled());
		field.addPropertyChangeListener("enabled", _ -> swatch.setEnabled(field.isEnabled()));
		swatch.setToolTipText(L10N.t("elementgui.createui.choose_color"));
		swatch.setIcon(new Icon() {
			@Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
				Graphics2D g = (Graphics2D) graphics.create();
				int width = getIconWidth();
				int height = getIconHeight();
				int tile = 4;
				for (int tileY = 0; tileY < height; tileY += tile) {
					for (int tileX = 0; tileX < width; tileX += tile) {
						g.setColor(((tileX + tileY) / tile) % 2 == 0 ? new Color(220, 220, 220) : Color.WHITE);
						g.fillRect(x + tileX, y + tileY, tile, tile);
					}
				}
				g.setColor(parseColor(field.getText(), Color.GRAY));
				g.fillRect(x, y, width, height);
				g.setColor(new Color(0, 0, 0, 120));
				g.drawRect(x, y, width - 1, height - 1);
				g.dispose();
			}

			@Override public int getIconWidth() {
				return 24;
			}

			@Override public int getIconHeight() {
				return 14;
			}
		});
		field.getDocument().addDocumentListener(new DocumentListener() {
			private void refresh() {
				swatch.repaint();
			}

			@Override public void insertUpdate(DocumentEvent event) {
				refresh();
			}

			@Override public void removeUpdate(DocumentEvent event) {
				refresh();
			}

			@Override public void changedUpdate(DocumentEvent event) {
				refresh();
			}
		});
		swatch.addActionListener(_ -> {
			Color selected = JColorChooser.showDialog(mcreator, L10N.t(titleKey),
					parseColor(field.getText(), Color.WHITE));
			if (selected != null) {
				field.setText(colorToHex(selected));
				field.requestFocusInWindow();
			}
		});
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		panel.setOpaque(false);
		panel.add(new JLabel("#"));
		panel.add(field);
		panel.add(swatch);
		return panel;
	}

	private static VTextField colorField(String defaultValue) {
		VTextField field = new VTextField(8);
		field.setText(defaultValue);
		field.setValidator(() -> isColor(field.getText()) ? ValidationResult.PASSED :
				new ValidationResult(ValidationResult.Type.ERROR, L10N.t("elementgui.createui.error_color")));
		field.enableRealtimeValidation();
		return field;
	}

	private static boolean isColor(String color) {
		if (color == null)
			return false;
		String normalized = color.trim().toUpperCase(Locale.ROOT);
		if (normalized.startsWith("#"))
			normalized = normalized.substring(1);
		if (normalized.startsWith("0X"))
			normalized = normalized.substring(2);
		return normalized.matches("[0-9A-F]{6}([0-9A-F]{2})?");
	}

	private static String normalizeColor(String color) {
		String normalized = color == null ? "" : color.trim().toUpperCase(Locale.ROOT);
		if (normalized.startsWith("#"))
			normalized = normalized.substring(1);
		if (normalized.startsWith("0X"))
			normalized = normalized.substring(2);
		return normalized;
	}

	private static String colorToHex(Color color) {
		return String.format(Locale.ROOT, "%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
	}

	@Override public void openInEditingMode(CreateUI createUI) {
		editor.setOpening(true);
		targetScreen.setSelectedItem(createUI.targetScreen);
		replaceVanillaMenu.setSelected(createUI.replaceVanillaMenu);
		responsiveLayout.setSelected(createUI.responsiveLayout);
		responsiveMode.setSelectedItem(createUI.responsiveMode != null ? createUI.responsiveMode : CreateUI.RESPONSIVE_FIT_DOWN);
		safeMargin.setValue(Math.max(0, createUI.safeMargin));
		titleText.setText(createUI.titleText);
		subtitleText.setText(createUI.subtitleText);
		titleColor.setText(createUI.titleColor);
		subtitleColor.setText(createUI.subtitleColor);
		accentColor.setText(createUI.accentColor);
		backgroundImages.clear();
		if (createUI.backgroundImages != null) {
			for (String image : createUI.backgroundImages)
				backgroundImages.addElement(image);
		}
		backgroundMode.setSelectedItem(createUI.backgroundMode != null ? createUI.backgroundMode : CreateUI.BACKGROUND_FADE);
		backgroundDurationTicks.setValue(Math.max(20, createUI.backgroundDurationTicks));
		backgroundFadeTicks.setValue(Math.max(0, createUI.backgroundFadeTicks));
		backgroundSlowZoom.setSelected(createUI.backgroundSlowZoom);
		backgroundZoomPercent.setValue(Math.max(0, Math.min(50, createUI.backgroundZoomPercent)));
		backgroundZoomDurationTicks.setValue(Math.max(20, createUI.backgroundZoomDurationTicks));
		backgroundOverlayColor.setText(createUI.backgroundOverlayColor != null ? createUI.backgroundOverlayColor : "000000");
		backgroundOverlayOpacity.setValue(Math.max(0, Math.min(100, createUI.backgroundOverlayOpacity)));
		backgroundOverlayGradient.setSelected(createUI.backgroundOverlayGradient);
		menuMusic.setSound(createUI.menuMusic);
		loopMenuMusic.setSelected(createUI.loopMenuMusic);
		menuMusicVolume.setValue(Math.max(0, Math.min(100, createUI.menuMusicVolume)));
		serverStatusEnabled.setSelected(createUI.serverStatusEnabled);
		serverStatusAddress.setText(createUI.serverStatusAddress != null ? createUI.serverStatusAddress : "");
		serverStatusRefreshTicks.setValue(Math.max(20, createUI.serverStatusRefreshTicks));
		serverStatusX.setValue(createUI.serverStatusX);
		serverStatusY.setValue(createUI.serverStatusY);
		serverStatusShowMOTD.setSelected(createUI.serverStatusShowMOTD);
		serverStatusShowPlayers.setSelected(createUI.serverStatusShowPlayers);
		serverStatusShowPing.setSelected(createUI.serverStatusShowPing);
		livePreviewEnabled.setSelected(createUI.livePreviewEnabled);
		showSingleplayer.setSelected(createUI.showSingleplayer);
		showMultiplayer.setSelected(createUI.showMultiplayer);
		showOptions.setSelected(createUI.showOptions);
		showQuit.setSelected(createUI.showQuit);
		editor.setComponentList(createUI.components != null ? createUI.components : List.of());
		applyAutoSizeToAllButtons();
		if (createUI.gridSettings != null) {
			editor.sx.setValue(createUI.gridSettings.sx);
			editor.sy.setValue(createUI.gridSettings.sy);
			editor.ox.setValue(createUI.gridSettings.ox);
			editor.oy.setValue(createUI.gridSettings.oy);
			editor.snapOnGrid.setSelected(createUI.gridSettings.snapOnGrid);
		}
		buttonActions.clear();
		if (createUI.buttonActions != null)
			buttonActions.addAll(createUI.buttonActions);
		refreshActionButtonList();
		previewPanel.clearCache();
		previewPanel.repaint();
		refreshTimelineComponents();
		updateSelectionInspector();
		refreshIssues();
		editor.setOpening(false);
	}

	@Override public CreateUI getElementFromGUI() {
		saveCurrentAction();
		applyAutoSizeToAllButtons();
		CreateUI createUI = new CreateUI(modElement);
		createUI.targetScreen = (String) targetScreen.getSelectedItem();
		createUI.replaceVanillaMenu = replaceVanillaMenu.isSelected();
		createUI.responsiveLayout = responsiveLayout.isSelected();
		createUI.responsiveMode = (String) responsiveMode.getSelectedItem();
		createUI.safeMargin = ((Number) safeMargin.getValue()).intValue();
		createUI.titleText = titleText.getText();
		createUI.subtitleText = subtitleText.getText();
		createUI.titleColor = titleColor.getText();
		createUI.subtitleColor = subtitleColor.getText();
		createUI.accentColor = accentColor.getText();
		createUI.backgroundImages = getBackgroundImageNames();
		createUI.backgroundMode = (String) backgroundMode.getSelectedItem();
		createUI.backgroundDurationTicks = ((Number) backgroundDurationTicks.getValue()).intValue();
		createUI.backgroundFadeTicks = ((Number) backgroundFadeTicks.getValue()).intValue();
		createUI.backgroundSlowZoom = backgroundSlowZoom.isSelected();
		createUI.backgroundZoomPercent = ((Number) backgroundZoomPercent.getValue()).intValue();
		createUI.backgroundZoomDurationTicks = ((Number) backgroundZoomDurationTicks.getValue()).intValue();
		createUI.backgroundOverlayColor = backgroundOverlayColor.getText();
		createUI.backgroundOverlayOpacity = ((Number) backgroundOverlayOpacity.getValue()).intValue();
		createUI.backgroundOverlayGradient = backgroundOverlayGradient.isSelected();
		createUI.menuMusic = menuMusic.getSound();
		createUI.loopMenuMusic = loopMenuMusic.isSelected();
		createUI.menuMusicVolume = ((Number) menuMusicVolume.getValue()).intValue();
		createUI.serverStatusEnabled = serverStatusEnabled.isSelected();
		createUI.serverStatusAddress = serverStatusAddress.getText();
		createUI.serverStatusRefreshTicks = ((Number) serverStatusRefreshTicks.getValue()).intValue();
		createUI.serverStatusX = ((Number) serverStatusX.getValue()).intValue();
		createUI.serverStatusY = ((Number) serverStatusY.getValue()).intValue();
		createUI.serverStatusShowMOTD = serverStatusShowMOTD.isSelected();
		createUI.serverStatusShowPlayers = serverStatusShowPlayers.isSelected();
		createUI.serverStatusShowPing = serverStatusShowPing.isSelected();
		createUI.livePreviewEnabled = livePreviewEnabled.isSelected();
		createUI.showSingleplayer = showSingleplayer.isSelected();
		createUI.showMultiplayer = showMultiplayer.isSelected();
		createUI.showOptions = showOptions.isSelected();
		createUI.showQuit = showQuit.isSelected();
		createUI.components = new ArrayList<>(editor.getComponentList());
		createUI.buttonActions = new ArrayList<>(buttonActions);
		createUI.gridSettings.sx = (int) editor.sx.getValue();
		createUI.gridSettings.sy = (int) editor.sy.getValue();
		createUI.gridSettings.ox = (int) editor.ox.getValue();
		createUI.gridSettings.oy = (int) editor.oy.getValue();
		createUI.gridSettings.snapOnGrid = editor.snapOnGrid.isSelected();
		return createUI;
	}

	private record PreviewTransform(Rectangle screen, int x, int y, double scale) {}
	private record PreviewSnap(int adjustment, int guide) {}
	private enum ResizeHandle {
		NORTH_WEST, NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST
	}

	private final class CreateUIPreviewPanel extends JPanel {
		private final Map<String, BufferedImage> imageCache = new LinkedHashMap<>();
		private final long previewStartedAtTicks = System.currentTimeMillis() / 50L;
		private final javax.swing.Timer animationTimer;
		private boolean mousePressed;
		private Point dragStart;
		private GUIComponent dragPrimary;
		private final Map<GUIComponent, Point> dragOrigins = new LinkedHashMap<>();
		private GUIComponent resizingComponent;
		private ResizeHandle resizeHandle;
		private Rectangle resizeOrigin;
		private boolean directManipulation;
		private int guideX = Integer.MIN_VALUE;
		private int guideY = Integer.MIN_VALUE;
		private final JTextArea inlineTextEditor = new JTextArea();
		private GUIComponent inlineEditingComponent;
		private String inlineOriginalText = "";

		private CreateUIPreviewPanel() {
			setOpaque(false);
			setLayout(null);
			setPreferredSize(new Dimension(760, 428));
			setFocusable(true);
			inlineTextEditor.setVisible(false);
			inlineTextEditor.setLineWrap(true);
			inlineTextEditor.setWrapStyleWord(true);
			inlineTextEditor.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(new Color(233, 30, 121), 2),
					BorderFactory.createEmptyBorder(4, 5, 4, 5)));
			inlineTextEditor.getDocument().addDocumentListener(new DocumentListener() {
				private void updateText() {
					if (inlineEditingComponent == null)
						return;
					setPreviewComponentText(inlineEditingComponent, inlineTextEditor.getText());
					updatingPreviewInspector = true;
					previewTextEditor.setText(inlineTextEditor.getText());
					if (inlineEditingComponent instanceof Button button && button.autoSize) {
						previewWidth.setValue(Math.max(1, button.width));
						previewHeight.setValue(Math.max(1, button.height));
					}
					updatingPreviewInspector = false;
					updateInlineEditorBounds();
					editor.editor.repaint();
					repaint();
				}

				@Override public void insertUpdate(DocumentEvent event) {
					updateText();
				}

				@Override public void removeUpdate(DocumentEvent event) {
					updateText();
				}

				@Override public void changedUpdate(DocumentEvent event) {
					updateText();
				}
			});
			inlineTextEditor.addFocusListener(new FocusAdapter() {
				@Override public void focusLost(FocusEvent event) {
					finishInlineTextEditing(true);
				}
			});
			bindInlineEditorKey("control ENTER", "commit", () -> finishInlineTextEditing(true));
			bindInlineEditorKey("ESCAPE", "cancel", () -> finishInlineTextEditing(false));
			add(inlineTextEditor);
			MouseAdapter mouseHandler = new MouseAdapter() {
				@Override public void mousePressed(MouseEvent event) {
					mousePressed = true;
					requestFocusInWindow();
					if (event.isPopupTrigger()) {
						showPreviewContextMenu(event);
						return;
					}
					if (!SwingUtilities.isLeftMouseButton(event))
						return;
					beginDirectManipulation(event);
					repaint();
				}

				@Override public void mouseDragged(MouseEvent event) {
					continueDirectManipulation(event);
				}

				@Override public void mouseMoved(MouseEvent event) {
					updatePreviewCursor(event.getPoint());
				}

				@Override public void mouseExited(MouseEvent event) {
					if (!directManipulation)
						setCursor(Cursor.getDefaultCursor());
				}

				@Override public void mouseReleased(MouseEvent event) {
					mousePressed = false;
					if (event.isPopupTrigger()) {
						showPreviewContextMenu(event);
						return;
					}
					finishDirectManipulation();
					repaint();
				}

				@Override public void mouseClicked(MouseEvent event) {
					if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2)
						return;
					GUIComponent component = findPreviewComponentAt(toDesignPoint(event.getPoint()));
					if (component == null)
						return;
					if (!component.locked && previewText(component) != null)
						startInlineTextEditing(component);
					else
						editSelectedComponent();
				}
			};
			addMouseListener(mouseHandler);
			addMouseMotionListener(mouseHandler);
			installPreviewKeyBindings();
			animationTimer = new javax.swing.Timer(80, _ -> repaint());
			animationTimer.start();
		}

		private void stopAnimation() {
			animationTimer.stop();
		}

		private void clearCache() {
			imageCache.clear();
		}

		@Override public void doLayout() {
			updateInlineEditorBounds();
		}

		private void bindInlineEditorKey(String stroke, String name, Runnable action) {
			String actionName = "remark-inline-" + name;
			inlineTextEditor.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(stroke), actionName);
			inlineTextEditor.getActionMap().put(actionName, new AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					action.run();
				}
			});
		}

		private void startInlineTextEditing(GUIComponent component) {
			String text = previewText(component);
			if (text == null)
				return;
			finishInlineTextEditing(true);
			recordHistoryCheckpoint();
			inlineEditingComponent = null;
			inlineTextEditor.setText(text);
			inlineOriginalText = text;
			inlineEditingComponent = component;
			updateInlineEditorBounds();
			inlineTextEditor.setVisible(true);
			inlineTextEditor.requestFocusInWindow();
			inlineTextEditor.selectAll();
		}

		private void finishInlineTextEditing(boolean commit) {
			GUIComponent component = inlineEditingComponent;
			if (component == null)
				return;
			inlineEditingComponent = null;
			if (!commit)
				setPreviewComponentText(component, inlineOriginalText);
			inlineTextEditor.setVisible(false);
			refreshStudioCanvas();
			recordHistoryCheckpoint();
			requestFocusInWindow();
		}

		private void updateInlineEditorBounds() {
			if (inlineEditingComponent == null)
				return;
			PreviewTransform transform = previewTransform(fitScreenBounds());
			int x = transform.x + (int) Math.round(inlineEditingComponent.x * transform.scale);
			int y = transform.y + (int) Math.round(inlineEditingComponent.y * transform.scale);
			int width = Math.max(140, (int) Math.round(
					inlineEditingComponent.getWidth(mcreator.getWorkspace()) * transform.scale));
			int height = Math.max(38, (int) Math.round(
					inlineEditingComponent.getHeight(mcreator.getWorkspace()) * transform.scale));
			x = Math.max(transform.screen.x, Math.min(x, transform.screen.x + transform.screen.width - 40));
			y = Math.max(transform.screen.y, Math.min(y, transform.screen.y + transform.screen.height - 30));
			width = Math.max(40, Math.min(width, transform.screen.x + transform.screen.width - x));
			height = Math.max(30, Math.min(height, transform.screen.y + transform.screen.height - y));
			inlineTextEditor.setBounds(x, y, width, height);
		}

		private void selectionChanged() {
			if (inlineEditingComponent != null && !selectedComponents().contains(inlineEditingComponent))
				finishInlineTextEditing(true);
		}

		@Nullable private Point toDesignPoint(@Nullable Point point) {
			if (point == null)
				return null;
			PreviewTransform transform = previewTransform(fitScreenBounds());
			if (!transform.screen.contains(point))
				return null;
			int x = (int) Math.floor((point.x - transform.x) / transform.scale);
			int y = (int) Math.floor((point.y - transform.y) / transform.scale);
			return new Point(Math.max(0, Math.min(WYSIWYG.W, x)), Math.max(0, Math.min(WYSIWYG.H, y)));
		}

		private PreviewTransform previewTransform(Rectangle screen) {
			Dimension resolution = selectedPreviewLogicalResolution();
			double viewportScale = Math.min(screen.width / (double) resolution.width,
					screen.height / (double) resolution.height);
			double layoutScale = 1.0;
			if (responsiveLayout.isSelected()) {
				double fit = Math.min((resolution.width - ((Number) safeMargin.getValue()).intValue() * 2.0) / WYSIWYG.W,
						(resolution.height - ((Number) safeMargin.getValue()).intValue() * 2.0) / WYSIWYG.H);
				String mode = String.valueOf(responsiveMode.getSelectedItem());
				if (CreateUI.RESPONSIVE_FIT.equals(mode))
					layoutScale = Math.max(0.1, fit);
				else if (CreateUI.RESPONSIVE_FIT_DOWN.equals(mode))
					layoutScale = Math.max(0.1, Math.min(1.0, fit));
			}
			double scale = Math.max(0.01, viewportScale * layoutScale);
			int x = screen.x + (int) Math.round((screen.width - WYSIWYG.W * scale) / 2.0);
			int y = screen.y + (int) Math.round((screen.height - WYSIWYG.H * scale) / 2.0);
			return new PreviewTransform(screen, x, y, scale);
		}

		private void beginDirectManipulation(MouseEvent event) {
			Point point = toDesignPoint(event.getPoint());
			List<GUIComponent> selected = selectedComponents();
			GUIComponent selectedSingle = selected.size() == 1 ? selected.getFirst() : null;
			ResizeHandle selectedHandle = findResizeHandle(selectedSingle, point);
			GUIComponent hit = selectedHandle != null ? selectedSingle : findPreviewComponentAt(point);
			if (hit == null) {
				if (!event.isControlDown())
					editor.list.clearSelection();
				return;
			}
			if (selectedHandle != null && !hit.locked) {
				recordHistoryCheckpoint();
				directManipulation = true;
				dragStart = point;
				dragPrimary = hit;
				dragOrigins.clear();
				resizingComponent = hit;
				resizeHandle = selectedHandle;
				resizeOrigin = new Rectangle(hit.x, hit.y,
						Math.max(1, hit.getWidth(mcreator.getWorkspace())),
						Math.max(1, hit.getHeight(mcreator.getWorkspace())));
				return;
			}

			int index = editor.components.indexOf(hit);
			if (event.isControlDown()) {
				if (editor.list.isSelectedIndex(index))
					editor.list.removeSelectionInterval(index, index);
				else
					editor.list.addSelectionInterval(index, index);
			} else if (!editor.list.isSelectedIndex(index)) {
				editor.list.setSelectedIndex(index);
			}
			if (!editor.list.isSelectedIndex(index) || hit.locked)
				return;

			recordHistoryCheckpoint();
			directManipulation = true;
			dragStart = point;
			dragPrimary = hit;
			dragOrigins.clear();
			for (GUIComponent component : selectedComponents()) {
				if (!component.locked)
					dragOrigins.put(component, new Point(component.x, component.y));
			}
		}

		private void continueDirectManipulation(MouseEvent event) {
			if (!directManipulation || dragStart == null)
				return;
			Point point = toDesignPoint(event.getPoint());
			if (point == null)
				return;
			if (resizingComponent != null) {
				resizeComponentFromPoint(point);
			} else if (!dragOrigins.isEmpty() && dragPrimary != null) {
				Point primaryOrigin = dragOrigins.get(dragPrimary);
				if (primaryOrigin == null)
					primaryOrigin = dragOrigins.values().iterator().next();
				int deltaX = snapPreviewCoordinate(primaryOrigin.x + point.x - dragStart.x, true) - primaryOrigin.x;
				int deltaY = snapPreviewCoordinate(primaryOrigin.y + point.y - dragStart.y, false) - primaryOrigin.y;
				PreviewSnap snapX = findSmartSnap(primaryOrigin.x + deltaX,
						dragPrimary.getWidth(mcreator.getWorkspace()), true);
				PreviewSnap snapY = findSmartSnap(primaryOrigin.y + deltaY,
						dragPrimary.getHeight(mcreator.getWorkspace()), false);
				deltaX += snapX.adjustment;
				deltaY += snapY.adjustment;
				guideX = snapX.guide;
				guideY = snapY.guide;
				for (Map.Entry<GUIComponent, Point> entry : dragOrigins.entrySet()) {
					entry.getKey().x = entry.getValue().x + deltaX;
					entry.getKey().y = entry.getValue().y + deltaY;
				}
			}
			refreshDirectManipulation();
		}

		private void finishDirectManipulation() {
			if (!directManipulation)
				return;
			directManipulation = false;
			dragStart = null;
			dragPrimary = null;
			dragOrigins.clear();
			resizingComponent = null;
			resizeHandle = null;
			resizeOrigin = null;
			guideX = Integer.MIN_VALUE;
			guideY = Integer.MIN_VALUE;
			setCursor(Cursor.getDefaultCursor());
			refreshStudioCanvas();
			recordHistoryCheckpoint();
		}

		private void resizeComponentFromPoint(Point point) {
			if (resizingComponent == null || resizeHandle == null || resizeOrigin == null)
				return;
			int left = resizeOrigin.x;
			int top = resizeOrigin.y;
			int right = resizeOrigin.x + resizeOrigin.width;
			int bottom = resizeOrigin.y + resizeOrigin.height;
			int snappedX = snapPreviewCoordinate(point.x, true);
			int snappedY = snapPreviewCoordinate(point.y, false);
			switch (resizeHandle) {
			case NORTH_WEST -> {
				left = snappedX;
				top = snappedY;
			}
			case NORTH -> top = snappedY;
			case NORTH_EAST -> {
				right = snappedX;
				top = snappedY;
			}
			case EAST -> right = snappedX;
			case SOUTH_EAST -> {
				right = snappedX;
				bottom = snappedY;
			}
			case SOUTH -> bottom = snappedY;
			case SOUTH_WEST -> {
				left = snappedX;
				bottom = snappedY;
			}
			case WEST -> left = snappedX;
			}
			if (right <= left) {
				if (resizeHandle == ResizeHandle.NORTH_WEST || resizeHandle == ResizeHandle.SOUTH_WEST
						|| resizeHandle == ResizeHandle.WEST)
					left = right - 1;
				else
					right = left + 1;
			}
			if (bottom <= top) {
				if (resizeHandle == ResizeHandle.NORTH_WEST || resizeHandle == ResizeHandle.NORTH
						|| resizeHandle == ResizeHandle.NORTH_EAST)
					top = bottom - 1;
				else
					bottom = top + 1;
			}
			setPreviewComponentBounds(resizingComponent, left, top, right - left, bottom - top);
		}

		private int snapPreviewCoordinate(int value, boolean horizontal) {
			if (!editor.snapOnGrid.isSelected())
				return value;
			int spacing = Math.max(1, ((Number) (horizontal ? editor.sx : editor.sy).getValue()).intValue());
			int offset = ((Number) (horizontal ? editor.ox : editor.oy).getValue()).intValue();
			return Math.round((value - offset) / (float) spacing) * spacing + offset;
		}

		private PreviewSnap findSmartSnap(int position, int size, boolean horizontal) {
			List<Integer> targets = new ArrayList<>();
			targets.add(0);
			targets.add((horizontal ? WYSIWYG.W : WYSIWYG.H) / 2);
			targets.add(horizontal ? WYSIWYG.W : WYSIWYG.H);
			for (GUIComponent component : editor.getComponentList()) {
				if (!component.visible || dragOrigins.containsKey(component))
					continue;
				int start = horizontal ? component.x : component.y;
				int componentSize = horizontal ? component.getWidth(mcreator.getWorkspace()) :
						component.getHeight(mcreator.getWorkspace());
				targets.add(start);
				targets.add(start + componentSize / 2);
				targets.add(start + componentSize);
			}

			int[] points = new int[] { position, position + size / 2, position + size };
			int bestAdjustment = 0;
			int bestGuide = Integer.MIN_VALUE;
			int bestDistance = 4;
			for (int target : targets) {
				for (int ownPoint : points) {
					int adjustment = target - ownPoint;
					int distance = Math.abs(adjustment);
					if (distance < bestDistance) {
						bestDistance = distance;
						bestAdjustment = adjustment;
						bestGuide = target;
					}
				}
			}
			return new PreviewSnap(bestAdjustment, bestGuide);
		}

		private void setPreviewComponentSize(GUIComponent component, int width, int height) {
			if (component instanceof SizedComponent sized) {
				sized.width = width;
				sized.height = height;
				if (sized instanceof Button button)
					button.autoSize = false;
			} else if (component instanceof net.mcreator.element.parts.gui.Image image) {
				image.customWidth = width;
				image.customHeight = height;
				image.use1Xscale = false;
			}
		}

		private void setPreviewComponentBounds(GUIComponent component, int x, int y, int width, int height) {
			component.x = x;
			component.y = y;
			setPreviewComponentSize(component, Math.max(1, width), Math.max(1, height));
		}

		private void refreshDirectManipulation() {
			editor.list.repaint();
			editor.editor.repaint();
			updateSelectionInspector();
			updatePreviewInspector();
			repaint();
		}

		@Nullable private GUIComponent findPreviewComponentAt(@Nullable Point point) {
			if (point == null)
				return null;
			List<GUIComponent> components = new ArrayList<>(editor.getComponentList());
			components.sort(Collections.reverseOrder());
			for (GUIComponent component : components) {
				if (!component.visible)
					continue;
				int width = component.getWidth(mcreator.getWorkspace());
				int height = component.getHeight(mcreator.getWorkspace());
				if (point.x >= component.x && point.x <= component.x + width && point.y >= component.y
						&& point.y <= component.y + height)
					return component;
			}
			return null;
		}

		@Nullable private ResizeHandle findResizeHandle(@Nullable GUIComponent component, @Nullable Point point) {
			if (component == null || point == null || component.locked
					|| !(component instanceof SizedComponent || component instanceof net.mcreator.element.parts.gui.Image))
				return null;
			int width = component.getWidth(mcreator.getWorkspace());
			int height = component.getHeight(mcreator.getWorkspace());
			int left = component.x;
			int top = component.y;
			int right = left + width;
			int bottom = top + height;
			int centerX = left + width / 2;
			int centerY = top + height / 2;
			int tolerance = 5;
			boolean nearLeft = Math.abs(point.x - left) <= tolerance;
			boolean nearRight = Math.abs(point.x - right) <= tolerance;
			boolean nearTop = Math.abs(point.y - top) <= tolerance;
			boolean nearBottom = Math.abs(point.y - bottom) <= tolerance;
			boolean insideHorizontal = point.x >= left - tolerance && point.x <= right + tolerance;
			boolean insideVertical = point.y >= top - tolerance && point.y <= bottom + tolerance;
			if (nearLeft && nearTop)
				return ResizeHandle.NORTH_WEST;
			if (nearRight && nearTop)
				return ResizeHandle.NORTH_EAST;
			if (nearRight && nearBottom)
				return ResizeHandle.SOUTH_EAST;
			if (nearLeft && nearBottom)
				return ResizeHandle.SOUTH_WEST;
			if (insideHorizontal && Math.abs(point.y - top) <= tolerance && Math.abs(point.x - centerX) <= tolerance)
				return ResizeHandle.NORTH;
			if (insideVertical && Math.abs(point.x - right) <= tolerance && Math.abs(point.y - centerY) <= tolerance)
				return ResizeHandle.EAST;
			if (insideHorizontal && Math.abs(point.y - bottom) <= tolerance && Math.abs(point.x - centerX) <= tolerance)
				return ResizeHandle.SOUTH;
			if (insideVertical && Math.abs(point.x - left) <= tolerance && Math.abs(point.y - centerY) <= tolerance)
				return ResizeHandle.WEST;
			return null;
		}

		private void updatePreviewCursor(Point panelPoint) {
			Point point = toDesignPoint(panelPoint);
			List<GUIComponent> selected = selectedComponents();
			ResizeHandle handle = selected.size() == 1 ? findResizeHandle(selected.getFirst(), point) : null;
			if (handle != null) {
				setCursor(Cursor.getPredefinedCursor(switch (handle) {
				case NORTH_WEST -> Cursor.NW_RESIZE_CURSOR;
				case NORTH -> Cursor.N_RESIZE_CURSOR;
				case NORTH_EAST -> Cursor.NE_RESIZE_CURSOR;
				case EAST -> Cursor.E_RESIZE_CURSOR;
				case SOUTH_EAST -> Cursor.SE_RESIZE_CURSOR;
				case SOUTH -> Cursor.S_RESIZE_CURSOR;
				case SOUTH_WEST -> Cursor.SW_RESIZE_CURSOR;
				case WEST -> Cursor.W_RESIZE_CURSOR;
				}));
				return;
			}
			GUIComponent hit = findPreviewComponentAt(point);
			setCursor(Cursor.getPredefinedCursor(hit != null && !hit.locked ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
		}

		private void showPreviewContextMenu(MouseEvent event) {
			Point point = toDesignPoint(event.getPoint());
			GUIComponent hit = findPreviewComponentAt(point);
			if (hit != null && !editor.list.getSelectedValuesList().contains(hit))
				editor.list.setSelectedValue(hit, true);

			Point addPoint = point != null ? point : new Point(WYSIWYG.W / 2, WYSIWYG.H / 2);
			JPopupMenu menu = new JPopupMenu();
			menu.add(createQuickActionButtonMenu(addPoint));
			JMenu add = new JMenu(L10N.t("elementgui.createui.preview_add"));
			add.add(previewMenuItem("elementgui.createui.preview_add_button", () -> addPreviewButton(false, addPoint)));
			add.add(previewMenuItem("elementgui.createui.preview_add_text_button", () -> addPreviewButton(true, addPoint)));
			add.add(previewMenuItem("elementgui.createui.preview_add_label", () -> addPreviewLabel(addPoint)));
			add.add(previewMenuItem("elementgui.createui.preview_add_image", () -> addPreviewImages(addPoint)));
			JMenu more = new JMenu(L10N.t("elementgui.createui.preview_more_elements"));
			for (WYSIWYGComponentRegistration<?> registration : WYSIWYGEditor.COMPONENT_REGISTRY) {
				if (net.mcreator.element.parts.gui.Slot.class.isAssignableFrom(registration.component())
						|| Set.of("button", "text_label", "image").contains(registration.machineName()))
					continue;
				more.add(previewMenuItem("elementgui.gui.add_" + registration.machineName(),
						() -> addPreviewElement(registration.machineName(), addPoint)));
			}
			add.add(more);
			menu.add(add);
			if (hit != null) {
				menu.addSeparator();
				menu.add(previewMenuItem("elementgui.createui.context_edit", CreateUIGUI.this::editSelectedComponent));
				if (hit instanceof Button button) {
					menu.add(createButtonActionMenu(button));
					menu.add(previewMenuItem("elementgui.createui.button_style",
							CreateUIGUI.this::configureSelectedButtonStyle));
					menu.add(previewMenuItem("elementgui.createui.auto_size",
							CreateUIGUI.this::configureAutoSizeForSelection));
					menu.add(previewMenuItem(button.isUndecorated ?
							"elementgui.createui.context_button_background" :
							"elementgui.createui.context_button_text_only", () -> toggleButtonDecoration(button)));
				}
				if (hit instanceof SizedComponent || hit instanceof net.mcreator.element.parts.gui.Image)
					menu.add(previewMenuItem("elementgui.createui.context_resize", () -> resizeComponent(hit)));
				if (hit instanceof net.mcreator.element.parts.gui.Image image) {
					menu.add(previewMenuItem("elementgui.createui.context_image_original_size",
							() -> resetImageSize(image)));
					menu.add(previewMenuItem("elementgui.createui.context_image_animation",
							() -> configureImageAnimation(image)));
				}
				menu.add(previewMenuItem("elementgui.createui.animation",
						CreateUIGUI.this::configureAnimationForSelection));

				JMenu arrange = new JMenu(L10N.t("elementgui.createui.context_arrange"));
				arrange.add(previewMenuItem("elementgui.createui.front", () -> changeSelectionLayer(true)));
				arrange.add(previewMenuItem("elementgui.createui.back", () -> changeSelectionLayer(false)));
				arrange.addSeparator();
				arrange.add(previewMenuItem("elementgui.createui.align_left", () -> alignSelection("LEFT")));
				arrange.add(previewMenuItem("elementgui.createui.align_center", () -> alignSelection("CENTER")));
				arrange.add(previewMenuItem("elementgui.createui.align_right", () -> alignSelection("RIGHT")));
				arrange.add(previewMenuItem("elementgui.createui.align_top", () -> alignSelection("TOP")));
				arrange.add(previewMenuItem("elementgui.createui.align_middle", () -> alignSelection("MIDDLE")));
				arrange.add(previewMenuItem("elementgui.createui.align_bottom", () -> alignSelection("BOTTOM")));
				arrange.addSeparator();
				arrange.add(previewMenuItem("elementgui.createui.distribute_horizontal",
						() -> distributeSelection(true)));
				arrange.add(previewMenuItem("elementgui.createui.distribute_vertical",
						() -> distributeSelection(false)));
				menu.add(arrange);
				menu.add(previewMenuItem("elementgui.createui.group", CreateUIGUI.this::groupSelection));
				menu.add(previewMenuItem("elementgui.createui.ungroup", CreateUIGUI.this::ungroupSelection));
				menu.add(previewMenuItem("elementgui.createui.show_hide",
						CreateUIGUI.this::toggleSelectionVisibility));
				menu.add(previewMenuItem("elementgui.createui.context_lock_unlock",
						CreateUIGUI.this::toggleSelectionLock));
				menu.addSeparator();
				menu.add(previewMenuItem("elementgui.createui.context_copy", CreateUIGUI.this::duplicatePreviewSelection));
				menu.add(previewMenuItem("elementgui.createui.component_save",
						CreateUIGUI.this::saveSelectionToComponentLibrary));
				menu.add(previewMenuItem("elementgui.createui.component_insert",
						CreateUIGUI.this::insertFromComponentLibrary));
				menu.add(previewMenuItem("elementgui.createui.context_delete", CreateUIGUI.this::deletePreviewSelection));
			} else {
				menu.addSeparator();
				menu.add(previewMenuItem("elementgui.createui.component_insert",
						CreateUIGUI.this::insertFromComponentLibrary));
			}
			menu.show(this, event.getX(), event.getY());
		}

		private JMenuItem previewMenuItem(String key, Runnable action) {
			JMenuItem item = new JMenuItem(L10N.t(key));
			item.addActionListener(_ -> action.run());
			return item;
		}

		private void installPreviewKeyBindings() {
			bindPreviewKey("DELETE", "delete", CreateUIGUI.this::deletePreviewSelection);
			bindPreviewKey("control D", "duplicate", CreateUIGUI.this::duplicatePreviewSelection);
			bindPreviewKey("ENTER", "edit", CreateUIGUI.this::editSelectedComponent);
			bindPreviewKey("LEFT", "left", () -> nudgeSelection(-1, 0));
			bindPreviewKey("RIGHT", "right", () -> nudgeSelection(1, 0));
			bindPreviewKey("UP", "up", () -> nudgeSelection(0, -1));
			bindPreviewKey("DOWN", "down", () -> nudgeSelection(0, 1));
			bindPreviewKey("shift LEFT", "left-fast", () -> nudgeSelection(-10, 0));
			bindPreviewKey("shift RIGHT", "right-fast", () -> nudgeSelection(10, 0));
			bindPreviewKey("shift UP", "up-fast", () -> nudgeSelection(0, -10));
			bindPreviewKey("shift DOWN", "down-fast", () -> nudgeSelection(0, 10));
		}

		private void bindPreviewKey(String stroke, String name, Runnable action) {
			String actionName = "remark-preview-" + name;
			getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(stroke), actionName);
			getActionMap().put(actionName, new AbstractAction() {
				@Override public void actionPerformed(java.awt.event.ActionEvent event) {
					action.run();
				}
			});
		}

		private void nudgeSelection(int deltaX, int deltaY) {
			List<GUIComponent> selected = selectedComponents().stream().filter(component -> !component.locked).toList();
			if (selected.isEmpty())
				return;
			recordHistoryCheckpoint();
			for (GUIComponent component : selected) {
				component.x += deltaX;
				component.y += deltaY;
			}
			refreshStudioCanvas();
			recordHistoryCheckpoint();
		}

		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			g.setColor(new Color(8, 8, 10));
			g.fillRect(0, 0, getWidth(), getHeight());

			Rectangle screen = fitScreenBounds();
			Shape previousClip = g.getClip();
			g.setClip(screen);
			paintPreviewBackground(g, screen);
			paintPreviewOverlay(g, screen);
			paintPreviewComponents(g, screen);
			g.setClip(previousClip);

			g.setColor(new Color(255, 255, 255, 55));
			g.drawRect(screen.x, screen.y, screen.width - 1, screen.height - 1);
			g.dispose();
		}

		private Rectangle fitScreenBounds() {
			Dimension resolution = selectedPreviewResolution();
			double targetRatio = resolution.width / (double) resolution.height;
			int width = getWidth() - 24;
			int height = getHeight() - 24;
			if (width / (double) height > targetRatio) {
				width = (int) Math.round(height * targetRatio);
			} else {
				height = (int) Math.round(width / targetRatio);
			}
			return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
		}

		private void paintPreviewBackground(Graphics2D g, Rectangle screen) {
			List<String> images = getBackgroundImageNames();
			if (images.isEmpty()) {
				if (CreateUI.TARGET_PAUSE_MENU.equals(targetScreen.getSelectedItem())) {
					for (int y = 0; y < screen.height; y += 20)
						for (int x = 0; x < screen.width; x += 20) {
							g.setColor((x / 20 + y / 20) % 2 == 0 ? new Color(75, 78, 80) : new Color(95, 98, 100));
							g.fillRect(screen.x + x, screen.y + y, Math.min(20, screen.width - x), Math.min(20, screen.height - y));
						}
					return;
				}
				GradientPaint gradient = new GradientPaint(screen.x, screen.y, new Color(18, 8, 20),
						screen.x, screen.y + screen.height, new Color(4, 4, 5));
				g.setPaint(gradient);
				g.fillRect(screen.x, screen.y, screen.width, screen.height);
				g.setPaint(null);
				g.setColor(parseColor(accentColor.getText(), new Color(233, 30, 121, 180)));
				g.fillRect(screen.x, screen.y, screen.width, Math.max(34, screen.height / 6));
				return;
			}

			String mode = (String) backgroundMode.getSelectedItem();
			if (images.size() == 1 || CreateUI.BACKGROUND_STATIC.equals(mode)) {
				drawCover(g, loadBackgroundImage(images.get(0)), screen, 1f);
				return;
			}

			int duration = Math.max(1, ((Number) backgroundDurationTicks.getValue()).intValue());
			long tick = System.currentTimeMillis() / 50L;
			int index = (int) ((tick / duration) % images.size());
			drawCover(g, loadBackgroundImage(images.get(index)), screen, 1f);

			if (CreateUI.BACKGROUND_FADE.equals(mode)) {
				int fade = Math.min(Math.max(0, ((Number) backgroundFadeTicks.getValue()).intValue()), duration);
				int phase = (int) (tick % duration);
				if (fade > 0 && phase >= duration - fade) {
					float alpha = (phase - (duration - fade)) / (float) fade;
					drawCover(g, loadBackgroundImage(images.get((index + 1) % images.size())), screen, alpha);
				}
			}
		}

		@Nullable private BufferedImage loadBackgroundImage(String image) {
			if (imageCache.containsKey(image))
				return imageCache.get(image);

			try {
				File texture = mcreator.getFolderManager()
						.getTextureFile(FilenameUtilsPatched.removeExtension(image), TextureType.SCREEN);
				BufferedImage bufferedImage = ImageIO.read(texture);
				imageCache.put(image, bufferedImage);
				return bufferedImage;
			} catch (Exception ignored) {
				imageCache.put(image, null);
				return null;
			}
		}

		private void drawCover(Graphics2D g, @Nullable BufferedImage image, Rectangle screen, float alpha) {
			if (image == null) {
				g.setColor(new Color(18, 8, 20));
				g.fillRect(screen.x, screen.y, screen.width, screen.height);
				return;
			}

			Composite composite = g.getComposite();
			if (alpha < 1f)
				g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
			double scale = Math.max(screen.width / (double) image.getWidth(), screen.height / (double) image.getHeight());
			if (backgroundSlowZoom.isSelected()) {
				int duration = Math.max(1, ((Number) backgroundZoomDurationTicks.getValue()).intValue());
				double phase = (System.currentTimeMillis() / 50.0 % duration) / duration;
				double amount = ((Number) backgroundZoomPercent.getValue()).doubleValue() / 100.0;
				scale *= 1.0 + amount * (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0));
			}
			int drawWidth = (int) Math.ceil(image.getWidth() * scale);
			int drawHeight = (int) Math.ceil(image.getHeight() * scale);
			int x = screen.x + (screen.width - drawWidth) / 2;
			int y = screen.y + (screen.height - drawHeight) / 2;
			g.drawImage(image, x, y, drawWidth, drawHeight, this);
			g.setComposite(composite);
		}

		private void paintPreviewOverlay(Graphics2D g, Rectangle screen) {
			int opacity = Math.max(0, Math.min(100, ((Number) backgroundOverlayOpacity.getValue()).intValue()));
			if (opacity == 0)
				return;

			Color base = parseColor(backgroundOverlayColor.getText(), Color.BLACK);
			int alpha = Math.round(opacity * 255.0F / 100.0F);
			if (backgroundOverlayGradient.isSelected()) {
				g.setPaint(new GradientPaint(screen.x, screen.y, new Color(base.getRed(), base.getGreen(), base.getBlue(), 0),
						screen.x, screen.y + screen.height,
						new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha)));
				g.fillRect(screen.x, screen.y, screen.width, screen.height);
				g.setPaint(null);
			} else {
				g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
				g.fillRect(screen.x, screen.y, screen.width, screen.height);
			}
		}

		private void paintPreviewComponents(Graphics2D g, Rectangle screen) {
			PreviewTransform transform = previewTransform(screen);
			Point mouse = getMousePosition();
			int designMouseX = mouse != null ? (int) Math.floor((mouse.x - transform.x) / transform.scale) :
					Integer.MIN_VALUE;
			int designMouseY = mouse != null ? (int) Math.floor((mouse.y - transform.y) / transform.scale) :
					Integer.MIN_VALUE;

			Graphics2D componentGraphics = (Graphics2D) g.create();
			componentGraphics.translate(transform.x, transform.y);
			componentGraphics.scale(transform.scale, transform.scale);
			componentGraphics.setFont(net.mcreator.ui.wysiwyg.WYSIWYG.fontMC);
			componentGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
			paintPreviewGrid(componentGraphics);

			List<GUIComponent> components = new ArrayList<>(editor.getComponentList());
			if (components.isEmpty()) {
				paintFallbackMenu(componentGraphics);
			} else {
				components.stream().filter(component -> component.visible).sorted().forEach(component -> {
					Font originalFont = componentGraphics.getFont();
					Stroke originalStroke = componentGraphics.getStroke();
					Composite originalComposite = componentGraphics.getComposite();
					AffineTransform originalTransform = componentGraphics.getTransform();
					paintAnimatedComponent(componentGraphics, component, designMouseX, designMouseY);
					componentGraphics.setTransform(originalTransform);
					componentGraphics.setComposite(originalComposite);
					componentGraphics.setFont(originalFont);
					componentGraphics.setStroke(originalStroke);
				});
			}
			paintPreviewServerStatus(componentGraphics);
			paintPreviewSelection(componentGraphics, transform.scale);
			componentGraphics.dispose();
		}

		private void paintPreviewSelection(Graphics2D g, double previewScale) {
			List<GUIComponent> selected = selectedComponents();
			if (selected.isEmpty())
				return;
			g.setStroke(new BasicStroke((float) Math.max(0.7, 1.5 / previewScale)));
			for (GUIComponent component : selected) {
				if (!component.visible)
					continue;
				int width = Math.max(1, component.getWidth(mcreator.getWorkspace()));
				int height = Math.max(1, component.getHeight(mcreator.getWorkspace()));
				g.setColor(component.locked ? new Color(255, 215, 106, 210) : new Color(233, 30, 121, 230));
				g.drawRect(component.x - 1, component.y - 1, width + 2, height + 2);
				if (!component.locked && (component instanceof SizedComponent
						|| component instanceof net.mcreator.element.parts.gui.Image)) {
					int handleSize = Math.max(4, (int) Math.ceil(7.0 / Math.max(0.1, previewScale)));
					int half = handleSize / 2;
					int left = component.x;
					int top = component.y;
					int right = component.x + width;
					int bottom = component.y + height;
					int centerX = component.x + width / 2;
					int centerY = component.y + height / 2;
					for (Point handle : List.of(new Point(left, top), new Point(centerX, top), new Point(right, top),
							new Point(right, centerY), new Point(right, bottom), new Point(centerX, bottom),
							new Point(left, bottom), new Point(left, centerY))) {
						g.setColor(Color.WHITE);
						g.fillRect(handle.x - half, handle.y - half, handleSize, handleSize);
						g.setColor(new Color(233, 30, 121));
						g.drawRect(handle.x - half, handle.y - half, handleSize, handleSize);
					}
				}
			}
			paintPreviewGuides(g, previewScale);
		}

		private void paintPreviewGrid(Graphics2D g) {
			if (!editor.snapOnGrid.isSelected())
				return;
			g.setColor(new Color(255, 255, 255, 24));
			int spacingX = Math.max(1, ((Number) editor.sx.getValue()).intValue());
			int spacingY = Math.max(1, ((Number) editor.sy.getValue()).intValue());
			int offsetX = ((Number) editor.ox.getValue()).intValue();
			int offsetY = ((Number) editor.oy.getValue()).intValue();
			for (int x = offsetX; x <= WYSIWYG.W; x += spacingX)
				g.drawLine(x, 0, x, WYSIWYG.H);
			for (int y = offsetY; y <= WYSIWYG.H; y += spacingY)
				g.drawLine(0, y, WYSIWYG.W, y);
		}

		private void paintPreviewGuides(Graphics2D g, double previewScale) {
			if (guideX == Integer.MIN_VALUE && guideY == Integer.MIN_VALUE)
				return;
			Stroke previous = g.getStroke();
			g.setStroke(new BasicStroke((float) Math.max(0.7, 1.0 / previewScale), BasicStroke.CAP_BUTT,
					BasicStroke.JOIN_MITER, 10f, new float[] { 3f, 3f }, 0f));
			g.setColor(new Color(255, 215, 106, 230));
			if (guideX != Integer.MIN_VALUE)
				g.drawLine(guideX, 0, guideX, WYSIWYG.H);
			if (guideY != Integer.MIN_VALUE)
				g.drawLine(0, guideY, WYSIWYG.W, guideY);
			g.setStroke(previous);
		}

		private void paintAnimatedComponent(Graphics2D g, GUIComponent component, int mouseX, int mouseY) {
			double tick = System.currentTimeMillis() / 50.0 - previewStartedAtTicks;
			double progress = animationProgress(component, tick);
			String animation = component.animationType == null ? "NONE" : component.animationType;
			if ("FADE".equals(animation))
				g.setComposite(AlphaComposite.SrcOver.derive((float) progress));
			else if ("SLIDE_UP".equals(animation))
				g.translate(0, (1.0 - progress) * 30.0);
			else if ("SLIDE_LEFT".equals(animation))
				g.translate((1.0 - progress) * 40.0, 0);
			else if ("SCALE".equals(animation)) {
				double scale = 0.7 + progress * 0.3;
				g.translate(component.x + component.getWidth(mcreator.getWorkspace()) / 2.0,
						component.y + component.getHeight(mcreator.getWorkspace()) / 2.0);
				g.scale(scale, scale);
				g.translate(-(component.x + component.getWidth(mcreator.getWorkspace()) / 2.0),
						-(component.y + component.getHeight(mcreator.getWorkspace()) / 2.0));
			} else if ("PULSE".equals(animation)) {
				double scale = 1.0 + Math.sin(tick * Math.PI * 2.0 / Math.max(1, component.animationDurationTicks)) * 0.04;
				g.translate(component.x + component.getWidth(mcreator.getWorkspace()) / 2.0,
						component.y + component.getHeight(mcreator.getWorkspace()) / 2.0);
				g.scale(scale, scale);
				g.translate(-(component.x + component.getWidth(mcreator.getWorkspace()) / 2.0),
						-(component.y + component.getHeight(mcreator.getWorkspace()) / 2.0));
			}

			if (component instanceof Button button)
				paintPreviewButton(g, button, mouseX, mouseY);
			else if (component instanceof net.mcreator.element.parts.gui.Image image && image.pulseAnimation)
				paintAnimatedImage(g, image);
			else
				component.paintComponent(component.getX(), component.getY(), editor, g);
		}

		private double animationProgress(GUIComponent component, double tick) {
			if (component.animationType == null || "NONE".equals(component.animationType))
				return 1.0;
			int delay = Math.max(0, component.animationDelayTicks);
			int duration = Math.max(1, component.animationDurationTicks);
			if (component.animationLoop) {
				double phase = tick % (delay + duration);
				return phase < delay ? 0.0 : Math.min(1.0, (phase - delay) / duration);
			}
			return Math.max(0.0, Math.min(1.0, (tick - delay) / duration));
		}

		private void paintPreviewButton(Graphics2D g, Button button, int mouseX, int mouseY) {
			boolean hovered = mouseX >= button.x && mouseX <= button.x + button.width && mouseY >= button.y
					&& mouseY <= button.y + button.height;
			boolean pressed = hovered && mousePressed && button.enabled;
			String texture = !button.enabled ? button.disabledTexture : pressed ? button.pressedTexture :
					hovered ? button.hoverTexture : button.normalTexture;
			String color = !button.enabled ? button.disabledTextColor : pressed ? button.pressedTextColor :
					hovered ? button.hoverTextColor : button.normalTextColor;
			double hoverScale = hovered && button.enabled ? Math.max(0.5, button.hoverScalePercent / 100.0) : 1.0;
			if (hoverScale != 1.0) {
				g.translate(button.x + button.width / 2.0, button.y + button.height / 2.0);
				g.scale(hoverScale, hoverScale);
				g.translate(-(button.x + button.width / 2.0), -(button.y + button.height / 2.0));
			}
			BufferedImage textureImage = texture != null && !texture.isBlank() ? loadBackgroundImage(texture) : null;
			if (textureImage != null)
				g.drawImage(textureImage, button.x, button.y, button.width, button.height, this);
			else if (!button.isUndecorated)
				g.drawImage(net.mcreator.minecraft.MinecraftImageGenerator.generateButton(button.width, button.height),
						button.x, button.y, button.width, button.height, this);
			g.setColor(parseColor(color, button.enabled ? Color.WHITE : Color.GRAY));
			String[] lines = button.text.split("\\R", -1);
			int lineHeight = Math.max(1, (int) Math.ceil(WYSIWYG.fontMC.getStringBounds("Ag", WYSIWYG.frc)
					.getHeight()));
			int baseline = button.y + Math.max(lineHeight,
					(button.height - lineHeight * lines.length) / 2 + lineHeight) - 2;
			for (String line : lines) {
				int textWidth = (int) Math.ceil(WYSIWYG.fontMC.getStringBounds(line, WYSIWYG.frc).getWidth());
				g.drawString(line, button.x + (button.width - textWidth) / 2, baseline);
				baseline += lineHeight;
			}
		}

		private void paintPreviewServerStatus(Graphics2D g) {
			if (!serverStatusEnabled.isSelected())
				return;
			int x = ((Number) serverStatusX.getValue()).intValue();
			int y = ((Number) serverStatusY.getValue()).intValue();
			StringBuilder status = new StringBuilder("Online");
			if (serverStatusShowPlayers.isSelected())
				status.append("  42/100");
			if (serverStatusShowPing.isSelected())
				status.append("  48ms");
			g.setColor(new Color(92, 220, 120));
			g.fillOval(x, y + 2, 5, 5);
			g.setColor(Color.WHITE);
			g.drawString(status.toString(), x + 9, y + 8);
			if (serverStatusShowMOTD.isSelected()) {
				g.setColor(new Color(210, 210, 210));
				g.drawString(serverStatusAddress.getText().isBlank() ? "Minecraft Server" : serverStatusAddress.getText(),
						x + 9, y + 18);
			}
		}

		private void paintAnimatedImage(Graphics2D g, net.mcreator.element.parts.gui.Image image) {
			int width = image.getWidth(mcreator.getWorkspace());
			int height = image.getHeight(mcreator.getWorkspace());
			int period = Math.max(1, image.pulsePeriodTicks);
			double tick = System.currentTimeMillis() / 50.0;
			double pulse = Math.sin(tick * Math.PI * 2.0 / period) * Math.max(0, image.pulseAmount);
			int drawWidth = Math.max(1, (int) Math.round(width + pulse));
			int drawHeight = Math.max(1, (int) Math.round(height + pulse));
			int x = image.getX() + (width - drawWidth) / 2;
			int y = image.getY() + (height - drawHeight) / 2;
			net.mcreator.element.parts.gui.Image.drawCrisp(g, image.getImage(mcreator.getWorkspace()), x, y,
					drawWidth, drawHeight, this);
		}

		private void paintFallbackMenu(Graphics2D g) {
			if (CreateUI.TARGET_PAUSE_MENU.equals(targetScreen.getSelectedItem())) {
				Label title = label("title", 0, 25, titleText.getText(), parseColor(titleColor.getText(), Color.WHITE), true);
				title.x = (WYSIWYG.W - title.getWidth(mcreator.getWorkspace())) / 2;
				title.paintComponent(title.x, 25, editor, g);
				String[] actions = { CreateUI.ACTION_RESUME_GAME, CreateUI.ACTION_ADVANCEMENTS,
						CreateUI.ACTION_STATISTICS, CreateUI.ACTION_OPTIONS, CreateUI.ACTION_VANILLA_PAUSE_MENU,
						CreateUI.ACTION_DISCONNECT };
				int y = 65;
				for (String action : actions) {
					if (CreateUI.ACTION_OPTIONS.equals(action) && !showOptions.isSelected()) continue;
					Button button = new Button(defaultActionButtonName(action), 114, y,
							defaultActionButtonText(action), 200, 20, false, null, null);
					button.paintComponent(114, y, editor, g);
					y += 24;
				}
				return;
			}
			label("title", 151, 38, titleText.getText(), parseColor(titleColor.getText(), new Color(255, 215, 106)),
					true).paintComponent(151, 38, editor, g);
			if (!subtitleText.getText().isBlank())
				label("subtitle", 153, 58, subtitleText.getText(),
						parseColor(subtitleColor.getText(), Color.WHITE), true).paintComponent(153, 58, editor, g);

			int y = 94;
			if (showSingleplayer.isSelected()) {
				button("singleplayer", 114, y, "Singleplayer").paintComponent(114, y, editor, g);
				y += 24;
			}
			if (showMultiplayer.isSelected()) {
				button("multiplayer", 114, y, "Multiplayer").paintComponent(114, y, editor, g);
				y += 24;
			}
			if (showOptions.isSelected()) {
				button("options", 114, y, "Options").paintComponent(114, y, editor, g);
				y += 24;
			}
			if (showQuit.isSelected())
				button("quit", 114, y, "Quit Game").paintComponent(114, y, editor, g);
		}
	}

	private static Color parseColor(String text, Color fallback) {
		try {
			String normalized = text == null ? "" : text.trim();
			if (normalized.startsWith("#"))
				normalized = normalized.substring(1);
			if (normalized.startsWith("0x") || normalized.startsWith("0X"))
				normalized = normalized.substring(2);
			if (normalized.length() == 6)
				return new Color(Integer.parseInt(normalized, 16));
			if (normalized.length() == 8)
				return new Color((int) Long.parseLong(normalized, 16), true);
		} catch (Exception ignored) {
		}
		return fallback;
	}

	@Override public @Nullable URI contextURL() throws URISyntaxException {
		return new URI(MCreatorApplication.SERVER_DOMAIN + "/wiki/gui-editor");
	}

}
