package dev.xetyk.miniassist.hud;

import dev.xetyk.miniassist.config.ConfigManager;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.impl.render.ClientHudModule;
import dev.xetyk.miniassist.util.Draw;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/** HUD layout editor available while the vanilla chat (T) is open. */
public final class HudEditor {
    private static final int ACCENT = 0xFF9A83FF;
    private static final int ACCENT_BLUE = 0xFF62D5FF;
    private static final int TEXT = 0xFFF2F4FA;
    private static final int MUTED = 0xFF9AA6B9;

    private static boolean initialized;
    private static String draggingId;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static int toolbarX;
    private static int toolbarY;
    private static int hudButtonX;
    private static int hudButtonW;
    private static int keybindButtonX;
    private static int keybindButtonW;
    private static int resetButtonX;
    private static int resetButtonW;
    private static int toolbarHeight = 22;

    private HudEditor() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen)) return;

            ScreenEvents.afterRender(screen).register((renderedScreen, context, mouseX, mouseY, tickDelta) -> {
                // Screen API coordinates are already scaled to the current GUI scale.
                tickDrag(client, renderedScreen, mouseX, mouseY);
                renderEditorOverlay(context, client, mouseX, mouseY);
            });

            ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, button) -> {
                if (button != GLFW.GLFW_MOUSE_BUTTON_1) return true;
                if (handleToolbarClick(client, mouseX, mouseY)) return false;
                if (beginDrag(client, screen, mouseX, mouseY)) {
                    return false; // Keep ChatScreen from moving its input cursor while dragging a HUD panel.
                }
                return true;
            });

            ScreenMouseEvents.beforeMouseRelease(screen).register((releasedScreen, mouseX, mouseY, button) -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_1) finishDrag();
            });

        });
    }

    private static boolean beginDrag(MinecraftClient client, Screen screen, double mouseX, double mouseY) {
        var widgets = MiniAssistHud.getWidgets(client);
        for (int i = widgets.size() - 1; i >= 0; i--) {
            MiniAssistHud.HudWidget widget = widgets.get(i);
            if (!widget.contains(mouseX, mouseY)) continue;
            draggingId = widget.id();
            dragOffsetX = (int) Math.round(mouseX) - widget.x();
            dragOffsetY = (int) Math.round(mouseY) - widget.y();
            return true;
        }
        return false;
    }

    private static void tickDrag(MinecraftClient client, Screen screen, double screenMouseX, double screenMouseY) {
        if (draggingId == null) return;
        if (client.currentScreen != screen || client.getWindow() == null
                || GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) != GLFW.GLFW_PRESS) {
            finishDrag();
            return;
        }

        // Use the same GUI-scaled coordinate space as ChatScreen and DrawContext.
        int mouseX = (int) Math.round(screenMouseX);
        int mouseY = (int) Math.round(screenMouseY);
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        MiniAssistHud.HudWidget current = MiniAssistHud.getWidgets(client).stream()
                .filter(widget -> widget.id().equals(draggingId)).findFirst().orElse(null);
        if (current == null) return;
        HudLayout.Position clamped = HudLayout.clamp(mouseX - dragOffsetX, mouseY - dragOffsetY,
                current.width(), current.height(), sw, sh);
        HudLayout.setPosition(draggingId, clamped.x(), clamped.y());
    }

    private static void finishDrag() {
        if (draggingId != null) ConfigManager.save();
        draggingId = null;
    }

    private static boolean handleToolbarClick(MinecraftClient client, double mouseX, double mouseY) {
        if (mouseY < toolbarY || mouseY >= toolbarY + toolbarHeight) return false;
        if (mouseX >= hudButtonX && mouseX < hudButtonX + hudButtonW) {
            if (ModuleManager.get("HUD") instanceof ClientHudModule hud) {
                hud.toggle();
                ConfigManager.save();
            }
            return true;
        }
        if (mouseX >= keybindButtonX && mouseX < keybindButtonX + keybindButtonW) {
            if (ModuleManager.get("HUD") instanceof ClientHudModule hud) {
                hud.toggleKeybinds();
                ConfigManager.save();
            }
            return true;
        }
        if (mouseX >= resetButtonX && mouseX < resetButtonX + resetButtonW) {
            HudLayout.reset();
            ConfigManager.save();
            return true;
        }
        return false;
    }

    private static void renderEditorOverlay(DrawContext context, MinecraftClient client, int mouseX, int mouseY) {
        if (client.currentScreen == null || client.player == null) return;
        var widgets = MiniAssistHud.getWidgets(client);
        for (MiniAssistHud.HudWidget widget : widgets) {
            boolean active = widget.id().equals(draggingId);
            int border = active ? ACCENT_BLUE : ACCENT;
            int x = widget.x() - 2;
            int y = widget.y() - 2;
            int w = widget.width() + 4;
            int h = widget.height() + 4;
            context.fill(x, y, x + w, y + 1, border);
            context.fill(x, y + h - 1, x + w, y + h, border);
            context.fill(x, y, x + 1, y + h, border);
            context.fill(x + w - 1, y, x + w, y + h, border);

            String label = widget.title();
            int labelW = client.textRenderer.getWidth(label) + 10;
            int labelY = Math.max(2, y - 12);
            Draw.rr(context, x, labelY, labelW, 10, 3, 0xE010111B);
            context.drawTextWithShadow(client.textRenderer, label, x + 5, labelY + 1,
                    active ? ACCENT_BLUE : TEXT);
        }

        int sw = client.getWindow().getScaledWidth();
        ClientHudModule hud = ModuleManager.get("HUD") instanceof ClientHudModule value ? value : null;
        boolean hudEnabled = hud != null && hud.isEnabled();
        boolean showBinds = hud != null && hud.showKeybinds();
        String title = draggingId == null ? "MINIASSIST HUD EDITOR" : "MOVING " + draggingId.toUpperCase(java.util.Locale.ROOT);
        String hudLabel = "HUD " + (hudEnabled ? "ON" : "OFF");
        String keyLabel = "BINDS " + (showBinds ? "ON" : "OFF");
        String resetLabel = "RESET";
        int titleW = client.textRenderer.getWidth(title) + 20;
        hudButtonW = client.textRenderer.getWidth(hudLabel) + 16;
        keybindButtonW = client.textRenderer.getWidth(keyLabel) + 16;
        resetButtonW = client.textRenderer.getWidth(resetLabel) + 16;
        int totalW = titleW + hudButtonW + keybindButtonW + resetButtonW + 16;
        toolbarX = Math.max(6, (sw - totalW) / 2);
        toolbarY = 8;
        hudButtonX = toolbarX + titleW + 4;
        keybindButtonX = hudButtonX + hudButtonW + 4;
        resetButtonX = keybindButtonX + keybindButtonW + 4;

        Draw.rr(context, toolbarX, toolbarY, totalW, toolbarHeight, 6, 0xF00D101A);
        Draw.rr(context, toolbarX + 7, toolbarY + 7, 7, 7, 3, draggingId == null ? ACCENT : ACCENT_BLUE);
        context.drawTextWithShadow(client.textRenderer, title, toolbarX + 19, toolbarY + 7,
                draggingId == null ? TEXT : ACCENT_BLUE);
        Draw.rr(context, hudButtonX, toolbarY + 3, hudButtonW, toolbarHeight - 6, 4,
                hudEnabled ? 0xFF302B4B : 0xFF252936);
        context.drawCenteredTextWithShadow(client.textRenderer, hudLabel,
                hudButtonX + hudButtonW / 2, toolbarY + 7, hudEnabled ? ACCENT_BLUE : MUTED);
        Draw.rr(context, keybindButtonX, toolbarY + 3, keybindButtonW, toolbarHeight - 6, 4,
                showBinds ? 0xFF302B4B : 0xFF252936);
        context.drawCenteredTextWithShadow(client.textRenderer, keyLabel,
                keybindButtonX + keybindButtonW / 2, toolbarY + 7, showBinds ? ACCENT_BLUE : MUTED);
        Draw.rr(context, resetButtonX, toolbarY + 3, resetButtonW, toolbarHeight - 6, 4, 0xFF252936);
        context.drawCenteredTextWithShadow(client.textRenderer, resetLabel,
                resetButtonX + resetButtonW / 2, toolbarY + 7, MUTED);

        String hint = draggingId == null
                ? "LMB drag panels  •  T chat editor  •  Esc close chat"
                : "Release LMB to save position";
        int hintW = client.textRenderer.getWidth(hint) + 14;
        int hintX = (sw - hintW) / 2;
        int hintY = toolbarY + toolbarHeight + 5;
        Draw.rr(context, hintX, hintY, hintW, 15, 4, 0xCC0D101A);
        context.drawCenteredTextWithShadow(client.textRenderer, hint,
                hintX + hintW / 2, hintY + 4, MUTED);
    }
}
