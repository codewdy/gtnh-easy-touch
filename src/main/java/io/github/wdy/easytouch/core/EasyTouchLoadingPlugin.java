package io.github.wdy.easytouch.core;

import java.util.Map;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions({"io.github.wdy.easytouch.core"})
public final class EasyTouchLoadingPlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[] {JourneyMapTransformer.class.getName(), ProgrammableHatchesTransformer.class.getName(), EzMinerTransformer.class.getName()}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String, Object> data) {}
    public String getAccessTransformerClass() { return null; }
}
