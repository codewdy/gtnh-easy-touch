package io.github.wdy.easytouch;

import java.io.File;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class TouchConfig {
    private final Configuration file;
    public final File layoutFile;
    public boolean buttonsVisible;
    public boolean autoStep;
    public boolean autoTool;
    public boolean optimizeTooltip;
    public boolean hideTooltips;
    public final boolean touchEnabled;
    public final boolean touchLogEvents;
    public final float touchDragSensitivity;
    public final boolean nativeTextInput;

    public TouchConfig(File path) {
        layoutFile = new File(path.getParentFile(), "gtnheasytouch-layout.json");
        file = new Configuration(path);
        file.load();
        hideTooltips = file.getBoolean("hideTooltips", "touch", false, "Hide native and Chromatic tooltips.");
        optimizeTooltip = file.getBoolean("optimizeTooltip", "touch", true,
            "Dock native tooltips in the lower half of the NEI right column and enable touch scrolling.");
        buttonsVisible = file.getBoolean("showButtons", "touch", true, "Show custom touch buttons and the movement wheel. The global menu stays visible.");
        autoStep = file.getBoolean("autoStep", "movement", false,
            "Choose step height from upcoming collision surfaces, up to 1.25 blocks on land and in water, with clear rise and landing space. Water stepping follows vanilla support/collision rules and allows swimming up. Sneaking pauses auto step. Applies to keyboard and touch movement.");
        autoTool = file.getBoolean("autoTool", "mining", true,
            "Automatically select a hotbar tool when mining. Prefer harvest capability, then mining speed.");
        io.github.wdy.easytouch.mining.EzMinerAutoVein.enabled = file.getBoolean("ezMinerAutoGtVein", "mining", true,
            "In EZMiner basic chain mode, use GT vein blast search for GT large ore targets. Applies to preview and harvesting; other modes stay manual.");
        touchEnabled = file.getBoolean("enabled", "touch", true,
            "Handle native SDL3 touch gestures and GUI mouse emulation. Disabling forwarding does not remove the lwjgl3ify/SDL3 requirement.");
        touchLogEvents = file.getBoolean("logEvents", "touch", false,
            "Log touch down/up/cancel/reset events for diagnostics. Motion events are not logged.");
        nativeTextInput = file.getBoolean("nativeTextInput", "text", true,
            "On Windows x64, expose focused Minecraft text fields and their actual bounds through UI Automation for streaming IME detection. "
                + "Works independently of touch input. Supports vanilla, NEI and ModularUI 1/2 single-line fields. Restart to apply.");
        Property dragSensitivity = file.get("touch", "dragSensitivity", 1.0,
            "Touch camera drag sensitivity multiplier (0.1 to 5.0). 1.0 keeps the default speed; "
                + "0.5 halves it; 2.0 doubles it. Multiplies Minecraft mouse sensitivity. "
                + "Does not affect physical mouse, GUI touch or gesture thresholds. Restart to apply.", 0.1, 5.0);
        double sensitivity = dragSensitivity.getDouble(1.0);
        if (!Double.isFinite(sensitivity)) sensitivity = 1.0;
        sensitivity = Math.max(0.1, Math.min(5.0, sensitivity));
        touchDragSensitivity = (float) sensitivity;
        dragSensitivity.set(sensitivity);
        Property mapScale = file.get("map", "journeyMapIconScale", 2.0,
            "JourneyMap 5.2 map icon multiplier, independent of GUI scale (1.0 = original size, 2.0 = twice the width and height). "
                + "Applies to self, entities, waypoints, VisualProspecting ore icons/fluid labels and TCNodeTracker nodes on JourneyMap. Restart to apply.", 1.0, 8.0);
        double mapMultiplier = mapScale.getDouble(2.0);
        if (!Double.isFinite(mapMultiplier)) mapMultiplier = 2.0;
        mapMultiplier = Math.max(1.0, Math.min(8.0, mapMultiplier));
        mapScale.set(mapMultiplier);
        io.github.wdy.easytouch.map.JourneyMapScale.configure(mapMultiplier);
        file.save();
    }

    public void toggleButtons() {
        buttonsVisible = !buttonsVisible;
        file.get("touch", "showButtons", true).set(buttonsVisible);
        file.save();
    }

    public void toggleHideTooltips() {
        hideTooltips = !hideTooltips;
        file.get("touch", "hideTooltips", false).set(hideTooltips);
        file.save();
    }

    public void toggleOptimizeTooltip() {
        optimizeTooltip = !optimizeTooltip;
        file.get("touch", "optimizeTooltip", true).set(optimizeTooltip);
        file.save();
    }

    public void toggleAutoTool() {
        autoTool = !autoTool;
        file.get("mining", "autoTool", true).set(autoTool);
        file.save();
    }

    public void toggleEzMinerOptimization() {
        boolean enabled = !io.github.wdy.easytouch.mining.EzMinerAutoVein.enabled;
        io.github.wdy.easytouch.mining.EzMinerAutoVein.enabled = enabled;
        file.get("mining", "ezMinerAutoGtVein", true).set(enabled);
        file.save();
    }

    public void toggleAutoStep() {
        autoStep = !autoStep;
        file.get("movement", "autoStep", false).set(autoStep);
        file.save();
    }

}
