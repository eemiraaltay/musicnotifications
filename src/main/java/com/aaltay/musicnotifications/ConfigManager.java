package com.aaltay.musicnotifications;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ConfigManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ScheduledExecutorService SAVE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "MusicNotifications-ConfigSave");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object SAVE_LOCK = new Object();
    private static ScheduledFuture<?> pendingSave;
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("musicnotifications.json");

    public static volatile ModConfig config = new ModConfig();

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                config = GSON.fromJson(json, ModConfig.class);
                if (config == null) config = new ModConfig();
                LOGGER.info("Config loaded.");
            } catch (IOException e) {
                LOGGER.error("Config read failed, using defaults.", e);
                config = new ModConfig();
            }
        } else {
            config = new ModConfig();
            saveNow();
        }
    }

    public static void save() {
        synchronized (SAVE_LOCK) {
            if (pendingSave != null) {
                pendingSave.cancel(false);
            }
            pendingSave = SAVE_EXECUTOR.schedule(ConfigManager::saveNow, 250, TimeUnit.MILLISECONDS);
        }
    }

    private static void saveNow() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(config));
        } catch (IOException e) {
            LOGGER.error("Config save failed.", e);
        }
    }
}
