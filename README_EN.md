# MiniAssist 1.5.0 — Fabric 1.21.4

Client-side Fabric mod for Minecraft 1.21.4 with a multi-column ClickGUI, draggable HUD editor, editable binds and compact panels inspired by the Nursultan 1.16.5 visual style. Nursultan is only a visual reference; its code and assets are not copied.

## ClickGUI

- **Right Shift** opens/closes the ClickGUI.
- Five category columns: Combat, Movement, Visuals, Player and Miscellaneous.
- Drag columns by their headers; positions persist between sessions.
- **Left-click a module row** to toggle it; **right-click a row** or click `...` to expand settings.
- Drag sliders to change numeric settings; click option chips to toggle them.
- Click a bind chip, then press a key. **Delete/Backspace** clears it; **Esc** cancels capture.
- Search filters modules. Click the search box or press **Ctrl+F**.
- Config Manager: `SAVE` writes the config; `RESET ALL` restores HUD and column positions.
- Settings, binds and interface positions are stored in `config/miniassist.properties`.

## HUD editor

- Press **T** to open the vanilla Minecraft chat. The HUD Editor appears over the game while chat is open.
- Drag any visible HUD panel with **left mouse button**; release to save its position.
- The top editor bar contains **HUD ON/OFF**, **BINDS ON/OFF** and **RESET** controls.
- The keybind list can be hidden from the editor bar or ClickGUI: Visuals → HUD → `Список биндов`.
- **G** hides/shows the complete custom HUD.
- Closing chat exits HUD editing.

HUD panels include watermark/FPS/ping, active modules, module binds, coordinates/speed/direction, armor durability, status effects, Target HUD and Keystrokes. Each panel can be toggled in Visuals → HUD and its position is saved.

## Modules

- **Aim Assist (F6)** — smooth local view assistance toward a selected visible target, with FOV, range and speed settings; it does not target through walls.
- **TriggerBot (F7)** — checks the crosshair target per game tick and respects vanilla attack cooldown plus an optional delay in ticks.
- **FreeCam (F8)** — detaches the camera, uses the user's Minecraft movement bindings and restores state on disable/disconnect.
- **HUD (G)** — configurable interface panels.

F6/F7/F8 can be reassigned in ClickGUI. G is reserved for hiding/showing the HUD. Multiplayer servers may restrict combat-assistance mods; follow server rules.

## GitHub Actions build

1. Upload the contents of this ZIP to the repository root. The root must contain `build.gradle`, `gradle.properties`, `settings.gradle`, `src/` and `.github/workflows/build.yml`.
2. Commit/push to any branch.
3. Open **Actions → MiniAssist Build**.
4. On success, download **Artifacts → MiniAssist-1.5.0** and extract `zenith-client-1.5.0.jar`.
5. On failure, download `MiniAssist-build-log` if it was produced and use it for diagnosis.

The workflow provisions Temurin Java 21 and Gradle 8.12. Build versions: Fabric Loom 1.10.5, Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric Loader 0.16.9 and Fabric API `0.112.2+1.21.4`. A local Gradle Wrapper is not required.

## Note

This project targets **Minecraft 1.21.4**; it is not a 1.16.5 port. The UI is an original implementation based on the category-column layout and compact dark HUD panels shown in the supplied reference screenshot.
