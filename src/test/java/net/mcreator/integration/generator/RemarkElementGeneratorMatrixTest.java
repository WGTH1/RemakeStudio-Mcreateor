/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.integration.generator;

import net.mcreator.element.ModElementType;
import net.mcreator.element.parts.ItemDisplaySettings;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.Fashion;
import net.mcreator.element.types.Item;
import net.mcreator.generator.Generator;
import net.mcreator.generator.GeneratorConfiguration;
import net.mcreator.generator.GeneratorFile;
import net.mcreator.integration.IntegrationTestSetup;
import net.mcreator.integration.TestWorkspaceDataProvider;
import net.mcreator.io.FileIO;
import net.mcreator.workspace.Workspace;
import net.mcreator.workspace.elements.ModElement;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(IntegrationTestSetup.class) public class RemarkElementGeneratorMatrixTest {

	@TestFactory Stream<DynamicTest> generateRemarkElementsForEverySupportedGenerator() {
		String filter = System.getenv().getOrDefault("REMARK_GENERATOR_FILTER", ".*");
		return Generator.GENERATOR_CACHE.values().stream()
				.filter(generator -> generator.getGeneratorName().matches(filter))
				.sorted(java.util.Comparator.comparing(GeneratorConfiguration::getGeneratorName))
				.flatMap(generator -> Stream.of(ModElementType.AAAPARTICLE, ModElementType.FASHION, ModElementType.ITEM)
						.filter(type -> generator.getDefinitionsProvider().getModElementDefinition(type) != null)
						.map(type -> DynamicTest.dynamicTest(generator.getGeneratorName() + " / " + type.getReadableName(),
								() -> runGeneration(generator, type))));
	}

	private void runGeneration(GeneratorConfiguration generator, ModElementType<?> type) throws Exception {
		File workspaceFolder = Files.createTempDirectory("remark_element_matrix").toFile();
		Workspace workspace = null;
		try {
			workspace = TestWorkspaceDataProvider.createTestWorkspace(workspaceFolder, generator, false, false, null);
			if (type == ModElementType.AAAPARTICLE)
				testAAAParticle(workspace);
			else if (type == ModElementType.FASHION)
				testFashion(workspace);
			else
				testItemDisplay(workspace);
		} finally {
			if (workspace != null)
				workspace.close();
			FileIO.deleteDir(workspaceFolder);
		}
	}

	private void testAAAParticle(Workspace workspace) throws Exception {
		ModElement modElement = new ModElement(workspace, "MatrixAAA", ModElementType.AAAPARTICLE);
		AAAParticle particle = new AAAParticle(modElement);
		File sourceRoot = new File(workspace.getWorkspaceFolder(), ".remark-assets/aaa/matrix");
		File sourceFile = new File(sourceRoot, "matrix.efkefc");
		assertTrue(sourceRoot.mkdirs() || sourceRoot.isDirectory());
		Files.writeString(sourceFile.toPath(), "remark-test-effect", StandardCharsets.UTF_8);
		particle.sourceRoot = relative(workspace, sourceRoot);
		particle.sourceFile = relative(workspace, sourceFile);
		particle.sourceName = sourceFile.getName();
		particle.effectPath = "matrix/matrix_aaa";
		particle.spawnMode = "source_follow_view";
		particle.offsetX = 1.25;
		particle.offsetY = 2.5;
		particle.offsetZ = -0.75;
		particle.rotationX = 15;
		particle.rotationY = 30;
		particle.rotationZ = 45;
		particle.scaleX = 1.5;
		particle.scaleY = 0.75;
		particle.scaleZ = 2;
		particle.speed = 1.25;
		workspace.addModElement(modElement);

		List<GeneratorFile> files = workspace.getGenerator().generateElement(particle, false, false);
		assertFalse(files.isEmpty());
		String generated = generatedText(files);
		assertTrue(generated.contains("aaaparticle"));
		assertTrue(generated.contains("matrix/matrix_aaa"));
		assertTrue(generated.contains("getViewYRot") && generated.contains("getViewXRot"),
				"Follow-view mode must read the source view direction");
	}

	private void testFashion(Workspace workspace) throws Exception {
		ModElement modElement = new ModElement(workspace, "MatrixFashion", ModElementType.FASHION);
		Fashion fashion = new Fashion(modElement);
		fashion.enableHelmet = true;
		fashion.enableBody = true;
		fashion.enableLeggings = true;
		fashion.enableBoots = true;
		fashion.simpleItemWearable = true;
		fashion.singleItemSet = true;
		fashion.getWornTransformFor("helmet").translationX = 11;
		fashion.getWornTransformFor("body").translationX = 22;
		fashion.getWornTransformFor("leggings").translationX = 33;
		fashion.getWornTransformFor("boots").translationX = 44;
		fashion.getWornDisplay().setLinked("leggings", false);
		fashion.getWornDisplay().setLinked("boots", false);
		fashion.getWornTransformFor("leggings", false).translationX = 55;
		fashion.getWornTransformFor("boots", false).translationX = 66;
		fashion.getWornDisplay().setSideEnabled("boots", false, false);
		fashion.textureHelmet = new TextureHolder(workspace, "test");
		fashion.textureBody = new TextureHolder(workspace, "test2");
		fashion.textureLeggings = new TextureHolder(workspace, "test3");
		fashion.textureBoots = new TextureHolder(workspace, "test4");
		fashion.helmetName = "Matrix Head";
		fashion.bodyName = "Matrix Body";
		fashion.leggingsName = "Matrix Legs";
		fashion.bootsName = "Matrix Feet";
		fashion.armorTextureFile = "armor_texture";
		fashion.helmetModelTexture = "From armor";
		fashion.bodyModelTexture = "From armor";
		fashion.leggingsModelTexture = "From armor";
		fashion.bootsModelTexture = "From armor";
		if (workspace.getGeneratorConfiguration().getGeneratorStats().hasBaseCoverage("model_obj")) {
			createOBJModel(workspace, "matrix_fashion");
			fashion.helmetItemRenderType = 2;
			fashion.helmetItemCustomModelName = "matrix_fashion";
			fashion.helmetItemDisplay = ItemDisplaySettings.compactFashion();
			fashion.helmetItemDisplay.gui.rotationX = 31.5;
			fashion.helmetItemDisplay.gui.scaleX = 0.19;
			fashion.helmetItemDisplay.gui.scaleY = 0.19;
			fashion.helmetItemDisplay.gui.scaleZ = 0.19;
		}
		workspace.addModElement(modElement);

		assertEquals(1, fashion.providedMCItems().size(), "Single-item Fashion must register one item");
		assertTrue(fashion.shouldGenerateHelmetItem());
		assertFalse(fashion.shouldGenerateBodyItem());
		assertFalse(fashion.shouldGenerateLeggingsItem());
		assertFalse(fashion.shouldGenerateBootsItem());

		List<GeneratorFile> files = workspace.getGenerator().generateElement(fashion, false, false);
		assertFalse(files.isEmpty());
		String generated = generatedText(files);
		if (net.mcreator.element.parts.WornDisplaySettings.supports(workspace.getGenerator().getGeneratorName())) {
			String compact = generated.replaceAll("\\s+", "");
			assertTrue(compact.contains("DisplayLivePreview.wornOverride(\"matrix_fashion\",slot,left)"));
			assertTrue(compact.contains("previewSideEnabled(boots?\"boots\":\"leggings\",false,boots?false:true)"));
			assertTrue(compact.contains("if(!applyPreviewTransform(poseStack,\"helmet\",true))"));
			for (int value : new int[] { 11, 22, 33, 44, 55, 66 })
				assertTrue(compact.contains("poseStack.translate(" + value + "/16.0,0/16.0,0/16.0)"), "Missing worn slot transform " + value);
			fashion.singleItemSet = false;
			String separate = generatedText(workspace.getGenerator().generateElement(fashion, false, false)).replaceAll("\\s+", "");
			assertEquals(4, fashion.providedMCItems().size());
			for (int value : new int[] { 11, 22, 33, 44, 55, 66 })
				assertTrue(separate.contains("poseStack.translate(" + value + "/16.0,0/16.0,0/16.0)"));
		}
		assertTrue(generated.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")
				.contains("matrixfashion"));
		if (fashion.helmetItemRenderType == 2) {
			assertTrue(generated.contains("matrix_fashion.obj"), "Fashion OBJ item model must be generated");
			assertTrue(generated.contains("\"display\""), "Fashion OBJ item model must include custom Display settings");
			assertTrue(generated.contains("\"rotation\": [31.5, 225, 0]"),
					"Fashion GUI rotation must be generated");
			assertTrue(generated.contains("\"scale\": [0.19, 0.19, 0.19]"),
					"Fashion GUI scale must be generated");
		}
	}

	private void testItemDisplay(Workspace workspace) throws Exception {
		if (!workspace.getGeneratorConfiguration().getGeneratorStats().hasBaseCoverage("model_obj"))
			return;

		Item item = (Item) TestWorkspaceDataProvider.getModElementExamplesFor(workspace, ModElementType.ITEM, false,
				new Random(0)).getFirst();
		for (java.lang.reflect.Field field : Item.class.getFields())
			if (net.mcreator.element.parts.procedure.Procedure.class.isAssignableFrom(field.getType()))
				field.set(item, null);
		createOBJModel(workspace, "matrix_fashion");
		item.renderType = 2;
		item.customModelName = "matrix_fashion";
		item.itemDisplay = ItemDisplaySettings.compactFashion();
		item.itemDisplay.thirdPersonRightHand.rotationX = 12.5;
		item.itemDisplay.thirdPersonRightHand.scaleX = 0.23;
		item.itemDisplay.thirdPersonRightHand.scaleY = 0.23;
		item.itemDisplay.thirdPersonRightHand.scaleZ = 0.23;
		workspace.addModElement(item.getModElement());

		List<GeneratorFile> files = workspace.getGenerator().generateElement(item, false, false);
		assertFalse(files.isEmpty());
		String generated = generatedText(files);
		assertTrue(generated.contains("matrix_fashion.obj"), "Item OBJ model must be generated");
		assertTrue(generated.contains("\"display\""), "Item OBJ model must include custom Display settings");
		assertTrue(generated.contains("\"rotation\": [12.5, 45, 0]"),
				"Item third-person rotation must be generated");
		assertTrue(generated.contains("\"scale\": [0.23, 0.23, 0.23]"),
				"Item third-person scale must be generated");
	}

	private static void createOBJModel(Workspace workspace, String name) throws Exception {
		File modelsDir = workspace.getFolderManager().getModelsDir();
		assertTrue(modelsDir.mkdirs() || modelsDir.isDirectory());
		Files.writeString(new File(modelsDir, name + ".obj").toPath(), """
				mtllib matrix_fashion.mtl
				o triangle
				v 0 0 0
				v 1 0 0
				v 0 1 0
				vt 0 0
				vt 1 0
				vt 0 1
				vn 0 0 1
				usemtl material
				f 1/1/1 2/2/1 3/3/1
				""", StandardCharsets.UTF_8);
		Files.writeString(new File(modelsDir, name + ".mtl").toPath(), """
				newmtl material
				map_Kd test
				""", StandardCharsets.UTF_8);
	}

	private static String generatedText(List<GeneratorFile> files) {
		return files.stream().map(GeneratorFile::contents).filter(Objects::nonNull)
				.collect(java.util.stream.Collectors.joining("\n"));
	}

	private static String relative(Workspace workspace, File file) {
		return workspace.getWorkspaceFolder().toPath().toAbsolutePath().normalize()
				.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace(File.separator, "/");
	}
}
