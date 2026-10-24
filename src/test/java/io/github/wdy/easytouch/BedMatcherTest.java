package io.github.wdy.easytouch;

import io.github.wdy.easytouch.matching.MatcherRegistry;
import io.github.wdy.easytouch.whitelist.BedMatcher;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class BedMatcherTest extends MatcherTestSupport {
    private static TestTarget target(Block value, int metadata, int z) {
        TestTarget target = new TestTarget() {
            @Override public Block block() { return value; }
        };
        target.blockId = "example:variant";
        target.metadata = metadata;
        target.hit = new MovingObjectPosition(12, 64, z, 1, Vec3.createVectorHelper(12.5, 64.5, z + 0.5));
        return target;
    }

    @Test
    public void vanillaAndBedSubclassesMatchAcrossMetadataWithoutDependingOnRegistryNames() throws Exception {
        MatcherRegistry rules = MatcherRegistry.builtIns(configuration());
        for (Block bed : new Block[] {new BlockBed(), new DyedBed()}) {
            for (int metadata = 0; metadata < 16; metadata++) {
                assertTrue(rules.matches(target(bed, metadata, -7)));
                assertTrue(rules.matches(target(bed, metadata, -8)));
            }
        }
    }

    @Test
    public void forgeBedHookReceivesTheActualHitPositionAndIsCheckedForEachHalf() {
        BedMatcher matcher = new BedMatcher();
        ForgeBed bed = new ForgeBed();
        assertTrue(matcher.matches(target(bed, 0, -7)));
        assertTrue(matcher.matches(target(bed, 8, -8)));
        assertFalse(matcher.matches(target(bed, 0, -9)));
        assertEquals(3, bed.queries);
    }

    @Test
    public void bedLikeNamesMissingBlocksAndNonBlockHitsAreNotAccepted() {
        BedMatcher matcher = new BedMatcher();
        ForgeBed bed = new ForgeBed();
        TestTarget ordinary = target(bed, 0, -9);
        ordinary.blockId = "minecraft:bedrock";
        assertFalse(matcher.matches(ordinary));
        ordinary.blockId = "example:bed_decoration";
        assertFalse(matcher.matches(ordinary));
        assertFalse(matcher.matches(target(null, 0, -7)));
        TestTarget noHit = target(bed, 0, -7);
        noHit.hit = null;
        assertFalse(matcher.matches(noHit));
        TestTarget miss = target(bed, 0, -7);
        miss.hit.typeOfHit = MovingObjectPosition.MovingObjectType.MISS;
        assertFalse(matcher.matches(miss));
        assertEquals(2, bed.queries);
    }

    @Test
    public void newMatcherWorksWithExistingListsAndCanBeDisabledIndependently() throws Exception {
        Configuration file = configuration();
        file.get("matchers.vanilla_blocks", "blocks", new String[] {"custom:box"});
        TestTarget modBed = target(new ForgeBed(), 0, -7);
        assertTrue(MatcherRegistry.builtIns(file).matches(modBed));
        assertArrayEquals(new String[] {"custom:box"},
            file.getCategory("matchers.vanilla_blocks").get("blocks").getStringList());
        file.get("matchers.beds", "enabled", true).set(false);
        file.save();
        MatcherRegistry disabled = MatcherRegistry.builtIns(new Configuration(file.getConfigFile()));
        assertFalse(disabled.matches(modBed));
        assertTrue(disabled.matches(block("custom:box", 0)));
    }

    @Test
    public void brokenBedHookFallsBackAndIsNotRetriedEveryFrame() {
        BedMatcher matcher = new BedMatcher();
        BrokenBed broken = new BrokenBed();
        assertFalse(matcher.matches(target(broken, 0, -7)));
        assertFalse(matcher.matches(target(broken, 0, -7)));
        assertEquals(1, broken.queries);
        assertTrue(matcher.matches(target(new ForgeBed(), 0, -7)));
    }

    private static final class DyedBed extends BlockBed {
        @Override public boolean isBed(IBlockAccess world, int x, int y, int z, EntityLivingBase player) {
            return false; // A legacy subclass can inherit the vanilla-only Forge default.
        }
    }

    private static class ForgeBed extends Block {
        int queries;
        ForgeBed() { super(Material.cloth); }
        @Override public boolean isBed(IBlockAccess world, int x, int y, int z, EntityLivingBase player) {
            queries++;
            return x == 12 && y == 64 && (z == -7 || z == -8);
        }
    }

    private static final class BrokenBed extends ForgeBed {
        @Override public boolean isBed(IBlockAccess world, int x, int y, int z, EntityLivingBase player) {
            queries++;
            throw new NoSuchMethodError("incompatible bed integration");
        }
    }
}
