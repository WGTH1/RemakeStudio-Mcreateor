<#assign mixins = []>
<#if w.hasElementsOfBaseType("item")>
  <#assign mixins += ["${JavaModName}RepairItemRecipeMixin"]>
</#if>
<#if w.getGElementsOfType('tool')?filter(e -> e.toolType = 'Fishing rod')?size != 0>
  <#assign mixins += ["${JavaModName}FishingHookMixin"]>
  <#assign mixins += ["${JavaModName}FishingHookRendererMixin"]>
  <#assign mixins += ["EntityMixin"]>
</#if>
<#if w.hasElementsOfType("biome")>
  <#assign mixins += ["NoiseGeneratorSettingsAccess"]>
</#if>
<#if w.hasElementsOfType("attribute")>
  <#assign mixins += ["AttributeSupplierAccessor"]>
</#if>
{
  "required": true,
  "minVersion": "0.8",
  "package": "${package}.mixins",
  "compatibilityLevel": "JAVA_17",
  "client": [<#if w.hasElementsOfType("fashion") || w.hasElementsOfType("item")>"DisplayPreviewRepositoryMixin"</#if>],
  "mixins": [
    <#list mixins as mixin>
      "${mixin}"<#sep>,
    </#list>
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
