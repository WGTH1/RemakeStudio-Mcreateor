/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.unit.workspace.resources;

import net.mcreator.workspace.resources.ModelUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelUtilsTest {

	@Test void normalizesMaterialLibraryToTheImportedOBJName() {
		String obj = "# Blockbench\r\nmtllib D31.mtl\r\no cube\r\n";

		assertEquals("# Blockbench\r\nmtllib d31.mtl\r\no cube\r\n",
				ModelUtils.normalizeOBJMaterialLibraryReference(obj, "d31.obj"));
	}

	@Test void sanitizesTheTargetNameAndPreservesIndentation() {
		String obj = "  mtllib My Fancy Material.MTL\nv 0 0 0\n";

		assertEquals("  mtllib my_fancy_model.mtl\nv 0 0 0\n",
				ModelUtils.normalizeOBJMaterialLibraryReference(obj, "My Fancy Model.OBJ"));
	}

	@Test void normalizesBlockbenchTextureCoordinatesExactlyOnce() {
		String obj = "# Made in Blockbench 4.12.6\r\nmtllib D31.mtl\r\nvt 0 0.4375\r\nvt 1 1\r\n";

		String normalized = ModelUtils.normalizeBlockbenchOBJTextureCoordinates(obj);

		assertTrue(normalized.startsWith("# Remake Studio: Blockbench V coordinates normalized\r\n"));
		assertTrue(normalized.contains("vt 0 0.5625\r\n"));
		assertTrue(normalized.contains("vt 1 0\r\n"));
		assertEquals(normalized, ModelUtils.normalizeBlockbenchOBJTextureCoordinates(normalized));
	}

	@Test void leavesTextureCoordinatesFromOtherExportersUnchanged() {
		String obj = "# Blender 4.1\nvt 0.25 0.75\n";

		assertEquals(obj, ModelUtils.normalizeBlockbenchOBJTextureCoordinates(obj));
	}

	@Test void resolvesMaterialTextureBesideTheMTLCaseInsensitively(@TempDir Path temporaryDirectory) throws Exception {
		Path mtl = temporaryDirectory.resolve("D31.mtl");
		Path texture = temporaryDirectory.resolve("D1texture.png");
		Files.writeString(mtl, "newmtl material\nmap_Kd d1TEXTURE.PNG\n");
		Files.write(texture, new byte[] { 1, 2, 3 });

		File resolved = ModelUtils.resolveOBJTextureFile(mtl.toFile(), "d1TEXTURE.PNG");

		assertEquals(texture.toFile(), resolved);
		assertEquals("d1texture", ModelUtils.getOBJTextureName("D1texture.png"));
	}
}
