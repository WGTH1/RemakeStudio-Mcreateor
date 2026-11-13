/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.ui.remark;

import net.mcreator.io.FileIO;
import net.mcreator.util.FilenameUtilsPatched;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.stream.Stream;

public final class RemarkAssetFiles {

	private RemarkAssetFiles() {
	}

	public static CopyResult copyCompact(File source, File folder, String preferredName) {
		if (!folder.isDirectory())
			folder.mkdirs();

		File duplicate = findDuplicateByContent(source, folder);
		if (duplicate != null)
			return new CopyResult(duplicate, false, true);

		File target = uniqueTargetFile(folder, preferredName);
		FileIO.copyFile(source, target);
		return new CopyResult(target, true, false);
	}

	public static CopyResult writeTextCompact(String text, File folder, String preferredName) {
		if (!folder.isDirectory())
			folder.mkdirs();

		String extension = extensionOf(preferredName);
		try (Stream<java.nio.file.Path> paths = Files.walk(folder.toPath(), 2)) {
			File duplicate = paths.filter(Files::isRegularFile).map(java.nio.file.Path::toFile)
					.filter(file -> extension.equals(extensionOf(file.getName()))).filter(file -> {
						try {
							return Files.readString(file.toPath()).equals(text);
						} catch (IOException ignored) {
							return false;
						}
					}).findFirst().orElse(null);
			if (duplicate != null)
				return new CopyResult(duplicate, false, true);
		} catch (IOException ignored) {
		}

		File target = uniqueTargetFile(folder, preferredName);
		FileIO.writeStringToFile(text, target);
		return new CopyResult(target, true, false);
	}

	public static File uniqueTargetFile(File folder, String fileName) {
		String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
		String baseName = FilenameUtilsPatched.removeExtension(fileName).replaceAll("[^a-zA-Z0-9_.-]", "_");
		if (baseName.isBlank())
			baseName = "asset";

		File target = new File(folder, baseName + extension);
		int index = 2;
		while (target.exists())
			target = new File(folder, baseName + "_" + index++ + extension);
		return target;
	}

	public static File findDuplicateByContent(File source, File folder) {
		String extension = extensionOf(source.getName());
		try (Stream<java.nio.file.Path> paths = Files.walk(folder.toPath(), 2)) {
			return paths.filter(Files::isRegularFile).map(java.nio.file.Path::toFile)
					.filter(file -> extension.equals(extensionOf(file.getName())))
					.filter(file -> sameContent(source, file)).findFirst().orElse(null);
		} catch (IOException ignored) {
			return null;
		}
	}

	private static boolean sameContent(File left, File right) {
		try {
			return Files.isRegularFile(left.toPath()) && Files.isRegularFile(right.toPath())
					&& Files.size(left.toPath()) == Files.size(right.toPath())
					&& Files.mismatch(left.toPath(), right.toPath()) == -1;
		} catch (IOException e) {
			return false;
		}
	}

	private static String extensionOf(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot == -1 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	public record CopyResult(File file, boolean copied, boolean reused) {}
}
