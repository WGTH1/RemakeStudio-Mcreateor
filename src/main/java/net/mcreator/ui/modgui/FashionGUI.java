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

package net.mcreator.ui.modgui;

import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.parts.WornDisplaySettings;
import net.mcreator.ui.dialogs.FashionDisplaySettingsDialog;
import net.mcreator.element.parts.TabEntry;
import net.mcreator.element.types.Fashion;
import net.mcreator.minecraft.JavaModels;
import net.mcreator.minecraft.MinecraftImageGenerator;
import net.mcreator.ui.MCreator;
import net.mcreator.ui.MCreatorApplication;
import net.mcreator.ui.component.SearchableComboBox;
import net.mcreator.ui.component.util.ComboBoxUtil;
import net.mcreator.ui.component.util.ComponentUtils;
import net.mcreator.ui.component.util.PanelUtils;
import net.mcreator.ui.dialogs.TypedTextureSelectorDialog;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.laf.renderer.ModelComboBoxRenderer;
import net.mcreator.ui.laf.themes.Theme;
import net.mcreator.ui.minecraft.TabListField;
import net.mcreator.ui.minecraft.TextureComboBox;
import net.mcreator.ui.minecraft.TextureSelectionButton;
import net.mcreator.ui.remark.RemarkStudioElementTools;
import net.mcreator.ui.validation.ValidationGroup;
import net.mcreator.ui.validation.ValidationResult;
import net.mcreator.ui.validation.component.VComboBox;
import net.mcreator.ui.validation.component.VTextField;
import net.mcreator.ui.validation.validators.TextureSelectionButtonValidator;
import net.mcreator.ui.workspace.resources.TextureType;
import net.mcreator.util.ListUtils;
import net.mcreator.util.StringUtils;
import net.mcreator.util.image.ImageUtils;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.resources.Model;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FashionGUI extends ModElementGUI<Fashion> {

	private static final Logger LOG = LogManager.getLogger("Fashion UI");
	private static final int ARMOR_TEXTURE_SIZE_FACTOR = 5;

	private static final Model defaultModel = new Model.BuiltInModel("Default");
	private final Model normalItemModel = new Model.BuiltInModel("Normal");
	private final Model toolItemModel = new Model.BuiltInModel("Tool");

	private final JCheckBox enableHelmet = L10N.checkbox("elementgui.fashion.slot.head");
	private final JCheckBox enableBody = L10N.checkbox("elementgui.fashion.slot.body");
	private final JCheckBox enableLeggings = L10N.checkbox("elementgui.fashion.slot.legs");
	private final JCheckBox enableBoots = L10N.checkbox("elementgui.fashion.slot.feet");
	private final JCheckBox simpleItemWearable = L10N.checkbox("elementgui.fashion.simple_item_wearable");
	private final JCheckBox singleItemSet = L10N.checkbox("elementgui.fashion.single_item_set");

	private TextureSelectionButton textureHelmet;
	private TextureSelectionButton textureBody;
	private TextureSelectionButton textureLeggings;
	private TextureSelectionButton textureBoots;

	private final VTextField helmetName = new VTextField().requireValue("elementgui.fashion.error.head_name");
	private final VTextField bodyName = new VTextField().requireValue("elementgui.fashion.error.body_name");
	private final VTextField leggingsName = new VTextField().requireValue("elementgui.fashion.error.legs_name");
	private final VTextField bootsName = new VTextField().requireValue("elementgui.fashion.error.feet_name");

	private final VComboBox<Model> helmetModel = new SearchableComboBox<>(new Model[] { defaultModel });
	private final VComboBox<Model> bodyModel = new SearchableComboBox<>(new Model[] { defaultModel });
	private final VComboBox<Model> leggingsModel = new SearchableComboBox<>(new Model[] { defaultModel });
	private final VComboBox<Model> bootsModel = new SearchableComboBox<>(new Model[] { defaultModel });

	private final SearchableComboBox<Model> helmetItemRenderType = new SearchableComboBox<>(
			new Model[] { normalItemModel, toolItemModel });
	private final SearchableComboBox<Model> bodyItemRenderType = new SearchableComboBox<>(
			new Model[] { normalItemModel, toolItemModel });
	private final SearchableComboBox<Model> leggingsItemRenderType = new SearchableComboBox<>(
			new Model[] { normalItemModel, toolItemModel });
	private final SearchableComboBox<Model> bootsItemRenderType = new SearchableComboBox<>(
			new Model[] { normalItemModel, toolItemModel });
	private final JButton helmetItemDisplayButton = L10N.button("elementgui.fashion.item_display");
	private final JButton bodyItemDisplayButton = L10N.button("elementgui.fashion.item_display");
	private final JButton leggingsItemDisplayButton = L10N.button("elementgui.fashion.item_display");
	private final JButton bootsItemDisplayButton = L10N.button("elementgui.fashion.item_display");

	private ItemDisplaySettings helmetItemDisplay = ItemDisplaySettings.inherited();
	private ItemDisplaySettings bodyItemDisplay = ItemDisplaySettings.inherited();
	private ItemDisplaySettings leggingsItemDisplay = ItemDisplaySettings.inherited();
	private ItemDisplaySettings bootsItemDisplay = ItemDisplaySettings.inherited();
	private WornDisplaySettings wornDisplay = new WornDisplaySettings();
	private final JButton wornDisplayButton = L10N.button("dialog.model_display.title");

	private final VComboBox<String> helmetModelPart = new SearchableComboBox<>();
	private final VComboBox<String> bodyModelPart = new SearchableComboBox<>();
	private final VComboBox<String> armsModelPartL = new SearchableComboBox<>();
	private final VComboBox<String> armsModelPartR = new SearchableComboBox<>();
	private final VComboBox<String> leggingsModelPartL = new SearchableComboBox<>();
	private final VComboBox<String> leggingsModelPartR = new SearchableComboBox<>();
	private final VComboBox<String> bootsModelPartL = new SearchableComboBox<>();
	private final VComboBox<String> bootsModelPartR = new SearchableComboBox<>();

	private TextureComboBox armorTextureFile;
	private TextureComboBox helmetModelTexture;
	private TextureComboBox bodyModelTexture;
	private TextureComboBox leggingsModelTexture;
	private TextureComboBox bootsModelTexture;

	private final JCheckBox helmetTranslucency = L10N.checkbox("elementgui.common.enable");
	private final JCheckBox bodyTranslucency = L10N.checkbox("elementgui.common.enable");
	private final JCheckBox leggingsTranslucency = L10N.checkbox("elementgui.common.enable");
	private final JCheckBox bootsTranslucency = L10N.checkbox("elementgui.common.enable");

	private final TabListField creativeTabs = new TabListField(mcreator);
	private final VTextField slotSelectionGuard = new VTextField();
	private final JLabel slotSummary = new JLabel();
	private final JLabel clo1 = new JLabel();
	private final JLabel clo2 = new JLabel();

	private final ValidationGroup visualGroup = new ValidationGroup();
	private final ValidationGroup propertiesGroup = new ValidationGroup();

	private ActionListener helmetModelListener;
	private ActionListener bodyModelListener;
	private ActionListener leggingsModelListener;
	private ActionListener bootsModelListener;

	public FashionGUI(MCreator mcreator, ModElement modElement, boolean editingMode) {
		super(mcreator, modElement, editingMode);
		this.initGUI();
		super.finalizeGUI();
	}

	@Override protected void initGUI() {
		armorTextureFile = new TextureComboBox(mcreator, TextureType.ARMOR, true);
		armorTextureFile.setAddPNGExtension(false);
		armorTextureFile.setValidator(() -> !needsArmorLayerTexture() || armorTextureFile.hasTexture() ?
				ValidationResult.PASSED :
				new ValidationResult(ValidationResult.Type.ERROR, L10N.t("elementgui.fashion.error.armor_texture")));

		helmetModelTexture = new TextureComboBox(mcreator, TextureType.ENTITY, true, "From armor");
		bodyModelTexture = new TextureComboBox(mcreator, TextureType.ENTITY, true, "From armor");
		leggingsModelTexture = new TextureComboBox(mcreator, TextureType.ENTITY, true, "From armor");
		bootsModelTexture = new TextureComboBox(mcreator, TextureType.ENTITY, true, "From armor");

		textureHelmet = new TextureSelectionButton(new TypedTextureSelectorDialog(mcreator, TextureType.ITEM));
		textureBody = new TextureSelectionButton(new TypedTextureSelectorDialog(mcreator, TextureType.ITEM));
		textureLeggings = new TextureSelectionButton(new TypedTextureSelectorDialog(mcreator, TextureType.ITEM));
		textureBoots = new TextureSelectionButton(new TypedTextureSelectorDialog(mcreator, TextureType.ITEM));

		textureHelmet.setValidator(new TextureSelectionButtonValidator(textureHelmet, this::usesHelmetItemControls).setEmptyMessage(
				L10N.t("elementgui.fashion.error.head_texture")));
		textureBody.setValidator(new TextureSelectionButtonValidator(textureBody, this::usesBodyItemControls).setEmptyMessage(
				L10N.t("elementgui.fashion.error.body_texture")));
		textureLeggings.setValidator(new TextureSelectionButtonValidator(textureLeggings,
				this::usesLeggingsItemControls).setEmptyMessage(L10N.t("elementgui.fashion.error.legs_texture")));
		textureBoots.setValidator(new TextureSelectionButtonValidator(textureBoots, this::usesBootsItemControls).setEmptyMessage(
				L10N.t("elementgui.fashion.error.feet_texture")));

		prepareComponentFonts();
		prepareModelListeners();
		prepareItemDisplayEditors();

		enableHelmet.setSelected(true);
		enableBody.setSelected(true);
		enableLeggings.setSelected(true);
		enableBoots.setSelected(true);
		simpleItemWearable.setSelected(true);
		singleItemSet.setSelected(true);

		JPanel slotsPanel = PanelUtils.column(10, createHeadPanel(), createBodyPanel(), createLeggingsPanel(),
				createBootsPanel());
		JScrollPane slotsScrollPane = new JScrollPane(slotsPanel);
		slotsScrollPane.setOpaque(false);
		slotsScrollPane.getViewport().setOpaque(false);
		slotsScrollPane.setBorder(BorderFactory.createEmptyBorder());

		JPanel visualPane = new JPanel(new BorderLayout(10, 10));
		visualPane.setOpaque(false);
		slotSummary.setForeground(Theme.current().getAltForegroundColor());
		JButton livePreview = L10N.button("remark.tools.preview.menu");
		livePreview.addActionListener(_ -> RemarkStudioElementTools.showPreviewAndTest(mcreator, getElementFromGUI()));
		wornDisplayButton.addActionListener(_ -> {
			String slot = enableHelmet.isSelected() ? "helmet" : enableBody.isSelected() ? "body" : enableLeggings.isSelected() ? "leggings" : "boots";
			editModelDisplay(slot, true);
		});
		visualPane.add("North",
				PanelUtils.centerInPanel(PanelUtils.column(2, simpleItemWearable, singleItemSet,
						PanelUtils.join(FlowLayout.LEFT, livePreview, wornDisplayButton, slotSummary))));
		visualPane.add("Center", slotsScrollPane);

		JPanel propertiesPane = new JPanel(new BorderLayout(35, 35));
		propertiesPane.setOpaque(false);

		JPanel propertiesGrid = new JPanel(new GridLayout(2, 2, 0, 4));
		propertiesGrid.setOpaque(false);
		propertiesGrid.add(L10N.label("elementgui.fashion.armor_layer_texture"));
		propertiesGrid.add(armorTextureFile);
		propertiesGrid.add(L10N.label("elementgui.common.creative_tabs"));
		propertiesGrid.add(creativeTabs);

		clo1.setPreferredSize(new Dimension(64 * ARMOR_TEXTURE_SIZE_FACTOR, 32 * ARMOR_TEXTURE_SIZE_FACTOR));
		clo2.setPreferredSize(new Dimension(64 * ARMOR_TEXTURE_SIZE_FACTOR, 32 * ARMOR_TEXTURE_SIZE_FACTOR));
		JPanel previews = PanelUtils.join(clo1, clo2);

		propertiesPane.add("Center",
				PanelUtils.totalCenterInPanel(PanelUtils.northAndCenterElement(propertiesGrid, previews, 0, 24)));

		slotSelectionGuard.setValidator(() -> enableHelmet.isSelected() || enableBody.isSelected()
				|| enableLeggings.isSelected() || enableBoots.isSelected() ?
				ValidationResult.PASSED :
				new ValidationResult(ValidationResult.Type.ERROR, L10N.t("elementgui.fashion.error.no_slot")));

		visualGroup.addValidationElement(textureHelmet);
		visualGroup.addValidationElement(textureBody);
		visualGroup.addValidationElement(textureLeggings);
		visualGroup.addValidationElement(textureBoots);
		visualGroup.addValidationElement(helmetName);
		visualGroup.addValidationElement(bodyName);
		visualGroup.addValidationElement(leggingsName);
		visualGroup.addValidationElement(bootsName);
		visualGroup.addValidationElement(slotSelectionGuard);
		propertiesGroup.addValidationElement(armorTextureFile);

		armorTextureFile.getComboBox().addActionListener(_ -> updateArmorTexturePreview());
		for (TextureComboBox textureBox : List.of(helmetModelTexture, bodyModelTexture, leggingsModelTexture,
				bootsModelTexture))
			textureBox.getComboBox().addActionListener(_ -> refreshArmorTextureRequirement());
		simpleItemWearable.addActionListener(_ -> {
			updateSlotStates();
			refreshArmorTextureRequirement();
		});
		singleItemSet.addActionListener(_ -> updateSlotStates());
		addSlotListeners();
		updateSlotStates();

		addPage(L10N.t("elementgui.common.page_visual"), visualPane).validate(visualGroup);
		addPage(L10N.t("elementgui.common.page_properties"), propertiesPane).validate(propertiesGroup);

		if (!isEditingMode()) {
			creativeTabs.setListElements(List.of(new TabEntry(mcreator.getWorkspace(), "COMBAT")));
			String baseName = StringUtils.machineToReadableName(modElement.getName());
			helmetName.setText(L10N.t("elementgui.fashion.default.head", baseName));
			bodyName.setText(L10N.t("elementgui.fashion.default.body", baseName));
			leggingsName.setText(L10N.t("elementgui.fashion.default.legs", baseName));
			bootsName.setText(L10N.t("elementgui.fashion.default.feet", baseName));
		}
	}

	private void prepareComponentFonts() {
		List<VComboBox<Model>> modelBoxes = List.of(helmetModel, bodyModel, leggingsModel, bootsModel);
		for (VComboBox<Model> modelBox : modelBoxes) {
			modelBox.setRenderer(new ModelComboBoxRenderer());
			ComponentUtils.deriveFont(modelBox, 16);
		}

		for (SearchableComboBox<Model> itemModelBox : List.of(helmetItemRenderType, bodyItemRenderType,
				leggingsItemRenderType, bootsItemRenderType)) {
			itemModelBox.setRenderer(new ModelComboBoxRenderer());
			ComponentUtils.deriveFont(itemModelBox, 16);
		}

		for (JComponent component : List.of(helmetModelPart, bodyModelPart, armsModelPartL, armsModelPartR,
				leggingsModelPartL, leggingsModelPartR, bootsModelPartL, bootsModelPartR, helmetModelTexture,
				bodyModelTexture, leggingsModelTexture, bootsModelTexture, armorTextureFile, helmetName, bodyName,
				leggingsName, bootsName, helmetItemDisplayButton, bodyItemDisplayButton, leggingsItemDisplayButton,
				bootsItemDisplayButton)) {
			ComponentUtils.deriveFont(component, 16);
		}

		for (JCheckBox checkBox : List.of(enableHelmet, enableBody, enableLeggings, enableBoots, simpleItemWearable,
				singleItemSet, helmetTranslucency, bodyTranslucency, leggingsTranslucency, bootsTranslucency)) {
			checkBox.setOpaque(false);
		}

		helmetName.enableRealtimeValidation();
		bodyName.enableRealtimeValidation();
		leggingsName.enableRealtimeValidation();
		bootsName.enableRealtimeValidation();
	}

	private void prepareModelListeners() {
		helmetModelListener = _ -> {
			updateParts(helmetModel, List.of(helmetModelPart), List.of("Helmet"));
			refreshArmorTextureRequirement();
		};
		bodyModelListener = _ -> {
			updateParts(bodyModel, List.of(bodyModelPart, armsModelPartL, armsModelPartR),
					List.of("Body", "Arms L", "Arms R"));
			refreshArmorTextureRequirement();
		};
		leggingsModelListener = _ -> {
			updateParts(leggingsModel, List.of(leggingsModelPartL, leggingsModelPartR),
					List.of("Leggings L", "Leggings R"));
			refreshArmorTextureRequirement();
		};
		bootsModelListener = _ -> {
			updateParts(bootsModel, List.of(bootsModelPartL, bootsModelPartR), List.of("Boots L", "Boots R"));
			refreshArmorTextureRequirement();
		};

		helmetModel.addActionListener(helmetModelListener);
		bodyModel.addActionListener(bodyModelListener);
		leggingsModel.addActionListener(leggingsModelListener);
		bootsModel.addActionListener(bootsModelListener);
		helmetModelListener.actionPerformed(null);
		bodyModelListener.actionPerformed(null);
		leggingsModelListener.actionPerformed(null);
		bootsModelListener.actionPerformed(null);
	}

	private void prepareItemDisplayEditors() {
		helmetItemDisplayButton.addActionListener(_ -> editItemDisplay("helmet"));
		bodyItemDisplayButton.addActionListener(_ -> editItemDisplay("body"));
		leggingsItemDisplayButton.addActionListener(_ -> editItemDisplay("leggings"));
		bootsItemDisplayButton.addActionListener(_ -> editItemDisplay("boots"));

		for (SearchableComboBox<Model> itemModelBox : List.of(helmetItemRenderType, bodyItemRenderType,
				leggingsItemRenderType, bootsItemRenderType))
			itemModelBox.addActionListener(_ -> updateSlotStates());
	}

	private void editItemDisplay(String part) {
		editModelDisplay(part, false);
	}

	private void editModelDisplay(String slot, boolean worn) {
		Fashion draft = getElementFromGUI();
		if (!FashionDisplaySettingsDialog.open(mcreator, draft, slot, worn)) return;
		helmetItemDisplay = draft.getItemDisplaySettingsFor("helmet").copy();
		bodyItemDisplay = draft.getItemDisplaySettingsFor("body").copy();
		leggingsItemDisplay = draft.getItemDisplaySettingsFor("leggings").copy();
		bootsItemDisplay = draft.getItemDisplaySettingsFor("boots").copy();
		wornDisplay = draft.getWornDisplay().copy();
		updateItemDisplayButtonLabels();
	}

	private JPanel createHeadPanel() {
		JPanel form = new JPanel(new GridLayout(5, 2, 4, 4));
		form.setOpaque(false);
		form.add(L10N.label("elementgui.fashion.item_name"));
		form.add(helmetName);
		form.add(L10N.label("elementgui.fashion.item_model"));
		form.add(PanelUtils.gridElements(1, 2, 2, 2, helmetItemRenderType, helmetItemDisplayButton));
		form.add(L10N.label("elementgui.fashion.model"));
		form.add(PanelUtils.gridElements(1, 2, 2, 2, helmetModel, helmetModelPart));
		form.add(L10N.label("elementgui.fashion.model_texture"));
		form.add(helmetModelTexture);
		form.add(L10N.label("elementgui.fashion.model_translucency"));
		form.add(helmetTranslucency);
		return wrapSlotPanel(enableHelmet, textureHelmet, form);
	}

	private JPanel createBodyPanel() {
		JPanel form = new JPanel(new GridLayout(6, 2, 4, 4));
		form.setOpaque(false);
		form.add(L10N.label("elementgui.fashion.item_name"));
		form.add(bodyName);
		form.add(L10N.label("elementgui.fashion.item_model"));
		form.add(PanelUtils.gridElements(1, 2, 2, 2, bodyItemRenderType, bodyItemDisplayButton));
		form.add(L10N.label("elementgui.fashion.model"));
		form.add(PanelUtils.gridElements(1, 2, 2, 2, bodyModel, bodyModelPart));
		form.add(L10N.label("elementgui.fashion.arm_parts"));
		form.add(PanelUtils.gridElements(1, 2, 2, 2, armsModelPartL, armsModelPartR));
		form.add(L10N.label("elementgui.fashion.model_texture"));
		form.add(bodyModelTexture);
		form.add(L10N.label("elementgui.fashion.model_translucency"));
		form.add(bodyTranslucency);
		return wrapSlotPanel(enableBody, textureBody, form);
	}

	private JPanel createLeggingsPanel() {
		JPanel form = new JPanel(new GridLayout(5, 2, 4, 4));
		form.setOpaque(false);
		form.add(L10N.label("elementgui.fashion.item_name"));
		form.add(leggingsName);
		form.add(L10N.label("elementgui.fashion.item_model"));
		form.add(PanelUtils.gridElements(1, 2, 2, 2, leggingsItemRenderType, leggingsItemDisplayButton));
		form.add(L10N.label("elementgui.fashion.model"));
		form.add(PanelUtils.gridElements(1, 3, 2, 2, leggingsModel, leggingsModelPartL, leggingsModelPartR));
		form.add(L10N.label("elementgui.fashion.model_texture"));
		form.add(leggingsModelTexture);
		form.add(L10N.label("elementgui.fashion.model_translucency"));
		form.add(leggingsTranslucency);
		return wrapSlotPanel(enableLeggings, textureLeggings, form);
	}

	private JPanel createBootsPanel() {
		JPanel form = new JPanel(new GridLayout(5, 2, 4, 4));
		form.setOpaque(false);
		form.add(L10N.label("elementgui.fashion.item_name"));
		form.add(bootsName);
		form.add(L10N.label("elementgui.fashion.item_model"));
		form.add(PanelUtils.gridElements(1, 2, 2, 2, bootsItemRenderType, bootsItemDisplayButton));
		form.add(L10N.label("elementgui.fashion.model"));
		form.add(PanelUtils.gridElements(1, 3, 2, 2, bootsModel, bootsModelPartL, bootsModelPartR));
		form.add(L10N.label("elementgui.fashion.model_texture"));
		form.add(bootsModelTexture);
		form.add(L10N.label("elementgui.fashion.model_translucency"));
		form.add(bootsTranslucency);
		return wrapSlotPanel(enableBoots, textureBoots, form);
	}

	private JPanel wrapSlotPanel(JCheckBox enable, TextureSelectionButton texture, JPanel form) {
		JPanel texturePanel = PanelUtils.centerAndSouthElement(PanelUtils.centerInPanelPadding(texture, 0, 0), enable);
		texturePanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Theme.current().getAltForegroundColor()),
				BorderFactory.createEmptyBorder(10, 8, 8, 8)));
		texturePanel.setPreferredSize(new Dimension(150, 118));

		JPanel panel = PanelUtils.westAndCenterElement(texturePanel, form, 12, 0);
		panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		return panel;
	}

	private void addSlotListeners() {
		enableHelmet.addActionListener(_ -> {
			updateSlotStates();
			refreshArmorTextureRequirement();
		});
		enableBody.addActionListener(_ -> {
			updateSlotStates();
			refreshArmorTextureRequirement();
		});
		enableLeggings.addActionListener(_ -> {
			updateSlotStates();
			refreshArmorTextureRequirement();
		});
		enableBoots.addActionListener(_ -> {
			updateSlotStates();
			refreshArmorTextureRequirement();
		});
	}

	private void updateSlotStates() {
		wornDisplayButton.setToolTipText(L10N.t("dialog.model_display.title"));
		wornDisplayButton.setEnabled(enableHelmet.isSelected() || enableBody.isSelected() || enableLeggings.isSelected() || enableBoots.isSelected());
		updateSlotState(enableHelmet, textureHelmet, helmetName, helmetItemRenderType, helmetModel, helmetModelPart,
				helmetModelTexture, helmetTranslucency);
		updateSlotState(enableBody, textureBody, bodyName, bodyItemRenderType, bodyModel, bodyModelPart, armsModelPartL,
				armsModelPartR, bodyModelTexture, bodyTranslucency);
		updateSlotState(enableLeggings, textureLeggings, leggingsName, leggingsModel, leggingsModelPartL,
				leggingsModelPartR, leggingsItemRenderType, leggingsModelTexture, leggingsTranslucency);
		updateSlotState(enableBoots, textureBoots, bootsName, bootsItemRenderType, bootsModel, bootsModelPartL,
				bootsModelPartR,
				bootsModelTexture, bootsTranslucency);
		boolean useAdvancedWearableModel = !simpleItemWearable.isSelected();
		updateWearableControls(enableHelmet, useAdvancedWearableModel, helmetModel, helmetModelPart,
				helmetModelTexture, helmetTranslucency);
		updateWearableControls(enableBody, useAdvancedWearableModel, bodyModel, bodyModelPart, armsModelPartL,
				armsModelPartR, bodyModelTexture, bodyTranslucency);
		updateWearableControls(enableLeggings, useAdvancedWearableModel, leggingsModel, leggingsModelPartL,
				leggingsModelPartR, leggingsModelTexture, leggingsTranslucency);
		updateWearableControls(enableBoots, useAdvancedWearableModel, bootsModel, bootsModelPartL, bootsModelPartR,
				bootsModelTexture, bootsTranslucency);
		updateItemControls(textureHelmet, helmetName, helmetItemRenderType, usesHelmetItemControls());
		updateItemControls(textureBody, bodyName, bodyItemRenderType, usesBodyItemControls());
		updateItemControls(textureLeggings, leggingsName, leggingsItemRenderType, usesLeggingsItemControls());
		updateItemControls(textureBoots, bootsName, bootsItemRenderType, usesBootsItemControls());
		updateItemDisplayButton(helmetItemDisplayButton, helmetItemRenderType, usesHelmetItemControls());
		updateItemDisplayButton(bodyItemDisplayButton, bodyItemRenderType, usesBodyItemControls());
		updateItemDisplayButton(leggingsItemDisplayButton, leggingsItemRenderType, usesLeggingsItemControls());
		updateItemDisplayButton(bootsItemDisplayButton, bootsItemRenderType, usesBootsItemControls());
		updateItemDisplayButtonLabels();
		slotSelectionGuard.getValidationStatus();
		updateSlotSummary();
	}

	private void updateItemDisplayButton(JButton button, VComboBox<Model> itemModel, boolean slotUsesItem) {
		Model model = itemModel.getSelectedItem();
		button.setEnabled(slotUsesItem && model != null
				&& (model.getType() == Model.Type.OBJ || model.getType() == Model.Type.JSON));
	}

	private void updateItemDisplayButtonLabels() {
		updateItemDisplayButtonLabel(helmetItemDisplayButton, helmetItemDisplay);
		updateItemDisplayButtonLabel(bodyItemDisplayButton, bodyItemDisplay);
		updateItemDisplayButtonLabel(leggingsItemDisplayButton, leggingsItemDisplay);
		updateItemDisplayButtonLabel(bootsItemDisplayButton, bootsItemDisplay);
	}

	private void updateItemDisplayButtonLabel(JButton button, ItemDisplaySettings settings) {
		button.setText(L10N.t(settings != null && settings.isEnabled() ?
				"elementgui.fashion.item_display_custom" :
				"elementgui.fashion.item_display"));
	}

	private void updateSlotSummary() {
		String slots = Arrays.stream(
						new String[][] { { L10N.t("elementgui.fashion.slot.head"), Boolean.toString(enableHelmet.isSelected()) },
								{ L10N.t("elementgui.fashion.slot.body"), Boolean.toString(enableBody.isSelected()) },
								{ L10N.t("elementgui.fashion.slot.legs"), Boolean.toString(enableLeggings.isSelected()) },
								{ L10N.t("elementgui.fashion.slot.feet"), Boolean.toString(enableBoots.isSelected()) } })
				.filter(entry -> Boolean.parseBoolean(entry[1])).map(entry -> entry[0]).collect(Collectors.joining(", "));

		if (slots.isBlank())
			slots = L10N.t("elementgui.fashion.slot_summary.none");

		String itemMode = singleItemSet.isSelected() ?
				L10N.t("elementgui.fashion.slot_summary.one_item") :
				L10N.t("elementgui.fashion.slot_summary.separate_items");
		String renderMode = simpleItemWearable.isSelected() ?
				L10N.t("elementgui.fashion.slot_summary.item_render") :
				L10N.t("elementgui.fashion.slot_summary.model_render");
		slotSummary.setText(L10N.t("elementgui.fashion.slot_summary", itemMode, slots, renderMode));
	}

	private void updateSlotState(JCheckBox enabled, TextureSelectionButton texture, VTextField name,
			JComponent... components) {
		boolean selected = enabled.isSelected();
		texture.setEnabled(selected);
		name.setEnabled(selected);
		for (JComponent component : components)
			component.setEnabled(selected);
	}

	private void updateWearableControls(JCheckBox slotEnabled, boolean useAdvancedWearableModel,
			JComponent... components) {
		boolean enabled = slotEnabled.isSelected() && useAdvancedWearableModel;
		for (JComponent component : components)
			component.setEnabled(enabled);
	}

	private void updateItemControls(TextureSelectionButton texture, VTextField name, JComponent itemModel,
			boolean enabled) {
		texture.setEnabled(enabled);
		name.setEnabled(enabled);
		itemModel.setEnabled(enabled);
	}

	private boolean usesHelmetItemControls() {
		return enableHelmet.isSelected();
	}

	private boolean usesBodyItemControls() {
		return enableBody.isSelected() && (!singleItemSet.isSelected() || !enableHelmet.isSelected());
	}

	private boolean usesLeggingsItemControls() {
		return enableLeggings.isSelected() && (!singleItemSet.isSelected() || !enableHelmet.isSelected()
				&& !enableBody.isSelected());
	}

	private boolean usesBootsItemControls() {
		return enableBoots.isSelected() && (!singleItemSet.isSelected() || !enableHelmet.isSelected()
				&& !enableBody.isSelected() && !enableLeggings.isSelected());
	}

	private void updateParts(VComboBox<Model> modelBox, List<VComboBox<String>> partBoxes, List<String> defaults) {
		Model model = modelBox.getSelectedItem();
		List<String> parts = getModelParts(model);
		for (int i = 0; i < partBoxes.size(); i++) {
			VComboBox<String> partBox = partBoxes.get(i);
			partBox.removeAllItems();
			if (!parts.isEmpty()) {
				ComboBoxUtil.updateComboBoxContents(partBox, parts);
			} else {
				partBox.addItem(defaults.get(i));
			}
		}
	}

	private List<String> getModelParts(Model model) {
		if (model != null && model != defaultModel) {
			try {
				return JavaModels.getModelParts((JavaClassSource) Roaster.parse(model.getFile()));
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
		}
		return List.of();
	}

	@Override public void reloadDataLists() {
		super.reloadDataLists();

		helmetModel.removeActionListener(helmetModelListener);
		bodyModel.removeActionListener(bodyModelListener);
		leggingsModel.removeActionListener(leggingsModelListener);
		bootsModel.removeActionListener(bootsModelListener);

		List<Model> armorModels = ListUtils.merge(Collections.singleton(defaultModel),
				Model.getModels(mcreator.getWorkspace()).stream()
						.filter(el -> el.getType() == Model.Type.JAVA || el.getType() == Model.Type.MCREATOR)
						.collect(Collectors.toList()));

		ComboBoxUtil.updateComboBoxContents(helmetModel, armorModels);
		ComboBoxUtil.updateComboBoxContents(bodyModel, armorModels);
		ComboBoxUtil.updateComboBoxContents(leggingsModel, armorModels);
		ComboBoxUtil.updateComboBoxContents(bootsModel, armorModels);

		List<Model> itemModels = ListUtils.merge(Arrays.asList(normalItemModel, toolItemModel),
				Model.getModelsWithTextureMaps(mcreator.getWorkspace()).stream()
						.filter(el -> el.getType() == Model.Type.JSON || el.getType() == Model.Type.OBJ)
						.collect(Collectors.toList()));

		ComboBoxUtil.updateComboBoxContents(helmetItemRenderType, itemModels);
		ComboBoxUtil.updateComboBoxContents(bodyItemRenderType, itemModels);
		ComboBoxUtil.updateComboBoxContents(leggingsItemRenderType, itemModels);
		ComboBoxUtil.updateComboBoxContents(bootsItemRenderType, itemModels);

		armorTextureFile.reload();
		helmetModelTexture.reload();
		bodyModelTexture.reload();
		leggingsModelTexture.reload();
		bootsModelTexture.reload();

		helmetModel.addActionListener(helmetModelListener);
		bodyModel.addActionListener(bodyModelListener);
		leggingsModel.addActionListener(leggingsModelListener);
		bootsModel.addActionListener(bootsModelListener);

		helmetModelListener.actionPerformed(null);
		bodyModelListener.actionPerformed(null);
		leggingsModelListener.actionPerformed(null);
		bootsModelListener.actionPerformed(null);
	}

	private void updateArmorTexturePreview() {
		String textureName = armorTextureFile.getTextureName();
		File[] armorTextures = mcreator.getFolderManager().getArmorTextureFilesForName(textureName);
		if (!textureName.isBlank() && armorTextures[0].isFile() && armorTextures[1].isFile()) {
			ImageIcon bg1 = new ImageIcon(
					ImageUtils.resize(new ImageIcon(armorTextures[0].getAbsolutePath()).getImage(),
							64 * ARMOR_TEXTURE_SIZE_FACTOR, 32 * ARMOR_TEXTURE_SIZE_FACTOR));
			ImageIcon bg2 = new ImageIcon(
					ImageUtils.resize(new ImageIcon(armorTextures[1].getAbsolutePath()).getImage(),
							64 * ARMOR_TEXTURE_SIZE_FACTOR, 32 * ARMOR_TEXTURE_SIZE_FACTOR));
			ImageIcon front1 = new ImageIcon(MinecraftImageGenerator.Preview.generateArmorPreviewFrame1());
			ImageIcon front2 = new ImageIcon(MinecraftImageGenerator.Preview.generateArmorPreviewFrame2());
			clo1.setIcon(ImageUtils.drawOver(bg1, front1));
			clo2.setIcon(ImageUtils.drawOver(bg2, front2));
		} else {
			clo1.setIcon(new ImageIcon(MinecraftImageGenerator.Preview.generateArmorPreviewFrame1()));
			clo2.setIcon(new ImageIcon(MinecraftImageGenerator.Preview.generateArmorPreviewFrame2()));
		}
	}

	private void refreshArmorTextureRequirement() {
		if (armorTextureFile != null)
			armorTextureFile.getValidationStatus();
	}

	private boolean needsArmorLayerTexture() {
		if (simpleItemWearable.isSelected())
			return false;
		return slotUsesArmorLayer(enableHelmet, helmetModel, helmetModelTexture) || slotUsesArmorLayer(enableBody,
				bodyModel, bodyModelTexture) || slotUsesArmorLayer(enableLeggings, leggingsModel, leggingsModelTexture)
				|| slotUsesArmorLayer(enableBoots, bootsModel, bootsModelTexture);
	}

	private boolean slotUsesArmorLayer(JCheckBox enabled, VComboBox<Model> modelBox, TextureComboBox modelTextureBox) {
		if (!enabled.isSelected())
			return false;

		Model model = modelBox.getSelectedItem();
		boolean hasCustomWearableModel = model != null && !"Default".equals(model.getReadableName());
		boolean hasCustomModelTexture = modelTextureBox.hasTexture();
		return !hasCustomWearableModel || !hasCustomModelTexture;
	}

	@Override public void openInEditingMode(Fashion fashion) {
		textureHelmet.setTexture(fashion.textureHelmet);
		textureBody.setTexture(fashion.textureBody);
		textureLeggings.setTexture(fashion.textureLeggings);
		textureBoots.setTexture(fashion.textureBoots);

		enableHelmet.setSelected(fashion.enableHelmet);
		enableBody.setSelected(fashion.enableBody);
		enableLeggings.setSelected(fashion.enableLeggings);
		enableBoots.setSelected(fashion.enableBoots);
		simpleItemWearable.setSelected(fashion.simpleItemWearable);
		singleItemSet.setSelected(fashion.singleItemSet);

		helmetName.setText(fashion.helmetName);
		bodyName.setText(fashion.bodyName);
		leggingsName.setText(fashion.leggingsName);
		bootsName.setText(fashion.bootsName);
		creativeTabs.setListElements(fashion.creativeTabs);
		armorTextureFile.setTextureFromTextureName(fashion.armorTextureFile);

		setModelIfPresent(helmetModel, fashion.getHelmetModel());
		setModelIfPresent(bodyModel, fashion.getBodyModel());
		setModelIfPresent(leggingsModel, fashion.getLeggingsModel());
		setModelIfPresent(bootsModel, fashion.getBootsModel());
		setModelIfPresent(helmetItemRenderType, fashion.getHelmetItemModel());
		setModelIfPresent(bodyItemRenderType, fashion.getBodyItemModel());
		setModelIfPresent(leggingsItemRenderType, fashion.getLeggingsItemModel());
		setModelIfPresent(bootsItemRenderType, fashion.getBootsItemModel());

		helmetModelPart.setSelectedItem(fashion.helmetModelPart);
		bodyModelPart.setSelectedItem(fashion.bodyModelPart);
		armsModelPartL.setSelectedItem(fashion.armsModelPartL);
		armsModelPartR.setSelectedItem(fashion.armsModelPartR);
		leggingsModelPartL.setSelectedItem(fashion.leggingsModelPartL);
		leggingsModelPartR.setSelectedItem(fashion.leggingsModelPartR);
		bootsModelPartL.setSelectedItem(fashion.bootsModelPartL);
		bootsModelPartR.setSelectedItem(fashion.bootsModelPartR);

		helmetModelTexture.setTextureFromTextureName(fashion.helmetModelTexture);
		bodyModelTexture.setTextureFromTextureName(fashion.bodyModelTexture);
		leggingsModelTexture.setTextureFromTextureName(fashion.leggingsModelTexture);
		bootsModelTexture.setTextureFromTextureName(fashion.bootsModelTexture);

		helmetTranslucency.setSelected(fashion.helmetTranslucency);
		bodyTranslucency.setSelected(fashion.bodyTranslucency);
		leggingsTranslucency.setSelected(fashion.leggingsTranslucency);
		bootsTranslucency.setSelected(fashion.bootsTranslucency);
		helmetItemDisplay = fashion.getItemDisplaySettingsFor("helmet").copy();
		wornDisplay = fashion.getWornDisplay().copy();
		bodyItemDisplay = fashion.getItemDisplaySettingsFor("body").copy();
		leggingsItemDisplay = fashion.getItemDisplaySettingsFor("leggings").copy();
		bootsItemDisplay = fashion.getItemDisplaySettingsFor("boots").copy();

		updateSlotStates();
		updateArmorTexturePreview();
	}

	private void setModelIfPresent(VComboBox<Model> comboBox, @Nullable Model model) {
		if (model != null)
			comboBox.setSelectedItem(model);
	}

	@Override public Fashion getElementFromGUI() {
		Fashion fashion = new Fashion(modElement);

		fashion.enableHelmet = enableHelmet.isSelected();
		fashion.textureHelmet = textureHelmet.getTextureHolder();
		fashion.enableBody = enableBody.isSelected();
		fashion.textureBody = textureBody.getTextureHolder();
		fashion.enableLeggings = enableLeggings.isSelected();
		fashion.textureLeggings = textureLeggings.getTextureHolder();
		fashion.enableBoots = enableBoots.isSelected();
		fashion.textureBoots = textureBoots.getTextureHolder();
		fashion.simpleItemWearable = simpleItemWearable.isSelected();
		fashion.singleItemSet = singleItemSet.isSelected();

		fashion.helmetName = helmetName.getText();
		fashion.bodyName = bodyName.getText();
		fashion.leggingsName = leggingsName.getText();
		fashion.bootsName = bootsName.getText();
		fashion.creativeTabs = creativeTabs.getListElements();
		fashion.armorTextureFile = armorTextureFile.getTextureName();
		if (fashion.armorTextureFile.isBlank())
			fashion.armorTextureFile = modElement.getRegistryName();

		fashion.helmetModelName = Objects.requireNonNull(helmetModel.getSelectedItem()).getReadableName();
		fashion.bodyModelName = Objects.requireNonNull(bodyModel.getSelectedItem()).getReadableName();
		fashion.leggingsModelName = Objects.requireNonNull(leggingsModel.getSelectedItem()).getReadableName();
		fashion.bootsModelName = Objects.requireNonNull(bootsModel.getSelectedItem()).getReadableName();

		fashion.helmetModelPart = helmetModelPart.getSelectedItem();
		fashion.bodyModelPart = bodyModelPart.getSelectedItem();
		fashion.armsModelPartL = armsModelPartL.getSelectedItem();
		fashion.armsModelPartR = armsModelPartR.getSelectedItem();
		fashion.leggingsModelPartL = leggingsModelPartL.getSelectedItem();
		fashion.leggingsModelPartR = leggingsModelPartR.getSelectedItem();
		fashion.bootsModelPartL = bootsModelPartL.getSelectedItem();
		fashion.bootsModelPartR = bootsModelPartR.getSelectedItem();

		fashion.helmetModelTexture = helmetModelTexture.getTextureName();
		fashion.bodyModelTexture = bodyModelTexture.getTextureName();
		fashion.leggingsModelTexture = leggingsModelTexture.getTextureName();
		fashion.bootsModelTexture = bootsModelTexture.getTextureName();

		applyItemModel(fashion, "helmet", Objects.requireNonNull(helmetItemRenderType.getSelectedItem()));
		applyItemModel(fashion, "body", Objects.requireNonNull(bodyItemRenderType.getSelectedItem()));
		applyItemModel(fashion, "leggings", Objects.requireNonNull(leggingsItemRenderType.getSelectedItem()));
		applyItemModel(fashion, "boots", Objects.requireNonNull(bootsItemRenderType.getSelectedItem()));
		fashion.setItemDisplaySettingsFor("helmet", helmetItemDisplay);
		fashion.wornDisplay = wornDisplay.copy();
		fashion.setItemDisplaySettingsFor("body", bodyItemDisplay);
		fashion.setItemDisplaySettingsFor("leggings", leggingsItemDisplay);
		fashion.setItemDisplaySettingsFor("boots", bootsItemDisplay);

		fashion.helmetTranslucency = helmetTranslucency.isSelected();
		fashion.bodyTranslucency = bodyTranslucency.isSelected();
		fashion.leggingsTranslucency = leggingsTranslucency.isSelected();
		fashion.bootsTranslucency = bootsTranslucency.isSelected();

		fashion.applyCosmeticDefaults();
		return fashion;
	}

	private void applyItemModel(Fashion fashion, String part, Model model) {
		int renderType = 0;
		if (model.getType() == Model.Type.JSON)
			renderType = 1;
		else if (model.getType() == Model.Type.OBJ)
			renderType = 2;

		switch (part) {
			case "helmet" -> {
				fashion.helmetItemRenderType = renderType;
				fashion.helmetItemCustomModelName = model.getReadableName();
			}
			case "body" -> {
				fashion.bodyItemRenderType = renderType;
				fashion.bodyItemCustomModelName = model.getReadableName();
			}
			case "leggings" -> {
				fashion.leggingsItemRenderType = renderType;
				fashion.leggingsItemCustomModelName = model.getReadableName();
			}
			case "boots" -> {
				fashion.bootsItemRenderType = renderType;
				fashion.bootsItemCustomModelName = model.getReadableName();
			}
		}
	}

	@Override public @Nullable URI contextURL() throws URISyntaxException {
		return new URI(MCreatorApplication.SERVER_DOMAIN + "/wiki/how-make-armor");
	}

}
