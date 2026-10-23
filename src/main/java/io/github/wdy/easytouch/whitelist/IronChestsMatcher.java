package io.github.wdy.easytouch.whitelist;

import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.SimpleMatcher;

/** 匹配 Iron Chests 箱子；默认不接受抽屉或 JABBA 储物桶。 */
public final class IronChestsMatcher extends SimpleMatcher {
    public IronChestsMatcher(MatcherSettings settings) {
        super(builder().blocks(settings.strings("blocks", "Exact block IDs, optionally @metadata. No wildcards.",
            "IronChest:BlockIronChest")));
    }
}
