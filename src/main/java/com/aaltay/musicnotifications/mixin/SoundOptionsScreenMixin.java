package com.aaltay.musicnotifications.mixin;

import com.aaltay.musicnotifications.ConfigManager;
import com.aaltay.musicnotifications.ModConfig;
import com.aaltay.musicnotifications.MusicNotificationsMod;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Arrays;

@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {

    private OptionsList musicnotifications_getList() {
        Class<?> clazz = this.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == OptionsList.class) {
                    f.setAccessible(true);
                    try {
                        return (OptionsList) f.get(this);
                    } catch (Exception e) {
                        MusicNotificationsMod.LOGGER.error("[Music Notifications] list field get error", e);
                        return null;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        MusicNotificationsMod.LOGGER.error("[Music Notifications] OptionsList field not found in class hierarchy");
        return null;
    }

    @Inject(method = "addOptions", at = @At("TAIL"), remap = false)
    private void addMusicNotificationOptions(CallbackInfo ci) {
        OptionsList list = musicnotifications_getList();
        if (list == null) return;

        ModConfig cfg = ConfigManager.config;

        list.addHeader(Component.translatable("musicnotifications.options.header"));

        CycleButton<Boolean> enabledBtn = CycleButton.onOffBuilder(cfg.enabled)
                .create(0, 0, 150, 20,
                        Component.translatable("musicnotifications.options.enabled"),
                        (btn, val) -> {
                            ConfigManager.config.enabled = val;
                            ConfigManager.save();
                        });

        CycleButton<Boolean> iconBtn = CycleButton.onOffBuilder(cfg.showDiscIcon)
                .create(0, 0, 150, 20,
                        Component.translatable("musicnotifications.options.icon"),
                        (btn, val) -> {
                            ConfigManager.config.showDiscIcon = val;
                            ConfigManager.save();
                        });

        list.addSmall(enabledBtn, iconBtn);

        CycleButton<ModConfig.Position> posBtn = CycleButton.<ModConfig.Position>builder(
                        pos -> Component.translatable(pos.translationKey),
                        () -> ConfigManager.config.position)
                .withValues(ModConfig.Position.values())
                .create(0, 0, 150, 20,
                        Component.translatable("musicnotifications.options.position"),
                        (btn, val) -> {
                            ConfigManager.config.position = val;
                            ConfigManager.save();
                        });

        CycleButton<Integer> durationBtn = CycleButton.<Integer>builder(
                        val -> Component.translatable("musicnotifications.options.duration.value", val),
                        () -> ConfigManager.config.durationSeconds)
                .withValues(Arrays.asList(3, 5))
                .create(0, 0, 150, 20,
                        Component.translatable("musicnotifications.options.duration"),
                        (btn, val) -> {
                            ConfigManager.config.durationSeconds = val;
                            ConfigManager.save();
                        });

        list.addSmall(posBtn, durationBtn);
    }
}
