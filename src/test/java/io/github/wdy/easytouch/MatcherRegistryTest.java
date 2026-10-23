package io.github.wdy.easytouch;

import io.github.wdy.easytouch.matching.MatcherRegistry;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.TargetContext;
import io.github.wdy.easytouch.matching.TargetMatcher;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatcherRegistryTest extends MatcherTestSupport {
    @Test
    public void aNewContextDependentMatcherNeedsOnlyRegistrationAndItsOwnSettings() throws Exception {
        Configuration file = configuration();
        file.get("matchers.high_surface", "minimumHeight", 90);
        MatcherRegistry registry = MatcherRegistry.builtIns(file);
        TestTarget target = surface(89, 1);
        assertFalse(registry.matches(target));
        registry.register("high_surface", HighSurfaceMatcher::new);
        assertFalse(registry.matches(target));
        assertTrue(registry.matches(surface(90, 1)));
        assertFalse(registry.matches(surface(90, 2)));
        assertTrue(registry.matches(block("minecraft:chest", 0)));
    }

    @Test
    public void matcherDisableIsIndependentOfOtherMatchers() throws Exception {
        Configuration file = configuration();
        file.get("matchers.high_surface", "enabled", false);
        file.get("matchers.vanilla_blocks", "enabled", false);
        MatcherRegistry registry = MatcherRegistry.builtIns(file).register("high_surface", HighSurfaceMatcher::new);
        assertFalse(registry.matches(surface(200, 1)));
        assertFalse(registry.matches(block("minecraft:chest", 0)));
        assertTrue(registry.matches(block("IronChest:BlockIronChest", 0)));
    }

    @Test
    public void registrySupportsRulesWithNoListsOrBuiltInTargetTypes() throws Exception {
        MatcherRegistry registry = new MatcherRegistry(configuration());
        TestTarget empty = new TestTarget();
        assertFalse(registry.matches(empty));
        registry.register("no_target", settings -> context -> context.hit() == null);
        assertTrue(registry.matches(empty));
        assertFalse(registry.matches(surface(90, 1)));
    }

    private static TestTarget surface(int height, int side) {
        TestTarget target = block("example:surface", 0);
        target.hit = new MovingObjectPosition(0, height, 0, side, Vec3.createVectorHelper(0.5, height + 1, 0.5));
        return target;
    }

    /** An extension unknown to production code: a new numeric setting and a hit-face/height predicate. */
    public static final class HighSurfaceMatcher implements TargetMatcher {
        private final int minimumHeight;

        public HighSurfaceMatcher(MatcherSettings settings) {
            minimumHeight = settings.integer("minimumHeight", 80, 0, 255, "Minimum height for this rule.");
        }

        public boolean matches(TargetContext context) {
            return context.isBlock() && "example:surface".equals(context.blockId())
                && context.hit().blockY >= minimumHeight && context.hit().sideHit == 1;
        }
    }
}
