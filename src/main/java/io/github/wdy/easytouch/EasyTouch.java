package io.github.wdy.easytouch;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import io.github.wdy.easytouch.touch.TouchInput;

@Mod(modid = EasyTouch.ID, name = "GTNH Easy Touch", version = "0.4.26",
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*",
    dependencies = "required-after:lwjgl3ify@[3.0.37,)")
public final class EasyTouch {
    public static final String ID = "gtnheasytouch";
    private TouchConfig config;

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
        }
    }
}
