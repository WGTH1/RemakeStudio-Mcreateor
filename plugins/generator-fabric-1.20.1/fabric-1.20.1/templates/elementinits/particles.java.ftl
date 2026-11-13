<#--
 # This file is part of Fabric-Generator-MCreator.
 # Copyright (C) 2020-2023, Goldorion, opensource contributors
 #
 # Fabric-Generator-MCreator is free software: you can redistribute it and/or modify
 # it under the terms of the GNU Lesser General Public License as published by
 # the Free Software Foundation, either version 3 of the License, or
 # (at your option) any later version.

 # Fabric-Generator-MCreator is distributed in the hope that it will be useful,
 # but WITHOUT ANY WARRANTY; without even the implied warranty of
 # MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 # GNU Lesser General Public License for more details.
 #
 # You should have received a copy of the GNU Lesser General Public License
 # along with Fabric-Generator-MCreator.  If not, see <https://www.gnu.org/licenses/>.
-->

<#-- @formatter:off -->
/*
 *	MCreator note: This file will be REGENERATED on each build.
 */

package ${package}.init;

import net.fabricmc.api.Environment;

public class ${JavaModName}ParticleTypes {

	<#assign normalParticles = w.getGElementsOfType("particle")>
	<#assign aaaParticles = w.getGElementsOfType("aaaparticle")>

	<#list normalParticles as particle>
	public static final SimpleParticleType ${particle.getModElement().getRegistryNameUpper()} = FabricParticleTypes.simple(${particle.alwaysShow});
	</#list>
	<#list aaaParticles as particle>
	public static final SimpleParticleType ${particle.getModElement().getRegistryNameUpper()} = FabricParticleTypes.simple(${particle.force});
	</#list>

	public static void clientLoad() {
		<#list normalParticles as particle>
		ParticleFactoryRegistry.getInstance().register(${particle.getModElement().getRegistryNameUpper()}, ${particle.getModElement().getName()}Particle::provider);
		</#list>
		<#list aaaParticles as particle>
		ParticleFactoryRegistry.getInstance().register(${particle.getModElement().getRegistryNameUpper()}, new ${particle.getModElement().getName()}AAAParticleProvider());
		</#list>
	}

	public static void load() {
		<#list normalParticles as particle>
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, new ResourceLocation("${modid}", "${particle.getModElement().getRegistryName()}"), ${particle.getModElement().getRegistryNameUpper()});
		</#list>
		<#list aaaParticles as particle>
		Registry.register(BuiltInRegistries.PARTICLE_TYPE, new ResourceLocation("${modid}", "${particle.getModElement().getRegistryName()}"), ${particle.getModElement().getRegistryNameUpper()});
		</#list>
	}

}
<#-- @formatter:on -->
