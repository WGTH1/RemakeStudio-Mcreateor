package net.mcreator.io.net.api;

import net.mcreator.io.net.api.update.UpdateInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Local-only web API used by Remake Studio distributions.
 */
public final class OfflineWebAPI implements IWebAPI {

	private static final UpdateInfo UPDATE_INFO = UpdateInfo.empty();

	@Override public boolean initAPI() {
		return false;
	}

	@Override public UpdateInfo getUpdateInfo() {
		return UPDATE_INFO;
	}

	@Override public void getWebsiteNews(CompletableFuture<String[]> data) {
		data.complete(null);
	}

	@Override public void getModOfTheWeekData(CompletableFuture<String[]> data) {
		data.complete(null);
	}

	@Override public String getSearchURL(String searchTerm) {
		return "";
	}
}
