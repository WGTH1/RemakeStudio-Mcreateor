/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.remark_first_mod.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

import net.minecraft.world.item.Item;

import net.mcreator.remark_first_mod.item.SpeedWandItem;
import net.mcreator.remark_first_mod.RemarkFirstModMod;

public class RemarkFirstModModItems {
	public static final DeferredRegister.Items REGISTRY = DeferredRegister.createItems(RemarkFirstModMod.MODID);
	public static final DeferredItem<Item> SPEED_WAND;
	static {
		SPEED_WAND = REGISTRY.register("speed_wand", SpeedWandItem::new);
	}
	// Start of user code block custom items
	// End of user code block custom items
}