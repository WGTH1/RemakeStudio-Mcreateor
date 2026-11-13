package net.mcreator.ui.dialogs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.nio.file.ExtendedOpenOption;
import net.mcreator.element.parts.ItemDisplaySettings.Transform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DisplayPresetStoreTest {

	private static final String TRANSFORM = """
			{"rotationX":15,"rotationY":-25,"rotationZ":35,
			 "translationX":1.25,"translationY":-2.5,"translationZ":3.75,
			 "scaleX":0.2,"scaleY":-0.3,"scaleZ":0.4}
			""";

	@TempDir Path directory;

	@Test void missingFileIsEmptyAndReadOnlyOperationsDoNotCreateIt() throws IOException {
		Path file = directory.resolve("preferences/presets.json");
		var store = new DisplayPresetStore(file);
		assertEquals(List.of(), store.list());
		assertNull(store.load("missing"));
		store.delete("missing");
		assertFalse(Files.exists(file.getParent()));
	}

	@Test void persistsAllComponentsAndUnicodeNamesInInsertionOrder() throws IOException {
		Path file = directory.resolve("preferences/presets.json");
		var store = new DisplayPresetStore(file);
		String unicode = "  \u0e0a\u0e38\u0e14\u0e44\u0e17\u0e22 \u65e5\u672c \uD83C\uDF1F  ";
		store.save("Zulu", sample());
		store.save(unicode, sample());
		store.save("Alpha", new Transform());
		var reopened = new DisplayPresetStore(file);
		assertEquals(List.of("Zulu", unicode, "Alpha"), reopened.list());
		assertTransform(sample(), reopened.load(unicode));
		String json = Files.readString(file, StandardCharsets.UTF_8);
		assertTrue(json.contains(unicode));
		JsonObject document = JsonParser.parseString(json).getAsJsonObject();
		assertEquals(1, document.get("version").getAsInt());
		assertTrue(document.getAsJsonObject("presets").get(unicode).isJsonObject());
	}

	@Test void returnedListsAreImmutableSnapshotsAndTransformsAreIndependent() throws IOException {
		var store = new DisplayPresetStore(directory.resolve("presets.json"));
		Transform source = sample();
		store.save("one", source);
		List<String> names = store.list();
		source.rotationX = 99;
		source.scaleY = 3;
		Transform loaded = store.load("one");
		assertNotSame(source, loaded);
		assertTransform(sample(), loaded);
		loaded.translationZ = 77;
		loaded.scaleX = 4;
		assertTransform(sample(), store.load("one"));
		store.save("two", new Transform());
		assertEquals(List.of("one"), names);
		assertThrows(UnsupportedOperationException.class, () -> names.add("injected"));
	}

	@Test void replacingKeepsPositionAndDeletePersists() throws IOException {
		Path file = directory.resolve("presets.json");
		var store = new DisplayPresetStore(file);
		store.save("one", sample());
		store.save("two", sample());
		store.save("one", new Transform());
		assertEquals(List.of("one", "two"), store.list());
		assertTransform(new Transform(), store.load("one"));
		store.delete("one");
		var reopened = new DisplayPresetStore(file);
		assertNull(reopened.load("one"));
		assertEquals(List.of("two"), reopened.list());
		byte[] previous = Files.readAllBytes(file);
		reopened.delete("missing");
		assertArrayEquals(previous, Files.readAllBytes(file));
		reopened.delete("two");
		assertEquals(List.of(), new DisplayPresetStore(file).list());
	}

	@Test void sequentialStoreInstancesSeeEachOthersChanges() throws IOException {
		Path file = directory.resolve("presets.json");
		var first = new DisplayPresetStore(file);
		var second = new DisplayPresetStore(file);
		first.save("one", sample());
		second.save("two", sample());
		assertEquals(List.of("one", "two"), first.list());
		first.delete("one");
		assertNull(second.load("one"));
	}

	@Test void acceptsReorderedJsonFieldsAndKeepsCaseSensitiveNamesDistinct() throws IOException {
		Path file = directory.resolve("presets.json");
		Files.writeString(file, "{\"presets\":{\"one\":" + TRANSFORM + "},\"version\":1}");
		var store = new DisplayPresetStore(file);
		assertTransform(sample(), store.load("one"));
		store.save("One", new Transform());
		assertEquals(List.of("one", "One"), store.list());
		assertTransform(sample(), store.load("one"));
		assertTransform(new Transform(), store.load("One"));
	}

	@Test void namesAreOnlyJsonKeysNeverFilesystemPaths() throws IOException {
		Path folder = Files.createDirectory(directory.resolve("preferences"));
		Path file = folder.resolve("presets.json");
		var store = new DisplayPresetStore(file);
		List<String> names = List.of("../escaped.json", "..\\escaped.json", "C:\\escaped.json", "a/b", "<tag>&\"");
		for (String name : names)
			store.save(name, sample());
		assertEquals(names, new DisplayPresetStore(file).list());
		for (String name : names)
			assertTransform(sample(), store.load(name));
		assertFalse(Files.exists(directory.resolve("escaped.json")));
		try (var files = Files.list(folder)) {
			assertEquals(List.of(file), files.toList());
		}
	}

	@Test void invalidNamesAreRejectedWithoutChangingBytes() throws IOException {
		Path file = directory.resolve("presets.json");
		var store = new DisplayPresetStore(file);
		store.save("keep", sample());
		byte[] previous = Files.readAllBytes(file);
		String[] invalid = { null, "", " \t", "\u2003", "bad\nname", "bad\u0000name", "x".repeat(81), "\uD800", "\uDC00" };
		for (String name : invalid) {
			assertThrows(IllegalArgumentException.class, () -> store.save(name, sample()));
			assertThrows(IllegalArgumentException.class, () -> store.load(name));
			assertThrows(IllegalArgumentException.class, () -> store.delete(name));
			assertArrayEquals(previous, Files.readAllBytes(file));
		}
	}

	@Test void nameLimitCountsUnicodeCodePointsNotUtf16Units() throws IOException {
		var store = new DisplayPresetStore(directory.resolve("presets.json"));
		String name = "\uD83C\uDF1F".repeat(80);
		store.save(name, sample());
		assertEquals(List.of(name), store.list());
		assertThrows(IllegalArgumentException.class, () -> store.save(name + "x", sample()));
	}

	@Test void validatesEveryComponentBeforeChangingTheFile() throws Exception {
		Path file = directory.resolve("presets.json");
		var store = new DisplayPresetStore(file);
		store.save("keep", sample());
		byte[] previous = Files.readAllBytes(file);
		assertThrows(IllegalArgumentException.class, () -> store.save("invalid", null));
		for (var field : Transform.class.getFields()) {
			double limit = field.getName().startsWith("rotation") ? 360 : field.getName().startsWith("translation") ? 80 : 4;
			for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
					Math.nextUp(limit), Math.nextDown(-limit) }) {
				Transform invalid = sample();
				field.setDouble(invalid, value);
				assertThrows(IllegalArgumentException.class, () -> store.save("invalid", invalid), field.getName());
				assertArrayEquals(previous, Files.readAllBytes(file));
			}
		}
	}

	@Test void acceptsInclusiveLimitsAndZeroOrNegativeScale() throws IOException {
		var store = new DisplayPresetStore(directory.resolve("presets.json"));
		Transform edges = new Transform(-360, 360, 0, -80, 80, 0, -4, 4, 0);
		store.save("edges", edges);
		assertTransform(edges, store.load("edges"));
	}

	@Test void enforcesRecordLimitButAllowsReplacingAndDeletingAtCapacity() throws IOException {
		Path file = directory.resolve("presets.json");
		Files.writeString(file, manyPresets(128), StandardCharsets.UTF_8);
		var store = new DisplayPresetStore(file);
		assertEquals(128, store.list().size());
		byte[] previous = Files.readAllBytes(file);
		assertThrows(IllegalArgumentException.class, () -> store.save("overflow", sample()));
		assertArrayEquals(previous, Files.readAllBytes(file));
		store.save("preset0", new Transform());
		assertTransform(new Transform(), store.load("preset0"));
		store.delete("preset0");
		store.save("replacement", sample());
		assertEquals(128, new DisplayPresetStore(file).list().size());
	}

	@ParameterizedTest
	@MethodSource("invalidDocuments")
	void rejectsCorruptAndUnsupportedFilesOnEveryOperationWithoutOverwriting(String json) throws IOException {
		Path file = directory.resolve("presets.json");
		var stale = new DisplayPresetStore(file);
		stale.save("old", sample());
		Files.writeString(file, json, StandardCharsets.UTF_8);
		assertUnreadableAndPreserved(file, stale);
	}

	@Test void validatesEveryPersistedComponentInsteadOfAcceptingGsonDefaults() throws IOException {
		Path file = directory.resolve("presets.json");
		var stale = new DisplayPresetStore(file);
		for (var field : Transform.class.getFields()) {
			String name = field.getName();
			double limit = name.startsWith("rotation") ? 360 : name.startsWith("translation") ? 80 : 4;
			for (double value : new double[] { Math.nextUp(limit), Math.nextDown(-limit) }) {
				JsonObject transform = JsonParser.parseString(TRANSFORM).getAsJsonObject();
				transform.addProperty(name, value);
				Files.writeString(file, document(transform.toString()));
				assertUnreadableAndPreserved(file, stale);
			}
			JsonObject missing = JsonParser.parseString(TRANSFORM).getAsJsonObject();
			missing.remove(name);
			Files.writeString(file, document(missing.toString()));
			assertUnreadableAndPreserved(file, stale);
		}
	}

	@Test void rejectsOversizedFilesBeforeParsingAndCountsBytesNotCharacters() throws IOException {
		Path file = directory.resolve("presets.json");
		var stale = new DisplayPresetStore(file);
		String prefix = "{\"version\":1,\"presets\":{},\"extra\":\"";
		String oversized = prefix + "\u0e01".repeat(400_000) + "\"}";
		assertTrue(oversized.length() < 1024 * 1024);
		assertTrue(oversized.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024);
		Files.writeString(file, oversized, StandardCharsets.UTF_8);
		assertUnreadableAndPreserved(file, stale);
	}

	@Test void acceptsExactlyTheByteLimitAndRejectsOneMoreByte() throws IOException {
		Path file = directory.resolve("presets.json");
		String empty = "{\"version\":1,\"presets\":{}}";
		String atLimit = empty + " ".repeat(1024 * 1024 - empty.length());
		Files.writeString(file, atLimit);
		var store = new DisplayPresetStore(file);
		assertEquals(List.of(), store.list());
		Files.writeString(file, atLimit + " ");
		assertUnreadableAndPreserved(file, store);
	}

	@Test void invalidUtf8IsNotSilentlyReplacedAndOverwritten() throws IOException {
		Path file = directory.resolve("presets.json");
		var stale = new DisplayPresetStore(file);
		byte[] prefix = "{\"version\":1,\"presets\":{\"".getBytes(StandardCharsets.UTF_8);
		byte[] suffix = ("\":" + TRANSFORM + "}}").getBytes(StandardCharsets.UTF_8);
		byte[] malformed = ByteBuffer.allocate(prefix.length + 2 + suffix.length).put(prefix)
				.put((byte) 0xc3).put((byte) 0x28).put(suffix).array();
		Files.write(file, malformed);
		assertUnreadableAndPreserved(file, stale);
	}

	@Test void realReadFailuresAreNotMistakenForMissingFiles() throws IOException {
		Path file = directory.resolve("presets.json");
		var store = new DisplayPresetStore(file);
		Files.createDirectory(file);
		assertThrows(IOException.class, () -> new DisplayPresetStore(file));
		assertThrows(IOException.class, store::list);
		assertThrows(IOException.class, () -> store.load("one"));
		assertThrows(IOException.class, () -> store.save("one", sample()));
		assertThrows(IOException.class, () -> store.delete("one"));
		assertTrue(Files.isDirectory(file));
	}

	@Test void failedParentCreationDoesNotAlterTheBlockingFile() throws IOException {
		Path parent = directory.resolve("preferences");
		var store = new DisplayPresetStore(parent.resolve("presets.json"));
		Files.writeString(parent, "keep these bytes");
		byte[] previous = Files.readAllBytes(parent);
		assertThrows(IOException.class, () -> store.save("one", sample()));
		assertArrayEquals(previous, Files.readAllBytes(parent));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void failedAtomicReplacementPreservesPreviousBytesAndDoesNotPublishChanges() throws IOException {
		Path file = directory.resolve("presets.json");
		var store = new DisplayPresetStore(file);
		store.save("keep", sample());
		byte[] previous = Files.readAllBytes(file);
		try (var lock = FileChannel.open(file, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
			assertTrue(lock.isOpen());
			assertThrows(IOException.class, () -> store.save("keep", new Transform()));
			assertArrayEquals(previous, Files.readAllBytes(file));
			assertThrows(IOException.class, () -> store.save("new", sample()));
			assertArrayEquals(previous, Files.readAllBytes(file));
			assertThrows(IOException.class, () -> store.delete("keep"));
			assertArrayEquals(previous, Files.readAllBytes(file));
		}
		assertEquals(List.of("keep"), store.list());
		assertTransform(sample(), store.load("keep"));
		try (var files = Files.list(directory)) {
			assertEquals(List.of(file), files.toList());
		}
		store.save("new", new Transform());
		assertEquals(List.of("keep", "new"), store.list());
	}

	private static Stream<String> invalidDocuments() {
		return Stream.of("", " ", "null", "[]", "{}", "{", "{\"version\":1}",
				"{\"presets\":{}}", "{\"version\":2,\"presets\":{}}", "{\"version\":1.5,\"presets\":{}}",
				"{\"version\":\"1\",\"presets\":{}}", "{\"version\":1,\"presets\":null}",
				"{\"version\":1,\"presets\":[]}", "{\"version\":1,\"presets\":{},\"unknown\":true}",
				"{\"version\":1,\"version\":1,\"presets\":{}}", "{\"version\":1,\"presets\":{},\"presets\":{}}",
				"{version:1,presets:{}}", "{\"version\":1,/*comment*/\"presets\":{}}",
				"{\"version\":1,\"presets\":{}} {}", document("null"), document("{}"), document("[]"),
				document(TRANSFORM.replace("\"rotationX\":15", "\"rotationX\":\"15\"")),
				document(TRANSFORM.replace("\"rotationX\":15", "\"rotationX\":null")),
				document(TRANSFORM.replace("\"rotationX\":15", "\"rotationX\":true")),
				document(TRANSFORM.replace("\"rotationX\":15", "\"rotationX\":NaN")),
				document(TRANSFORM.replace("\"rotationX\":15", "\"rotationX\":1e999")),
				document(TRANSFORM.replace("\"rotationX\":15", "\"rotationX\":15,\"rotationX\":20")),
				document(TRANSFORM.replace("\"rotationX\":15", "\"unknown\":0,\"rotationX\":15")),
				"{\"version\":1,\"presets\":{\"one\":" + TRANSFORM + ",\"one\":" + TRANSFORM + "}}",
				document(TRANSFORM).replace("\"one\":", "\"\":"),
				document(TRANSFORM).replace("\"one\":", "\"bad\\nname\":"),
				document(TRANSFORM).replace("\"one\":", "\"\\ud800\":"),
				document(TRANSFORM).replace("\"one\":", "\"\\udc00\":"),
				document(TRANSFORM).replace("\"one\":", "\"" + "x".repeat(81) + "\":"),
				manyPresets(129));
	}

	private static void assertUnreadableAndPreserved(Path file, DisplayPresetStore stale) throws IOException {
		byte[] previous = Files.readAllBytes(file);
		assertThrows(IOException.class, () -> new DisplayPresetStore(file));
		assertThrows(IOException.class, stale::list);
		assertThrows(IOException.class, () -> stale.load("one"));
		assertThrows(IOException.class, () -> stale.save("new", sample()));
		assertThrows(IOException.class, () -> stale.delete("one"));
		assertArrayEquals(previous, Files.readAllBytes(file));
	}

	private static String document(String transform) {
		return "{\"version\":1,\"presets\":{\"one\":" + transform + "}}";
	}

	private static String manyPresets(int count) {
		JsonObject presets = new JsonObject();
		for (int i = 0; i < count; i++)
			presets.add("preset" + i, JsonParser.parseString(TRANSFORM));
		JsonObject document = new JsonObject();
		document.addProperty("version", 1);
		document.add("presets", presets);
		return document.toString();
	}

	private static Transform sample() {
		return new Transform(15, -25, 35, 1.25, -2.5, 3.75, .2, -.3, .4);
	}

	private static void assertTransform(Transform expected, Transform actual) {
		assertNotNull(actual);
		assertArrayEquals(new double[] { expected.rotationX, expected.rotationY, expected.rotationZ,
				expected.translationX, expected.translationY, expected.translationZ, expected.scaleX, expected.scaleY,
				expected.scaleZ }, new double[] { actual.rotationX, actual.rotationY, actual.rotationZ,
				actual.translationX, actual.translationY, actual.translationZ, actual.scaleX, actual.scaleY, actual.scaleZ });
	}
}
