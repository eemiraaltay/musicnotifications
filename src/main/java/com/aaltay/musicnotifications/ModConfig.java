package com.aaltay.musicnotifications;

public class ModConfig {
    public boolean enabled = true;
    public Position position = Position.TOP_RIGHT;
    public int durationSeconds = 5;
    public boolean showDiscIcon = true;

    public enum Position {
        TOP_RIGHT("musicnotifications.options.position.top_right"),
        TOP_LEFT("musicnotifications.options.position.top_left");

        public final String translationKey;

        Position(String translationKey) {
            this.translationKey = translationKey;
        }

        @Override
        public String toString() {
            return translationKey;
        }
    }
}
