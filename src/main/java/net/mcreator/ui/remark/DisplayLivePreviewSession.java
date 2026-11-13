package net.mcreator.ui.remark;

import com.google.gson.*;
import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.types.Fashion;
import net.mcreator.generator.GeneratorUtils;
import net.mcreator.ui.MCreator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local run-directory transport. Never generates code, launches a client, or edits source assets. */
public final class DisplayLivePreviewSession implements AutoCloseable {
	public enum State { WAITING, CONNECTED, PARTIAL, DISCONNECTED, ERROR, CLOSED }
	private static final Set<String> GENERATORS = Set.of("forge-1.20.1", "fabric-1.20.1",
			"neoforge-1.21.1", "neoforge-1.21.1-21.1.219", "neoforge-26.1.2", "fabric-26.1.2");
	private static final String[] SLOTS = { "helmet", "body", "leggings", "boots" };
	private static final String[] SUFFIXES = { "helmet", "chestplate", "leggings", "boots" };
	private static final int MAX_JSON = 262144, MAX_FILES = 64, MAX_TOTAL = 4 * 1024 * 1024;
	private final Path run, assets, manifest, acknowledgement, pack;
	private final String modid, registry, generator, token = UUID.randomUUID().toString();
	private final Set<Path> owned = new LinkedHashSet<>();
	private final ScheduledExecutorService heartbeat;
	private JsonObject payload;
	private Map<String, String> lastModels = Map.of();
	private long revision, packRevision;
	private State state = State.WAITING;
	private String detail = "Waiting for a local development client. Build/run once after installing the helper.";
	private boolean closed;

	public DisplayLivePreviewSession(MCreator mcreator, Fashion fashion) {
		this(mcreator.getFolderManager().getClientRunDir().toPath(),
				GeneratorUtils.getModAssetsRoot(mcreator.getWorkspace(), mcreator.getGeneratorConfiguration()).toPath(),
				mcreator.getWorkspace().getWorkspaceSettings().getModID(), fashion.getModElement().getRegistryName(),
				mcreator.getGenerator().getGeneratorName(), fashion);
	}

	/** Explicit roots also allow isolated tests without constructing Swing or a workspace. */
	public DisplayLivePreviewSession(Path run, Path modAssets, String modid, String registry,
			String generator, Fashion fashion) {
		if (!GENERATORS.contains(generator)) throw new IllegalArgumentException("Unsupported generator: " + generator);
		if (!modid.matches("[a-z0-9_]{1,64}") || !registry.matches("[a-z0-9_]{1,128}"))
			throw new IllegalArgumentException("Invalid preview namespace or element name");
		this.run = run.toAbsolutePath().normalize();
		this.assets = modAssets.toAbsolutePath().normalize();
		this.modid = modid;
		this.registry = registry;
		this.generator = generator;
		Path sessions = this.run.resolve(".remark-display-preview").resolve(modid);
		manifest = sessions.resolve(token + ".json");
		acknowledgement = sessions.resolve(token + ".ack");
		// Not in resourcepacks: only a dev-guarded repository source can discover this pack.
		pack = sessions.resolve(token + ".pack");
		heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread thread = new Thread(r, "Display preview lease");
			thread.setDaemon(true);
			return thread;
		});
		publish(fashion);
		heartbeat.scheduleWithFixedDelay(this::renew, 3, 3, TimeUnit.SECONDS);
	}

	/** Publishes a validated snapshot. Errors are reported through getStatus; the previous valid revision is retained. */
	public synchronized void publish(Fashion fashion) {
		if (closed) return;
		try {
			JsonObject next = new JsonObject();
			next.addProperty("protocol", 1);
			next.addProperty("session", token);
			next.addProperty("element", registry);
			next.addProperty("generator", generator);
			JsonObject worn = new JsonObject();
			for (String slot : SLOTS) for (boolean left : new boolean[] { true, false }) {
				JsonObject value = transform(fashion.getWornTransformFor(slot, left), true);
				value.addProperty("enabled", fashion.getWornDisplay().isSideEnabled(slot, left));
				worn.add(slot + (left ? "_left" : "_right"), value);
			}
			next.add("worn", worn);
			Map<String, String> models = new LinkedHashMap<>();
			boolean[] enabled = { fashion.shouldGenerateHelmetItem(), fashion.shouldGenerateBodyItem(),
					fashion.shouldGenerateLeggingsItem(), fashion.shouldGenerateBootsItem() };
			for (int i = 0; i < SLOTS.length; i++) {
				if (!enabled[i]) continue;
				ItemDisplaySettings settings = fashion.getItemDisplaySettingsFor(SLOTS[i]);
				JsonObject display = display(settings);
				String model = "item/" + registry + "_" + SUFFIXES[i];
				cloneModel(model, model, display, models, new HashMap<>());
				if (generator.endsWith("26.1.2")) {
					String definitionPath = "items/" + registry + "_" + SUFFIXES[i] + ".json";
					JsonObject definition = readAsset(definitionPath);
					rewriteItemModels(definition, display, models, new HashMap<>(), model);
					models.put(definitionPath, definition.toString());
				}
			}
			if (models.isEmpty()) throw new IOException("No generated Fashion items to preview");
			long total = models.values().stream().mapToLong(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();
			if (models.size() > MAX_FILES || total > MAX_TOTAL) throw new IOException("Preview model budget exceeded");
			ensureDirectory(manifest.getParent(), false);
			if (!Files.exists(pack, LinkOption.NOFOLLOW_LINKS)) {
				ensureDirectory(pack.getParent(), false);
				Files.createDirectory(pack);
				owned.add(pack);
				writeOwned(pack.resolve(".owner"), token);
			} else if (!readSmall(pack.resolve(".owner"), 128).equals(token)) {
				throw new IOException("Preview pack ownership changed");
			}
			if (!models.equals(lastModels)) {
				// Withdraw the manifest while the bounded pack snapshot is being replaced.
				Files.deleteIfExists(manifest);
				for (var entry : models.entrySet()) {
					Path output = pack.resolve("assets").resolve(modid).resolve(entry.getKey());
					ensureDirectory(output.getParent(), true);
					writeOwned(output, entry.getValue());
				}
				writeOwned(pack.resolve("pack.mcmeta"), packMetadata());
				packRevision++;
				lastModels = Map.copyOf(models);
			}
			next.addProperty("revision", ++revision);
			next.addProperty("packRevision", packRevision);
			next.addProperty("updated", System.currentTimeMillis());
			next.addProperty("lease", System.currentTimeMillis() + 15000);
			writeOwned(manifest, next.toString());
			payload = next;
			state = State.WAITING;
			detail = "Waiting for runtime acknowledgement of revision " + revision;
		} catch (IOException | RuntimeException ex) {
			state = State.ERROR;
			detail = "Preview unavailable: " + ex.getMessage();
		}
	}

	private synchronized void renew() {
		if (closed || payload == null) return;
		try {
			refreshStatus();
			if (state == State.DISCONNECTED) { close(); return; }
			payload.addProperty("lease", System.currentTimeMillis() + 15000);
			writeOwned(manifest, payload.toString());
		} catch (IOException | RuntimeException ex) {
			state = State.ERROR;
			detail = "Preview lease failed: " + ex.getMessage();
		}
	}

	public synchronized State getState() { refreshStatus(); return state; }
	public synchronized String getStatus() { refreshStatus(); return detail; }
	public Path getManifestPath() { return manifest; }
	public Path getAcknowledgementPath() { return acknowledgement; }
	public Path getPackDirectory() { return pack; }

	private void refreshStatus() {
		if (closed || state == State.ERROR || payload == null) return;
		state = State.WAITING;
		detail = "Waiting for local development client; no current runtime acknowledgement.";
		try {
			JsonObject ack = parse(readSmall(acknowledgement, 16384));
			long age = System.currentTimeMillis() - ack.get("time").getAsLong();
			if (!token.equals(ack.get("session").getAsString()) || ack.get("revision").getAsLong() != revision
					|| age < -1000 || age > 5000) return;
			String result = ack.get("state").getAsString();
			state = switch (result) {
				case "APPLIED" -> State.CONNECTED;
				case "ITEM_ONLY" -> State.PARTIAL;
				case "DISCONNECTED" -> State.DISCONNECTED;
				case "ERROR" -> State.ERROR;
				default -> State.WAITING;
			};
			detail = switch (state) {
				case CONNECTED -> "Local dev client applied revision " + revision;
				case PARTIAL -> "Item display applied; no worn renderer hook has been observed for this element.";
				case DISCONNECTED -> "Local client disconnected; preview removed.";
				case ERROR -> "Runtime rejected the preview or resource reload failed.";
				default -> "Runtime found; waiting for a local single-player world or resource reload.";
			};
		} catch (IOException | RuntimeException ignored) { }
	}

	private void cloneModel(String source, String target, JsonObject display, Map<String, String> output,
			Map<String, String> visited) throws IOException {
		if (visited.putIfAbsent(source, target) != null) return;
		if (visited.size() + output.size() > MAX_FILES) throw new IOException("Too many model references");
		JsonObject model = readAsset("models/" + source + ".json");
		if (display != null) {
			JsonObject merged = model.has("display") ? model.getAsJsonObject("display").deepCopy() : new JsonObject();
			for (String context : List.of("gui", "ground", "fixed", "thirdperson_righthand", "thirdperson_lefthand",
					"firstperson_righthand", "firstperson_lefthand")) merged.remove(context);
			display.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue().deepCopy()));
			model.add("display", merged);
		}
		if (model.has("parent") && model.get("parent").getAsString().startsWith(modid + ":"))
			model.addProperty("parent", cloneReference(model.get("parent").getAsString(), display, output, visited));
		if (model.has("overrides")) for (JsonElement entry : model.getAsJsonArray("overrides")) {
			JsonObject override = entry.getAsJsonObject();
			override.addProperty("model", cloneReference(override.get("model").getAsString(), display, output, visited));
		}
		output.put("models/" + target + ".json", model.toString());
	}

	private String cloneReference(String reference, JsonObject display, Map<String, String> output,
			Map<String, String> visited) throws IOException {
		if (!reference.startsWith(modid + ":")) throw new IOException("External override models cannot be previewed: " + reference);
		String source = reference.substring(modid.length() + 1);
		String target = visited.get(source);
		if (target == null) {
			target = "item/remark_display_" + token + "_" + output.size() + "_" + visited.size();
			cloneModel(source, target, display, output, visited);
		}
		return modid + ":" + target;
	}

	private void rewriteItemModels(JsonElement node, JsonObject display, Map<String, String> output,
			Map<String, String> visited, String primary) throws IOException {
		if (node.isJsonArray()) {
			for (JsonElement child : node.getAsJsonArray()) rewriteItemModels(child, display, output, visited, primary);
		} else if (node.isJsonObject()) {
			JsonObject object = node.getAsJsonObject();
			for (var entry : new ArrayList<>(object.entrySet())) {
				if (entry.getKey().equals("model") && entry.getValue().isJsonPrimitive()
						&& entry.getValue().getAsJsonPrimitive().isString()) {
					String reference = entry.getValue().getAsString();
					if (!reference.equals(modid + ":" + primary))
						object.addProperty("model", cloneReference(reference, display, output, visited));
				} else rewriteItemModels(entry.getValue(), display, output, visited, primary);
			}
		}
	}

	private JsonObject readAsset(String relative) throws IOException {
		if (!relative.matches("[a-z0-9_./-]+") || Arrays.asList(relative.split("/")).contains(".."))
			throw new IOException("Unsafe model reference");
		Path source = assets.resolve(relative).normalize();
		if (!source.startsWith(assets)) throw new IOException("Model outside asset root");
		if (!Files.exists(source)) throw new IOException("Generate/build the model once first: " + relative);
		if (!source.toRealPath().startsWith(assets.toRealPath())) throw new IOException("Linked model outside asset root");
		return parse(readSmall(source, MAX_JSON));
	}

	private static JsonObject display(ItemDisplaySettings settings) {
		JsonObject display = new JsonObject();
		if (!settings.enabled) return display;
		display.add("gui", transform(settings.getGui(), false));
		display.add("ground", transform(settings.getGround(), false));
		display.add("fixed", transform(settings.getFixed(), false));
		display.add("thirdperson_righthand", transform(settings.getThirdPersonRightHand(), false));
		display.add("thirdperson_lefthand", transform(settings.getThirdPersonLeftHand(), false));
		display.add("firstperson_righthand", transform(settings.getFirstPersonRightHand(), false));
		display.add("firstperson_lefthand", transform(settings.getFirstPersonLeftHand(), false));
		return display;
	}

	private static JsonObject transform(ItemDisplaySettings.Transform t, boolean worn) {
		JsonObject result = new JsonObject();
		result.add("rotation", vector(t.rotationX, t.rotationY, t.rotationZ, -360, 360));
		result.add("translation", vector(t.translationX, t.translationY, t.translationZ, -80, 80));
		result.add("scale", vector(t.scaleX, t.scaleY, t.scaleZ, -4, 4));
		return result;
	}

	private static JsonArray vector(double x, double y, double z, double min, double max) {
		JsonArray array = new JsonArray();
		for (double value : new double[] { x, y, z }) {
			if (!Double.isFinite(value) || value < min || value > max)
				throw new IllegalArgumentException("Transform must be finite and between " + min + " and " + max);
			array.add(value);
		}
		return array;
	}

	private String packMetadata() {
		return generator.endsWith("26.1.2")
				? "{\"pack\":{\"description\":\"Temporary Remake Studio display preview\",\"min_format\":84,\"max_format\":84}}"
				: "{\"pack\":{\"description\":\"Temporary Remake Studio display preview\",\"pack_format\":"
						+ (generator.contains("1.20.1") ? 15 : 34) + "}}";
	}

	private void ensureDirectory(Path directory, boolean track) throws IOException {
		checkRunPath(directory);
		if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
		if (!directory.equals(run)) ensureDirectory(directory.getParent(), track && directory.getParent().startsWith(pack));
		Files.createDirectory(directory);
		if (track) owned.add(directory);
	}

	private void checkRunPath(Path path) throws IOException {
		if (!path.normalize().startsWith(run)) throw new IOException("Path outside local run directory");
		for (Path current = path; current != null; current = current.getParent())
			if (Files.isSymbolicLink(current) || (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
					&& Files.readAttributes(current, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()))
				throw new IOException("Linked preview path is not allowed");
	}

	private void writeOwned(Path target, String text) throws IOException {
		checkRunPath(target);
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		checkRunPath(temp);
		try {
			Files.writeString(temp, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			owned.add(temp);
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			owned.add(target);
		} finally { Files.deleteIfExists(temp); }
	}

	private static String readSmall(Path path, int maximum) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum)
			throw new IOException("Missing, linked, or oversized JSON file");
		try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
			byte[] bytes = input.readNBytes(maximum + 1);
			if (bytes.length > maximum) throw new IOException("JSON byte limit exceeded");
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	private static JsonObject parse(String text) throws IOException {
		int depth = 0;
		boolean quoted = false, escaped = false;
		for (char c : text.toCharArray()) {
			if (quoted) {
				if (escaped) escaped = false;
				else if (c == '\\') escaped = true;
				else if (c == '"') quoted = false;
			} else if (c == '"') quoted = true;
			else if (c == '{' || c == '[') { if (++depth > 32) throw new IOException("JSON nesting limit exceeded"); }
			else if (c == '}' || c == ']') depth--;
		}
		return JsonParser.parseString(text).getAsJsonObject();
	}

	/** Idempotent. Only exact paths created by this session (and its acknowledgement) are deleted. */
	@Override public synchronized void close() {
		if (closed) return;
		closed = true;
		heartbeat.shutdownNow();
		try { checkRunPath(manifest); Files.deleteIfExists(manifest); } catch (IOException ignored) { }
		List<Path> paths = new ArrayList<>(owned);
		paths.add(acknowledgement);
		paths.add(manifest);
		paths.sort(Comparator.comparingInt(Path::getNameCount).reversed());
		boolean incomplete = false;
		for (Path path : paths) try {
			checkRunPath(path);
			Files.deleteIfExists(path);
		} catch (IOException ex) { incomplete = true; }
		payload = null;
		state = State.CLOSED;
		detail = incomplete ? "Closed; some owned files could not be removed. The runtime lease expires within 15 seconds."
				: "Closed; runtime will remove the preview pack and restore base resources on its next tick.";
	}
}
