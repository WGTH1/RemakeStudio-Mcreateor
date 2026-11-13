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
    "forge_marker": 1,
    "parent": "neoforge:item/default",
    "loader": "neoforge:obj",
    <#if var_item??> <#-- used by armor where item type is specified (helmet, body, ...) -->
    "model": "${modid}:models/item/${data.getItemCustomModelNameFor(var_item)}.obj",
    "textures": {
        <@textures data.getItemModelTextureMap(var_item)/>
        "particle": "<@textureRef data.getItemTextureFor(var_item) "item"/>"
    }
    <#else>
    "model": "${modid}:models/item/${data.customModelName.split(":")[0]}.obj",
    "textures": {
        <@textures data.getTextureMap()/>
        "particle": "<@textureRef data.texture "item"/>"
    }
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
