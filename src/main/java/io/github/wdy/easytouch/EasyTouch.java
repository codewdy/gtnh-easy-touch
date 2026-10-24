package io.github.wdy.easytouch;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;

@Mod(modid = EasyTouch.ID, name = "GTNH Easy Touch", version = "0.4.7",
    acceptedMinecraftVersions = "[1.7.10]", acceptableRemoteVersions = "*")
public final class EasyTouch {
    public static final String ID = "gtnheasytouch";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide().isClient()) ClientInput.install(new TouchConfig(event.getSuggestedConfigurationFile()));
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (event.getSide().isClient()) ClientInput.installNeiCrafting();
    }
}
