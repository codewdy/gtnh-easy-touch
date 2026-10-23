package io.github.wdy.easytouch;

import io.github.wdy.easytouch.matching.MatcherRegistry;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.whitelist.ThaumcraftMatcher;
import io.github.wdy.easytouch.whitelist.VanillaEntityMatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class MatcherBehaviorTest extends MatcherTestSupport {
    @Test
    public void builtInMatchersAcceptTheirOwnTargetsAndRejectUnknownBlocks() throws Exception {
        MatcherRegistry rules = MatcherRegistry.builtIns(configuration());
        for (String id : new String[] {"minecraft:chest", "minecraft:lit_furnace", "IronChest:BlockIronChest",
            "appliedenergistics2:tile.BlockDrive", "TConstruct:CraftingStation"}) {
            assertTrue(id, rules.matches(block(id, 3)));
        }
        for (String id : new String[] {"minecraft:stone", "minecraft:wheat", "unknown:interactive_machine",
            "StorageDrawers:fullDrawers1", "JABBA:barrel", "gregtech:gt.blockmachines",
            "appliedenergistics2:tile.BlockCableBus", "appliedenergistics2:tile.BlockController"}) {
            assertFalse(id, rules.matches(block(id, 0)));
        }
        assertFalse(rules.matches(new TestTarget()));
    }

    @Test
    public void tinkersMatcherRestrictsMetadata() throws Exception {
        MatcherRegistry rules = MatcherRegistry.builtIns(configuration());
        for (int meta : new int[] {0, 3, 12}) assertTrue(rules.matches(block("TConstruct:ToolStationBlock", meta)));
        for (int meta : new int[] {5, 15}) assertFalse(rules.matches(block("TConstruct:ToolStationBlock", meta)));
    }

    @Test
    public void entityMatcherOwnsTamingConditionAndRejectsUnknownCreatures() throws Exception {
        VanillaEntityMatcher matcher = new VanillaEntityMatcher(new MatcherSettings(configuration(), "vanilla_entities"));
        assertTrue(matcher.matches(entity("Villager", false)));
        assertTrue(matcher.matches(entity("EntityHorse", false)));
        for (String id : new String[] {"Wolf", "Ozelot"}) {
            assertFalse(matcher.matches(entity(id, false)));
            assertTrue(matcher.matches(entity(id, true)));
        }
        for (String id : new String[] {null, "Zombie", "PigZombie", "Player", "unknown:FriendlyPet",
            "MinecartTNT", "MinecartFurnace"}) assertFalse(matcher.matches(entity(id, true)));
    }

    @Test
    public void duplicatePetEntryCannotBypassTaming() throws Exception {
        Configuration file = configuration();
        file.get("matchers.vanilla_entities", "entities", new String[] {"Wolf"});
        VanillaEntityMatcher matcher = new VanillaEntityMatcher(new MatcherSettings(file, "vanilla_entities"));
        assertFalse(matcher.matches(entity("Wolf", false)));
        assertTrue(matcher.matches(entity("Wolf", true)));
    }

    @Test
    public void thaumcraftMatcherUsesExactClassesAndNoImplicitSubclasses() throws Exception {
        Configuration file = configuration();
        file.get("matchers.thaumcraft", "tileEntities", new String[] {Workbench.class.getName()});
        ThaumcraftMatcher matcher = new ThaumcraftMatcher(new MatcherSettings(file, "thaumcraft"));
        assertTrue(matcher.matches(tile(new Workbench())));
        assertFalse(matcher.matches(tile(new OtherWorkbench())));
        assertFalse(matcher.matches(tile(null)));
    }

    @Test
    public void customListDoesNotReenableDefaultsOrExpandWildcards() throws Exception {
        Configuration file = configuration();
        file.get("matchers.vanilla_blocks", "blocks", new String[] {"custom:box@7", "custom:*"});
        MatcherRegistry rules = MatcherRegistry.builtIns(file);
        assertTrue(rules.matches(block("custom:box", 7)));
        assertFalse(rules.matches(block("custom:box", 6)));
        assertFalse(rules.matches(block("custom:other", 0)));
        assertFalse(rules.matches(block("minecraft:chest", 0)));
    }

    public static class Workbench extends TileEntity { }
    public static class OtherWorkbench extends Workbench { }
}
