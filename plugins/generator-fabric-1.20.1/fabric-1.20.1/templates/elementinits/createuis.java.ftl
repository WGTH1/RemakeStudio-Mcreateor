<#-- @formatter:off -->
package ${package}.init;

public class ${JavaModName}CreateUIs {

	public static void load() {
		<#list w.getGElementsOfType("createui") as createui>
		${package}.client.gui.${createui.getModElement().getName()}CreateUIScreen.clientLoad();
		</#list>
	}

}
<#-- @formatter:on -->
