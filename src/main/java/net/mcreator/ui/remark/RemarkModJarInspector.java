/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFlavor;
import net.mcreator.io.zip.ZipIO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class RemarkModJarInspector {

	private static final Pattern TOML_KEY_VALUE = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=\\s*(.*)$");
	private static final Pattern VERSION = Pattern.compile("(?<![0-9])([0-9]+(?:\\.[0-9]+){1,3})(?![0-9])");

	private RemarkModJarInspector() {
	}

	public static Inspection inspect(File file) throws IOException {
		if (file == null || !file.isFile())
			throw new IOException("Mod JAR does not exist");
		if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".jar"))
			throw new IOException("Only .jar files can be inspected");

		MutableMetadata metadata = new MutableMetadata();
		EnumSet<Loader> loaders = EnumSet.noneOf(Loader.class);
		Set<String> namespaces = new LinkedHashSet<>();
		Set<String> licenseFiles = new LinkedHashSet<>();
		List<String> warnings = new ArrayList<>();
		Counters counters = new Counters();

		try (ZipFile zip = ZipIO.openZipFile(file)) {
			parseFabricMetadata(zip, metadata, loaders);
			parseTomlMetadata(zip, "META-INF/neoforge.mods.toml", Loader.NEOFORGE, metadata, loaders);
			parseTomlMetadata(zip, "META-INF/mods.toml", Loader.FORGE, metadata, loaders);
			parseQuiltMetadata(zip, metadata, loaders);
			readManifest(zip, metadata);

			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory())
					continue;
				String name = entry.getName().replace('\\', '/');
				String lower = name.toLowerCase(Locale.ROOT);
				if (lower.endsWith(".class"))
					counters.classes++;
				if (lower.startsWith("assets/") && lower.endsWith(".png"))
					counters.textures++;
				if (lower.startsWith("assets/") && lower.contains("/models/") && lower.endsWith(".json"))
					counters.models++;
				if (lower.startsWith("assets/") && lower.endsWith(".ogg"))
					counters.sounds++;
				if (lower.startsWith("data/"))
					counters.dataFiles++;
				if (lower.endsWith(".mixins.json") || lower.contains("mixin") && lower.endsWith(".json"))
					counters.mixinConfigs++;
				if (lower.endsWith(".jar"))
					counters.nestedJars++;

				String[] parts = name.split("/");
				if (parts.length > 2 && (parts[0].equals("assets") || parts[0].equals("data")))
					namespaces.add(parts[1]);

				String leaf = parts[parts.length - 1].toUpperCase(Locale.ROOT);
				if (leaf.startsWith("LICENSE") || leaf.startsWith("COPYING") || leaf.startsWith("NOTICE"))
					licenseFiles.add(name);
			}
		}

		if (loaders.isEmpty()) {
			loaders.add(Loader.UNKNOWN);
			warnings.add("No Fabric, Forge, NeoForge, or Quilt metadata was found");
		}
		if (metadata.id.isBlank())
			warnings.add("Mod ID could not be detected");
		if (metadata.version.isBlank())
			warnings.add("Mod version could not be detected");
		if (metadata.license.isBlank() && licenseFiles.isEmpty())
			warnings.add("No declared license or license file was found");

		metadata.dependencies.sort(Comparator.comparing(Dependency::required).reversed()
				.thenComparing(Dependency::id));
		return new Inspection(file.getName(), file.getAbsolutePath(), Files.size(file.toPath()), sha256(file),
				List.copyOf(loaders), metadata.id, metadata.name, metadata.version, metadata.description,
				metadata.license, metadata.environment, List.copyOf(metadata.dependencies), List.copyOf(namespaces),
				List.copyOf(licenseFiles), counters.classes, counters.textures, counters.models, counters.sounds,
				counters.dataFiles, counters.mixinConfigs, counters.nestedJars, List.copyOf(warnings));
	}

	public static Compatibility compare(Inspection inspection, GeneratorConfiguration generator) {
		List<String> messages = new ArrayList<>();
		Loader expected = loaderFor(generator.getGeneratorFlavor());
		boolean knownLoader = !inspection.loaders().contains(Loader.UNKNOWN);
		if (expected != Loader.UNKNOWN && knownLoader && !inspection.loaders().contains(expected))
			messages.add("Loader mismatch: JAR is " + inspection.loaders() + " but target is " + expected);

		Dependency minecraft = inspection.dependencies().stream().filter(dependency -> dependency.id().equals("minecraft"))
				.findFirst().orElse(null);
		VersionMatch versionMatch = minecraft == null ? VersionMatch.UNKNOWN :
				matchesVersion(minecraft.version(), generator.getGeneratorMinecraftVersion());
		if (versionMatch == VersionMatch.NO)
			messages.add("Minecraft version mismatch: requires " + minecraft.version() + " but target is "
					+ generator.getGeneratorMinecraftVersion());
		else if (versionMatch == VersionMatch.UNKNOWN && minecraft != null)
			messages.add("Minecraft constraint needs manual review: " + minecraft.version());

		CompatibilityStatus status;
		if (messages.stream().anyMatch(message -> message.startsWith("Loader mismatch")
				|| message.startsWith("Minecraft version mismatch")))
			status = CompatibilityStatus.BLOCKED;
		else if (!messages.isEmpty() || !knownLoader)
			status = CompatibilityStatus.REVIEW;
		else
			status = CompatibilityStatus.READY;
		return new Compatibility(status, List.copyOf(messages));
	}

	public static String formatReport(Inspection inspection, GeneratorConfiguration generator) {
		List<String> lines = new ArrayList<>();
		lines.add("File: " + inspection.fileName());
		lines.add("Path: " + inspection.path());
		lines.add("Size: " + RemarkStudioQualityAnalyzer.formatBytes(inspection.size()));
		lines.add("SHA-256: " + inspection.sha256());
		lines.add("");
		lines.add("Mod ID: " + valueOrUnknown(inspection.id()));
		lines.add("Name: " + valueOrUnknown(inspection.name()));
		lines.add("Version: " + valueOrUnknown(inspection.version()));
		lines.add("Loader: " + inspection.loaders());
		lines.add("Environment: " + valueOrUnknown(inspection.environment()));
		lines.add("License: " + valueOrUnknown(inspection.license()));
		if (!inspection.description().isBlank())
			lines.add("Description: " + inspection.description().replaceAll("\\s+", " ").trim());

		if (generator != null) {
			Compatibility compatibility = compare(inspection, generator);
			lines.add("");
			lines.add("Current workspace: " + compatibility.status());
			for (String message : compatibility.messages())
				lines.add("  - " + message);
		}

		lines.add("");
		lines.add("Dependencies (" + inspection.dependencies().size() + "):");
		for (Dependency dependency : inspection.dependencies())
			lines.add("  " + (dependency.required() ? "[required] " : "[optional] ") + dependency.id() + " "
					+ dependency.version() + (dependency.side().isBlank() ? "" : " | " + dependency.side()));
		if (inspection.dependencies().isEmpty())
			lines.add("  (none declared)");

		lines.add("");
		lines.add("Contents:");
		lines.add("  Classes: " + inspection.classCount());
		lines.add("  Textures: " + inspection.textureCount());
		lines.add("  Models: " + inspection.modelCount());
		lines.add("  Sounds: " + inspection.soundCount());
		lines.add("  Data files: " + inspection.dataFileCount());
		lines.add("  Mixin configs: " + inspection.mixinConfigCount());
		lines.add("  Nested JARs: " + inspection.nestedJarCount());
		lines.add("  Namespaces: " + inspection.namespaces());
		lines.add("  License files: " + inspection.licenseFiles());
		if (!inspection.warnings().isEmpty()) {
			lines.add("");
			lines.add("Warnings:");
			for (String warning : inspection.warnings())
				lines.add("  - " + warning);
		}
		return String.join("\n", lines);
	}

	private static void parseFabricMetadata(ZipFile zip, MutableMetadata metadata, Set<Loader> loaders)
			throws IOException {
		ZipEntry entry = zip.getEntry("fabric.mod.json");
		if (entry == null)
			return;
		loaders.add(Loader.FABRIC);
		JsonObject root = JsonParser.parseString(read(zip, entry)).getAsJsonObject();
		metadata.merge(value(root, "id"), value(root, "name"), value(root, "version"), value(root, "description"),
				jsonText(root.get("license")), value(root, "environment"));
		readJsonDependencies(root, "depends", true, metadata.dependencies);
		readJsonDependencies(root, "recommends", false, metadata.dependencies);
		readJsonDependencies(root, "suggests", false, metadata.dependencies);
	}

	private static void parseQuiltMetadata(ZipFile zip, MutableMetadata metadata, Set<Loader> loaders)
			throws IOException {
		ZipEntry entry = zip.getEntry("quilt.mod.json");
		if (entry == null)
			return;
		loaders.add(Loader.QUILT);
		JsonObject root = JsonParser.parseString(read(zip, entry)).getAsJsonObject();
		JsonObject loader = root.has("quilt_loader") && root.get("quilt_loader").isJsonObject() ?
				root.getAsJsonObject("quilt_loader") : root;
		JsonObject metadataObject = loader.has("metadata") && loader.get("metadata").isJsonObject() ?
				loader.getAsJsonObject("metadata") : new JsonObject();
		metadata.merge(value(loader, "id"), value(metadataObject, "name"), value(loader, "version"),
				value(metadataObject, "description"), jsonText(metadataObject.get("license")), "*");
		readQuiltDependencies(loader.get("depends"), true, metadata.dependencies);
	}

	private static void parseTomlMetadata(ZipFile zip, String path, Loader loader, MutableMetadata metadata,
			Set<Loader> loaders) throws IOException {
		ZipEntry entry = zip.getEntry(path);
		if (entry == null)
			return;
		loaders.add(loader);
		TomlMetadata toml = parseToml(read(zip, entry));
		metadata.merge(toml.mod.getOrDefault("modId", ""), toml.mod.getOrDefault("displayName", ""),
				toml.mod.getOrDefault("version", ""), toml.mod.getOrDefault("description", ""),
				toml.global.getOrDefault("license", ""), toml.mod.getOrDefault("displayTest", "*"));
		for (Map<String, String> dependency : toml.dependencies) {
			String id = dependency.getOrDefault("modId", "").trim();
			if (id.isBlank())
				continue;
			boolean required = Boolean.parseBoolean(dependency.getOrDefault("mandatory", "false"))
					|| "required".equalsIgnoreCase(dependency.getOrDefault("type", ""));
			metadata.addDependency(new Dependency(id, dependency.getOrDefault("versionRange", "*"), required,
					dependency.getOrDefault("side", "BOTH")));
		}
	}

	private static TomlMetadata parseToml(String text) {
		TomlMetadata result = new TomlMetadata();
		Map<String, String> current = result.global;
		String[] lines = text.replace("\r", "").split("\n", -1);
		for (int index = 0; index < lines.length; index++) {
			String line = stripTomlComment(lines[index]).trim();
			if (line.isBlank())
				continue;
			if (line.equals("[[mods]]")) {
				current = result.mod;
				continue;
			}
			if (line.startsWith("[[dependencies.") && line.endsWith("]]")) {
				current = new LinkedHashMap<>();
				result.dependencies.add(current);
				continue;
			}
			Matcher matcher = TOML_KEY_VALUE.matcher(line);
			if (!matcher.matches())
				continue;
			String key = matcher.group(1);
			String rawValue = matcher.group(2).trim();
			if (rawValue.startsWith("\"\"\"") || rawValue.startsWith("'''")) {
				String delimiter = rawValue.substring(0, 3);
				StringBuilder value = new StringBuilder(rawValue.substring(3));
				while (value.indexOf(delimiter) < 0 && ++index < lines.length)
					value.append('\n').append(lines[index]);
				int end = value.indexOf(delimiter);
				current.put(key, end < 0 ? value.toString().trim() : value.substring(0, end).trim());
			} else {
				current.put(key, unquote(rawValue));
			}
		}
		return result;
	}

	private static void readManifest(ZipFile zip, MutableMetadata metadata) {
		ZipEntry entry = zip.getEntry("META-INF/MANIFEST.MF");
		if (entry == null)
			return;
		try (InputStream stream = zip.getInputStream(entry)) {
			Attributes attributes = new Manifest(stream).getMainAttributes();
			metadata.merge("", attributes.getValue("Implementation-Title"),
					attributes.getValue("Implementation-Version"), "", "", "");
		} catch (Exception ignored) {
		}
	}

	private static void readJsonDependencies(JsonObject root, String key, boolean required,
			List<Dependency> dependencies) {
		if (!root.has(key) || !root.get(key).isJsonObject())
			return;
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet())
			addDependency(dependencies, new Dependency(entry.getKey(), jsonText(entry.getValue()), required, "BOTH"));
	}

	private static void readQuiltDependencies(JsonElement element, boolean required, List<Dependency> dependencies) {
		if (element == null || element.isJsonNull())
			return;
		if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray())
				readQuiltDependencies(child, required, dependencies);
		} else if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			String id = value(object, "id");
			if (!id.isBlank())
				addDependency(dependencies,
						new Dependency(id, jsonText(object.get("versions")), required, "BOTH"));
		}
	}

	private static void addDependency(List<Dependency> dependencies, Dependency dependency) {
		if (dependency.id().isBlank())
			return;
		for (int index = 0; index < dependencies.size(); index++) {
			Dependency existing = dependencies.get(index);
			if (existing.id().equals(dependency.id())) {
				if (dependency.required() && !existing.required())
					dependencies.set(index, dependency);
				return;
			}
		}
		dependencies.add(dependency);
	}

	private static VersionMatch matchesVersion(String constraint, String version) {
		if (constraint == null || constraint.isBlank() || constraint.equals("*") || constraint.equals("[*]"))
			return VersionMatch.YES;
		String normalized = constraint.replace(" ", "");
		if (normalized.contains(version))
			return VersionMatch.YES;
		Matcher matcher = VERSION.matcher(normalized);
		List<String> versions = new ArrayList<>();
		while (matcher.find())
			versions.add(matcher.group(1));
		if (versions.isEmpty())
			return VersionMatch.UNKNOWN;
		if (normalized.matches("[0-9]+(?:\\.[0-9]+){1,3}"))
			return compareVersions(version, versions.getFirst()) == 0 ? VersionMatch.YES : VersionMatch.NO;
		if (normalized.startsWith(">=") && versions.size() == 1)
			return compareVersions(version, versions.getFirst()) >= 0 ? VersionMatch.YES : VersionMatch.NO;
		if (normalized.startsWith(">") && versions.size() == 1)
			return compareVersions(version, versions.getFirst()) > 0 ? VersionMatch.YES : VersionMatch.NO;
		if (normalized.startsWith("<=") && versions.size() == 1)
			return compareVersions(version, versions.getFirst()) <= 0 ? VersionMatch.YES : VersionMatch.NO;
		if (normalized.startsWith("<") && versions.size() == 1)
			return compareVersions(version, versions.getFirst()) < 0 ? VersionMatch.YES : VersionMatch.NO;
		if ((normalized.startsWith("[") || normalized.startsWith("(")) && versions.size() >= 2) {
			int lower = compareVersions(version, versions.get(0));
			int upper = compareVersions(version, versions.get(1));
			boolean lowerOk = normalized.startsWith("[") ? lower >= 0 : lower > 0;
			boolean upperOk = normalized.endsWith("]") ? upper <= 0 : upper < 0;
			return lowerOk && upperOk ? VersionMatch.YES : VersionMatch.NO;
		}
		return VersionMatch.UNKNOWN;
	}

	private static int compareVersions(String left, String right) {
		String[] leftParts = left.split("\\.");
		String[] rightParts = right.split("\\.");
		for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
			int leftValue = index < leftParts.length ? parseInt(leftParts[index]) : 0;
			int rightValue = index < rightParts.length ? parseInt(rightParts[index]) : 0;
			if (leftValue != rightValue)
				return Integer.compare(leftValue, rightValue);
		}
		return 0;
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static Loader loaderFor(GeneratorFlavor flavor) {
		return switch (flavor) {
			case FABRIC -> Loader.FABRIC;
			case FORGE -> Loader.FORGE;
			case NEOFORGE -> Loader.NEOFORGE;
			default -> Loader.UNKNOWN;
		};
	}

	private static String read(ZipFile zip, ZipEntry entry) throws IOException {
		try (InputStream stream = zip.getInputStream(entry)) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String value(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}

	private static String jsonText(JsonElement element) {
		if (element == null || element.isJsonNull())
			return "";
		if (element.isJsonPrimitive())
			return element.getAsString();
		if (element.isJsonArray()) {
			List<String> values = new ArrayList<>();
			for (JsonElement value : element.getAsJsonArray())
				values.add(jsonText(value));
			return String.join(" | ", values);
		}
		return element.toString();
	}

	private static String stripTomlComment(String line) {
		boolean quoted = false;
		char quote = 0;
		for (int index = 0; index < line.length(); index++) {
			char character = line.charAt(index);
			if ((character == '\"' || character == '\'') && (index == 0 || line.charAt(index - 1) != '\\')) {
				if (!quoted) {
					quoted = true;
					quote = character;
				} else if (quote == character) {
					quoted = false;
				}
			} else if (character == '#' && !quoted) {
				return line.substring(0, index);
			}
		}
		return line;
	}

	private static String unquote(String value) {
		String trimmed = value.trim();
		if (trimmed.length() >= 2 && (trimmed.startsWith("\"") && trimmed.endsWith("\"")
				|| trimmed.startsWith("'") && trimmed.endsWith("'")))
			return trimmed.substring(1, trimmed.length() - 1).replace("\\n", "\n").replace("\\\"", "\"");
		return trimmed;
	}

	private static String sha256(File file) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream stream = Files.newInputStream(file.toPath())) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = stream.read(buffer)) >= 0)
					digest.update(buffer, 0, read);
			}
			return java.util.HexFormat.of().formatHex(digest.digest());
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IOException(e);
		}
	}

	private static String valueOrUnknown(String value) {
		return value == null || value.isBlank() ? "(unknown)" : value;
	}

	public enum Loader { FABRIC, FORGE, NEOFORGE, QUILT, UNKNOWN }

	public enum CompatibilityStatus { READY, REVIEW, BLOCKED }

	private enum VersionMatch { YES, NO, UNKNOWN }

	public record Dependency(String id, String version, boolean required, String side) {}

	public record Compatibility(CompatibilityStatus status, List<String> messages) {}

	public record Inspection(String fileName, String path, long size, String sha256, List<Loader> loaders, String id,
			String name, String version, String description, String license, String environment,
			List<Dependency> dependencies, List<String> namespaces, List<String> licenseFiles, int classCount,
			int textureCount, int modelCount, int soundCount, int dataFileCount, int mixinConfigCount,
			int nestedJarCount, List<String> warnings) {}

	private static final class MutableMetadata {
		String id = "";
		String name = "";
		String version = "";
		String description = "";
		String license = "";
		String environment = "";
		final List<Dependency> dependencies = new ArrayList<>();

		void merge(String candidateId, String candidateName, String candidateVersion, String candidateDescription,
				String candidateLicense, String candidateEnvironment) {
			id = first(id, candidateId);
			name = first(name, candidateName);
			version = first(version, candidateVersion);
			description = first(description, candidateDescription);
			license = first(license, candidateLicense);
			environment = first(environment, candidateEnvironment);
		}

		void addDependency(Dependency dependency) {
			RemarkModJarInspector.addDependency(dependencies, dependency);
		}

		private static String first(String current, String candidate) {
			return current == null || current.isBlank() ? candidate == null ? "" : candidate : current;
		}
	}

	private static final class TomlMetadata {
		final Map<String, String> global = new LinkedHashMap<>();
		final Map<String, String> mod = new LinkedHashMap<>();
		final List<Map<String, String>> dependencies = new ArrayList<>();
	}

	private static final class Counters {
		int classes;
		int textures;
		int models;
		int sounds;
		int dataFiles;
		int mixinConfigs;
		int nestedJars;
	}
}
