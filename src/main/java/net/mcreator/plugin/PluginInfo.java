/*
 * MCreator (https://mcreator.net/)
 * Copyright (C) 2020 Pylo and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.mcreator.plugin;

import java.util.List;

/**
 * <p>PluginInfo contains all info about the plugin such as its name, its author or a description of it. Most of them are optional.</p>
 */
@SuppressWarnings("unused") public class PluginInfo {

	public static final String VERSION_NOT_SPECIFIED = "not specified";

	private String name;
	private String description;
	private String author;
	private String credits;

	private String version;

	private List<String> dependencies;

	private String updateJSONURL;
	private int pluginPageID;

	/**
	 * @return <p>The displayed name of the plugin</p>
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return <p>A description displayed in the plugins panel.</p>
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return <p>The author(s) of the plugin to be displayed in the plugins panel.</p>
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * See {@link Plugin} to read more about this legacy metadata field.
	 *
	 * @return <p>The plugin's version if provided</p>
	 */
	public String getVersion() {
		if (version == null)
			return VERSION_NOT_SPECIFIED;
		return version;
	}

	/**
	 * See isLoaded in {@link Plugin} to get more info about its usage.
	 *
	 * @return <p>A list of plugin's IDs needed to use the plugin</p>
	 */
	public List<String> getDependencies() {
		return dependencies;
	}

	/**
	 * @return <p>A String with optional credits to give to someone.</p>
	 */
	public String getCredits() {
		if (credits == null) {
			return "None";
		}
		return credits;
	}

	/**
	 * <p>Legacy plugin metadata retained for file-format compatibility. Remake Studio does not contact this URL.</p>
	 *
	 * @return <p>A link to an online JSON file</p>
	 */
	public String getUpdateJSONURL() {
		return updateJSONURL;
	}

	/**
	 * <p>Legacy plugin page identifier retained for file-format compatibility.</p>
	 *
	 * @return <p>The legacy plugin page identifier.</p>
	 */
	public int getPluginPageID() {
		return pluginPageID;
	}
}
