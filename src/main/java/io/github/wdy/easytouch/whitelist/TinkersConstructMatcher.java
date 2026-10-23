package io.github.wdy.easytouch.whitelist;

import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.SimpleMatcher;

/** 匹配匠魂工作台。ToolStationBlock 必须精确到允许的元数据。 */
public final class TinkersConstructMatcher extends SimpleMatcher {
    public TinkersConstructMatcher(MatcherSettings settings) {
        super(builder().blocks(settings.strings("blocks", "Exact block IDs, optionally @metadata. No wildcards.",
            "TConstruct:CraftingStation",
            "TConstruct:CraftingSlab",
            "TConstruct:ToolForgeBlock",
            "TConstruct:ToolStationBlock@0",
            "TConstruct:ToolStationBlock@1",
            "TConstruct:ToolStationBlock@2",
            "TConstruct:ToolStationBlock@3",
            "TConstruct:ToolStationBlock@4",
            "TConstruct:ToolStationBlock@10",
            "TConstruct:ToolStationBlock@11",
            "TConstruct:ToolStationBlock@12",
            "TConstruct:ToolStationBlock@13")));
    }
}
