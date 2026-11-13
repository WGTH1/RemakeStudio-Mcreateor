/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.workspace.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.javagl.obj.Mtl;
import de.javagl.obj.MtlReader;
import de.javagl.obj.MtlWriter;
import net.mcreator.element.parts.TextureHolder;
import net.mcreator.generator.io.JSONWriter;
import net.mcreator.io.FileIO;
import net.mcreator.minecraft.RegistryNameFixer;
import net.mcreator.util.FilenameUtilsPatched;
import net.mcreator.workspace.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelUtils {

	private static final Logger LOG = LogManager.getLogger(ModelUtils.class);
	private static final Pattern OBJ_MATERIAL_LIBRARY_PATTERN = Pattern.compile("(?im)^([\\t ]*mtllib[\\t ]+).*$");
	private static final Pattern BLOCKBENCH_OBJ_PATTERN = Pattern.compile("(?im)^#[\\t ]*Made in Blockbench\\b");
	private static final String BLOCKBENCH_UV_MARKER = "# Remake Studio: Blockbench V coordinates normalized";
	private static final String OBJ_NUMBER = "[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?";
	private static final Pattern OBJ_TEXTURE_COORDINATE_PATTERN = Pattern.compile(
			"(?m)^([\\t ]*vt[\\t ]+)(" + OBJ_NUMBER + ")([\\t ]+)(" + OBJ_NUMBER + ")([^\\r\\n]*)(\\r?)$");

	public static String removeInlineTexturesSectionFromJSONModel(String jsonModel) {
		try {
			JsonElement json = JsonParser.parseString(jsonModel);
			json.getAsJsonObject().remove("textures");
			return JSONWriter.gson.toJson(json);
		} catch (Exception e) {
			LOG.info("Failed to remove inline textures from JSON model");
		}
		return jsonModel;
	}

	public static void copyOBJorMTLApplyTextureMapping(Workspace workspace, File objFrom, File objTo, Model model,
			String prefix) {
		String extension = FilenameUtilsPatched.getExtension(objFrom.getName());
		if (extension.equalsIgnoreCase("obj")) {
			copyOBJWithNormalizedMaterialLibrary(objFrom, objTo);
			return;
		} else if (extension.equalsIgnoreCase("mtl")) {
			Map<String, TexturedModel.TextureMapping> textureMappingMap = TexturedModel.getTextureMappingsForModel(
					workspace, model);
			if (textureMappingMap != null && textureMappingMap.containsKey("default")) {
				Map<String, TextureHolder> textureMap = textureMappingMap.get("default").getTextureMap();
				try {
					List<Mtl> mtlList = MtlReader.read(new FileInputStream(objFrom));
					for (Mtl mtlElement : mtlList) {
						String elementName = mtlElement.getName();
						if (textureMap.containsKey(elementName)) {
							mtlElement.setMapKd(prefix + textureMap.get(elementName));
						}
					}

					MtlWriter.write(mtlList, new FileOutputStream(objTo));
				} catch (Exception ignore) {
				}
				return;
			}
		}
		FileIO.copyFile(objFrom, objTo); // fallback
	}

	public static void copyOBJorMTLApplyTextureReferences(File objFrom, File objTo) {
		String extension = FilenameUtilsPatched.getExtension(objFrom.getName());
		if (extension.equalsIgnoreCase("obj")) {
			copyOBJWithNormalizedMaterialLibrary(objFrom, objTo);
			return;
		} else if (extension.equalsIgnoreCase("mtl")) {
			try {
				List<Mtl> mtlList = MtlReader.read(new FileInputStream(objFrom));
				for (Mtl mtlElement : mtlList) {
					mtlElement.setMapKd(mtlElement.getName());
				}
				MtlWriter.write(mtlList, new FileOutputStream(objTo));
			} catch (Exception ignore) {
			}
			return;
		}
		FileIO.copyFile(objFrom, objTo); // fallback
	}

	public static void copyOBJWithNormalizedMaterialLibrary(File objFrom, File objTo) {
		String normalized = normalizeOBJMaterialLibraryReference(FileIO.readFileToString(objFrom), objTo.getName());
		normalized = normalizeBlockbenchOBJTextureCoordinates(normalized);
		FileIO.writeStringToFile(normalized, objTo);
	}

	public static String normalizeOBJMaterialLibraryReference(String objContents, String targetOBJFileName) {
		String mtlName = RegistryNameFixer.fix(FilenameUtilsPatched.getBaseName(targetOBJFileName)) + ".mtl";
		return OBJ_MATERIAL_LIBRARY_PATTERN.matcher(objContents)
				.replaceAll(match -> Matcher.quoteReplacement(match.group(1) + mtlName));
	}

	public static String normalizeBlockbenchOBJTextureCoordinates(String objContents) {
		if (!BLOCKBENCH_OBJ_PATTERN.matcher(objContents).find() || objContents.contains(BLOCKBENCH_UV_MARKER))
			return objContents;

		String normalized = OBJ_TEXTURE_COORDINATE_PATTERN.matcher(objContents).replaceAll(match -> {
			BigDecimal originalV = new BigDecimal(match.group(4));
			String flippedV = BigDecimal.ONE.subtract(originalV).stripTrailingZeros().toPlainString();
			return Matcher.quoteReplacement(
					match.group(1) + match.group(2) + match.group(3) + flippedV + match.group(5) + match.group(6));
		});
		String lineSeparator = objContents.contains("\r\n") ? "\r\n" : "\n";
		return BLOCKBENCH_UV_MARKER + lineSeparator + normalized;
	}

	public static String getOBJTextureName(String textureReference) {
		String reference = cleanOBJTextureReference(textureReference);
		return RegistryNameFixer.fix(FilenameUtilsPatched.getBaseName(reference));
	}

	@Nullable public static File resolveOBJTextureFile(File mtlFile, String textureReference) {
		String reference = cleanOBJTextureReference(textureReference);
		if (reference.isBlank())
			return null;

		// Minecraft resource locations are not local files.
		if (reference.contains(":") && !reference.matches("^[A-Za-z]:[\\\\/].*"))
			return null;

		File referencedFile = new File(reference);
		if (!referencedFile.isAbsolute())
			referencedFile = new File(mtlFile.getParentFile(), reference);
		if (referencedFile.isFile())
			return referencedFile;

		String fileName = FilenameUtilsPatched.getName(reference);
		File[] siblings = mtlFile.getParentFile()
				.listFiles(file -> file.isFile() && file.getName().equalsIgnoreCase(fileName));
		return siblings != null && siblings.length > 0 ? siblings[0] : null;
	}

	private static String cleanOBJTextureReference(String textureReference) {
		if (textureReference == null)
			return "";
		String reference = textureReference.trim();
		if (reference.length() >= 2 && ((reference.startsWith("\"") && reference.endsWith("\"")) || (
				reference.startsWith("'") && reference.endsWith("'"))))
			reference = reference.substring(1, reference.length() - 1);
		return reference;
	}

}
