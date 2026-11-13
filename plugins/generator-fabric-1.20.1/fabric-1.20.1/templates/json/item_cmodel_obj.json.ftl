{
  "parent": "special-model-loader:builtin/obj",
<#if var_item??>
  "model": "${modid}:models/item/${data.getItemCustomModelNameFor(var_item)}.obj"
<#else>
  "model": "${modid}:models/item/${data.customModelName.split(":")[0]}.obj"
</#if>
<#if var_item?? && data.getModElement().getTypeString() == "fashion">
  <#assign itemDisplay = data.getItemDisplaySettingsFor(var_item)>
  <#if itemDisplay.enabled>,
  <@itemDisplaySettings itemDisplay/>
  </#if>
<#elseif !var_item?? && data.getModElement().getTypeString() == "item">
  <#assign itemDisplay = data.getItemDisplaySettings()>
  <#if itemDisplay.enabled>,
  <@itemDisplaySettings itemDisplay/>
  </#if>
</#if>
}

<#macro itemDisplaySettings settings>
  "display": {
    <@itemDisplayTransform "gui" settings.gui/>,
    <@itemDisplayTransform "ground" settings.ground/>,
    <@itemDisplayTransform "fixed" settings.fixed/>,
    <@itemDisplayTransform "thirdperson_righthand" settings.thirdPersonRightHand/>,
    <@itemDisplayTransform "thirdperson_lefthand" settings.thirdPersonLeftHand/>,
    <@itemDisplayTransform "firstperson_righthand" settings.firstPersonRightHand/>,
    <@itemDisplayTransform "firstperson_lefthand" settings.firstPersonLeftHand/>
  }
</#macro>

<#macro itemDisplayTransform name transform>
    "${name}": {
      "rotation": [${transform.rotationX?c}, ${transform.rotationY?c}, ${transform.rotationZ?c}],
      "translation": [${transform.translationX?c}, ${transform.translationY?c}, ${transform.translationZ?c}],
      "scale": [${transform.scaleX?c}, ${transform.scaleY?c}, ${transform.scaleZ?c}]
    }
</#macro>
