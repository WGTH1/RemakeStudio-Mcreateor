package net.mcreator.integration.generator;

import net.mcreator.generator.Generator;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.io.EffekseerAssetReferences;
import net.mcreator.ui.remark.RemarkAAAParticleImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class AAAParticleQuickImportTest {
    @TempDir Path temporary;

    @ParameterizedTest @ValueSource(ints = {0, 1500, 1610, 1700, 1800})
    void readsVersionedDependencyTablesIncludingUnicode(int version) throws Exception {
        Path file = effect(temporary.resolve("effect.efkefc"), version,
                List.of("Texture/leaf.png", "Model/tree.efkmodel", "Texture/ใบ.png"));
        assertEquals(Set.of("Texture/leaf.png", "Model/tree.efkmodel", "Texture/ใบ.png"),
                EffekseerAssetReferences.read(file));
    }

    @Test void importCreatesNamedPortableElementsWithIsolatedAssetsAndUsefulDefaults() throws Exception {
        try (var workspace = TestWorkspaceDataProvider.createTestWorkspace(temporary.resolve("workspace").toFile(),
                Generator.GENERATOR_CACHE.get("neoforge-1.21.1"), false, false, null)) {
            Path first = external("first", "first-texture");
            Path second = external("second", "second-texture");
            var a = RemarkAAAParticleImporter.create(workspace, first, "source_follow_view");
            var b = RemarkAAAParticleImporter.create(workspace, second, "world");
            assertEquals("Spark", a.getModElement().getName());
            assertEquals("Spark2", b.getModElement().getName());
            assertEquals("source_follow_view", a.spawnMode);
            assertEquals(1, a.scaleX);
            assertEquals(1, a.scaleY);
            assertEquals(1, a.scaleZ);
            assertEquals(1, a.speed);
            assertTrue(a.unlimitedDistance);
            assertTrue(workspace.getWorkspaceSettings().getMCreatorDependenciesRaw().contains("aaa_particles"));
            assertNotEquals(a.sourceFile, b.sourceFile);
            Path root = workspace.getWorkspaceFolder().toPath();
            Path aFile = root.resolve(a.sourceFile);
            Path bFile = root.resolve(b.sourceFile);
            assertTrue(a.sourceFile.startsWith("src/main/resources/"));
            assertEquals("first-texture", Files.readString(aFile.getParent().resolve("Texture/leaf.png")));
            assertEquals("second-texture", Files.readString(bFile.getParent().resolve("Texture/leaf.png")));
            assertFalse(Files.exists(aFile.getParent().resolve("Texture/unrelated.png")));
            assertEquals(1, a.importedAssetFiles.size());
            Files.delete(first);
            Files.delete(first.getParent().resolve("Texture/leaf.png"));
            a.finalizeModElementGeneration();
            assertTrue(Files.isRegularFile(aFile));
            assertFalse(Files.readString(root.resolve("elements/Spark.mod.json")).contains("external"));
        }
    }

    @Test void missingDependenciesDoNotCreateAnElementOrEnableAaa() throws Exception {
        try (var workspace = TestWorkspaceDataProvider.createTestWorkspace(temporary.resolve("workspace").toFile(),
                Generator.GENERATOR_CACHE.get("neoforge-1.21.1"), false, false, null)) {
            Path file = effect(temporary.resolve("broken.efkefc"), 1700, List.of("Texture/missing.png"));
            IOException failure = assertThrows(IOException.class,
                    () -> RemarkAAAParticleImporter.create(workspace, file, "world"));
            assertTrue(failure.getMessage().contains("Texture/missing.png"));
            assertTrue(workspace.getModElements().isEmpty());
            assertFalse(workspace.getWorkspaceSettings().getMCreatorDependenciesRaw().contains("aaa_particles"));
        }
    }

    @Test void rejectsEscapingPathsAndCorruptChunksBeforeCopying() throws Exception {
        Path file = effect(temporary.resolve("bad.efkefc"), 1700, List.of("../private.png"));
        Path output = temporary.resolve("output");
        assertThrows(IOException.class, () -> RemarkAAAParticleImporter.stage(file, output));
        assertFalse(Files.exists(output));
        Files.write(file, new byte[]{'E', 'F', 'K', 'E', 0, 0, 0, 0, 'I', 'N', 'F', 'O', -1, -1, -1, 127});
        assertThrows(IOException.class, () -> EffekseerAssetReferences.read(file));
    }

    @Test void batchDiscoveryDeduplicatesFilesAndFolders() throws Exception {
        Path file = external("first", "texture");
        assertEquals(List.of(file.toAbsolutePath()),
                RemarkAAAParticleImporter.findEffects(List.of(file, file.getParent(), file)));
    }

    @Test void preservesUnderscoresInAutomaticCommandIds() throws Exception {
        try (var workspace = TestWorkspaceDataProvider.createTestWorkspace(temporary.resolve("workspace").toFile(),
                Generator.GENERATOR_CACHE.get("neoforge-1.21.1"), false, false, null)) {
            Path file = effect(temporary.resolve("skill_1.efkefc"), 1700, List.of());
            var first = RemarkAAAParticleImporter.create(workspace, file, "source_facing");
            var second = RemarkAAAParticleImporter.create(workspace, file, "source_facing");
            assertEquals("skill_1", first.getModElement().getRegistryName());
            assertEquals("skill_12", second.getModElement().getRegistryName());
            assertTrue(Files.isRegularFile(workspace.getWorkspaceFolder().toPath().resolve(first.sourceFile)));
        }
    }

    private Path external(String directory, String texture) throws Exception {
        Path root = temporary.resolve("external").resolve(directory);
        Files.createDirectories(root.resolve("Texture"));
        Files.writeString(root.resolve("Texture/leaf.png"), texture);
        Files.writeString(root.resolve("Texture/unrelated.png"), "do-not-package");
        return effect(root.resolve("spark.efkefc"), 1700, List.of("Texture/leaf.png"));
    }

    private Path effect(Path file, int version, List<String> references) throws Exception {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        if (version != 0) integer(info, version);
        integer(info, references.size());
        for (String reference : references) {
            if (version >= 1700) { integer(info, 1); integer(info, 1); }
            byte[] path = (reference + "\0").getBytes(StandardCharsets.UTF_16LE);
            integer(info, path.length / 2);
            info.write(path);
        }
        if (version < 1700)
            for (int group = 1; group < (version >= 1610 ? 7 : version >= 1500 ? 6 : 5); group++) integer(info, 0);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        result.write("EFKE".getBytes(StandardCharsets.US_ASCII)); integer(result, 0);
        result.write("INFO".getBytes(StandardCharsets.US_ASCII)); integer(result, info.size()); result.write(info.toByteArray());
        result.write("BIN_".getBytes(StandardCharsets.US_ASCII)); integer(result, 4); integer(result, 1700);
        Files.createDirectories(file.getParent());
        Files.write(file, result.toByteArray());
        return file;
    }

    private void integer(ByteArrayOutputStream output, int value) throws IOException {
        output.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array());
    }
}
