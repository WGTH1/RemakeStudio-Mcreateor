/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.element.converter.v2026_2;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mcreator.element.GeneratableElement;
import net.mcreator.element.converter.IConverter;
import net.mcreator.element.types.AAAParticle;
import net.mcreator.element.types.CreateUI;
import net.mcreator.element.types.Fashion;
import net.mcreator.workspace.Workspace;

public class RemarkElementSchemaConverter implements IConverter {

	@Override public GeneratableElement convert(Workspace workspace, GeneratableElement input,
			JsonElement jsonElementInput) {
		JsonObject definition = jsonElementInput.getAsJsonObject().getAsJsonObject("definition");
		if (input instanceof AAAParticle particle) {
			particle.normalizeRemarkSchema();
		} else if (input instanceof Fashion fashion) {
			fashion.normalizeRemarkSchema(definition.has("simpleItemWearable"), definition.has("singleItemSet"));
		} else if (input instanceof CreateUI createUI) {
			createUI.normalizeRemarkSchema();
		}
		return input;
	}

	@Override public int getVersionConvertingTo() {
		return 90;
	}
}
