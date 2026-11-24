package dev.xetyk.miniassist.gui;

import dev.xetyk.miniassist.config.ConfigManager;
import dev.xetyk.miniassist.hud.HudLayout;
import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import dev.xetyk.miniassist.util.Draw;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Nursultan-inspired multi-column ClickGUI with movable category panels. */
public final class MiniAssistMenuScreen extends Screen {
    private static final int PANEL = 0xE90D0F18;
    private static final int PANEL_INNER = 0xFF171923;
    private static final int ROW = 0xFF191C28;
    private static final int ROW_HOVER = 0xFF24283A;
    private static final int ROW_ON = 0xFF24203B;
    private static final int CHIP = 0xFF242636;
    private static final int TRACK = 0xFF303348;
    private static final int ACCENT = 0xFF9A83FF;
    private static final int ACCENT_BLUE = 0xFF62D5FF;
    private static final int TEXT = 0xFFF2F4FA;
    private static final int MUTED = 0xFF9AA6B9;
    private static final int FAINT = 0xFF68768B;
    private static final int GAP = 8;
    private static final int ROW_H = 27;

    private static final class Hit {
        final int x, y, w, h;
        final Runnable left;
        final Runnable right;
        final NumberSetting slider;
        Hit(int x, int y, int w, int h, Runnable left, Runnable right, NumberSetting slider) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.left = left; this.right = right; this.slider = slider;
        }
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();
    private final Map<Category, int[]> panelPositions = new EnumMap<>(Category.class);
    private final Map<Category, Integer> scrolls = new EnumMap<>(Category.class);
    private final Map<Category, Integer> maxScrolls = new EnumMap<>(Category.class);

    private Module bindingModule;
    private NumberSetting draggingSlider;
    private Hit activeSlider;
    private Category draggingCategory;
    private int panelDragOffsetX, panelDragOffsetY;
    private String search = "";
    private boolean searchFocused;
    private String notice;

    private int panelW;
    private int panelH;
    private int gridX;
    private int gridY;
    private int searchX;
    private int searchY;
    private int searchW = 224;
    private int configX;
    private int configY;

    public MiniAssistMenuScreen() {
        super(Text.literal("MiniAssist ClickGUI"));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep the 3D world visible behind the client panels.
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void layout() {
        panelW = Math.max(118, Math.min(174, (width - 48 - GAP * 4) / 5));
        panelH = Math.max(180, Math.min(352, height - 112));
        int gridW = panelW * 5 + GAP * 4;
        gridX = Math.max(8, (width - gridW) / 2);
        gridY = Math.max(42, (height - panelH - 66) / 2);

        int index = 0;
        for (Category category : Category.values()) {
            if (!panelPositions.containsKey(category)) {
                MenuPanelLayout.Position stored = MenuPanelLayout.resolve(category,
                        gridX + index * (panelW + GAP), gridY, panelW, panelH, width, height);
                panelPositions.put(category, new int[]{stored.x(), stored.y()});
            }
            int[] pos = panelPositions.get(category);
            pos[0] = clamp(pos[0], 4, Math.max(4, width - panelW - 4));
            pos[1] = clamp(pos[1], 28, Math.max(28, height - panelH - 48));
            index++;
        }

        searchW = Math.min(260, Math.max(180, width / 4));
        searchX = (width - searchW) / 2;
        searchY = height - 31;
        configX = width - 174;
        configY = Math.min(height - 76, gridY + panelH + 10);
        if (configY < 28) configY = height - 76;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        layout();
        hits.clear();
        context.fill(0, 0, width, height, 0x7204070D);

        drawBrand(context);
        for (Category category : Category.values()) drawCategoryPanel(context, category, mouseX, mouseY);
        drawConfigManager(context, mouseX, mouseY);
        drawSearch(context, mouseX, mouseY);
        drawFooter(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBrand(DrawContext context) {
        int x = Math.max(10, (width - 280) / 2);
        Draw.rr(context, x, 8, 280, 25, 7, 0xD70D101A);
        Draw.rr(context, x + 7, 14, 12, 12, 4, ACCENT);
        context.drawTextWithShadow(textRenderer, "MINIASSIST", x + 25, 12, TEXT);
        context.drawTextWithShadow(textRenderer, "1.5.0  •  FABRIC 1.21.4", x + 104, 13, MUTED);
        context.drawTextWithShadow(textRenderer, "RIGHT SHIFT", x + 215, 13, ACCENT_BLUE);
    }

    private void drawCategoryPanel(DrawContext context, Category category, int mouseX, int mouseY) {
        int[] pos = panelPositions.get(category);
        int x = pos[0], y = pos[1];
        Draw.rr(context, x - 2, y - 2, panelW + 4, panelH + 4, 10, 0x40000000);
        Draw.rr(context, x, y, panelW, panelH, 9, PANEL);
        Draw.rr(context, x + 1, y + 1, panelW - 2, 31, 8, 0xFF1A1D2A);
        context.fill(x + 8, y + 32, x + panelW - 8, y + 33, 0xFF34364B);
        context.fill(x + 8, y + 32, x + Math.min(panelW - 8, 38), y + 33, ACCENT);

        int count = filteredModules(category).size();
        int enabled = 0;
        for (Module module : ModuleManager.getModules()) {
            if (module.getCategory() == category && module.isEnabled() && matches(module)) enabled++;
        }
        context.drawCenteredTextWithShadow(textRenderer, category.title, x + panelW / 2, y + 10, TEXT);
        String countText = enabled + "/" + count;
        context.drawTextWithShadow(textRenderer, countText, x + panelW - textRenderer.getWidth(countText) - 9,
                y + 11, ACCENT_BLUE);

        if (inside(mouseX, mouseY, x, y, panelW, 32)) {
            Draw.rr(context, x + 2, y + 2, panelW - 4, 28, 6, 0x183D4260);
        }

        List<Module> modules = filteredModules(category);
        int scroll = scrolls.getOrDefault(category, 0);
        int contentTop = y + 39;
        int contentBottom = y + panelH - 9;
        int contentH = Math.max(30, contentBottom - contentTop);
        context.enableScissor(x + 5, contentTop, x + panelW - 5, contentBottom);

        int cursorY = contentTop - scroll;
        if (modules.isEmpty()) {
            String empty = search.isBlank() ? "No modules yet" : "No matches";
            context.drawCenteredTextWithShadow(textRenderer, empty, x + panelW / 2, contentTop + 18, FAINT);
        }

        for (Module module : modules) {
            int moduleH = moduleHeight(module, panelW - 28);
            if (cursorY + moduleH >= contentTop && cursorY < contentBottom) {
                drawModule(context, category, module, x + 7, cursorY, panelW - 14, mouseX, mouseY);
            }
            cursorY += moduleH + 5;
        }
        context.disableScissor();

        int total = cursorY - (contentTop - scroll);
        int maxScroll = Math.max(0, total - contentH);
        maxScrolls.put(category, maxScroll);
        if (scroll > maxScroll) scrolls.put(category, maxScroll);
        if (maxScroll > 0) {
            int trackX = x + panelW - 4;
            int thumbH = Math.max(24, (int) ((double) contentH * contentH / (contentH + maxScroll)));
            int thumbY = contentTop + (int) ((double) (contentH - thumbH) * scroll / maxScroll);
            Draw.rr(context, trackX, contentTop, 2, contentH, 1, 0xFF252839);
            Draw.rr(context, trackX, thumbY, 2, thumbH, 1, ACCENT);
        }
    }

    private List<Module> filteredModules(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module.getCategory() == category && matches(module)) result.add(module);
        }
        return result;
    }

    private boolean matches(Module module) {
        if (search.isBlank()) return true;
        String q = search.toLowerCase(Locale.ROOT);
        return module.getName().toLowerCase(Locale.ROOT).contains(q)
                || module.getDescription().toLowerCase(Locale.ROOT).contains(q);
    }

    private int moduleHeight(Module module, int availableWidth) {
        if (!expanded.contains(module)) return ROW_H;
        int h = ROW_H;
        if (!module.getDescription().isBlank()) h += 17;
        for (BooleanSetting setting : module.getBooleanSettings()) if (setting.isVisible()) h += 18;
        for (NumberSetting setting : module.getNumberSettings()) if (setting.isVisible()) h += 27;
        for (MultiBooleanSetting setting : module.getMultiSettings()) {
            h += 14 + multiRows(setting, availableWidth) * 17;
        }
        return h + 5;
    }

    private int multiRows(MultiBooleanSetting setting, int availableWidth) {
        int rows = 1;
        int used = 0;
        for (String option : setting.getOptions()) {
            int chipWidth = Math.min(availableWidth, Math.max(34, textRenderer.getWidth(option) + 12));
            if (used > 0 && used + chipWidth > availableWidth) {
                rows++;
                used = 0;
            }
            used += chipWidth + 4;
        }
        return rows;
    }

    private void drawModule(DrawContext context, Category category, Module module,
                            int x, int y, int w, int mouseX, int mouseY) {
        boolean enabled = module.isEnabled();
        boolean expandedNow = expanded.contains(module);
        boolean hovered = inside(mouseX, mouseY, x, y, w, ROW_H);
        int bg = enabled ? ROW_ON : (hovered ? ROW_HOVER : ROW);
        Draw.rr(context, x, y, w, ROW_H, 5, bg);
        Draw.rr(context, x + 1, y + 6, 2, ROW_H - 12, 1, enabled ? ACCENT : 0xFF4B5062);
        Draw.rr(context, x + 7, y + 10, 6, 6, 3, enabled ? ACCENT_BLUE : FAINT);

        int switchX = x + w - 25;
        int bindW = 35;
        int bindX = switchX - bindW - 7;
        int dotsX = bindX - 15;
        int maxNameW = Math.max(30, dotsX - (x + 19) - 5);
        String name = trim(module.getName(), maxNameW);
        context.drawTextWithShadow(textRenderer, name, x + 19, y + 9, enabled ? TEXT : MUTED);

        Draw.rr(context, bindX, y + 6, bindW, 15, 4, 0xFF25283A);
        String bind = module.getBindName();
        context.drawCenteredTextWithShadow(textRenderer, trim(bind, bindW - 5), bindX + bindW / 2,
                y + 10, "NONE".equals(bind) ? FAINT : ACCENT_BLUE);
        context.drawTextWithShadow(textRenderer, expandedNow ? "v" : "...", dotsX, y + 9, ACCENT);
        Draw.rr(context, switchX, y + 8, 18, 10, 5, enabled ? ACCENT : TRACK);
        Draw.rr(context, switchX + (enabled ? 10 : 2), y + 10, 6, 6, 3, TEXT);

        hits.add(new Hit(x, y, w, ROW_H, module::toggle, () -> toggleExpanded(module), null));
        hits.add(new Hit(bindX, y + 5, bindW, 17, () -> startBind(module), null, null));
        hits.add(new Hit(dotsX - 3, y + 4, 17, 20, () -> toggleExpanded(module), null, null));

        if (expandedNow) {
            int settingY = y + ROW_H + 3;
            if (!module.getDescription().isBlank()) {
                String desc = trim(module.getDescription(), w - 18);
                context.drawTextWithShadow(textRenderer, desc, x + 7, settingY + 2, FAINT);
                settingY += 17;
            }

            for (BooleanSetting setting : module.getBooleanSettings()) {
                if (!setting.isVisible()) continue;
                context.drawTextWithShadow(textRenderer, trim(setting.getName(), w - 42), x + 7, settingY + 4, MUTED);
                int sx = x + w - 27;
                Draw.rr(context, sx, settingY + 4, 19, 10, 5, setting.isEnabled() ? ACCENT : TRACK);
                Draw.rr(context, sx + (setting.isEnabled() ? 10 : 2), settingY + 6, 6, 6, 3, TEXT);
                hits.add(new Hit(x + 3, settingY, w - 6, 18, setting::toggle, null, null));
                settingY += 18;
            }

            for (NumberSetting setting : module.getNumberSettings()) {
                if (!setting.isVisible()) continue;
                String label = trim(setting.getName(), w - 54);
                context.drawTextWithShadow(textRenderer, label, x + 7, settingY + 1, MUTED);
                String value = setting.getDisplayValue();
                context.drawTextWithShadow(textRenderer, value, x + w - textRenderer.getWidth(value) - 7,
                        settingY + 1, ACCENT_BLUE);
                int sx = x + 7, sy = settingY + 17, sw = w - 14;
                Draw.rr(context, sx, sy, sw, 4, 2, TRACK);
                int filled = (int) Math.round(sw * setting.getRatio());
                if (filled > 0) Draw.rr(context, sx, sy, Math.max(3, filled), 4, 2, ACCENT);
                Draw.rr(context, sx + filled - 4, sy - 2, 8, 8, 4, TEXT);
                hits.add(new Hit(sx, sy - 5, sw, 14, null, null, setting));
                settingY += 27;
            }

            for (MultiBooleanSetting setting : module.getMultiSettings()) {
                context.drawTextWithShadow(textRenderer, setting.getName(), x + 7, settingY, MUTED);
                settingY += 14;
                int chipX = x + 7;
                int rowY = settingY;
                for (String option : setting.getOptions()) {
                    int chipW = Math.min(w - 14, Math.max(34, textRenderer.getWidth(option) + 12));
                    if (chipX > x + 7 && chipX + chipW > x + w - 7) {
                        chipX = x + 7;
                        rowY += 17;
                    }
                    boolean selected = setting.isSelected(option);
                    Draw.rr(context, chipX, rowY, chipW, 14, 4,
                            selected ? 0xFF403775 : 0xFF25283A);
                    context.drawCenteredTextWithShadow(textRenderer, trim(option, chipW - 5),
                            chipX + chipW / 2, rowY + 3, selected ? TEXT : MUTED);
                    final String selectedOption = option;
                    hits.add(new Hit(chipX, rowY, chipW, 14,
                            () -> setting.toggle(selectedOption), null, null));
                    chipX += chipW + 4;
                }
                settingY += multiRows(setting, w - 14) * 17;
            }
        }
    }

    private String trim(String value, int maxWidth) {
        if (value == null) return "";
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String shortened = value;
        while (!shortened.isEmpty() && textRenderer.getWidth(shortened + "…") > maxWidth) {
            shortened = shortened.substring(0, shortened.length() - 1);
        }
        return shortened + "…";
    }

    private void drawConfigManager(DrawContext context, int mouseX, int mouseY) {
        int x = configX;
        int y = configY;
        int w = 164;
        int h = 58;
        Draw.rr(context, x - 2, y - 2, w + 4, h + 4, 9, 0x40000000);
        Draw.rr(context, x, y, w, h, 8, PANEL);
        context.drawTextWithShadow(textRenderer, "CONFIG MANAGER", x + 9, y + 7, TEXT);
        int bw = 68;
        Draw.rr(context, x + 8, y + 27, bw, 21, 5, 0xFF28243F);
        Draw.rr(context, x + 88, y + 27, bw, 21, 5, 0xFF242838);
        context.drawCenteredTextWithShadow(textRenderer, "SAVE", x + 8 + bw / 2, y + 34, ACCENT_BLUE);
        context.drawCenteredTextWithShadow(textRenderer, "RESET ALL", x + 88 + bw / 2, y + 34, MUTED);
        hits.add(new Hit(x + 8, y + 27, bw, 21, () -> {
            ConfigManager.save();
            notice = "Configuration saved";
        }, null, null));
        hits.add(new Hit(x + 88, y + 27, bw, 21, () -> {
            HudLayout.reset();
            MenuPanelLayout.reset();
            panelPositions.clear();
            ConfigManager.save();
            notice = "All layouts reset";
        }, null, null));
    }

    private void drawSearch(DrawContext context, int mouseX, int mouseY) {
        Draw.rr(context, searchX, searchY, searchW, 22, 6,
                searchFocused ? 0xF0202034 : 0xE010121C);
        Draw.rr(context, searchX + 1, searchY + 1, searchW - 2, 1, 0, searchFocused ? ACCENT : 0xFF2D3043);
        String value = search.isBlank() ? "Search modules..." : search;
        int color = search.isBlank() ? FAINT : TEXT;
        context.drawTextWithShadow(textRenderer, value, searchX + 9, searchY + 7, color);
        context.drawTextWithShadow(textRenderer, "CTRL+F", searchX + searchW - 44, searchY + 7, ACCENT_BLUE);
    }

    private void drawFooter(DrawContext context) {
        String hint = bindingModule != null
                ? "Press a key to bind  •  DEL clears  •  ESC cancels"
                : (notice != null ? notice : "LMB toggle  •  RMB settings  •  drag headers  •  click bind chip  •  T = HUD editor");
        int w = textRenderer.getWidth(hint);
        context.drawTextWithShadow(textRenderer, hint, Math.max(8, (width - w) / 2), height - 46,
                bindingModule == null ? MUTED : ACCENT);
    }

    private void toggleExpanded(Module module) {
        if (!expanded.remove(module)) expanded.add(module);
    }

    private void startBind(Module module) {
        bindingModule = module;
        searchFocused = false;
        notice = null;
    }

    private void updateSlider(Hit hit, double mouseX) {
        if (hit != null && hit.slider != null) hit.slider.setFromRatio((mouseX - hit.x) / Math.max(1.0, hit.w));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchFocused && !inside(mouseX, mouseY, searchX, searchY, searchW, 22)) searchFocused = false;
        if (inside(mouseX, mouseY, searchX, searchY, searchW, 22) && button == 0) {
            searchFocused = true;
            return true;
        }

        if (button == 0) {
            for (Category category : Category.values()) {
                int[] pos = panelPositions.get(category);
                if (pos != null && inside(mouseX, mouseY, pos[0], pos[1], panelW, 32)) {
                    draggingCategory = category;
                    panelDragOffsetX = (int) Math.round(mouseX) - pos[0];
                    panelDragOffsetY = (int) Math.round(mouseY) - pos[1];
                    return true;
                }
            }
        }

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mouseX, mouseY)) continue;
            if (button == 0 && hit.slider != null) {
                draggingSlider = hit.slider;
                activeSlider = hit;
                updateSlider(hit, mouseX);
                return true;
            }
            Runnable action = button == 0 ? hit.left : (button == 1 ? hit.right : null);
            if (action != null) {
                action.run();
                ConfigManager.save();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingCategory != null) {
            int x = (int) Math.round(mouseX) - panelDragOffsetX;
            int y = (int) Math.round(mouseY) - panelDragOffsetY;
            int safeX = clamp(x, 4, Math.max(4, width - panelW - 4));
            int safeY = clamp(y, 28, Math.max(28, height - panelH - 48));
            panelPositions.put(draggingCategory, new int[]{safeX, safeY});
            MenuPanelLayout.setPosition(draggingCategory, safeX, safeY);
            return true;
        }
        if (button == 0 && draggingSlider != null && activeSlider != null) {
            updateSlider(activeSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingCategory != null) {
            ConfigManager.save();
            draggingCategory = null;
            return true;
        }
        if (button == 0 && draggingSlider != null) {
            draggingSlider = null;
            activeSlider = null;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Category category : Category.values()) {
            int[] pos = panelPositions.get(category);
            if (pos != null && inside(mouseX, mouseY, pos[0], pos[1], panelW, panelH)) {
                int max = maxScrolls.getOrDefault(category, 0);
                int scroll = scrolls.getOrDefault(category, 0);
                scrolls.put(category, clamp((int) Math.round(scroll - verticalAmount * 24), 0, max));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && bindingModule == null && !Character.isISOControl(chr)) {
            if (search.length() < 32) search += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingModule != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                bindingModule = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String name = bindingModule.getName();
                bindingModule.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                bindingModule = null;
                notice = name + " key cleared";
                ConfigManager.save();
                return true;
            }
            if (keyCode >= 0) {
                Module changed = bindingModule;
                boolean clearedConflict = false;
                for (Module other : ModuleManager.getModules()) {
                    if (other != changed && other.getKeyCode() == keyCode) {
                        other.setKeyCode(GLFW.GLFW_KEY_UNKNOWN);
                        clearedConflict = true;
                    }
                }
                changed.setKeyCode(keyCode);
                notice = changed.getName() + " -> " + changed.getBindName()
                        + (clearedConflict ? " (conflict cleared)" : "");
                bindingModule = null;
                ConfigManager.save();
                return true;
            }
        }

        if (searchFocused && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
            return true;
        }
        if (searchFocused && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            searchFocused = false;
            return true;
        }
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && keyCode == GLFW.GLFW_KEY_F) {
            searchFocused = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_T && !searchFocused) {
            client.setScreen(new ChatScreen(""));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        draggingSlider = null;
        activeSlider = null;
        if (draggingCategory != null) ConfigManager.save();
        draggingCategory = null;
        bindingModule = null;
        ConfigManager.save();
        super.removed();
    }
}
