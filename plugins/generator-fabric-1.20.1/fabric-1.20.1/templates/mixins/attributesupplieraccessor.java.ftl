<#-- @formatter:off -->
package ${package}.mixins;

import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Mutable;

@Mixin(AttributeSupplier.class)
public interface AttributeSupplierAccessor {
	@Accessor("instances") @Mutable
	Map<Attribute, AttributeInstance> getInstances();
}
<#-- @formatter:on -->
