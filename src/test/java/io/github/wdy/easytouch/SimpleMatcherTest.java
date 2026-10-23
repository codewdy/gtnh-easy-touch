package io.github.wdy.easytouch;

import io.github.wdy.easytouch.matching.AnyOfMatcher;
import io.github.wdy.easytouch.matching.MatcherRegistry;
import io.github.wdy.easytouch.matching.SimpleMatcher;
import io.github.wdy.easytouch.matching.TargetMatcher;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimpleMatcherTest extends MatcherTestSupport {
    @Test
    public void simpleRulesMatchExactIdsAndMetadataWithoutExpandingWildcards() {
        SimpleMatcher matcher = SimpleMatcher.builder()
            .blocks("example:box", "example:table@3", "example:*", "example:strict@03")
            .entities("ExampleMob").build();
        assertTrue(matcher.matches(block("example:box", 15)));
        assertTrue(matcher.matches(block("example:table", 3)));
        assertFalse(matcher.matches(block("example:table", 4)));
        assertFalse(matcher.matches(block("example:strict", 3)));
        assertFalse(matcher.matches(block("example:other", 0)));
        assertTrue(matcher.matches(entity("ExampleMob", false)));
        assertFalse(matcher.matches(entity("OtherMob", true)));
        assertFalse(matcher.matches(new TestTarget()));
        assertFalse(SimpleMatcher.builder().build().matches(block("example:box", 0)));
    }

    @Test
    public void buildersCannotChangePreviouslyBuiltOrRegisteredRules() throws Exception {
        SimpleMatcher.Builder builder = SimpleMatcher.builder().blocks("example:first");
        SimpleMatcher original = builder.build();
        MatcherRegistry registry = new MatcherRegistry(configuration()).register("original", settings -> original);
        builder.blocks("example:later").entities("LaterMob");
        assertFalse(original.matches(block("example:later", 0)));
        assertFalse(registry.matches(block("example:later", 0)));
        assertFalse(registry.matches(entity("LaterMob", false)));
        registry.register("later", settings -> builder.build());
        assertTrue(registry.matches(block("example:first", 0)));
        assertTrue(registry.matches(block("example:later", 0)));
        assertTrue(registry.matches(entity("LaterMob", false)));
    }

    @Test
    public void manySimpleMatchersUseOneLookupPerTargetFactOnBothHitsAndMisses() throws Exception {
        MatcherRegistry registry = new MatcherRegistry(configuration());
        int[] fallbackCalls = {0};
        registry.register("fallback", settings -> context -> { fallbackCalls[0]++; return false; });
        for (int i = 0; i < 100; i++) {
            final int id = i;
            registry.register("simple_" + i, settings -> SimpleMatcher.builder()
                .blocks("example:box_" + id + "@3").entities("ExampleMob" + id).build());
        }

        CountingTarget block = countedBlock("example:box_99", 3);
        assertTrue(registry.matches(block));
        assertEquals(1, block.blockReads);
        assertEquals(1, block.metadataReads);
        assertEquals(0, fallbackCalls[0]);

        CountingTarget entity = new CountingTarget();
        entity.entityId = "ExampleMob99";
        assertTrue(registry.matches(entity));
        assertEquals(1, entity.entityReads);
        assertEquals(0, fallbackCalls[0]);

        CountingTarget miss = countedBlock("example:missing", 3);
        assertFalse(registry.matches(miss));
        assertEquals(1, miss.blockReads);
        assertEquals(1, miss.metadataReads);
        assertEquals(1, miss.entityReads);
        assertEquals(1, fallbackCalls[0]);
    }

    @Test
    public void nestedAlternativesAreFlattenedWithoutLosingTheirPredicates() throws Exception {
        TargetMatcher composite = new AnyOfMatcher(
            SimpleMatcher.builder().blocks("example:first").build(),
            new AnyOfMatcher(SimpleMatcher.builder().blocks("example:last@2").entities("ExampleMob").build(),
                context -> context.entityTamed()));
        MatcherRegistry registry = new MatcherRegistry(configuration()).register("composite", settings -> composite);
        CountingTarget last = countedBlock("example:last", 2);
        assertTrue(registry.matches(last));
        assertEquals(1, last.blockReads);
        for (TestTarget target : new TestTarget[] {block("example:first", 0), block("example:last", 2),
            block("example:last", 3), entity("ExampleMob", false), entity("Pet", true),
            entity("Pet", false), new TestTarget()}) {
            assertEquals(composite.matches(target), registry.matches(target));
        }
    }

    @Test
    public void disabledCompositeContributesNeitherSimpleRulesNorPredicates() throws Exception {
        Configuration file = configuration();
        file.get("matchers.disabled", "enabled", false);
        MatcherRegistry registry = new MatcherRegistry(file)
            .register("enabled", settings -> SimpleMatcher.builder().blocks("example:shared").build())
            .register("disabled", settings -> new AnyOfMatcher(SimpleMatcher.builder()
                .blocks(settings.strings("blocks", "Test defaults.", "example:shared", "example:disabled")).build(),
                new AnyOfMatcher(SimpleMatcher.builder().entities("DisabledMob").build(), context -> {
                    throw new AssertionError("Disabled predicate must not run");
                })));
        assertTrue(registry.matches(block("example:shared", 0)));
        assertFalse(registry.matches(block("example:disabled", 0)));
        assertFalse(registry.matches(entity("DisabledMob", false)));
        assertTrue(file.hasKey("matchers.disabled", "blocks"));
    }

    @Test
    public void tamingPrecedenceStaysLocalToItsRuleWhenEntitiesAreMerged() throws Exception {
        Configuration file = configuration();
        file.get("matchers.vanilla_entities", "entities", new String[] {"Wolf", "Cow"});
        MatcherRegistry registry = MatcherRegistry.builtIns(file);
        assertTrue(registry.matches(entity("Cow", false)));
        assertFalse(registry.matches(entity("Wolf", false)));
        assertTrue(registry.matches(entity("Wolf", true)));
        // A separate explicit allow rule still follows OR semantics; taming is not a global veto.
        registry.register("explicit_wolf", settings -> SimpleMatcher.builder().entities("Wolf").build());
        assertTrue(registry.matches(entity("Wolf", false)));
    }

    @Test
    public void builtInBlockRulesIncludingAeAreMergedIntoOneIndex() throws Exception {
        MatcherRegistry registry = MatcherRegistry.builtIns(configuration());
        for (String id : new String[] {"minecraft:chest", "IronChest:BlockIronChest",
            "TConstruct:ToolStationBlock", "appliedenergistics2:tile.BlockDrive", "example:unknown"}) {
            CountingTarget target = countedBlock(id, 3);
            assertEquals(!id.equals("example:unknown"), registry.matches(target));
            assertEquals(id, 1, target.blockReads);
        }
    }

    @Test
    public void idsRemainUniqueEvenWhenRulesAreMergedOrDisabled() throws Exception {
        Configuration file = configuration();
        file.get("matchers.disabled", "enabled", false);
        MatcherRegistry registry = new MatcherRegistry(file)
            .register("simple", settings -> SimpleMatcher.builder().blocks("example:box").build())
            .register("disabled", settings -> SimpleMatcher.builder().build());
        assertThrows(IllegalArgumentException.class, () -> registry.register("simple", settings -> context -> true));
        assertThrows(IllegalArgumentException.class, () -> registry.register("disabled", settings -> context -> true));
    }

    private static CountingTarget countedBlock(String id, int metadata) {
        CountingTarget target = new CountingTarget();
        target.blockId = id;
        target.metadata = metadata;
        return target;
    }

    private static final class CountingTarget extends TestTarget {
        int blockReads, metadataReads, entityReads;
        @Override public String blockId() { blockReads++; return super.blockId(); }
        @Override public int blockMetadata() { metadataReads++; return super.blockMetadata(); }
        @Override public String entityId() { entityReads++; return super.entityId(); }
    }
}
