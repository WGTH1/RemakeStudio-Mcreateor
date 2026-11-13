package net.mcreator.ui.remark;

import com.google.gson.JsonParser;
import net.mcreator.element.types.Fashion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DisplayLivePreviewSessionTest {

	@TempDir Path root;

	private Fashion fashion() {
		Fashion value = new Fashion(null);
		value.enableHelmet = true;
		value.helmetItemDisplay.enabled = true;
		value.helmetItemDisplay.gui.scaleX = .23;
		return value;
	}

	private Path source(String text) throws Exception {
		Path path = root.resolve("assets/demo/models/item/hat_helmet.json");
		Files.createDirectories(path.getParent());
		Files.writeString(path, text);
		return path;
	}

	@Test void bridgeIsPresent() throws Exception {
		assertNotNull(Class.forName("net.mcreator.ui.remark.DisplayLivePreviewSession"));
	}

	@Test void clonesObjAndOverridesWithoutChangingSourceAndCloseRemovesOnlyOwnedFiles() throws Exception {
		String original = """
			{"loader":"neoforge:obj","model":"demo:models/item/hat.obj","flip_v":true,
			 "textures":{"particle":"demo:item/hat"},"display":{"head":{"scale":[2,2,2]}},
			 "overrides":[{"predicate":{"custom_model_data":1},"model":"demo:item/alternate"}]}
			""";
		Path input = source(original);
		Files.writeString(input.resolveSibling("alternate.json"), "{\"parent\":\"demo:item/base\",\"custom\":42}");
		Files.writeString(input.resolveSibling("base.json"), "{\"elements\":[]}");
		Path unrelated = root.resolve("run/keep.txt");
		Files.createDirectories(unrelated.getParent());
		Files.writeString(unrelated, "keep");
		try (var session = new DisplayLivePreviewSession(root.resolve("run"), root.resolve("assets/demo"),
				"demo", "hat", "neoforge-1.21.1", fashion())) {
			assertEquals(DisplayLivePreviewSession.State.WAITING, session.getState());
			var cloned = JsonParser.parseString(Files.readString(session.getPackDirectory()
					.resolve("assets/demo/models/item/hat_helmet.json"))).getAsJsonObject();
			var old = JsonParser.parseString(original).getAsJsonObject();
			for (String key : new String[] { "loader", "model", "flip_v", "textures" })
				assertEquals(old.get(key), cloned.get(key), key);
			assertEquals(old.getAsJsonObject("display").get("head"), cloned.getAsJsonObject("display").get("head"));
			assertEquals(.23, cloned.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("scale").get(0).getAsDouble());
			var override = cloned.getAsJsonArray("overrides").get(0).getAsJsonObject();
			assertEquals(old.getAsJsonArray("overrides").get(0).getAsJsonObject().get("predicate"), override.get("predicate"));
			String reference = override.get("model").getAsString().substring("demo:".length());
			assertTrue(reference.startsWith("item/remark_display_"));
			assertTrue(Files.exists(session.getPackDirectory().resolve("assets/demo/models/" + reference + ".json")));
			assertEquals(original, Files.readString(input));
			Path pack = session.getPackDirectory();
			session.close();
			session.close();
			assertFalse(Files.exists(pack));
			assertEquals(DisplayLivePreviewSession.State.CLOSED, session.getState());
		}
		assertEquals("keep", Files.readString(unrelated));
		assertEquals(original, Files.readString(input));
	}

	@Test void rejectsNonFiniteAndOutOfRangeTransformsWithoutPublishing() throws Exception {
		source("{}");
		try (var session = new DisplayLivePreviewSession(root.resolve("run"), root.resolve("assets/demo"),
				"demo", "hat", "fabric-1.20.1", fashion())) {
			String prior = Files.readString(session.getManifestPath());
			Fashion invalid = fashion();
			invalid.wornDisplay.helmet.rotationX = Double.NaN;
			session.publish(invalid);
			assertEquals(DisplayLivePreviewSession.State.ERROR, session.getState());
			assertEquals(prior, Files.readString(session.getManifestPath()));
			invalid.wornDisplay.helmet.rotationX = 0;
			invalid.helmetItemDisplay.gui.scaleX = 100;
			session.publish(invalid);
			assertEquals(DisplayLivePreviewSession.State.ERROR, session.getState());
		}
	}

	@Test void missingModelsAndUnknownGeneratorsAreNotConnected() throws Exception {
		try (var session = new DisplayLivePreviewSession(root.resolve("run"), root.resolve("assets/demo"),
				"demo", "hat", "fabric-26.1.2", fashion())) {
			assertEquals(DisplayLivePreviewSession.State.ERROR, session.getState());
			assertFalse(Files.exists(session.getManifestPath()));
		}
		assertThrows(IllegalArgumentException.class, () -> new DisplayLivePreviewSession(root.resolve("run"),
				root.resolve("assets/demo"), "demo", "hat", "unknown", fashion()));
		assertThrows(IllegalArgumentException.class, () -> new DisplayLivePreviewSession(root.resolve("run"),
				root.resolve("assets/demo"), "../bad", "hat", "forge-1.20.1", fashion()));
	}

	@Test void boundedJsonRejectsOversizedAndDeepModels() throws Exception {
		Path input = source("{\"large\":\"" + "x".repeat(262144) + "\"}");
		try (var session = new DisplayLivePreviewSession(root.resolve("run"), root.resolve("assets/demo"),
				"demo", "hat", "forge-1.20.1", fashion())) {
			assertEquals(DisplayLivePreviewSession.State.ERROR, session.getState());
			Files.writeString(input, "{\"deep\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}");
			session.publish(fashion());
			assertEquals(DisplayLivePreviewSession.State.ERROR, session.getState());
			assertFalse(Files.exists(session.getManifestPath()));
		}
	}

	@Test void acknowledgementsMustMatchSessionRevisionAndBeFresh() throws Exception {
		source("{}");
		try (var session = new DisplayLivePreviewSession(root.resolve("run"), root.resolve("assets/demo"),
				"demo", "hat", "neoforge-1.21.1-21.1.219", fashion())) {
			var ack = JsonParser.parseString(Files.readString(session.getManifestPath())).getAsJsonObject();
			ack.addProperty("state", "APPLIED");
			ack.addProperty("time", System.currentTimeMillis());
			Files.writeString(session.getAcknowledgementPath(), ack.toString());
			assertEquals(DisplayLivePreviewSession.State.CONNECTED, session.getState());
			ack.addProperty("session", "wrong");
			Files.writeString(session.getAcknowledgementPath(), ack.toString());
			assertEquals(DisplayLivePreviewSession.State.WAITING, session.getState());
		}
	}

	@Test void negativeScaleAndFullTranslationRangeArePublishedForEachSide() throws Exception {
		source("{}");
		Fashion value = fashion();
		value.helmetItemDisplay.gui.scaleX = -4;
		value.wornDisplay.setLinked("boots", false);
		value.wornDisplay.getForSide("boots", false).translationY = -80;
		value.wornDisplay.getForSide("boots", false).scaleZ = -3;
		value.wornDisplay.setSideEnabled("boots", false, false);
		try (var session = new DisplayLivePreviewSession(root.resolve("run"), root.resolve("assets/demo"),
				"demo", "hat", "forge-1.20.1", value)) {
			assertEquals(DisplayLivePreviewSession.State.WAITING, session.getState());
			var worn = JsonParser.parseString(Files.readString(session.getManifestPath())).getAsJsonObject().getAsJsonObject("worn");
			var right = worn.getAsJsonObject("boots_right");
			assertEquals(-80, right.getAsJsonArray("translation").get(1).getAsDouble());
			assertEquals(-3, right.getAsJsonArray("scale").get(2).getAsDouble());
			assertFalse(right.get("enabled").getAsBoolean());
			assertTrue(worn.getAsJsonObject("boots_left").get("enabled").getAsBoolean());
		}
	}

	@Test void disablingCustomDisplayRemovesEditableContextsButPreservesHeadAndModelData() throws Exception {
		Path input = source("{\"parent\":\"minecraft:item/generated\",\"display\":{\"head\":{\"scale\":[2,2,2]},\"gui\":{\"scale\":[3,3,3]}}}");
		String original = Files.readString(input);
		Fashion value = fashion();
		value.helmetItemDisplay.enabled = false;
		try (var session = new DisplayLivePreviewSession(root.resolve("run"), root.resolve("assets/demo"),
				"demo", "hat", "forge-1.20.1", value)) {
			var cloned = JsonParser.parseString(Files.readString(session.getPackDirectory().resolve("assets/demo/models/item/hat_helmet.json"))).getAsJsonObject();
			assertFalse(cloned.getAsJsonObject("display").has("gui"));
			assertTrue(cloned.getAsJsonObject("display").has("head"));
			assertEquals("minecraft:item/generated", cloned.get("parent").getAsString());
			assertEquals(original, Files.readString(input));
		}
	}
}
