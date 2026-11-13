package net.mcreator.integration.generator;

import net.mcreator.element.ModElementType;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.generator.setup.WorkspaceGeneratorSetup;
import net.mcreator.gradle.GradleUtils;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.ui.component.ConsolePane;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import net.mcreator.workspace.settings.WorkspaceSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
public class AAAParticleBuildTest {
    @Test @EnabledIfEnvironmentVariable(named = "REMAKE_AAA_BUILD", matches = "true")
    void buildsAllModesAgainstTheRealLoaderAndAaaApi() throws Exception {
        Path root = Path.of(System.getenv("REMAKE_AAA_BUILD_OUTPUT"));
        assertFalse(Files.exists(root.resolve("aaa_proof.mcreator")), "Use an unused build directory");
        WorkspaceSettings settings = new WorkspaceSettings("aaa_proof");
        settings.setCurrentGenerator(System.getenv().getOrDefault("REMAKE_AAA_BUILD_TARGET", "neoforge-1.21.1"));
        settings.setMCreatorDependencies(Set.of("aaa_particles"));
        settings.setModName("Remake AAA verification");
        settings.setVersion("1.0.0");
        boolean debugConsole = ConsolePane.DEBUG_CONTENTS_TO_LOG;
        // Keep compiler diagnostics out of the application's ERROR logger; Gradle's exit status is authoritative.
        ConsolePane.DEBUG_CONTENTS_TO_LOG = false;
        try (Workspace workspace = Workspace.createWorkspace(root.resolve("aaa_proof.mcreator").toFile(), settings)) {
            Path source = root.resolve("imported");
            Files.createDirectories(source);
            Files.writeString(source.resolve("effect.efkefc"), "compile-only-fixture");
            for (String mode : new String[]{"world", "source_facing", "source_follow_view"}) {
                ModElement element = new ModElement(workspace, mode.equals("world") ? "WorldEffect" :
                        mode.equals("source_facing") ? "FaceEffect" : "FollowEffect", ModElementType.AAAPARTICLE);
                AAAParticle particle = new AAAParticle(element);
                particle.sourceRoot = "imported";
                particle.sourceFile = "imported/effect.efkefc";
                particle.effectPath = element.getRegistryName();
                particle.spawnMode = mode;
                particle.unlimitedDistance = true;
                particle.offsetX = -1.25;
                particle.offsetY = 0.5;
                particle.offsetZ = 2;
                particle.rotationX = 15;
                particle.rotationY = 30;
                particle.rotationZ = 45;
                particle.scaleX = particle.scaleY = particle.scaleZ = particle.speed = 1;
                workspace.addModElement(element);
                particle.useModAssetFiles(true);
                workspace.getModElementManager().storeModElement(particle);
            }
            WorkspaceGeneratorSetup.setupWorkspaceBase(workspace);
            var generator = workspace.getGenerator();
            generator.runResourceSetupTasks();
            var connection = GradleUtils.getGradleProjectConnection(workspace);
            GradleUtils.getGradleSyncLauncher(workspace.getGeneratorConfiguration(), connection)
                    .addArguments("--max-workers=1").setStandardOutput(System.out).setStandardError(System.out).run();
            generator.reloadGradleCaches();
            for (ModElement element : workspace.getModElements()) {
                AAAParticle particle = (AAAParticle) element.getGeneratableElement();
                generator.generateElement(particle, true);
                workspace.getModElementManager().storeModElement(particle);
            }
            generator.generateBase(true);
            workspace.getFileManager().saveWorkspaceDirectlyAndWait();
            GradleUtils.getGradleTaskLauncher(workspace.getGeneratorConfiguration(), connection, "build")
                    .addArguments("--max-workers=1").setStandardOutput(System.out).setStandardError(System.out).run();
            Path artifact = root.resolve(workspace.getGeneratorConfiguration().getGradleTaskFor("export_file"));
            assertTrue(Files.isRegularFile(artifact));
            try (ZipFile zip = new ZipFile(artifact.toFile())) {
                assertNotNull(zip.getEntry("assets/aaa_proof/effeks/follow_effect.efkefc"));
                assertTrue(zip.stream().anyMatch(entry -> entry.getName().endsWith("/FollowEffectAAAParticle.class")));
            }
            System.out.println("AAA_COMPILE_PROOF=" + artifact);
        } finally {
            ConsolePane.DEBUG_CONTENTS_TO_LOG = debugConsole;
        }
    }
}
