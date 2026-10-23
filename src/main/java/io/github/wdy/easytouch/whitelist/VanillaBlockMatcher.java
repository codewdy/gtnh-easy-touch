package io.github.wdy.easytouch.whitelist;

import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.SimpleMatcher;

/** 默认匹配原版容器、工作台和机关。迁移的自定义方块也保存在此 matcher 的配置中。 */
public final class VanillaBlockMatcher extends SimpleMatcher {
    public VanillaBlockMatcher(MatcherSettings settings) {
        super(builder().blocks(settings.strings("blocks", "Exact block IDs, optionally @metadata. No wildcards.",
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:ender_chest",
            "minecraft:hopper",
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:crafting_table",
            "minecraft:furnace",
            "minecraft:lit_furnace",
            "minecraft:brewing_stand",
            "minecraft:enchanting_table",
            "minecraft:anvil",
            "minecraft:beacon",
            "minecraft:bed",
            "minecraft:wooden_door",
            "minecraft:trapdoor",
            "minecraft:fence_gate",
            "minecraft:stone_button",
            "minecraft:wooden_button",
            "minecraft:lever",
            "minecraft:unpowered_repeater",
            "minecraft:powered_repeater",
            "minecraft:unpowered_comparator",
            "minecraft:powered_comparator",
            "minecraft:jukebox")));
    }
}
