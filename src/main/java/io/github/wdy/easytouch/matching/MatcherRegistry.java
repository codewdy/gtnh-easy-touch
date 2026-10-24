package io.github.wdy.easytouch.matching;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import io.github.wdy.easytouch.whitelist.AppliedEnergisticsMatcher;
import io.github.wdy.easytouch.whitelist.BedMatcher;
import io.github.wdy.easytouch.whitelist.GregTechMatcher;
import io.github.wdy.easytouch.whitelist.IronChestsMatcher;
import io.github.wdy.easytouch.whitelist.ThaumcraftMatcher;
import io.github.wdy.easytouch.whitelist.TinkersConstructMatcher;
import io.github.wdy.easytouch.whitelist.VanillaBlockMatcher;
import io.github.wdy.easytouch.whitelist.VanillaEntityMatcher;
import net.minecraftforge.common.config.Configuration;

/** Combines declarative rules once at registration; only arbitrary predicates need individual evaluation. */
public final class MatcherRegistry {
    private final Configuration file;
    private final TargetOverrides overrides;
    private final Set<String> registeredIds = new HashSet<String>();
    private final List<TargetMatcher> predicates = new ArrayList<TargetMatcher>();
    private final SimpleMatcher.Builder simpleRules = SimpleMatcher.builder();
    private SimpleMatcher merged = simpleRules.build();

    public MatcherRegistry(Configuration file) {
        this.file = file;
        overrides = new TargetOverrides(file);
    }

    public static MatcherRegistry builtIns(Configuration file) {
        return new MatcherRegistry(file)
            .register("vanilla_blocks", VanillaBlockMatcher::new)
            .register("beds", settings -> new BedMatcher())
            .register("vanilla_entities", VanillaEntityMatcher::new)
            .register("iron_chests", IronChestsMatcher::new)
            .register("tinkers_construct", TinkersConstructMatcher::new)
            .register("gregtech", GregTechMatcher::new)
            .register("applied_energistics", AppliedEnergisticsMatcher::new)
            .register("thaumcraft", ThaumcraftMatcher::new);
    }

    public MatcherRegistry register(String id, Function<MatcherSettings, TargetMatcher> factory) {
        if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid matcher id: " + id);
        if (registeredIds.contains(id)) throw new IllegalArgumentException("Duplicate matcher id: " + id);
        MatcherSettings settings = new MatcherSettings(file, id);
        TargetMatcher matcher = factory.apply(settings);
        if (matcher == null) throw new IllegalArgumentException("Null matcher: " + id);
        boolean enabled = settings.flag("enabled", true, "Enable this matcher.");
        registeredIds.add(id);
        if (enabled) {
            collect(matcher);
            merged = simpleRules.build();
        }
        return this;
    }

    private void collect(TargetMatcher matcher) {
        if (matcher instanceof SimpleMatcher) {
            simpleRules.include((SimpleMatcher) matcher);
        } else if (matcher instanceof AnyOfMatcher) {
            for (TargetMatcher alternative : ((AnyOfMatcher) matcher).alternatives()) collect(alternative);
        } else {
            predicates.add(matcher);
        }
    }

    public boolean matches(TargetContext context) {
        if (overrides.appliesTo(context)) {
            TargetIdentity identity = TargetIdentity.resolve(context);
            Boolean match = identity == null ? null : overrides.get(identity.key);
            if (match != null) return match;
        }
        return matchesDefaults(context);
    }

    public ToggleResult toggleTarget(TargetContext context) {
        TargetIdentity identity = TargetIdentity.resolve(context);
        if (identity == null) return null;
        Boolean previous = overrides.get(identity.key);
        boolean match = !(previous != null ? previous : matchesDefaults(context));
        overrides.set(identity.key, match);
        return new ToggleResult(identity, match);
    }

    public static final class ToggleResult {
        public final TargetIdentity identity;
        public final boolean match;

        private ToggleResult(TargetIdentity identity, boolean match) {
            this.identity = identity;
            this.match = match;
        }
    }

    private boolean matchesDefaults(TargetContext context) {
        if (merged.matches(context)) return true;
        for (TargetMatcher matcher : predicates) if (matcher.matches(context)) return true;
        return false;
    }
}
