package net.mcreator.integration.generator;

import net.mcreator.element.ModElementType;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorUtils;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.WorkspaceUtils;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class AAAParticleAssetStorageTest {
    @TempDir Path temporary;

    @Test void importedFilesRemainUsableWithoutOriginalAndKeepInModEdits() throws Exception {
        try (Workspace workspace = workspace()) {
            Path original = temporary.resolve("external");
            Files.createDirectories(original.resolve("Texture"));
            Files.createDirectories(original.resolve("Model"));
            Files.writeString(original.resolve("effect.efkefc"), "effect-original");
            Files.writeString(original.resolve("Texture/leaf.png"), "texture-original");
            Files.writeString(original.resolve("Model/tree.efkmodel"), "model-original");
            AAAParticle particle = particle(workspace, original);
            particle.effectPath = "forest/wisteria";
            particle.useModAssetFiles(true);
            Path packaged = workspace.getWorkspaceFolder().toPath().resolve(particle.sourceFile);
            assertTrue(particle.sourceFile.startsWith("src/main/resources/"));
            assertEquals(2, particle.importedAssetFiles.size());
            Path texture = packaged.getParent().resolve("Texture/leaf.png");
            assertEquals("texture-original", Files.readString(texture));
            Files.delete(original.resolve("effect.efkefc"));
            Files.delete(original.resolve("Texture/leaf.png"));
            Files.delete(original.resolve("Model/tree.efkmodel"));
            Files.writeString(packaged, "edited-in-mod");
            Files.writeString(texture, "edited-texture-in-mod");
            workspace.getGenerator().generateElement(particle, false, true);
            assertEquals("edited-in-mod", Files.readString(packaged));
            assertEquals("edited-texture-in-mod", Files.readString(texture));
            workspace.getModElementManager().storeModElement(particle);
            String saved = Files.readString(workspace.getWorkspaceFolder().toPath().resolve("elements/PortableAAA.mod.json"));
            assertFalse(saved.contains("external"));
            assertTrue(saved.contains("src/main/resources/"));
        }
    }

    @Test void legacyMigrationPrefersExistingModFilesOverTheImportCopy() throws Exception {
        try (Workspace workspace = workspace()) {
            Path original = temporary.resolve("old-import");
            Files.createDirectories(original.resolve("Texture"));
            Files.writeString(original.resolve("effect.efkefc"), "stale-import");
            Files.writeString(original.resolve("Texture/leaf.png"), "stale-texture");
            AAAParticle particle = particle(workspace, original);
            Path output = output(workspace);
            Files.createDirectories(output.resolve("Texture"));
            Files.writeString(output.resolve("portable_aaa.efkefc"), "new-mod-version");
            Files.writeString(output.resolve("Texture/leaf.png"), "new-mod-texture");
            particle.useModAssetFiles(false);
            assertEquals("new-mod-version", Files.readString(output.resolve("portable_aaa.efkefc")));
            assertEquals("new-mod-texture", Files.readString(output.resolve("Texture/leaf.png")));
            assertEquals(workspace.getWorkspaceFolder().toPath().relativize(output).toString().replace('\\', '/'),
                    particle.sourceRoot);
        }
    }

    @Test void explicitReimportReplacesWithBackupAndRenameRetainsDependencies() throws Exception {
        try (Workspace workspace = workspace()) {
            Path original = temporary.resolve("replace");
            Files.createDirectories(original.resolve("Texture"));
            Files.writeString(original.resolve("effect.efkefc"), "version-one");
            Files.writeString(original.resolve("Texture/leaf.png"), "leaf");
            AAAParticle first = particle(workspace, original);
            first.useModAssetFiles(true);
            Files.writeString(original.resolve("effect.efkefc"), "version-two");
            AAAParticle second = particle(workspace, original);
            second.useModAssetFiles(true);
            assertEquals("version-two", Files.readString(output(workspace).resolve("portable_aaa.efkefc")));
            try (var backups = Files.walk(workspace.getWorkspaceFolder().toPath().resolve(".remarkstudio/backups/aaa-import"))) {
                assertTrue(backups.filter(Files::isRegularFile).anyMatch(path -> {
                    try { return Files.readString(path).equals("version-one"); }
                    catch (Exception e) { return false; }
                }));
            }
            second.effectPath = "renamed/effect";
            second.useModAssetFiles(false);
            assertTrue(Files.isRegularFile(output(workspace).resolve("renamed/Texture/leaf.png")));
            assertTrue(second.sourceFile.endsWith("renamed/effect.efkefc"));
        }
    }

    @Test void regenerationDoesNotClaimOtherEffectsOrOverwriteSharedAssets() throws Exception {
        try (Workspace workspace = workspace()) {
            Path original = temporary.resolve("isolated");
            Files.createDirectories(original);
            Files.writeString(original.resolve("effect.efkefc"), "one");
            AAAParticle particle = particle(workspace, original);
            particle.useModAssetFiles(true);
            Path unrelated = output(workspace).resolve("other.efkefc");
            Files.writeString(unrelated, "other");
            particle.finalizeModElementGeneration();
            assertFalse(particle.getModElement().getAssociatedFiles().contains(unrelated.toFile()));
            assertEquals("other", Files.readString(unrelated));
            assertEquals(List.of(), particle.importedAssetFiles);
        }
    }

    private Workspace workspace() throws Exception {
        return TestWorkspaceDataProvider.createTestWorkspace(temporary.resolve("workspace").toFile(),
                Generator.GENERATOR_CACHE.get("neoforge-1.21.1"), false, false, null);
    }

    @ParameterizedTest
    @ValueSource(longs = {202500100000L, 202600299999L})
    void fullWorkspaceRegenerationKeepsInModAssetsAndTheirEdits(long version) throws Exception {
        try (Workspace workspace = workspace()) {
            workspace.setMCreatorVersion(version);
            Path original = temporary.resolve("full-regeneration");
            Files.createDirectories(original.resolve("Texture"));
            Files.writeString(original.resolve("effect.efkefc"), "original-effect");
            Files.writeString(original.resolve("Texture/leaf.png"), "original-texture");
            AAAParticle particle = particle(workspace, original);
            particle.useModAssetFiles(true);
            workspace.getGenerator().generateElement(particle, false, true);
            workspace.getModElementManager().storeModElement(particle);
            Path effect = output(workspace).resolve("portable_aaa.efkefc");
            Path texture = output(workspace).resolve("Texture/leaf.png");
            Files.writeString(effect, "in-mod-effect-edit");
            Files.writeString(texture, "in-mod-texture-edit");
            Files.delete(original.resolve("effect.efkefc"));
            Files.delete(original.resolve("Texture/leaf.png"));
            var generatedJava = particle.getModElement().getAssociatedFiles().stream()
                    .filter(file -> file.getName().endsWith(".java")).toList();
            assertFalse(generatedJava.isEmpty());

            for (int pass = 0; pass < 2; pass++) {
                WorkspaceUtils.removeAutoGeneratedCode(workspace, null);
                assertEquals("in-mod-effect-edit", Files.readString(effect));
                assertEquals("in-mod-texture-edit", Files.readString(texture));
                assertTrue(generatedJava.stream().noneMatch(java.io.File::exists));
                workspace.getGenerator().generateElement(particle, false, true);
                workspace.getModElementManager().storeModElement(particle);
                assertEquals("in-mod-effect-edit", Files.readString(effect));
                assertEquals("in-mod-texture-edit", Files.readString(texture));
                assertTrue(generatedJava.stream().allMatch(java.io.File::isFile));
            }
        }
    }

    @Test void deletingOneEffectPreservesDependenciesUsedByAnother() throws Exception {
        try (Workspace workspace = workspace()) {
            Path original = temporary.resolve("shared");
            Files.createDirectories(original.resolve("Texture"));
            Files.writeString(original.resolve("effect.efkefc"), "effect");
            Files.writeString(original.resolve("Texture/leaf.png"), "shared-leaf");
            AAAParticle particle = particle(workspace, original);
            particle.useModAssetFiles(true);
            Path shared = output(workspace).resolve("Texture/leaf.png");
            ModElement other = new ModElement(workspace, "OtherEffect", ModElementType.AAAPARTICLE);
            workspace.addModElement(other);
            other.setAssociatedFiles(List.of(shared.toFile()));
            workspace.getGenerator().removeElementFilesAndWorkspaceLinks(particle);
            assertTrue(Files.isRegularFile(shared));
            assertFalse(Files.exists(output(workspace).resolve("portable_aaa.efkefc")));
        }
    }

    private AAAParticle particle(Workspace workspace, Path original) {
        ModElement element = workspace.getModElementByName("PortableAAA");
        if (element == null) {
            element = new ModElement(workspace, "PortableAAA", ModElementType.AAAPARTICLE);
            workspace.addModElement(element);
        }
        AAAParticle particle = new AAAParticle(element);
        particle.sourceRoot = original.toString();
        particle.sourceFile = original.resolve("effect.efkefc").toString();
        particle.effectPath = "portable_aaa";
        return particle;
    }

    private Path output(Workspace workspace) {
        return GeneratorUtils.getSpecificRoot(workspace, workspace.getGeneratorConfiguration(), "aaa_particles_effeks_dir").toPath();
    }
}
