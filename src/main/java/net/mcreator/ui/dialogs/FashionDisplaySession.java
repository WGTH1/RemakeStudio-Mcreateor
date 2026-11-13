package net.mcreator.ui.dialogs;

import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import net.mcreator.element.parts.WornDisplaySettings;
import net.mcreator.element.types.Fashion;
import net.mcreator.workspace.resources.Model;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Detached display draft. Switching equipment slots never changes the source element. */
final class FashionDisplaySession {
	static final int WORN = 7;
	private final Fashion source;
	private final List<String> slots = new ArrayList<>();
	private final Map<String, ItemDisplaySettings> items = new LinkedHashMap<>();
	private final WornDisplaySettings worn;

	FashionDisplaySession(Fashion source) {
		this.source = source;
		if (source.enableHelmet) slots.add("helmet");
		if (source.enableBody) slots.add("body");
		if (source.enableLeggings) slots.add("leggings");
		if (source.enableBoots) slots.add("boots");
		for (String slot : List.of("helmet", "body", "leggings", "boots"))
			items.put(slot, source.getItemDisplaySettingsFor(slot).copy());
		worn = source.getWornDisplay().copy();
	}
	List<String> slots() { return List.copyOf(slots); }
	String itemSlotFor(String slot) { return source.singleItemSet && !slots.isEmpty() ? slots.getFirst() : slot; }
	ItemDisplaySettings itemSettings(String slot) { return items.get(itemSlotFor(slot)); }
	WornDisplaySettings wornSettings() { return worn; }
	String key(String slot, int view) { return (view == WORN ? slot : itemSlotFor(slot)) + ":" + view; }
	Transform transform(String slot, int view) {
		return transform(slot, view, true);
	}
	Transform transform(String slot, int view, boolean left) {
		if (view == WORN) return worn.getForSide(slot, left);
		return transforms(itemSettings(slot))[view];
	}
	void update(String slot, int view, Transform value) {
		update(slot, view, true, value);
	}
	void update(String slot, int view, boolean left, Transform value) {
		Transform target = transform(slot, view, left);
		target.rotationX = value.rotationX; target.rotationY = value.rotationY; target.rotationZ = value.rotationZ;
		target.translationX = value.translationX; target.translationY = value.translationY; target.translationZ = value.translationZ;
		target.scaleX = value.scaleX; target.scaleY = value.scaleY; target.scaleZ = value.scaleZ;
		if (view != WORN) itemSettings(slot).enabled = true;
	}
	Model model(String slot) {
		return switch (itemSlotFor(slot)) {
			case "helmet" -> source.getHelmetItemModel();
			case "body" -> source.getBodyItemModel();
			case "leggings" -> source.getLeggingsItemModel();
			default -> source.getBootsItemModel();
		};
	}
	void applyTo(Fashion target) {
		items.forEach(target::setItemDisplaySettingsFor);
		target.wornDisplay = worn.copy();
	}
	Fashion snapshot() {
		Fashion copy = new Fashion(source.getModElement());
		copy.enableHelmet = source.enableHelmet; copy.enableBody = source.enableBody;
		copy.enableLeggings = source.enableLeggings; copy.enableBoots = source.enableBoots;
		copy.singleItemSet = source.singleItemSet; copy.simpleItemWearable = source.simpleItemWearable;
		applyTo(copy);
		return copy;
	}
	static Transform[] transforms(ItemDisplaySettings settings) {
		return new Transform[] { settings.getGui(), settings.getGround(), settings.getFixed(),
				settings.getThirdPersonRightHand(), settings.getThirdPersonLeftHand(),
				settings.getFirstPersonRightHand(), settings.getFirstPersonLeftHand() };
	}
}
