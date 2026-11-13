<#-- Local development transport only. Keep copies byte-identical across generators. -->
package ${package}.client.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;

<#if previewLoader == "forge">
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "${modid}", value = net.minecraftforge.api.distmarker.Dist.CLIENT)
<#elseif previewLoader == "neoforge">
@net.neoforged.fml.common.EventBusSubscriber(modid = "${modid}", value = net.neoforged.api.distmarker.Dist.CLIENT)
</#if>
public final class DisplayLivePreview {
	private static final boolean DEV = <#if previewLoader == "fabric">net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()<#elseif previewLoader == "forge">!net.minecraftforge.fml.loading.FMLEnvironment.production<#elseif previewNext>!net.neoforged.fml.loading.FMLEnvironment.isProduction()<#else>!net.neoforged.fml.loading.FMLEnvironment.production</#if>;
	private static final String PACK_ID = "remark_display_preview:${modid}";
	private static final Map<String, Long> disconnected = new HashMap<>();
	private static final Map<String, WornOverride> worn = new HashMap<>();
	private static JsonObject active;
	private static Path directory, activePack;
	private static boolean initialized, sourceReady, wornHookObserved, failed;
	private static long nextPoll, nextReload, loadedPackRevision = -1;
	private static String loadedSession = "", loadingSession = "", failedRevision = "";
	private static long loadingPackRevision;
	private static CompletableFuture<Void> reload;

	public record WornOverride(boolean enabled, double rotationX, double rotationY, double rotationZ,
			double translationX, double translationY, double translationZ, double scaleX, double scaleY, double scaleZ) { }

	/** Null uses generated defaults; enabled=false skips this side before pushing a pose. */
	public static WornOverride wornOverride(String element, String slot, boolean left) {
		if (!DEV) return null;
		Minecraft client = Minecraft.getInstance();
		if (active == null || client.level == null || !client.hasSingleplayerServer()
				|| active.get("lease").getAsLong() < System.currentTimeMillis()
				|| !element.equals(active.get("element").getAsString())) return null;
		wornHookObserved = true;
		return worn.get(slot + (left ? "_left" : "_right"));
	}

<#if previewLoader == "fabric">
	public static void clientLoad() {
		if (DEV && !initialized) {
			initialized = true;
			sourceReady = true; // The client-only repository mixin supplies previewPack().
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		}
	}
<#elseif previewLoader == "forge">
	@net.minecraftforge.eventbus.api.SubscribeEvent
	public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
		if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) tick();
	}
<#else>
	@net.neoforged.bus.api.SubscribeEvent
	public static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) { tick(); }
</#if>

	private static void tick() {
		if (!DEV) return; // Production never reads live files or registers their resource pack.
		long now = System.currentTimeMillis();
		if (now < nextPoll) return;
		nextPoll = now + 250;
		Minecraft client = Minecraft.getInstance();
		if (directory == null) directory = client.gameDirectory.toPath().toAbsolutePath().normalize()
				.resolve(".remark-display-preview/${modid}");
<#if previewLoader != "fabric">
		if (!initialized) {
			initialized = true;
			try {
				RepositorySource source = consumer -> { Pack pack = previewPack(); if (pack != null) consumer.accept(pack); };
<#if previewNext>
				// Cached vanilla 26 APIs lack loader patches: probe the capability and fail closed.
				client.getResourcePackRepository().getClass().getMethod("addPackFinder", RepositorySource.class)
						.invoke(client.getResourcePackRepository(), source);
<#else>
				client.getResourcePackRepository().addPackFinder(source);
</#if>
				sourceReady = true;
			} catch (Exception unavailable) { sourceReady = false; }
		}
</#if>
		if (reload != null && reload.isDone()) {
			try {
				reload.join();
				loadedSession = loadingSession;
				loadedPackRevision = loadingPackRevision;
				failed = false;
			} catch (RuntimeException error) {
				failed = true;
				if (active != null) { failedRevision = signature(active); acknowledge(active, "ERROR", now); }
				active = null;
				activePack = null;
				worn.clear();
				loadedSession = "failed";
			}
			reload = null;
		}
		disconnected.entrySet().removeIf(entry -> entry.getValue() < now);
		boolean local = client.level != null && client.hasSingleplayerServer();
		if (!local && active != null) {
			if (disconnected.size() < 16) disconnected.put(active.get("session").getAsString(), now + 20000);
			acknowledge(active, "DISCONNECTED", now);
			active = null;
			activePack = null;
			worn.clear();
		}
		JsonObject candidate = findSession(now);
		if (candidate != null && (!local || !sourceReady)) {
			acknowledge(candidate, sourceReady ? "WAITING_LOCAL" : "ERROR", now);
			candidate = null;
		}
		if (candidate == null) {
			active = null;
			activePack = null;
			worn.clear();
		} else if (!signature(candidate).equals(failedRevision)) {
			try {
				Map<String, WornOverride> values = parseWorn(candidate.getAsJsonObject("worn"));
				Path packPath = directory.resolve(candidate.get("session").getAsString() + ".pack");
				if (!safe(packPath) || !read(packPath.resolve(".owner"), 128).equals(candidate.get("session").getAsString()))
					throw new IOException("Pack ownership mismatch");
				if (active == null || !active.get("session").equals(candidate.get("session"))) wornHookObserved = false;
				if (active == null || !signature(active).equals(signature(candidate))) validatePack(packPath);
				active = candidate;
				activePack = packPath;
				worn.clear();
				worn.putAll(values);
			} catch (RuntimeException | IOException invalid) {
				acknowledge(candidate, "ERROR", now);
				active = null;
				activePack = null;
				worn.clear();
			}
		} else { acknowledge(candidate, "ERROR", now); }
		String desired = active == null ? "" : active.get("session").getAsString();
		long desiredPack = active == null ? -1 : active.get("packRevision").getAsLong();
		boolean changed = !desired.equals(loadedSession) || desiredPack != loadedPackRevision
				|| (active != null && !client.getResourcePackRepository().getSelectedIds().contains(PACK_ID));
		if (changed && reload == null && now >= nextReload
				&& (active == null || now - active.get("updated").getAsLong() >= 350)) {
			try {
				PackRepository repository = client.getResourcePackRepository();
				List<String> selected = new ArrayList<>(repository.getSelectedIds());
				selected.remove(PACK_ID);
				repository.reload();
				if (active != null) {
					if (repository.getPack(PACK_ID) == null) throw new IllegalStateException("Preview pack not available");
					selected.add(PACK_ID);
				}
				repository.setSelected(selected); // Never save Options; preserve other selections and order.
				loadingSession = desired;
				loadingPackRevision = desiredPack;
				reload = client.reloadResourcePacks();
				nextReload = now + 1000;
			} catch (RuntimeException error) {
				if (active != null) { failedRevision = signature(active); acknowledge(active, "ERROR", now); }
				active = null;
				activePack = null;
				worn.clear();
				failed = true;
				nextReload = now + 1000;
			}
		}
		if (active != null) acknowledge(active, !changed && reload == null && !failed
				? (wornHookObserved ? "APPLIED" : "ITEM_ONLY") : "RELOADING", now);
	}

	/** Only a dev repository source calls this; the directory is outside ordinary resourcepacks. */
	public static Pack previewPack() {
		if (!DEV || active == null || activePack == null || !safe(activePack)
				|| active.get("lease").getAsLong() < System.currentTimeMillis()) return null;
		Path content = activePack;
<#if previewModern>
		return Pack.readMetaAndCreate(new PackLocationInfo(PACK_ID, Component.literal("Remake Studio live display"),
				PackSource.BUILT_IN, Optional.empty()), new PathPackResources.PathResourcesSupplier(content),
				PackType.CLIENT_RESOURCES, new PackSelectionConfig(false, Pack.Position.TOP, false));
<#else>
		return Pack.readMetaAndCreate(PACK_ID, Component.literal("Remake Studio live display"), false,
				id -> new PathPackResources(id, content, false), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
</#if>
	}

	private static JsonObject findSession(long now) {
		if (!safe(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return null;
		JsonObject latest = null;
		int count = 0;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
			for (Path path : files) {
				if (++count > 16) return null;
				try {
					String filename = path.getFileName().toString();
					if (!filename.matches("[a-f0-9-]{36}\\.json")) continue;
					JsonObject data = json(read(path, 16384));
					String session = data.get("session").getAsString();
					if (!(session + ".json").equals(filename) || disconnected.containsKey(session)) continue;
					long lease = data.get("lease").getAsLong(), updated = data.get("updated").getAsLong();
					if (data.get("protocol").getAsInt() != 1 || lease < now || lease > now + 20000
							|| updated > now + 1000 || data.get("revision").getAsLong() < 1
							|| data.get("packRevision").getAsLong() < 1
							|| !data.get("element").getAsString().matches("[a-z0-9_]{1,128}")) continue;
					if (latest == null || updated > latest.get("updated").getAsLong()) latest = data;
				} catch (RuntimeException | IOException invalid) { }
			}
		} catch (IOException ignored) { }
		return latest;
	}

	private static Map<String, WornOverride> parseWorn(JsonObject data) {
		if (data.size() != 8) throw new IllegalArgumentException("Invalid worn slot count");
		Map<String, WornOverride> result = new HashMap<>();
		for (String slot : new String[] { "helmet", "body", "leggings", "boots" }) for (String side : new String[] { "left", "right" }) {
			String key = slot + "_" + side;
			JsonObject value = data.getAsJsonObject(key);
			double[] r = vector(value, "rotation", -360, 360), t = vector(value, "translation", -80, 80), s = vector(value, "scale", -4, 4);
			if (!value.get("enabled").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid visibility");
			result.put(key, new WornOverride(value.get("enabled").getAsBoolean(), r[0], r[1], r[2], t[0], t[1], t[2], s[0], s[1], s[2]));
		}
		return result;
	}

	private static double[] vector(JsonObject value, String key, double min, double max) {
		JsonArray array = value.getAsJsonArray(key);
		if (array.size() != 3) throw new IllegalArgumentException("Invalid vector length");
		double[] result = new double[3];
		for (int i = 0; i < 3; i++) {
			if (!array.get(i).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid number");
			result[i] = array.get(i).getAsDouble();
			if (!Double.isFinite(result[i]) || result[i] < min || result[i] > max) throw new IllegalArgumentException("Invalid transform");
		}
		return result;
	}

	private static String signature(JsonObject data) { return data.get("session").getAsString() + ":" + data.get("revision").getAsLong(); }

	private static void validatePack(Path path) throws IOException {
		int count = 0;
		long total = 0;
		try (var paths = Files.walk(path, 16)) {
			var iterator = paths.iterator();
			while (iterator.hasNext()) {
				Path entry = iterator.next();
				if (++count > 160 || !safe(entry)) throw new IOException("Preview pack path budget exceeded");
				if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
					long size = Files.size(entry);
					if (size > 262144 || (total += size) > 4198400) throw new IOException("Preview pack byte budget exceeded");
				}
			}
		}
	}

	private static void acknowledge(JsonObject data, String state, long now) {
		String session = data.get("session").getAsString();
		Path target = directory.resolve(session + ".ack"), temporary = directory.resolve(session + ".ack.tmp");
		if (!safe(target) || !safe(temporary)) return;
		JsonObject ack = new JsonObject();
		ack.addProperty("session", session);
		ack.addProperty("revision", data.get("revision").getAsLong());
		ack.addProperty("time", now);
		ack.addProperty("state", state);
		try {
			if (!Files.isRegularFile(directory.resolve(session + ".json"), LinkOption.NOFOLLOW_LINKS)) return;
			Files.writeString(temporary, ack.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) { }
		finally { try { if (safe(temporary)) Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
	}

	private static boolean safe(Path path) {
		if (path == null || directory == null || !path.normalize().startsWith(directory)) return false;
		try {
			for (Path p = path; p != null; p = p.getParent()) {
				if (Files.isSymbolicLink(p)) return false;
				if (Files.exists(p, LinkOption.NOFOLLOW_LINKS)
						&& Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther()) return false;
			}
			return true;
		} catch (IOException ignored) { return false; }
	}

	private static String read(Path path, int maximum) throws IOException {
		if (!safe(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > maximum)
			throw new IOException("Invalid preview file");
		try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
			byte[] bytes = input.readNBytes(maximum + 1);
			if (bytes.length > maximum) throw new IOException("Preview byte limit");
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	private static JsonObject json(String text) throws IOException {
		int depth = 0;
		boolean quoted = false, escaped = false;
		for (char c : text.toCharArray()) {
			if (quoted) {
				if (escaped) escaped = false;
				else if (c == '\\') escaped = true;
				else if (c == '"') quoted = false;
			} else if (c == '"') quoted = true;
			else if (c == '{' || c == '[') { if (++depth > 16) throw new IOException("Preview nesting limit"); }
			else if (c == '}' || c == ']') depth--;
		}
		return JsonParser.parseString(text).getAsJsonObject();
	}
}
