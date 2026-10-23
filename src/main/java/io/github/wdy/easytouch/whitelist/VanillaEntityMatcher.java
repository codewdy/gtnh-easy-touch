package io.github.wdy.easytouch.whitelist;

import java.util.HashSet;
import java.util.Set;
import io.github.wdy.easytouch.matching.AnyOfMatcher;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.SimpleMatcher;

/** 生物、载具和展示框的完整匹配规则；宠物驯服条件由此 matcher 自己判断。 */
public final class VanillaEntityMatcher extends AnyOfMatcher {
    public VanillaEntityMatcher(MatcherSettings settings) {
        this(settings.strings("entities", "Exact entity registry names.",
            "Villager",
            "Cow",
            "MushroomCow",
            "Sheep",
            "Pig",
            "Chicken",
            "EntityHorse",
            "Boat",
            "MinecartRideable",
            "MinecartChest",
            "MinecartHopper",
            "ItemFrame"),
            settings.strings("tamedEntities", "These entries require taming even if also listed in entities.",
            "Wolf",
            "Ozelot"));
    }

    private VanillaEntityMatcher(Set<String> entities, Set<String> tamedEntities) {
        super(ordinaryEntities(entities, tamedEntities),
            context -> tamedEntities.contains(context.entityId()) && context.entityTamed());
    }

    private static SimpleMatcher ordinaryEntities(Set<String> entities, Set<String> tamedEntities) {
        // Apply this rule's taming precedence before merging; never add pets to its unconditional index.
        Set<String> ordinary = new HashSet<String>(entities);
        ordinary.removeAll(tamedEntities);
        return SimpleMatcher.builder().entities(ordinary).build();
    }
}
