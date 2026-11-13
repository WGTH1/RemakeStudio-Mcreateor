package net.mcreator.ui.notifications;

import net.mcreator.plugin.PluginLoadFailure;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.ui.component.util.ThreadUtil;
import net.mcreator.ui.init.L10N;
import net.mcreator.ui.init.UIRES;
import net.mcreator.util.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.stream.Collectors;

public final class StartupNotifications {

	private static boolean notificationsHandled;

	private StartupNotifications() {
	}

	public static <T extends Window & INotificationConsumer> void handleStartupNotifications(T parent) {
		if (!notificationsHandled) {
			ThreadUtil.runOnSwingThreadAndWait(() -> handlePluginLoadFails(parent));
			notificationsHandled = true;
		}
	}

	private static <T extends Window & INotificationConsumer> void handlePluginLoadFails(T parent) {
		Collection<PluginLoadFailure> failedPlugins = PluginLoader.INSTANCE.getFailedPlugins();
		if (failedPlugins.isEmpty())
			return;

		parent.addNotification(UIRES.get("18px.warning"),
				L10N.t("notification.plugin_load_failed.msg") + "<br><p style='width:240px'><kbd>"
						+ failedPlugins.stream().map(PluginLoadFailure::pluginID).collect(Collectors.joining(", ")),
				new NotificationsRenderer.ActionButton(L10N.t("notification.common.more_info"), e -> {
					StringBuilder sb = new StringBuilder("<html>");
					sb.append(L10N.t("dialog.plugin_load_failed.msg1")).append("<ul>");
					for (PluginLoadFailure plugin : failedPlugins) {
						sb.append("<li><b>").append(plugin.pluginID()).append("</b> - reason: ")
								.append(StringUtils.abbreviateString(plugin.message(), 100, true))
								.append("<br><small>Location: ").append(plugin.pluginFile()).append("</small></li>");
					}
					sb.append("</ul><br>").append(L10N.t("dialog.plugin_load_failed.msg2"));
					JOptionPane.showMessageDialog(parent, sb.toString(), L10N.t("dialog.plugin_load_failed.title"),
							JOptionPane.WARNING_MESSAGE);
				}));
	}
}
