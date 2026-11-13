package net.mcreator.ui.remark;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class DisplayLivePreviewTemplateTest {
	@Test void everyInstalledGeneratorRegistersAndRendersTheDevOnlyHelper() throws Exception {
		String canonical = null;
		for (String generator : List.of("forge-1.20.1", "fabric-1.20.1", "neoforge-1.21.1",
				"neoforge-1.21.1-21.1.219", "neoforge-26.1.2", "fabric-26.1.2")) {
			String wrapper = read(generator, "templates/display/live_preview.java.ftl");
			String body = read(generator, "templates/display/live_preview_body.java.ftl");
			if (canonical == null) canonical = body;
			assertEquals(canonical, body, "Keep protocol identical: " + generator);
			for (String definition : List.of("item", "fashion")) {
				String yaml = read(generator, definition + ".definition.yaml");
				assertTrue(yaml.contains("display/live_preview.java.ftl"), generator);
				assertTrue(yaml.contains("@BASEPACKAGEPATH/client/preview/DisplayLivePreview.java"), generator);
			}
			Configuration configuration = new Configuration(Configuration.VERSION_2_3_34);
			StringTemplateLoader loader = new StringTemplateLoader();
			loader.putTemplate("display/live_preview.java.ftl", wrapper);
			loader.putTemplate("display/live_preview_body.java.ftl", body);
			configuration.setTemplateLoader(loader);
			StringWriter writer = new StringWriter();
			configuration.getTemplate("display/live_preview.java.ftl").process(Map.of("package", "test.preview", "modid", "example"), writer);
			String java = writer.toString();
			assertTrue(java.contains("if (!DEV) return;"), generator);
			assertTrue(java.contains("hasSingleplayerServer()"), generator);
			assertTrue(java.contains("wornOverride(String element, String slot, boolean left)"), generator);
			assertTrue(java.contains("record WornOverride(boolean enabled"), generator);
			assertFalse(java.contains("<#"), generator);
			assertFalse(java.contains("options.save"), generator);
			if (generator.startsWith("fabric")) {
				assertTrue(read(generator, "templates/display/repository_mixin.java.ftl").contains("isDevelopmentEnvironment()"));
				String init = read(generator, "templates/modbase/" + (generator.endsWith("26.1.2") ? "mod_client" : "client") + ".java.ftl");
				assertTrue(init.contains("DisplayLivePreview.clientLoad()"));
				assertTrue(read(generator, "templates/modbase/" + (generator.endsWith("26.1.2") ? "mixin" : "mixins") + ".json.ftl")
						.contains("DisplayPreviewRepositoryMixin"));
			}
		}
	}

	private static String read(String generator, String relative) throws Exception {
		// The launcher installs this staged archive when an open Studio window locks the installed ZIP.
		Path pending = Path.of(".remarkstudio-pending/generator-fabric-26.1.2-2026.2.zip");
		Path archive = Files.isRegularFile(pending) ? pending : Path.of("plugins/generator-fabric-26.1.2-2026.2.zip");
		if (generator.equals("fabric-26.1.2")) try (ZipFile zip = new ZipFile(archive.toFile())) {
			var entry = zip.stream().filter(e -> e.getName().replace('\\', '/').equals(generator + "/" + relative)).findFirst().orElseThrow();
			try (var input = zip.getInputStream(entry)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
		}
		String plugin = switch (generator) {
			case "neoforge-1.21.1" -> "generator-1.21.1";
			case "neoforge-26.1.2" -> "generator-26.1.x";
			default -> "generator-" + generator;
		};
		return Files.readString(Path.of("plugins", plugin, generator, relative));
	}
}
