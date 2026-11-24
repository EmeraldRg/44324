package dev.xetyk.miniassist.hud;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent GUI-scaled positions for draggable HUD widgets. */
public final class HudLayout {
    public static final String[] WIDGET_IDS = {
            "watermark", "active_modules", "keybinds", "effects",
            "coordinates", "armor", "keystrokes", "target"
    };

    public record Position(int x, int y) {}

    private static final Map<String, Position> POSITIONS = new LinkedHashMap<>();

    private HudLayout() {}

    public static synchronized Position resolve(String id, int defaultX, int defaultY,
                                                 int widgetWidth, int widgetHeight,
                                                 int screenWidth, int screenHeight) {
        Position saved = POSITIONS.get(id);
        int x = saved == null ? defaultX : saved.x();
        int y = saved == null ? defaultY : saved.y();
        return clamp(x, y, widgetWidth, widgetHeight, screenWidth, screenHeight);
    }

    public static synchronized Position clamp(int x, int y, int widgetWidth, int widgetHeight,
                                               int screenWidth, int screenHeight) {
        int maxX = Math.max(0, screenWidth - Math.max(1, widgetWidth));
        int maxY = Math.max(0, screenHeight - Math.max(1, widgetHeight));
        return new Position(Math.max(0, Math.min(maxX, x)), Math.max(0, Math.min(maxY, y)));
    }

    public static synchronized void setPosition(String id, int x, int y) {
        if (id == null || id.isBlank()) return;
        POSITIONS.put(id, new Position(x, y));
    }

    public static synchronized Position getStored(String id) {
        return POSITIONS.get(id);
    }

    public static synchronized Map<String, Position> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(POSITIONS));
    }

    public static synchronized void reset() {
        POSITIONS.clear();
    }
}
