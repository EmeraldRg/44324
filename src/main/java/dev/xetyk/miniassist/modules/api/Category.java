package dev.xetyk.miniassist.modules.api;

/** Category columns used by the multi-panel ClickGUI. */
public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    RENDER("Visuals"),
    PLAYER("Player"),
    MISCELLANEOUS("Miscellaneous");

    public final String title;
    Category(String title) { this.title = title; }
}
