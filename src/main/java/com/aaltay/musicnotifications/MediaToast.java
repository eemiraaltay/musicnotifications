package com.aaltay.musicnotifications;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.concurrent.ThreadLocalRandom;

public class MediaToast implements Toast {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("minecraft", "toast/recipe");

    private static final Identifier[] DISC_SPRITES = {
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/13"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/cat"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/blocks"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/chirp"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/far"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/mall"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/mellohi"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/stal"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/strad"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/ward"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/11"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/wait"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/otherside"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/pigstep"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/5"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/relic"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/precipice"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/tears"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/lava_chicken"),
        Identifier.fromNamespaceAndPath("musicnotifications", "disc/creator"),
    };

    private static final int TOAST_WIDTH = 160;
    private static final int TOAST_HEIGHT = 32;
    private static final int COLOR_TITLE_GOLD = 0xFFD700;
    private static final int COLOR_ARTIST_GRAY = 0xAAAAAA;
    private static final int COLOR_PROGRESS_BAR = 0xFFFFD700;
    private static final int PADDING_X = 5;
    private static final int PADDING_Y = 7;
    private static final int ICON_SIZE = 18;
    private static final int TEXT_X_WITH_ICON = 27;
    private static final int TEXT_X_NO_ICON = 8;
    private static final int TEXT_RIGHT_PADDING = 7;
    private static final String ELLIPSIS = "...";
    private static final long DUPLICATE_SUPPRESS_MILLIS = 1500L;

    private final String title;
    private final String artist;
    private final Identifier discSprite;
    private long firstTime = -1L;
    private Visibility wantedVisibility = Visibility.SHOW;
    public static long lastShownTime = 0;
    private static String lastToastKey = "";
    private static long lastToastKeyTime = 0;

    public MediaToast(String title, String artist) {
        this.title = title;
        this.artist = artist;
        this.discSprite = DISC_SPRITES[ThreadLocalRandom.current().nextInt(DISC_SPRITES.length)];
    }

    @Override
    public SoundEvent getSoundEvent() {
        return null;
    }

    @Override
    public float xPos(int guiWidth, float visiblePortion) {
        ModConfig config = ConfigManager.config;
        if (config.position == ModConfig.Position.TOP_LEFT) {
            return (visiblePortion - 1f) * this.width();
        }
        return guiWidth - visiblePortion * this.width();
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.wantedVisibility;
    }

    @Override
    public void update(ToastManager manager, long startTime) {
        if (this.firstTime == -1L) {
            this.firstTime = startTime;
        }
        ModConfig config = ConfigManager.config;
        long duration = (long)(config.durationSeconds) * 1000L;
        if (startTime - this.firstTime > duration) {
            this.wantedVisibility = Visibility.HIDE;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long lastTime) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, this.width(), this.height());

        ModConfig config = ConfigManager.config;
        boolean showIcon = config.showDiscIcon;
        int textX = showIcon ? TEXT_X_WITH_ICON : TEXT_X_NO_ICON;
        int textWidth = this.width() - textX - TEXT_RIGHT_PADDING;

        if (showIcon) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.discSprite, PADDING_X, PADDING_Y, ICON_SIZE, ICON_SIZE);
        }

        Component titleText = Component.literal(trimToWidth(font, this.title, textWidth)).withStyle(s -> s.withColor(COLOR_TITLE_GOLD));
        Component artistText = Component.literal(trimToWidth(font, this.artist, textWidth)).withStyle(s -> s.withColor(COLOR_ARTIST_GRAY));

        graphics.textRenderer().accept(TextAlignment.LEFT, textX, PADDING_Y, titleText.getVisualOrderText());
        graphics.textRenderer().accept(TextAlignment.LEFT, textX, PADDING_Y + 11, artistText.getVisualOrderText());

        if (this.firstTime != -1L) {
            long duration = (long)(config.durationSeconds) * 1000L;
            float progress = 1.0f - (float)(lastTime - this.firstTime) / (float)duration;
            if (progress > 0) {
                int barWidth = (int)((this.width() - (PADDING_X * 2)) * progress);
                graphics.fill(PADDING_X, this.height() - 3, PADDING_X + barWidth, this.height() - 2, COLOR_PROGRESS_BAR);
            }
        }
    }

    @Override
    public int width() { return TOAST_WIDTH; }

    @Override
    public int height() { return TOAST_HEIGHT; }

    public static void show(String title, String artist) {
        ModConfig config = ConfigManager.config;
        if (!config.enabled) return;

        long now = System.currentTimeMillis();
        String toastKey = normalizeToastKey(title, artist);
        if (toastKey.equals(lastToastKey) && now - lastToastKeyTime < DUPLICATE_SUPPRESS_MILLIS) {
            return;
        }
        lastToastKey = toastKey;
        lastToastKeyTime = now;
        MediaToast.lastShownTime = now;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getToastManager() == null) return;
        client.execute(() -> {
            try {
                client.getToastManager().addToast(new MediaToast(title, artist));
            } catch (Exception e) {
                MusicNotificationsMod.LOGGER.error("Toast add failed", e);
            }
        });
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        int ellipsisWidth = font.width(ELLIPSIS);
        int limit = Math.max(0, maxWidth - ellipsisWidth);
        StringBuilder builder = new StringBuilder();
        int width = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charWidth = font.width(String.valueOf(c));
            if (width + charWidth > limit) {
                break;
            }
            builder.append(c);
            width += charWidth;
        }

        return builder.append(ELLIPSIS).toString();
    }

    private static String normalizeToastKey(String title, String artist) {
        return normalize(title) + "\n" + normalize(artist);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}
