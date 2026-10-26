package io.github.wdy.easytouch;

import java.io.File;
import io.github.wdy.easytouch.matching.LegacyWhitelistMigration;
import io.github.wdy.easytouch.matching.MatcherRegistry;
import net.minecraftforge.common.config.Configuration;

public final class TouchConfig {
    private final Configuration file;
    public boolean enabled;
    public final boolean hud;
    public final boolean touchEnabled;
    public final boolean touchLogEvents;
    public final MatcherRegistry matchers;

    public TouchConfig(File path) {
        file = new Configuration(path);
        file.load();
        enabled = file.getBoolean("enabled", "general", true, "Enable adaptive mouse buttons.");
        hud = file.getBoolean("showHud", "general", true, "Show Interact near the crosshair when aiming at a whitelisted target.");
        touchEnabled = file.getBoolean("enabled", "touch", true,
            "Forward native SDL3 touch events. Disabling forwarding does not remove the lwjgl3ify/SDL3 requirement.");
        touchLogEvents = file.getBoolean("logEvents", "touch", false,
            "Log touch down/up/cancel/reset events for diagnostics. Motion events are not logged.");
        LegacyWhitelistMigration.apply(file);
        matchers = MatcherRegistry.builtIns(file);
        if (file.hasCategory("rules")) {
            file.addCustomCategoryComment("rules", "Legacy 0.1.0 rules are ignored. Edit matchers.* instead."
                + " Held items no longer cause swapping. Entries are preserved here for reference only.");
        }
        file.save();
    }

    public void toggle() {
        enabled = !enabled;
        file.get("general", "enabled", true).set(enabled);
        file.save();
    }

}
