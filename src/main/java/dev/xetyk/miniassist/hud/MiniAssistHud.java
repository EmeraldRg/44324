package dev.xetyk.miniassist.hud;

import dev.xetyk.miniassist.ClientKeybinds;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.impl.combat.AimAssist;
import dev.xetyk.miniassist.modules.impl.render.ClientHudModule;
import dev.xetyk.miniassist.util.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Draggable, configurable HUD inspired by classic multi-panel PvP clients. */
public final class MiniAssistHud {
    private static final int PANEL = 0xD20E1018;
    private static final int PANEL_ALT = 0xE0191A28;
    private static final int ACCENT = 0xFF9A83FF;
    private static final int ACCENT_BLUE = 0xFF62D5FF;
    private static final int TEXT = 0xFFF2F4FA;
    private static final int MUTED = 0xFF9AA6B9;
    private static final int FAINT = 0xFF68768B;
    private static final int TRACK = 0xFF2A2A3A;
    private static float targetHealthAnim = -1f;

    public record HudWidget(String id, String title, int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private MiniAssistHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        HudEditor.init();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientKeybinds.isHidden() || client.options.hudHidden || client.player == null || client.world == null) return;

        Module module = ModuleManager.get("HUD");
        if (!(module instanceof ClientHudModule hud) || !hud.isEnabled()) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        if (width < 180 || height < 100) return;

        List<HudWidget> widgets = getWidgets(client);
        for (HudWidget widget : widgets) {
            switch (widget.id()) {
                case "watermark" -> drawWatermark(context, client, widget);
                case "active_modules" -> drawActiveModules(context, client, widget);
                case "keybinds" -> drawKeybinds(context, client, widget);
                case "effects" -> drawEffects(context, client, widget);
                case "coordinates" -> drawCoordinates(context, client, widget);
                case "armor" -> drawArmor(context, client, widget);
                case "keystrokes" -> drawKeystrokes(context, client, widget);
                case "target" -> drawTarget(context, client, widget);
                default -> { }
            }
        }
    }

    /** Exact visible widget bounds, also used by the chat HUD editor for hit testing. */
    public static List<HudWidget> getWidgets(MinecraftClient client) {
        List<HudWidget> out = new ArrayList<>();
        if (client == null || client.player == null || client.world == null
                || ClientKeybinds.isHidden() || client.options.hudHidden) return out;
        Module module = ModuleManager.get("HUD");
        if (!(module instanceof ClientHudModule hud) || !hud.isEnabled()) return out;

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        var tr = client.textRenderer;

        if (hud.showWatermark()) {
            String line = watermarkLine(client);
            int w = Math.min(sw - 12, Math.max(92, tr.getWidth(line) + 28));
            add(out, "watermark", "WATERMARK", 6, 6, w, 20, sw, sh);
        }

        List<Module> active = activeModules();
        int activeW = active.stream().mapToInt(m -> tr.getWidth(m.getName())).max().orElse(42) + 22;
        int activeH = Math.max(16, Math.min(10, active.size()) * 16);
        if (hud.showActiveModules() && !active.isEmpty()) {
            add(out, "active_modules", "ACTIVE MODULES", sw - activeW - 6, 6,
                    activeW, activeH, sw, sh);
        }

        List<Module> bound = boundModules();
        int keyW = bound.stream().mapToInt(m -> tr.getWidth(m.getName()) + tr.getWidth(m.getBindName())).max().orElse(80) + 28;
        keyW = Math.min(190, Math.max(112, keyW));
        int keyH = 18 + Math.min(8, bound.size()) * 14;
        if (hud.showKeybinds() && !bound.isEmpty()) {
            int keyY = hud.showActiveModules() && !active.isEmpty() ? 12 + activeH : 6;
            add(out, "keybinds", "KEYBINDS", sw - keyW - 6, keyY, keyW, keyH, sw, sh);
        }

        List<StatusEffectInstance> effects = effectList(client);
        if (hud.showEffects() && !effects.isEmpty() && sh >= 220) {
            int effectY = hud.showWatermark() ? 32 : 6;
            add(out, "effects", "EFFECTS", 7, effectY, 160,
                    19 + effects.size() * 14, sw, sh);
        }

        if (hud.showCoordinates()) {
            int w = coordinatesWidth(client);
            add(out, "coordinates", "COORDINATES", 6, sh - 34, w, 28, sw, sh);
        }

        if (hud.showArmor() && (sw >= 460 || sh >= 190)) {
            int w = 4 * 22 + 12;
            int y = sw < 460 ? sh - 94 : sh - 48;
            add(out, "armor", "ARMOR", (sw - w) / 2, y, w, 38, sw, sh);
        }

        if (hud.showKeystrokes() && sw >= 280 && sh >= 180) {
            add(out, "keystrokes", "KEYSTROKES", sw - 111, sh - 135,
                    104, 70, sw, sh);
        }

        if (hud.showTarget() && sw >= 360 && findHudTarget(client) != null) {
            add(out, "target", "TARGET HUD", sw / 2 + 24, sh / 2 + 20,
                    130, 36, sw, sh);
        }
        return out;
    }

    private static void add(List<HudWidget> list, String id, String title,
                            int defaultX, int defaultY, int width, int height,
                            int screenWidth, int screenHeight) {
        HudLayout.Position pos = HudLayout.resolve(id, defaultX, defaultY, width, height, screenWidth, screenHeight);
        list.add(new HudWidget(id, title, pos.x(), pos.y(), width, height));
    }

    private static String watermarkLine(MinecraftClient client) {
        String user = client.getSession().getUsername();
        String fps = client.getCurrentFps() + " fps";
        String ping = getLocalPing(client);
        return "MiniAssist  |  " + user + "  |  " + fps + "  |  " + ping;
    }

    private static List<Module> activeModules() {
        List<Module> active = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module.isEnabled() && !module.getName().equalsIgnoreCase("HUD")) active.add(module);
        }
        active.sort(Comparator.comparingInt((Module m) -> MinecraftClient.getInstance().textRenderer.getWidth(m.getName())).reversed());
        return active;
    }

    private static List<Module> boundModules() {
        List<Module> bound = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (!module.getName().equalsIgnoreCase("HUD") && !"NONE".equalsIgnoreCase(module.getBindName())) {
                bound.add(module);
            }
        }
        bound.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        return bound;
    }

    private static List<StatusEffectInstance> effectList(MinecraftClient client) {
        return client.player.getStatusEffects().stream()
                .sorted(Comparator.comparing(effect -> effect.getEffectType().value().getName().getString()))
                .limit(5)
                .toList();
    }

    private static int coordinatesWidth(MinecraftClient client) {
        var tr = client.textRenderer;
        var player = client.player;
        double speed = Math.sqrt(player.getVelocity().x * player.getVelocity().x
                + player.getVelocity().z * player.getVelocity().z) * 20.0;
        String xyz = String.format(Locale.ROOT, "%.0f, %.0f, %.0f", player.getX(), player.getY(), player.getZ());
        String bps = String.format(Locale.ROOT, "%.1f", speed);
        return Math.max(tr.getWidth("XYZ  " + xyz), tr.getWidth("BPS  " + bps + "   " + direction(player.getYaw()))) + 14;
    }

    private static void drawWatermark(DrawContext context, MinecraftClient client, HudWidget box) {
        panel(context, box.x(), box.y(), box.width(), box.height());
        Draw.rr(context, box.x() + 4, box.y() + 5, 10, 10, 3, ACCENT);
        String line = trimToWidth(client, watermarkLine(client), box.width() - 28);
        context.drawTextWithShadow(client.textRenderer, line, box.x() + 19, box.y() + 6, TEXT);
    }

    private static void drawActiveModules(DrawContext context, MinecraftClient client, HudWidget box) {
        List<Module> active = activeModules();
        var tr = client.textRenderer;
        long time = System.currentTimeMillis();
        for (int i = 0; i < active.size() && i < 10; i++) {
            Module module = active.get(i);
            int rowY = box.y() + i * 16;
            float wave = (float) (Math.sin(time / 700.0 + i * 0.6) * 0.5 + 0.5);
            int color = Draw.mix(ACCENT, ACCENT_BLUE, wave);
            Draw.rr(context, box.x(), rowY, box.width(), 14, 4, PANEL);
            Draw.rr(context, box.x() + box.width() - 2, rowY + 3, 2, 8, 1, color);
            context.drawTextWithShadow(tr, module.getName(), box.x() + 6, rowY + 3, color);
        }
    }

    private static void drawKeybinds(DrawContext context, MinecraftClient client, HudWidget box) {
        var tr = client.textRenderer;
        panel(context, box.x(), box.y(), box.width(), box.height());
        context.drawTextWithShadow(tr, "KEYBINDS", box.x() + 7, box.y() + 5, ACCENT);
        int rowY = box.y() + 19;
        List<Module> bound = boundModules();
        for (int i = 0; i < bound.size() && i < 8; i++) {
            Module module = bound.get(i);
            int dot = module.isEnabled() ? ACCENT_BLUE : FAINT;
            Draw.rr(context, box.x() + 7, rowY + 3, 5, 5, 2, dot);
            String name = trimToWidth(client, module.getName(), box.width() - 62);
            context.drawTextWithShadow(tr, name, box.x() + 17, rowY + 1, module.isEnabled() ? TEXT : MUTED);
            String bind = trimToWidth(client, module.getBindName(), 38);
            int bw = tr.getWidth(bind);
            context.drawTextWithShadow(tr, bind, box.x() + box.width() - bw - 7, rowY + 1, ACCENT_BLUE);
            rowY += 14;
        }
    }

    private static void drawEffects(DrawContext context, MinecraftClient client, HudWidget box) {
        var tr = client.textRenderer;
        List<StatusEffectInstance> effects = effectList(client);
        panel(context, box.x(), box.y(), box.width(), box.height());
        context.drawTextWithShadow(tr, "EFFECTS", box.x() + 7, box.y() + 5, ACCENT);
        int rowY = box.y() + 19;
        for (StatusEffectInstance effect : effects) {
            String name = effect.getEffectType().value().getName().getString();
            String level = effect.getAmplifier() > 0 ? " " + (effect.getAmplifier() + 1) : "";
            String duration = StatusEffectUtil.getDurationText(effect, 1.0F, 20.0F).getString();
            String safeDuration = trimToWidth(client, duration, box.width() - 46);
            int durationW = tr.getWidth(safeDuration);
            int maxNameWidth = Math.max(24, box.width() - 21 - durationW);
            String left = trimToWidth(client, name + level, maxNameWidth);
            context.drawTextWithShadow(tr, left, box.x() + 7, rowY, TEXT);
            context.drawTextWithShadow(tr, safeDuration, box.x() + box.width() - durationW - 7, rowY, MUTED);
            rowY += 14;
        }
    }

    private static void drawCoordinates(DrawContext context, MinecraftClient client, HudWidget box) {
        var tr = client.textRenderer;
        var player = client.player;
        double speed = Math.sqrt(player.getVelocity().x * player.getVelocity().x
                + player.getVelocity().z * player.getVelocity().z) * 20.0;
        String xyz = String.format(Locale.ROOT, "%.0f, %.0f, %.0f", player.getX(), player.getY(), player.getZ());
        String bps = String.format(Locale.ROOT, "%.1f", speed);
        String dir = direction(player.getYaw());
        panel(context, box.x(), box.y(), box.width(), box.height());
        context.drawTextWithShadow(tr, "XYZ", box.x() + 7, box.y() + 5, ACCENT);
        context.drawTextWithShadow(tr, xyz, box.x() + 7 + tr.getWidth("XYZ  "), box.y() + 5, TEXT);
        context.drawTextWithShadow(tr, "BPS", box.x() + 7, box.y() + 16, ACCENT);
        context.drawTextWithShadow(tr, bps, box.x() + 7 + tr.getWidth("BPS  "), box.y() + 16, TEXT);
        context.drawTextWithShadow(tr, dir, box.x() + 7 + tr.getWidth("BPS  " + bps + "   "), box.y() + 16, MUTED);
    }

    private static String direction(float yaw) {
        float angle = MathHelper.wrapDegrees(yaw);
        if (angle >= -45.0f && angle < 45.0f) return "S";
        if (angle >= 45.0f && angle < 135.0f) return "W";
        if (angle >= -135.0f && angle < -45.0f) return "E";
        return "N";
    }

    private static void drawArmor(DrawContext context, MinecraftClient client, HudWidget box) {
        List<ItemStack> armor = new ArrayList<>();
        for (ItemStack stack : client.player.getArmorItems()) armor.add(stack);
        java.util.Collections.reverse(armor);
        panel(context, box.x(), box.y(), box.width(), box.height());
        for (int i = 0; i < armor.size(); i++) {
            ItemStack stack = armor.get(i);
            int sx = box.x() + 7 + i * 22;
            if (stack.isEmpty()) {
                Draw.rr(context, sx + 2, box.y() + 9, 16, 16, 3, 0x55313B4D);
                continue;
            }
            context.drawItem(stack, sx + 1, box.y() + 4);
            context.drawStackOverlay(client.textRenderer, stack, sx + 1, box.y() + 4);
            if (stack.isDamageable()) {
                double durability = 1.0 - (double) stack.getDamage() / Math.max(1, stack.getMaxDamage());
                int color = durability > 0.5 ? 0xFF70D7A2 : (durability > 0.2 ? 0xFFFFC56B : 0xFFFF6B83);
                Draw.rr(context, sx + 2, box.y() + 32, 16, 2, 1, TRACK);
                Draw.rr(context, sx + 2, box.y() + 32, Math.max(2, (int) Math.round(16 * durability)), 2, 1, color);
            }
        }
    }

    private static void drawKeystrokes(DrawContext context, MinecraftClient client, HudWidget box) {
        panel(context, box.x(), box.y(), box.width(), box.height());
        context.drawTextWithShadow(client.textRenderer, "KEYSTROKES", box.x() + 7, box.y() + 5, ACCENT);
        drawKey(context, client, "W", box.x() + 41, box.y() + 18, client.options.forwardKey.isPressed());
        drawKey(context, client, "A", box.x() + 17, box.y() + 34, client.options.leftKey.isPressed());
        drawKey(context, client, "S", box.x() + 41, box.y() + 34, client.options.backKey.isPressed());
        drawKey(context, client, "D", box.x() + 65, box.y() + 34, client.options.rightKey.isPressed());
        drawWideKey(context, client, "JUMP", box.x() + 8, box.y() + 52, 40, client.options.jumpKey.isPressed());
        drawMouseKey(context, client, "LMB", box.x() + 51, box.y() + 52, client.options.attackKey.isPressed());
        drawMouseKey(context, client, "RMB", box.x() + 77, box.y() + 52, client.options.useKey.isPressed());
    }

    private static void drawKey(DrawContext context, MinecraftClient client, String label,
                                int x, int y, boolean pressed) {
        Draw.rr(context, x, y, 22, 14, 4, pressed ? 0xFF4A3F8F : 0xFF1E1E2C);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + 11, y + 3,
                pressed ? TEXT : MUTED);
    }

    private static void drawWideKey(DrawContext context, MinecraftClient client, String label,
                                    int x, int y, int width, boolean pressed) {
        Draw.rr(context, x, y, width, 12, 4, pressed ? 0xFF4A3F8F : 0xFF1E1E2C);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + width / 2, y + 2,
                pressed ? ACCENT_BLUE : MUTED);
    }

    private static void drawMouseKey(DrawContext context, MinecraftClient client, String label,
                                     int x, int y, boolean pressed) {
        Draw.rr(context, x, y, 22, 12, 4, pressed ? 0xFF4A3F8F : 0xFF1E1E2C);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + 11, y + 2,
                pressed ? ACCENT_BLUE : MUTED);
    }

    private static void drawTarget(DrawContext context, MinecraftClient client, HudWidget box) {
        LivingEntity target = findHudTarget(client);
        if (target == null || !target.isAlive()) { targetHealthAnim = -1f; return; }
        var tr = client.textRenderer;
        panel(context, box.x(), box.y(), box.width(), box.height());
        float ratio = target.getMaxHealth() <= 0.0f ? 0.0f
                : MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0.0f, 1.0f);
        targetHealthAnim = targetHealthAnim < 0 ? ratio : targetHealthAnim + (ratio - targetHealthAnim) * 0.15f;
        String healthText = String.format(Locale.ROOT, "%.1f", target.getHealth());
        int hw = tr.getWidth(healthText);
        String name = trimToWidth(client, target.getDisplayName().getString(), box.width() - 20 - hw);
        context.drawTextWithShadow(tr, name, box.x() + 7, box.y() + 6, TEXT);
        context.drawTextWithShadow(tr, healthText, box.x() + box.width() - hw - 7, box.y() + 6, MUTED);
        int barX = box.x() + 7, barY = box.y() + 19, barW = box.width() - 14;
        Draw.rr(context, barX, barY, barW, 5, 2, TRACK);
        int fw = Math.round(barW * targetHealthAnim);
        if (fw > 0) Draw.rr(context, barX, barY, Math.max(4, fw), 5, 2,
                Draw.mix(0xFFFF6B83, 0xFF67D99A, targetHealthAnim));
        String meta = String.format(Locale.ROOT, "%.1fm", client.player.distanceTo(target));
        if (target instanceof PlayerEntity targetPlayer && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(targetPlayer.getUuid());
            if (entry != null) meta += "   " + entry.getLatency() + "ms";
        }
        context.drawTextWithShadow(tr, trimToWidth(client, meta, box.width() - 14), box.x() + 7, box.y() + 27, FAINT);
    }

    private static LivingEntity findHudTarget(MinecraftClient client) {
        Module module = ModuleManager.get("Aim Assist");
        if (module instanceof AimAssist aimAssist && aimAssist.isEnabled()) {
            LivingEntity target = aimAssist.getLockedTarget();
            if (target != null && target.isAlive()) return target;
        }
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living && living != client.player) return living;
        return null;
    }

    private static String getLocalPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) return "singleplayer";
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? "-- ms" : entry.getLatency() + " ms";
    }

    public static String trimToWidth(MinecraftClient client, String value, int maxWidth) {
        if (client.textRenderer.getWidth(value) <= maxWidth) return value;
        String suffix = "…";
        String result = value;
        while (!result.isEmpty() && client.textRenderer.getWidth(result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private static void panel(DrawContext context, int x, int y, int width, int height) {
        Draw.rr(context, x - 1, y - 1, width + 2, height + 2, 5, 0x45000000);
        Draw.rr(context, x, y, width, height, 4, PANEL);
        Draw.rr(context, x + 5, y, Math.max(4, width - 10), 1, 0, 0x338F7CFF);
    }
}
