/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.mcreator.workspace;

import net.mcreator.element.ModElementType;
import net.mcreator.workspace.elements.ModElement;

import java.util.HashSet;
import java.util.Set;

public final class RemarkWorkspaceDependencyManager {

	public static final String AAA_PARTICLES = "aaa_particles";

	private RemarkWorkspaceDependencyManager() {
	}

	public static boolean reconcileAfterRemoval(Workspace workspace, ModElement removedElement) {
		if (removedElement.getType() != ModElementType.AAAPARTICLE)
			return false;
		return removeAAAParticlesIfUnused(workspace);
	}

	public static boolean removeAAAParticlesIfUnused(Workspace workspace) {
		if (workspace.containsType(ModElementType.AAAPARTICLE))
			return false;

		Set<String> currentDependencies = workspace.getWorkspaceSettings().getMCreatorDependenciesRaw();
		if (!currentDependencies.contains(AAA_PARTICLES))
			return false;

		Set<String> updatedDependencies = new HashSet<>(currentDependencies);
		updatedDependencies.remove(AAA_PARTICLES);
		workspace.getWorkspaceSettings().setMCreatorDependencies(updatedDependencies);
		return true;
	}
}
