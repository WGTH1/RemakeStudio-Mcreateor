/*
 * Remake Studio
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or later.
 */

package net.mcreator.integration;

import net.mcreator.io.net.api.OfflineWebAPI;
import net.mcreator.util.DesktopUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemarkUpstreamIsolationTest {

    static {
        System.setProperty("log_directory", System.getProperty("java.io.tmpdir"));
    }

    @Test void webApiIsOfflineOnly() {
        OfflineWebAPI api = new OfflineWebAPI();
        assertFalse(api.initAPI());
        assertEquals("0.0.0", api.getUpdateInfo().getLatestMajor());
        assertTrue(api.getUpdateInfo().getReleases().isEmpty());
        assertEquals("", api.getSearchURL("anything"));

        CompletableFuture<String[]> news = new CompletableFuture<>();
        CompletableFuture<String[]> showcase = new CompletableFuture<>();
        api.getWebsiteNews(news);
        api.getModOfTheWeekData(showcase);
        assertNull(news.join());
        assertNull(showcase.join());
    }

    @Test void removedUpstreamChannelsAreBlockedCentrally() {
        assertTrue(DesktopUtils.isBlockedUpstreamContact("https://mcreator.net"));
        assertTrue(DesktopUtils.isBlockedUpstreamContact("https://translate.mcreator.net/project"));
        assertTrue(DesktopUtils.isBlockedUpstreamContact("https://github.com/MCreator/MCreator"));
        assertTrue(DesktopUtils.isBlockedUpstreamContact("https://reddit.com/r/MCreator"));
        assertTrue(DesktopUtils.isBlockedUpstreamContact("https://discord.gg/sbrcQPP5eZ"));
        assertFalse(DesktopUtils.isBlockedUpstreamContact("https://modrinth.com"));
        assertFalse(DesktopUtils.isBlockedUpstreamContact("https://www.minecraft.net/eula"));
    }
}
