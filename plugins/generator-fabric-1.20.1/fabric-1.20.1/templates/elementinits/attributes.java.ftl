<#-- @formatter:off -->
package ${package}.init;

public class ${JavaModName}Attributes {
	<#list attributes as attribute>
	public static Attribute ${attribute.getModElement().getRegistryNameUpper()};
	</#list>

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void load() {
		<#list attributes as attribute>
		${attribute.getModElement().getRegistryNameUpper()} = register("${attribute.getModElement().getRegistryName()}",
				new RangedAttribute("attribute.${modid}.${attribute.getModElement().getRegistryName()}", ${attribute.defaultValue}d, ${attribute.minValue}d, ${attribute.maxValue}d).setSyncable(true));
		</#list>

		<#list attributes as attribute>
			<#if attribute.addToAllEntities>
		BuiltInRegistries.ENTITY_TYPE.stream()
				.filter(entityType -> entityType instanceof EntityType<?> && DefaultAttributes.hasSupplier((EntityType<? extends LivingEntity>) entityType))
				.map(entityType -> (EntityType<? extends LivingEntity>) entityType)
				.forEach(entityType -> FabricDefaultAttributeRegistry.register(entityType,
						addAttributeToEntity(DefaultAttributes.getSupplier(entityType), ${JavaModName}Attributes.${attribute.getModElement().getRegistryNameUpper()})));
			<#else>
				<#if attribute.entities?has_content>
		List.of(
					<#list attribute.entities as entity>
			${generator.map(entity.getUnmappedValue(), "entities", 1)}<#sep>,
					</#list>
		).stream()
				.filter(DefaultAttributes::hasSupplier)
				.map(entityType -> (EntityType<? extends LivingEntity>) entityType)
				.forEach(entityType -> FabricDefaultAttributeRegistry.register(entityType,
						addAttributeToEntity(DefaultAttributes.getSupplier(entityType), ${JavaModName}Attributes.${attribute.getModElement().getRegistryNameUpper()})));
				</#if>
				<#if attribute.addToPlayers>
		FabricDefaultAttributeRegistry.register(EntityType.PLAYER,
				addAttributeToEntity(DefaultAttributes.getSupplier(EntityType.PLAYER), ${JavaModName}Attributes.${attribute.getModElement().getRegistryNameUpper()}));
				</#if>
			</#if>
		</#list>
	}

	private static AttributeSupplier addAttributeToEntity(AttributeSupplier existingSupplier, Attribute newAttribute) {
		AttributeSupplier.Builder builder = AttributeSupplier.builder();
		AttributeSupplierAccessor accessor = (AttributeSupplierAccessor) existingSupplier;
		accessor.getInstances().forEach((attribute, instance) -> builder.add(attribute, instance.getBaseValue()));
		return builder.add(newAttribute).build();
	}

	private static Attribute register(String registryName, Attribute attribute) {
		return Registry.register(BuiltInRegistries.ATTRIBUTE, new ResourceLocation(${JavaModName}.MODID, registryName), attribute);
	}
}
<#-- @formatter:on -->
