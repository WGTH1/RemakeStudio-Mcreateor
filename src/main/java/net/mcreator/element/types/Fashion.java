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

package net.mcreator.element.types;

import net.mcreator.element.parts.procedure.LogicProcedure;
import net.mcreator.element.parts.procedure.StringListProcedure;
import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.parts.WornDisplaySettings;
import net.mcreator.element.parts.Sound;
import net.mcreator.workspace.elements.ModElement;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused") public class Fashion extends Armor {
	public static final int CURRENT_REMARK_SCHEMA = 3;

	public int remarkSchemaVersion = CURRENT_REMARK_SCHEMA;
	public boolean simpleItemWearable = true;
	public boolean singleItemSet = true;
	public WornDisplaySettings wornDisplay = new WornDisplaySettings();

	public WornDisplaySettings getWornDisplay() {
		if (wornDisplay == null) wornDisplay = new WornDisplaySettings();
		return wornDisplay;
	}

	public ItemDisplaySettings.Transform getWornTransformFor(String slot) {
		return getWornDisplay().get(slot);
	}
	public ItemDisplaySettings.Transform getWornTransformFor(String slot, boolean left) {
		return getWornDisplay().getForSide(slot, left);
	}
	public ItemDisplaySettings helmetItemDisplay = ItemDisplaySettings.inherited();
	public ItemDisplaySettings bodyItemDisplay = ItemDisplaySettings.inherited();
	public ItemDisplaySettings leggingsItemDisplay = ItemDisplaySettings.inherited();
	public ItemDisplaySettings bootsItemDisplay = ItemDisplaySettings.inherited();

	private Fashion() {
		this(null);
	}

	public Fashion(ModElement element) {
		super(element);
		applyCosmeticDefaults();
	}

	public void applyCosmeticDefaults() {
		this.maxDamage = 0;
		this.damageValueHelmet = 0;
		this.damageValueBody = 0;
		this.damageValueLeggings = 0;
		this.damageValueBoots = 0;
		this.enchantability = 1;
		this.toughness = 0;
		this.knockbackResistance = 0;
		this.repairItems = new ArrayList<>();
		this.attributeModifiers = new ArrayList<>();
		if (this.equipSound == null && getModElement() != null)
			this.equipSound = new Sound(getModElement().getWorkspace(), "item.armor.equip_generic");
		this.helmetGlowCondition = new LogicProcedure(null, false);
		this.bodyGlowCondition = new LogicProcedure(null, false);
		this.leggingsGlowCondition = new LogicProcedure(null, false);
		this.bootsGlowCondition = new LogicProcedure(null, false);
		this.helmetPiglinNeutral = new LogicProcedure(null, false);
		this.bodyPiglinNeutral = new LogicProcedure(null, false);
		this.leggingsPiglinNeutral = new LogicProcedure(null, false);
		this.bootsPiglinNeutral = new LogicProcedure(null, false);
		this.helmetSpecialInformation = new StringListProcedure(null, List.of());
		this.bodySpecialInformation = new StringListProcedure(null, List.of());
		this.leggingsSpecialInformation = new StringListProcedure(null, List.of());
		this.bootsSpecialInformation = new StringListProcedure(null, List.of());
		this.onHelmetTick = null;
		this.onBodyTick = null;
		this.onLeggingsTick = null;
		this.onBootsTick = null;
		ensureItemDisplaySettings();
	}

	public void normalizeRemarkSchema(boolean hadSimpleItemWearable, boolean hadSingleItemSet) {
		if (!hadSimpleItemWearable)
			simpleItemWearable = true;
		if (!hadSingleItemSet)
			singleItemSet = true;
		applyCosmeticDefaults();
		remarkSchemaVersion = CURRENT_REMARK_SCHEMA;
	}

	private void ensureItemDisplaySettings() {
		if (helmetItemDisplay == null)
			helmetItemDisplay = ItemDisplaySettings.inherited();
		if (bodyItemDisplay == null)
			bodyItemDisplay = ItemDisplaySettings.inherited();
		if (leggingsItemDisplay == null)
			leggingsItemDisplay = ItemDisplaySettings.inherited();
		if (bootsItemDisplay == null)
			bootsItemDisplay = ItemDisplaySettings.inherited();
	}

	public ItemDisplaySettings getItemDisplaySettingsFor(String part) {
		ensureItemDisplaySettings();
		return switch (part) {
			case "helmet" -> helmetItemDisplay;
			case "body" -> bodyItemDisplay;
			case "leggings" -> leggingsItemDisplay;
			case "boots" -> bootsItemDisplay;
			default -> ItemDisplaySettings.inherited();
		};
	}

	public void setItemDisplaySettingsFor(String part, ItemDisplaySettings settings) {
		ItemDisplaySettings value = settings != null ? settings.copy() : ItemDisplaySettings.inherited();
		switch (part) {
			case "helmet" -> helmetItemDisplay = value;
			case "body" -> bodyItemDisplay = value;
			case "leggings" -> leggingsItemDisplay = value;
			case "boots" -> bootsItemDisplay = value;
		}
	}

	@Override public List<net.mcreator.minecraft.MCItem> providedMCItems() {
		ArrayList<net.mcreator.minecraft.MCItem> retval = new ArrayList<>();
		if (this.shouldGenerateHelmetItem())
			retval.add(new net.mcreator.minecraft.MCItem.Custom(this.getModElement(), "helmet", "item", "Head fashion"));
		if (this.shouldGenerateBodyItem())
			retval.add(new net.mcreator.minecraft.MCItem.Custom(this.getModElement(), "body", "item", "Body fashion"));
		if (this.shouldGenerateLeggingsItem())
			retval.add(new net.mcreator.minecraft.MCItem.Custom(this.getModElement(), "legs", "item", "Leg fashion"));
		if (this.shouldGenerateBootsItem())
			retval.add(new net.mcreator.minecraft.MCItem.Custom(this.getModElement(), "boots", "item", "Foot fashion"));
		return retval;
	}

	public boolean shouldGenerateHelmetItem() {
		return enableHelmet;
	}

	public boolean shouldGenerateBodyItem() {
		return enableBody && (!singleItemSet || !enableHelmet);
	}

	public boolean shouldGenerateLeggingsItem() {
		return enableLeggings && (!singleItemSet || !enableHelmet && !enableBody);
	}

	public boolean shouldGenerateBootsItem() {
		return enableBoots && (!singleItemSet || !enableHelmet && !enableBody && !enableLeggings);
	}

	public String getPrimaryItemConstantSuffix() {
		if (shouldGenerateHelmetItem())
			return "HELMET";
		if (shouldGenerateBodyItem())
			return "CHESTPLATE";
		if (shouldGenerateLeggingsItem())
			return "LEGGINGS";
		return "BOOTS";
	}

	public String getPrimaryEquipmentSlot() {
		if (shouldGenerateHelmetItem())
			return "HEAD";
		if (shouldGenerateBodyItem())
			return "CHEST";
		if (shouldGenerateLeggingsItem())
			return "LEGS";
		return "FEET";
	}

}
