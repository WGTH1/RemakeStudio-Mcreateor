package net.mcreator.io.net.analytics;

/**
 * Compatibility facade retained for plugins. Remake Studio does not collect or transmit analytics.
 */
public final class GoogleAnalytics {

	public static final boolean ANALYTICS_ENABLED = false;

	public GoogleAnalytics(DeviceInfo deviceInfo) {
	}

	public void trackPageSync(String page) {
	}

	public void trackPage(String page) {
	}

	public void trackEvent(String name, String context) {
	}
}
