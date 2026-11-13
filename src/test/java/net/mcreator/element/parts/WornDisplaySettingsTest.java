package net.mcreator.element.parts;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WornDisplaySettingsTest {
	@Test void legacySidesStayLinkedAndCanBeSeparatedWithoutLosingSavedOffsets() {
		var value = new Gson().fromJson("{}", WornDisplaySettings.class);
		value.leggings.translationX = 2;
		assertTrue(value.isLinked("leggings"));
		assertEquals(2, value.getForSide("leggings", false).translationX);
		value.setLinked("leggings", false);
		value.getForSide("leggings", false).translationX = 5;
		assertEquals(2, value.getForSide("leggings", true).translationX);
		assertEquals(5, value.copy().getForSide("leggings", false).translationX);
		value.setSideEnabled("leggings", false, false);
		assertTrue(value.isSideEnabled("leggings", true));
		assertFalse(value.copy().isSideEnabled("leggings", false));
		value.setLinked("leggings", true);
		assertEquals(2, value.getForSide("leggings", false).translationX);
		value.setLinked("leggings", false);
		assertEquals(5, value.getForSide("leggings", false).translationX);
	}
	@Test void legacyAndNullSlotsDefaultToIdentity() {
		for (String json : new String[] { "{}", "{\"helmet\":null,\"body\":null}" }) {
			var value = new Gson().fromJson(json, WornDisplaySettings.class);
			for (String slot : new String[] { "helmet", "body", "leggings", "boots" }) {
				assertEquals(0, value.get(slot).translationY);
				assertEquals(1, value.get(slot).scaleX);
			}
		}
	}
	@Test void copyAndSerializationKeepSlotsIndependent() {
		var value = new WornDisplaySettings();
		value.helmet.translationY = 3;
		value.boots.rotationZ = 45;
		var copy = value.copy();
		copy.helmet.translationY = 5;
		assertEquals(3, value.helmet.translationY);
		var restored = new Gson().fromJson(new Gson().toJson(copy), WornDisplaySettings.class);
		assertEquals(5, restored.helmet.translationY);
		assertEquals(0, restored.body.translationY);
		assertEquals(45, restored.boots.rotationZ);
		assertFalse(WornDisplaySettings.supports("neoforge-26.1.2"));
	}
}
