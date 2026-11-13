{
    <#if parent?? && parent.hasGUITexture?? && parent.hasGUITexture()><#assign guiTexture = parent.guiTexture><#elseif data.guiTexture??><#assign guiTexture = data.guiTexture></#if>
    <#if guiTexture?has_content>
    "loader": "neoforge:separate_transforms",
    "gui_light": "front",
    "base": { <@modelDefinition/> },
    "perspectives": {
        "gui": {
            "parent": "item/generated",
            "textures": {
                "layer0": "<@textureRef guiTexture "item"/>"
            }
        },
        "fixed": {
            "parent": "item/generated",
            "textures": {
                "layer0": "<@textureRef guiTexture "item"/>"
            }
        },
        "ground": {
            "parent": "item/generated",
            "textures": {
                "layer0": "<@textureRef guiTexture "item"/>"
            }
        }
    }
    <#else>
    <@modelDefinition/>
    </#if>
    <#macro modelDefinition>
    <#if var_item??> <#-- used by armor where item type is specified (helmet, body, ...) -->
    "parent": "${modid}:custom/${data.getItemCustomModelNameFor(var_item)}",
    "textures": {
      <@textures data.getItemModelTextureMap(var_item)/>
      "particle": "<@textureRef data.getItemTextureFor(var_item) "item"/>"
    }
    <#else>
    "parent": "${modid}:custom/${data.customModelName.split(":")[0]}",
    "textures": {
      <@textures data.getTextureMap()/>
      "particle": "<@textureRef data.texture "item"/>"
    }
    </#if>
    </#macro>
    <#if data.getModels?? && data.getModels()?has_content>,
    "overrides": [
        <#list data.getModels() as model>
        {
            "predicate": {
                <#list model.stateMap.keySet() as property>
                    <#assign value = model.stateMap.get(property)>
                    "${generator.map(property.getPrefixedName(registryname + "_"), "itemproperties")}": ${value?is_boolean?then(value?then("1", "0"), value)}<#sep>,
                </#list>
            },
            "model": "${modid}:item/${registryname}_${model?index}"
        }<#sep>,
        </#list>
    ]
    </#if>
}

<#macro textures textureMap>
    <#if textureMap??>
        <#list textureMap.entrySet() as texture>
            "${texture.getKey()}": "<@textureRef texture.getValue() "block"/>",
        </#list>
    </#if>
</#macro>

<#macro textureRef texture folder><#local raw = texture?string><#if raw?contains(":")>${raw?replace(":", ":" + folder + "/")}<#else>${modid}:${folder}/${raw}</#if></#macro>
