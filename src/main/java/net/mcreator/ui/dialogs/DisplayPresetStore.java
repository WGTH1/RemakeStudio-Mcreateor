/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.dialogs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.mcreator.element.parts.ItemDisplaySettings.Transform;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Local named transforms in one UTF-8 file: {@code {"version":1,"presets":{"name":{...}}}}.
 * Names are case-sensitive, retained verbatim, and listed in insertion order; replacing one keeps its position.
 * Every operation rereads the file. Invalid or unsupported files are never treated as an empty store.
 * Calls on one instance are serialized; callers must serialize concurrent writers using other instances/processes.
 */
public final class DisplayPresetStore {

	public static final int MAX_NAME_CODE_POINTS = 80;
	public static final int MAX_PRESETS = 128;
	public static final int MAX_FILE_BYTES = 1024 * 1024;
	public static final double MAX_ROTATION = 360;
	public static final double MAX_TRANSLATION = 80;
	public static final double MAX_SCALE = 4;

	private static final int VERSION = 1;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final List<String> FIELDS = List.of("rotationX", "rotationY", "rotationZ", "translationX",
			"translationY", "translationZ", "scaleX", "scaleY", "scaleZ");

	private final Path presetFile;

	/** Validates an existing file without creating the file or its parent directories. */
	public DisplayPresetStore(Path presetFile) throws IOException {
		this.presetFile = Objects.requireNonNull(presetFile, "presetFile").toAbsolutePath().normalize();
		readPresets();
	}

	/** Returns an immutable snapshot of the names in insertion order. */
	public synchronized List<String> list() throws IOException {
		return List.copyOf(readPresets().keySet());
	}

	/**
	 * Saves a copy, replacing an existing name without changing its position.
	 * @throws IllegalArgumentException if the name/transform is invalid or the record limit would be exceeded
	 * @throws IOException if reading or atomic replacement fails, including corrupt/unsupported existing data
	 */
	public synchronized void save(String name, Transform transform) throws IOException {
		validateName(name);
		if (transform == null)
			throw new IllegalArgumentException("Transform is required");
		Transform copy = transform.copy();
		validateTransform(copy);
		LinkedHashMap<String, Transform> presets = readPresets();
		if (!presets.containsKey(name) && presets.size() >= MAX_PRESETS)
			throw new IllegalArgumentException("At most " + MAX_PRESETS + " presets are allowed");
		presets.put(name, copy);
		writePresets(presets);
	}

	/** Returns an independent transform, or {@code null} if the valid name is absent. */
	public synchronized Transform load(String name) throws IOException {
		validateName(name);
		Transform transform = readPresets().get(name);
		return transform == null ? null : transform.copy();
	}

	/** Deletes a valid name; an absent name is a no-op and does not rewrite or create the file. */
	public synchronized void delete(String name) throws IOException {
		validateName(name);
		LinkedHashMap<String, Transform> presets = readPresets();
		if (presets.remove(name) != null)
			writePresets(presets);
	}

	private LinkedHashMap<String, Transform> readPresets() throws IOException {
		BasicFileAttributes attributes;
		try {
			attributes = Files.readAttributes(presetFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
		} catch (NoSuchFileException e) {
			return new LinkedHashMap<>();
		}
		if (!attributes.isRegularFile())
			throw new IOException("Preset path must be a regular file: " + presetFile);
		if (attributes.size() > MAX_FILE_BYTES)
			throw new IOException("Preset file exceeds " + MAX_FILE_BYTES + " bytes");
		byte[] bytes;
		try (var input = Files.newInputStream(presetFile, LinkOption.NOFOLLOW_LINKS)) {
			// Bound the actual read too: the file may have grown since readAttributes.
			bytes = input.readNBytes(MAX_FILE_BYTES + 1);
		}
		if (bytes.length > MAX_FILE_BYTES)
			throw new IOException("Preset file exceeds " + MAX_FILE_BYTES + " bytes");
		String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		try (JsonReader reader = new JsonReader(new StringReader(json))) {
			reader.setStrictness(Strictness.STRICT);
			reader.beginObject();
			Set<String> fields = new HashSet<>();
			LinkedHashMap<String, Transform> presets = null;
			while (reader.hasNext()) {
				String field = reader.nextName();
				if (!fields.add(field))
					throw new IOException("Duplicate preset document field: " + field);
				switch (field) {
				case "version" -> {
					if (reader.peek() != JsonToken.NUMBER || !reader.nextString().equals(Integer.toString(VERSION)))
						throw new IOException("Unsupported preset file version");
				}
				case "presets" -> presets = readEntries(reader);
				default -> throw new IOException("Unknown preset document field: " + field);
				}
			}
			reader.endObject();
			if (!fields.contains("version") || presets == null || reader.peek() != JsonToken.END_DOCUMENT)
				throw new IOException("Incomplete preset document or trailing data");
			return presets;
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw new IOException("Invalid preset file: " + presetFile, e);
		}
	}

	private static LinkedHashMap<String, Transform> readEntries(JsonReader reader) throws IOException {
		LinkedHashMap<String, Transform> presets = new LinkedHashMap<>();
		reader.beginObject();
		while (reader.hasNext()) {
			if (presets.size() >= MAX_PRESETS)
				throw new IOException("Preset file contains more than " + MAX_PRESETS + " presets");
			String name = reader.nextName();
			validateName(name);
			if (presets.containsKey(name))
				throw new IOException("Duplicate preset name: " + name);
			presets.put(name, readTransform(reader));
		}
		reader.endObject();
		return presets;
	}

	private static Transform readTransform(JsonReader reader) throws IOException {
		double[] values = new double[FIELDS.size()];
		Set<String> seen = new HashSet<>();
		reader.beginObject();
		while (reader.hasNext()) {
			String field = reader.nextName();
			int index = FIELDS.indexOf(field);
			if (index < 0 || !seen.add(field) || reader.peek() != JsonToken.NUMBER)
				throw new IOException("Invalid, duplicate or non-numeric transform field: " + field);
			values[index] = reader.nextDouble();
		}
		reader.endObject();
		if (seen.size() != FIELDS.size())
			throw new IOException("A preset must contain all nine transform components");
		Transform transform = new Transform(values[0], values[1], values[2], values[3], values[4], values[5],
				values[6], values[7], values[8]);
		validateTransform(transform);
		return transform;
	}

	private void writePresets(Map<String, Transform> presets) throws IOException {
		JsonObject entries = new JsonObject();
		for (var preset : presets.entrySet()) {
			JsonObject transform = new JsonObject();
			double[] values = components(preset.getValue());
			for (int i = 0; i < FIELDS.size(); i++)
				transform.addProperty(FIELDS.get(i), values[i]);
			entries.add(preset.getKey(), transform);
		}
		JsonObject document = new JsonObject();
		document.addProperty("version", VERSION);
		document.add("presets", entries);
		byte[] bytes = GSON.toJson(document).getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_FILE_BYTES)
			throw new IOException("Preset file would exceed " + MAX_FILE_BYTES + " bytes");

		Path parent = presetFile.getParent();
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, ".display-presets-", ".tmp");
		try {
			try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining())
					channel.write(buffer);
				channel.force(true);
			}
			// Never fall back to a non-atomic replacement that could destroy the previous file on failure.
			Files.move(temporary, presetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException | RuntimeException e) {
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException cleanup) {
				e.addSuppressed(cleanup);
			}
			throw e;
		}
	}

	private static void validateName(String name) {
		if (name == null || name.isBlank() || name.codePointCount(0, name.length()) > MAX_NAME_CODE_POINTS)
			throw new IllegalArgumentException("Preset name must contain 1 to " + MAX_NAME_CODE_POINTS + " code points");
		for (int offset = 0; offset < name.length();) {
			int point = name.codePointAt(offset);
			if (Character.isISOControl(point) || point >= Character.MIN_SURROGATE && point <= Character.MAX_SURROGATE)
				throw new IllegalArgumentException("Preset name must not contain control characters or unpaired surrogates");
			offset += Character.charCount(point);
		}
	}

	private static void validateTransform(Transform transform) {
		double[] values = components(transform);
		for (int i = 0; i < values.length; i++) {
			double limit = i < 3 ? MAX_ROTATION : i < 6 ? MAX_TRANSLATION : MAX_SCALE;
			if (!Double.isFinite(values[i]) || values[i] < -limit || values[i] > limit)
				throw new IllegalArgumentException(FIELDS.get(i) + " must be finite and between " + -limit + " and " + limit);
		}
	}

	private static double[] components(Transform transform) {
		return new double[] { transform.rotationX, transform.rotationY, transform.rotationZ, transform.translationX,
				transform.translationY, transform.translationZ, transform.scaleX, transform.scaleY, transform.scaleZ };
	}
}
