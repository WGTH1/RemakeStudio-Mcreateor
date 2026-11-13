package net.mcreator.ui.dialogs;

import net.mcreator.element.types.Fashion;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FashionDisplaySessionTest {
	private Fashion fashion(boolean single) {
		Fashion fashion = new Fashion(null);
		fashion.enableHelmet = fashion.enableBody = fashion.enableLeggings = fashion.enableBoots = true;
		fashion.singleItemSet = single;
		return fashion;
	}
	@Test void sharedItemHasOneHeldDisplayButFourIndependentWornTransforms() {
		Fashion source = fashion(true);
		FashionDisplaySession draft = new FashionDisplaySession(source);
		assertEquals("helmet", draft.itemSlotFor("boots"));
		draft.itemSettings("boots").getGui().scaleX = .123;
		assertEquals(.123, draft.itemSettings("body").getGui().scaleX);
		draft.wornSettings().boots.translationY = 7;
		assertEquals(0, draft.wornSettings().helmet.translationY);
		assertNotEquals(.123, source.helmetItemDisplay.getGui().scaleX);
		assertEquals(0, source.getWornDisplay().boots.translationY);
		draft.applyTo(source);
		assertEquals(.123, source.helmetItemDisplay.getGui().scaleX);
		assertEquals(7, source.getWornDisplay().boots.translationY);
		assertFalse(source.helmetItemDisplay.enabled);
	}
	@Test void separateItemsKeepTheirOwnSettingsAndCommitCopies() {
		Fashion source = fashion(false);
		FashionDisplaySession draft = new FashionDisplaySession(source);
		assertEquals("boots", draft.itemSlotFor("boots"));
		draft.itemSettings("body").getFirstPersonRightHand().translationZ = 5;
		assertEquals(0, draft.itemSettings("helmet").getFirstPersonRightHand().translationZ);
		draft.applyTo(source);
		draft.itemSettings("body").getFirstPersonRightHand().translationZ = 9;
		assertEquals(5, source.bodyItemDisplay.getFirstPersonRightHand().translationZ);
	}
	@Test void disabledHeadUsesFirstEnabledSlotWithoutTouchingHiddenSettings() {
		Fashion source = fashion(true);
		source.enableHelmet = false;
		source.helmetItemDisplay.getGui().translationX = 42;
		FashionDisplaySession draft = new FashionDisplaySession(source);
		assertEquals(java.util.List.of("body", "leggings", "boots"), draft.slots());
		assertEquals("body", draft.itemSlotFor("boots"));
		draft.applyTo(source);
		assertEquals(42, source.helmetItemDisplay.getGui().translationX);
	}
}
