package dev.xetyk.miniassist.config;

import dev.xetyk.miniassist.gui.MenuPanelLayout;
import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import dev.xetyk.miniassist.hud.HudLayout;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent user settings; writes atomically so an interrupted save cannot wipe the config. */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("MiniAssist/Config");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("miniassist.properties");
    private static final Path TEMP_FILE = FILE.resolveSibling("miniassist.properties.tmp");

    private ConfigManager() {}

    public static void load() {
        if (!Files.exists(FILE)) return;
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.warn("Could not read MiniAssist config; defaults will be used", exception);
            return;
        }

        for (String widgetId : HudLayout.WIDGET_IDS) {
            String base = "hud." + widgetId;
            String x = props.getProperty(base + ".x");
            String y = props.getProperty(base + ".y");
            if (x != null && y != null) {
                try { HudLayout.setPosition(widgetId, Integer.parseInt(x), Integer.parseInt(y)); }
                catch (NumberFormatException ignored) { /* retain default position */ }
            }
        }

        for (var category : dev.xetyk.miniassist.modules.api.Category.values()) {
            String base = "gui." + category.name().toLowerCase(java.util.Locale.ROOT);
            String x = props.getProperty(base + ".x");
            String y = props.getProperty(base + ".y");
            if (x != null && y != null) {
                try { MenuPanelLayout.setPosition(category, Integer.parseInt(x), Integer.parseInt(y)); }
                catch (NumberFormatException ignored) { /* retain default position */ }
            }
        }

        for (Module module : ModuleManager.getModules()) {
            String base = "m." + module.getName();
            String bind = props.getProperty(base + ".key");
            if (bind != null) {
                try { module.setKeyCode(Integer.parseInt(bind)); }
                catch (NumberFormatException ignored) { /* keep module default */ }
            }

            for (BooleanSetting setting : module.getBooleanSettings()) {
                String value = props.getProperty(base + ".b." + setting.getName());
                if (value != null) setting.setEnabled(Boolean.parseBoolean(value));
            }
            for (NumberSetting setting : module.getNumberSettings()) {
                String value = props.getProperty(base + ".n." + setting.getName());
                if (value != null) {
                    try { setting.setValue(Double.parseDouble(value)); }
                    catch (NumberFormatException ignored) { /* retain default */ }
                }
            }
            for (MultiBooleanSetting multi : module.getMultiSettings()) {
                for (String option : multi.getOptions()) {
                    String value = props.getProperty(base + ".x." + multi.getName() + "." + option);
                    if (value != null) multi.set(option, Boolean.parseBoolean(value));
                }
            }

            String enabled = props.getProperty(base + ".enabled");
            if (enabled != null) {
                try {
                    module.setEnabled(Boolean.parseBoolean(enabled));
                } catch (RuntimeException exception) {
                    LOGGER.error("Could not restore module {} from config; it remains disabled", module.getName(), exception);
                }
            }
        }
    }

    public static void save() {
        Properties props = new Properties();
        for (Module module : ModuleManager.getModules()) {
            String base = "m." + module.getName();
            props.setProperty(base + ".enabled", String.valueOf(module.isEnabled()));
            props.setProperty(base + ".key", Integer.toString(module.getKeyCode()));
            for (BooleanSetting setting : module.getBooleanSettings()) {
                props.setProperty(base + ".b." + setting.getName(), String.valueOf(setting.isEnabled()));
            }
            for (NumberSetting setting : module.getNumberSettings()) {
                props.setProperty(base + ".n." + setting.getName(), Double.toString(setting.getValue()));
            }
            for (MultiBooleanSetting multi : module.getMultiSettings()) {
                for (String option : multi.getOptions()) {
                    props.setProperty(base + ".x." + multi.getName() + "." + option,
                            String.valueOf(multi.isSelected(option)));
                }
            }
        }

        for (var entry : HudLayout.snapshot().entrySet()) {
            String base = "hud." + entry.getKey();
            props.setProperty(base + ".x", Integer.toString(entry.getValue().x()));
            props.setProperty(base + ".y", Integer.toString(entry.getValue().y()));
        }
        for (var entry : MenuPanelLayout.snapshot().entrySet()) {
            String base = "gui." + entry.getKey();
            props.setProperty(base + ".x", Integer.toString(entry.getValue().x()));
            props.setProperty(base + ".y", Integer.toString(entry.getValue().y()));
        }

        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(TEMP_FILE, StandardCharsets.UTF_8)) {
                props.store(writer, "MiniAssist client config");
            }
            try {
                Files.move(TEMP_FILE, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveNotSupported) {
                Files.move(TEMP_FILE, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            // A config write failure should never crash the client, but keep it diagnosable.
            LOGGER.warn("Could not save MiniAssist config", exception);
        }
    }
}
