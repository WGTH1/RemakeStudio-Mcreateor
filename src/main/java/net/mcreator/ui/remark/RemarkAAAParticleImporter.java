package net.mcreator.ui.remark;

import net.mcreator.element.ModElementType;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.io.EffekseerAssetReferences;
import net.mcreator.java.JavaConventions;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.generator.GeneratorUtils;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class RemarkAAAParticleImporter {

	private RemarkAAAParticleImporter() {}

	public static AAAParticle create(Workspace workspace, Path source, String mode) throws Exception {
		if (!isEffect(source) || !Files.isRegularFile(source))
			throw new IOException("Select an .efkefc file: " + source);
		if (!Set.of("world", "source_facing", "source_follow_view").contains(mode))
			throw new IOException("Unknown AAA movement mode: " + mode);
		String filename = source.getFileName().toString();
		String base = JavaConventions.convertToValidClassName(filename.substring(0, filename.lastIndexOf('.')));
		if (base == null || base.isBlank())
			base = "VfxEffect";
		String registryBase = RegistryNameFixer.fix(filename.substring(0, filename.lastIndexOf('.')));
		if (registryBase.isBlank())
			registryBase = "vfx_effect";
		String name = base;
		String registry = registryBase;
		int suffix = 2;
		while (workspace.containsModElement(name) || registryTaken(workspace, registry)) {
			name = base + suffix;
			registry = registryBase + suffix++;
		}
		ModElement element = new ModElement(workspace, name, ModElementType.AAAPARTICLE);
		element.setRegistryName(registry);
		Path stage = workspace.getWorkspaceFolder().toPath().resolve(".remark-assets/aaa/quick")
				.resolve(element.getRegistryName());
		Files.createDirectories(stage);
		stage = Files.createTempDirectory(stage, "import-");
		stage(source, stage);
		AAAParticle particle = new AAAParticle(element);
		particle.sourceRoot = relative(workspace, stage);
		particle.sourceFile = relative(workspace, stage.resolve(filename));
		particle.sourceName = filename;
		// Namespace dependencies too, so two effects with Texture/1.png cannot overwrite each other.
		particle.effectPath = element.getRegistryName() + "/" + element.getRegistryName();
		particle.spawnMode = mode;
		particle.force = true;
		particle.unlimitedDistance = true;
		particle.useModAssetFiles(true);
		workspace.addModElement(element);
		workspace.getModElementManager().storeModElement(particle);
		Set<String> dependencies = new HashSet<>(workspace.getWorkspaceSettings().getMCreatorDependenciesRaw());
		dependencies.add("aaa_particles");
		workspace.getWorkspaceSettings().setMCreatorDependencies(dependencies);
		workspace.markDirty();
		return particle;
	}

	private static boolean registryTaken(Workspace workspace, String registry) {
		File output = GeneratorUtils.getSpecificRoot(workspace, workspace.getGeneratorConfiguration(), "aaa_particles_effeks_dir");
		return workspace.getModElements().stream().anyMatch(element -> element.getRegistryName().equals(registry))
				|| output != null && Files.exists(output.toPath().resolve(registry));
	}

	/** Copies only declared dependencies; never traverses whole neighbouring texture libraries. */
	public static void stage(Path source, Path target) throws IOException {
		Set<String> references = EffekseerAssetReferences.read(source);
		List<Path> sources = new ArrayList<>();
		for (String reference : references) {
			Path found = findDependency(source, reference);
			if (found == null)
				throw new IOException("Missing effect asset: " + reference + " (" + source.getFileName() + ")");
			sources.add(found);
		}
		Files.createDirectories(target);
		Files.copy(source, target.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
		int index = 0;
		for (String reference : references) {
			Path output = target.resolve(reference).normalize();
			if (!output.startsWith(target.normalize()))
				throw new IOException("Effect asset escapes its import folder");
			Files.createDirectories(output.getParent());
			Files.copy(sources.get(index++), output, StandardCopyOption.REPLACE_EXISTING);
		}
		Path metadata = Path.of(source + ".mcmeta");
		if (Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS))
			Files.copy(metadata, target.resolve(metadata.getFileName()), StandardCopyOption.REPLACE_EXISTING);
	}

	private static Path findDependency(Path effect, String reference) {
		Path directory = effect.toAbsolutePath().normalize().getParent();
		String basename = effect.getFileName().toString().replaceFirst("(?i)\\.efkefc$", "");
		for (int depth = 0; directory != null && depth < 4; depth++, directory = directory.getParent()) {
			for (Path candidate : List.of(directory.resolve(reference), directory.resolve(basename).resolve(reference)))
				if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
					return candidate;
		}
		return null;
	}

	public static List<Path> findEffects(List<Path> selected) throws IOException {
		Set<Path> effects = new java.util.LinkedHashSet<>();
		for (Path path : selected) {
			if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
				try (var files = Files.walk(path, 32)) {
					files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
							.filter(RemarkAAAParticleImporter::isEffect).map(file -> file.toAbsolutePath().normalize())
							.sorted().limit(129).forEach(effects::add);
				}
			} else if (isEffect(path)) {
				effects.add(path.toAbsolutePath().normalize());
			}
			if (effects.size() > 128)
				throw new IOException("Import up to 128 effects at a time");
		}
		if (effects.isEmpty())
			throw new IOException("No .efkefc files found");
		return List.copyOf(effects);
	}

	public static boolean isEffect(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".efkefc");
	}

	private static String relative(Workspace workspace, Path file) {
		return workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize()
				.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
	}
}
