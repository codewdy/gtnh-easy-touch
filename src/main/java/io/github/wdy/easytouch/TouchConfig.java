package io.github.wdy.easytouch;

import java.io.File;
import io.github.wdy.easytouch.matching.LegacyWhitelistMigration;
import io.github.wdy.easytouch.matching.MatcherRegistry;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class TouchConfig {
    private final Configuration file;
    public final File layoutFile;
    public boolean enabled;
    public boolean buttonsVisible;
    public final boolean hud;
    public final boolean touchEnabled;
    public final boolean touchLogEvents;
    public final float touchDragSensitivity;
    public final MatcherRegistry matchers;

    public TouchConfig(File path) {
        layoutFile = new File(path.getParentFile(), "gtnheasytouch-layout.json");
        file = new Configuration(path);
        file.load();
        enabled = file.getBoolean("enabled", "general", true, "Enable smart target actions for native touch clicks and holds. Physical mouse buttons are unchanged.");
        buttonsVisible = file.getBoolean("showButtons", "touch", true, "Show custom touch buttons and the movement wheel. The global menu stays visible.");
        hud = file.getBoolean("showHud", "general", true, "Show Interact near the crosshair when aiming at a whitelisted target.");
        touchEnabled = file.getBoolean("enabled", "touch", true,
            "Handle native SDL3 touch gestures and GUI mouse emulation. Disabling forwarding does not remove the lwjgl3ify/SDL3 requirement.");
        touchLogEvents = file.getBoolean("logEvents", "touch", false,
            "Log touch down/up/cancel/reset events for diagnostics. Motion events are not logged.");
        Property dragSensitivity = file.get("touch", "dragSensitivity", 1.0,
            "Touch camera drag sensitivity multiplier (0.1 to 5.0). 1.0 keeps the default speed; "
                + "0.5 halves it; 2.0 doubles it. Multiplies Minecraft mouse sensitivity. "
                + "Does not affect physical mouse, GUI touch or gesture thresholds. Restart to apply.", 0.1, 5.0);
        double sensitivity = dragSensitivity.getDouble(1.0);
        if (!Double.isFinite(sensitivity)) sensitivity = 1.0;
        sensitivity = Math.max(0.1, Math.min(5.0, sensitivity));
        touchDragSensitivity = (float) sensitivity;
        dragSensitivity.set(sensitivity);
        LegacyWhitelistMigration.apply(file);
        matchers = MatcherRegistry.builtIns(file);
        if (file.hasCategory("rules")) {
            file.addCustomCategoryComment("rules", "Legacy 0.1.0 rules are ignored. Edit matchers.* instead."
                + " Held items no longer cause swapping. Entries are preserved here for reference only.");
        }
        file.save();
    }

    public void toggleButtons() {
        buttonsVisible = !buttonsVisible;
        file.get("touch", "showButtons", true).set(buttonsVisible);
        file.save();
    }

    public void toggle() {
        enabled = !enabled;
        file.get("general", "enabled", true).set(enabled);
        file.save();
    }

}
