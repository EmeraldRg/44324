package dev.xetyk.miniassist.gui;

import dev.xetyk.miniassist.modules.api.Category;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent positions for the five ClickGUI category columns. */
public final class MenuPanelLayout {
    public record Position(int x, int y) {}

    private static final Map<String, Position> POSITIONS = new LinkedHashMap<>();

    private MenuPanelLayout() {}

    public static synchronized Position resolve(Category category, int defaultX, int defaultY,
                                                int panelWidth, int panelHeight,
                                                int screenWidth, int screenHeight) {
        Position stored = POSITIONS.get(key(category));
        int x = stored == null ? defaultX : stored.x();
        int y = stored == null ? defaultY : stored.y();
        return clamp(x, y, panelWidth, panelHeight, screenWidth, screenHeight);
    }

    public static synchronized Position clamp(int x, int y, int panelWidth, int panelHeight,
                                               int screenWidth, int screenHeight) {
        int maxX = Math.max(4, screenWidth - panelWidth - 4);
        int maxY = Math.max(28, screenHeight - panelHeight - 48);
        return new Position(Math.max(4, Math.min(maxX, x)), Math.max(28, Math.min(maxY, y)));
    }

    public static synchronized void setPosition(Category category, int x, int y) {
        if (category == null) return;
        POSITIONS.put(key(category), new Position(x, y));
    }

    public static synchronized Map<String, Position> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(POSITIONS));
    }

    public static synchronized void reset() {
        POSITIONS.clear();
    }

    private static String key(Category category) {
        return category.name().toLowerCase(java.util.Locale.ROOT);
    }
}
