/* SPDX-License-Identifier: GPL-3.0-or-later */
package net.mcreator.integration;

import com.google.gson.JsonParser;
import net.mcreator.ui.init.L10N;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(IntegrationTestSetup.class)
class RemakeBrandingTest {
    @Test void visibleLabelsUseRemakeWithExistingTranslationIds() {
        assertEquals("Exit Remake Studio", L10N.t("action.exit"));
        assertEquals("Remake tools", L10N.t("remark.tools.button"));
        assertEquals("Remake Studio Center", L10N.t("remark.center.title"));
    }

    @Test void allLocaleValuesUseTheNewBrand() throws Exception {
        Pattern oldBrand = Pattern.compile("\\bRemark\\b");
        try (var files = Files.list(Path.of("plugins/remark-localization/lang"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().startsWith("texts")
                    && path.toString().endsWith(".properties")).toList()) {
                Properties bundle = new Properties();
                try (var reader = Files.newBufferedReader(file)) { bundle.load(reader); }
                for (var entry : bundle.entrySet()) {
                    assertFalse(oldBrand.matcher(entry.getValue().toString()).find(),
                            file.getFileName() + ": " + entry.getKey());
                }
            }
        }
    }

    @Test void pluginThemeAndInstallerIdentitiesStayCompatible() throws Exception {
        var core = JsonParser.parseString(Files.readString(Path.of("plugins/remark-core/plugin.json"))).getAsJsonObject();
        assertEquals("core", core.get("id").getAsString());
        assertEquals("Remake Studio core plugin", core.getAsJsonObject("info").get("name").getAsString());
        var theme = JsonParser.parseString(Files.readString(Path.of("plugins/remark-themes/themes/remark_gold/theme.json"))).getAsJsonObject();
        assertEquals("Remake Gold", theme.get("name").getAsString());
        String installer = Files.readString(Path.of("platform/windows/installer/install.nsi"));
        assertTrue(installer.contains("!define MUI_PRODUCT \"Remark Studio\""));
        assertTrue(installer.contains("Name \"Remake Studio ${MCREATOR_VERSION}\""));
        String msix = Files.readString(Path.of("platform/windows/msix/AppxManifest.xml"));
        assertTrue(msix.contains("Name=\"RemarkStudio.App\""));
        assertTrue(msix.contains("<DisplayName>Remake Studio</DisplayName>"));
    }

    @Test void newLauncherAndLegacyGradleTaskResolveToTheSameApplication() throws Exception {
        String launcher = Files.readString(Path.of("RUN_REMAKE_STUDIO.bat"));
        assertFalse(launcher.contains("RUN_REMARK_STUDIO.bat"));
        assertTrue(launcher.contains("run-remake-studio.ps1"));
        String runner = Files.readString(Path.of("platform/windows/run-remake-studio.ps1"));
        assertTrue(runner.contains("& '.\\gradlew.bat' 'runRemakeStudio'"));
        assertTrue(runner.contains("$occupied -contains"));
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("tasks.register('runRemakeStudio', JavaExec.class)"));
        assertTrue(build.contains("tasks.register('runRemarkStudio')"));
        assertTrue(build.contains("dependsOn tasks.named('runRemakeStudio')"));
    }

    @Test void bundledFabricTemplateHasTheNewServerDisplayName() throws Exception {
        Path update = Path.of(".remarkstudio-pending/generator-fabric-26.1.2-2026.2.zip");
        Path plugin = Files.exists(update) ? update : Path.of("plugins/generator-fabric-26.1.2-2026.2.zip");
        try (ZipFile zip = new ZipFile(plugin.toFile());
             var input = zip.getInputStream(zip.getEntry("fabric-26.1.2\\templates\\createui\\createui.java.ftl"))) {
            String template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(template.contains("\"Remake Server\""));
            assertFalse(template.contains("\"Remark Server\""));
        }
    }

    @Test @org.junit.jupiter.api.condition.EnabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void launcherReleasesItsOwnDriveWhenGradleFails(@TempDir Path root) throws Exception {
        Path javaExecutable = root.resolve("jdk/jbr25_win_64/bin/java.exe");
        Files.createDirectories(javaExecutable.getParent());
        Files.write(javaExecutable, new byte[0]);
        Files.writeString(root.resolve("gradlew.bat"), "@echo off\r\necho %CD%>\"%~dp0drive.txt\"\r\n"
                + "echo %*>\"%~dp0task.txt\"\r\nexit /b 7\r\n");
        var existingDrives = java.util.Arrays.stream(File.listRoots()).map(File::toString).toList();
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                Path.of("platform/windows/run-remake-studio.ps1").toAbsolutePath().toString(), "-Root", root.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("launcher.log").toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Launcher did not exit after the fake Gradle task");
            assertEquals(7, process.exitValue(), Files.readString(root.resolve("launcher.log")));
            String allocated = Files.readString(root.resolve("drive.txt")).trim();
            assertFalse(existingDrives.contains(allocated));
            assertFalse(new File(allocated).exists(), "The temporary drive should have been released");
            assertEquals("runRemakeStudio", Files.readString(root.resolve("task.txt")).trim());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
