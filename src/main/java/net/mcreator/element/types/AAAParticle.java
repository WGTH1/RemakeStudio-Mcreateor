/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.element.types;

import net.mcreator.element.GeneratableElement;
import net.mcreator.element.types.interfaces.LimitedOptions;
import net.mcreator.element.types.interfaces.Numeric;
import net.mcreator.generator.GeneratorUtils;
import net.mcreator.workspace.elements.ModElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@SuppressWarnings("unused") public class AAAParticle extends GeneratableElement {
	public static final int CURRENT_REMARK_SCHEMA = 2;

	private static final Set<String> SHARED_ASSET_DIRECTORIES = Set.of("texture", "textures", "material", "materials",
			"model", "models", "sound", "sounds");

	public int remarkSchemaVersion = CURRENT_REMARK_SCHEMA;

	public String sourceRoot;
	public String sourceFile;
	public String sourceName;
	public String effectPath;
	public List<String> importedAssetFiles = new ArrayList<>();
	@LimitedOptions({ "world", "source_facing", "source_follow_view" }) public String spawnMode = "world";

	@Numeric(init = 0, min = -4096, max = 4096, step = 0.1) public double offsetX;
	@Numeric(init = 0, min = -4096, max = 4096, step = 0.1) public double offsetY;
	@Numeric(init = 0, min = -4096, max = 4096, step = 0.1) public double offsetZ;

	@Numeric(init = 0, min = -360, max = 360, step = 1) public double rotationX;
	@Numeric(init = 0, min = -360, max = 360, step = 1) public double rotationY;
	@Numeric(init = 0, min = -360, max = 360, step = 1) public double rotationZ;

	@Numeric(init = 1, min = 0.001, max = 4096, step = 0.1) public double scaleX = 1;
	@Numeric(init = 1, min = 0.001, max = 4096, step = 0.1) public double scaleY = 1;
	@Numeric(init = 1, min = 0.001, max = 4096, step = 0.1) public double scaleZ = 1;
	@Numeric(init = 1, min = 0, max = 4096, step = 0.1) public double speed = 1;

	public boolean force;
	public boolean unlimitedDistance;

	private AAAParticle() {
		this(null);
	}

	public AAAParticle(ModElement element) {
		super(element);
	}

	public void normalizeRemarkSchema() {
		int previousSchema = remarkSchemaVersion;
		sourceRoot = sourceRoot == null ? "" : sourceRoot.replace('\\', '/');
		sourceFile = sourceFile == null ? "" : sourceFile.replace('\\', '/');
		sourceName = sourceName == null ? "" : sourceName;
		effectPath = effectPath == null ? "" : effectPath;
		if (!Set.of("world", "source_facing", "source_follow_view").contains(spawnMode))
			spawnMode = "world";
		if (previousSchema <= 0) {
			if (scaleX <= 0)
				scaleX = 1;
			if (scaleY <= 0)
				scaleY = 1;
			if (scaleZ <= 0)
				scaleZ = 1;
			if (speed == 0)
				speed = 1;
		}
		remarkSchemaVersion = CURRENT_REMARK_SCHEMA;
	}

	public String getSafeEffectPath() {
		String path = effectPath == null || effectPath.isBlank() ? getModElement().getRegistryName() : effectPath;
		return path.replace('\\', '/').replace("..", "").replaceAll("^/+", "").replaceAll("/+$", "");
	}

	public String getSpawnModeSafe() {
		return spawnMode == null || spawnMode.isBlank() ? "world" : spawnMode;
	}

	public double getRotationXRadians() {
		return Math.toRadians(rotationX);
	}

	public double getRotationYRadians() {
		return Math.toRadians(rotationY);
	}

	public double getRotationZRadians() {
		return Math.toRadians(rotationZ);
	}

	@Override public void finalizeModElementGeneration() {
		try {
			useModAssetFiles(false);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to install AAA particle assets for " + getModElement().getName(), e);
		}
	}

	@Override public boolean keepAssociatedFileOnRegeneration(File file) {
		File output = GeneratorUtils.getSpecificRoot(getModElement().getWorkspace(),
				getModElement().getGeneratorConfiguration(), "aaa_particles_effeks_dir");
		Path path = file.toPath().toAbsolutePath().normalize();
		if (output != null && path.startsWith(output.toPath().toAbsolutePath().normalize()))
			return true;
		Path workspace = getModElement().getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		return sourceFile != null && path.equals(workspace.resolve(sourceFile).normalize())
				|| importedAssetFiles != null && importedAssetFiles.stream()
				.map(workspace::resolve).map(Path::normalize).anyMatch(path::equals);
	}

	/** Imports once; subsequent generation treats packaged resources as authoritative. */
	public void useModAssetFiles(boolean replaceExisting) throws IOException {
		if (sourceFile == null || sourceFile.isBlank())
			return;
		Path workspace = getModElement().getWorkspaceFolder().toPath().toAbsolutePath().normalize();
		Path selected = workspace.resolve(sourceFile).normalize();
		Path root = sourceRoot == null || sourceRoot.isBlank() ? selected.getParent() : workspace.resolve(sourceRoot).normalize();
		File effeksRoot = GeneratorUtils.getSpecificRoot(getModElement().getWorkspace(),
				getModElement().getGeneratorConfiguration(), "aaa_particles_effeks_dir");
		if (effeksRoot == null)
			return;
		Path output = effeksRoot.toPath().toAbsolutePath().normalize();
		if (!output.startsWith(workspace))
			throw new IOException("AAA asset directory must be inside the workspace");
		Path targetEffect = output.resolve(getSafeEffectPath() + ".efkefc").normalize();
		List<File> associatedFiles = new ArrayList<>(getModElement().getAssociatedFiles());
		copyModAsset(selected, targetEffect, output, replaceExisting);
		if (!associatedFiles.contains(targetEffect.toFile()))
			associatedFiles.add(targetEffect.toFile());
		List<String> installed = new ArrayList<>();
		boolean managed = selected.startsWith(output) || importedAssetFiles != null && !importedAssetFiles.isEmpty();
		List<Path> assets;
		if (managed) {
			assets = importedAssetFiles == null ? List.of() : importedAssetFiles.stream().map(workspace::resolve)
					.map(Path::normalize).filter(path -> !path.equals(selected)).toList();
		} else if (Files.isDirectory(root)) {
			try (Stream<Path> files = Files.walk(root)) {
				assets = files.filter(Files::isRegularFile).filter(path -> !path.equals(selected))
						.filter(path -> !path.toString().toLowerCase(Locale.ROOT).endsWith(".efkefc")).toList();
			}
		} else {
			assets = List.of();
		}
		if (assets.isEmpty()) {
			assets = associatedFiles.stream().map(File::toPath).map(Path::toAbsolutePath).map(Path::normalize)
					.filter(path -> path.startsWith(output))
					.filter(path -> !path.toString().toLowerCase(Locale.ROOT).endsWith(".efkefc")).toList();
			if (!assets.isEmpty()) {
				root = output;
				managed = true;
			}
		}
		for (Path asset : assets) {
			if (!asset.startsWith(root))
				throw new IOException("AAA dependency is outside its imported folder: " + asset);
			String relative = root.relativize(asset).toString().replace(File.separator, "/");
			Path target = managed ? output.resolve(relative) : isSharedAsset(relative) ?
					targetEffect.getParent().resolve(relative) : output.resolve(getSafeEffectPath()).resolve(relative);
			if (managed && asset.startsWith(selected.getParent())) {
				Path oldFallback = Path.of(selected.toString().replaceFirst("\\.efkefc$", ""));
				target = asset.startsWith(oldFallback) ? output.resolve(getSafeEffectPath()).resolve(oldFallback.relativize(asset)) :
						targetEffect.getParent().resolve(selected.getParent().relativize(asset));
			}
			if (asset.equals(Path.of(selected + ".mcmeta")))
				target = Path.of(targetEffect + ".mcmeta");
			target = target.normalize();
			copyModAsset(asset, target, output, replaceExisting);
			installed.add(workspace.relativize(target).toString().replace(File.separator, "/"));
			if (!associatedFiles.contains(target.toFile()))
				associatedFiles.add(target.toFile());
		}
		sourceFile = workspace.relativize(targetEffect).toString().replace(File.separator, "/");
		sourceRoot = workspace.relativize(output).toString().replace(File.separator, "/");
		importedAssetFiles = installed;
		getModElement().setAssociatedFiles(associatedFiles);
	}

	private boolean isSharedAsset(String relativePath) {
		int separator = relativePath.indexOf('/');
		String firstSegment = separator == -1 ? relativePath : relativePath.substring(0, separator);
		return SHARED_ASSET_DIRECTORIES.contains(firstSegment.toLowerCase(Locale.ROOT));
	}

	private void copyModAsset(Path source, Path target, Path output, boolean replaceExisting) throws IOException {
		if (!target.startsWith(output))
			throw new IOException("AAA asset path escapes the mod: " + target);
		if (Files.isRegularFile(target) && (!replaceExisting || Files.isSameFile(source, target)))
			return;
		if (!Files.isRegularFile(source))
			throw new IOException("AAA asset not found: " + source);
		Files.createDirectories(target.getParent());
		if (Files.isRegularFile(target)) {
			Path backup = getModElement().getWorkspaceFolder().toPath().resolve(".remarkstudio/backups/aaa-import")
					.resolve(java.util.UUID.randomUUID().toString()).resolve(output.relativize(target));
			Files.createDirectories(backup.getParent());
			Files.copy(target, backup);
		}
		Path pending = Files.createTempFile(target.getParent(), ".aaa-import-", ".tmp");
		try {
			Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException e) {
				Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(pending);
		}
	}
}
