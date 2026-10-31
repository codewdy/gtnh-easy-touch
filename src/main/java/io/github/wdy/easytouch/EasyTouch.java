package io.github.wdy.easytouch;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import io.github.wdy.easytouch.touch.TouchInput;

@Mod(modid = EasyTouch.ID, name = "GTNH Easy Touch", version = "0.5.0",
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*",
    dependencies = "required-after:lwjgl3ify@[3.0.37,)")
public final class EasyTouch {
    public static final String ID = "gtnheasytouch";
    private TouchConfig config;

    public EasyTouch() {
        // Chromatic registers HIGHEST in preInit. Register before all preInit handlers
        // so its queued native draw can be adapted without reordering other mods.
        if (cpw.mods.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            io.github.wdy.easytouch.touch.TooltipRenderPhase.registerEarly();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide().isClient()) {
            config = new TouchConfig(event.getSuggestedConfigurationFile());
        }
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (event.getSide().isClient()) {
            TouchInput.install(config);
            io.github.wdy.easytouch.text.NativeTextInput.install(config);
        }
    }
}
