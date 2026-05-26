package com.aaltay.musicnotifications;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicNotificationsMod implements ClientModInitializer {

	public static final String MOD_ID = "musicnotifications";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		ConfigManager.load();
		LOGGER.info("Config loaded. Enabled={}, Position={}, Duration={}s",
				ConfigManager.config.enabled,
				ConfigManager.config.position,
				ConfigManager.config.durationSeconds);

		LOGGER.info("Starting ultra-lite SMTC listener for Music Notifications...");

		MediaManager.startListening();
	}
}
