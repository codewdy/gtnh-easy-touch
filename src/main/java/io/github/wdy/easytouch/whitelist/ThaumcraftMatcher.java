package io.github.wdy.easytouch.whitelist;

import io.github.wdy.easytouch.matching.MatchSupport;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.TargetContext;
import io.github.wdy.easytouch.matching.TargetMatcher;
import java.util.Set;

/** 以精确 TileEntity 类型匹配研究台和奥术工作台，不放行其他桌子变体。 */
public final class ThaumcraftMatcher implements TargetMatcher {
    private final Set<String> tileEntities;

    public ThaumcraftMatcher(MatcherSettings settings) {
        tileEntities = settings.strings("tileEntities", "Exact tile entity class names.",
            "thaumcraft.common.tiles.TileResearchTable",
            "thaumcraft.common.tiles.TileMagicWorkbench");
    }

    @Override
    public boolean matches(TargetContext context) {
        return MatchSupport.exactClass(tileEntities, context.tileEntity());
    }
}
