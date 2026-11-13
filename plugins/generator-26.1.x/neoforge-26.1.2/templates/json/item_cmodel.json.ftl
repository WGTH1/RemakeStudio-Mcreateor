{
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
}

<#macro textures textureMap>
    <#if textureMap??>
        <#list textureMap.entrySet() as texture>
            "${texture.getKey()}": "<@textureRef texture.getValue() "block"/>",
        </#list>
    </#if>
</#macro>

<#macro textureRef texture folder><#local raw = texture?string><#if raw?contains(":")>${raw?replace(":", ":" + folder + "/")}<#else>${modid}:${folder}/${raw}</#if></#macro>
