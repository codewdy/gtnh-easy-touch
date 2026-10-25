package io.github.wdy.easytouch;

import io.github.wdy.easytouch.matching.MatcherRegistry;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.whitelist.CropMatcher;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.BlockStem;
import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class CropMatcherTest extends MatcherTestSupport {
    private static TestTarget target(Block value, int metadata) {
        TestTarget target = new TestTarget() {
            @Override public Block block() { return value; }
        };
        target.blockId = "example:crop";
        target.metadata = metadata;
        target.hit = new MovingObjectPosition(12, 64, -7, 1, Vec3.createVectorHelper(12.5, 64.5, -6.5));
        return target;
    }

    private CropMatcher matcher(Configuration file) {
        return new CropMatcher(new MatcherSettings(file, "crops"));
    }

    @Test
    public void standardCropFamiliesAndModSubclassesMatchEveryGrowthStage() throws Exception {
        MatcherRegistry rules = MatcherRegistry.builtIns(configuration());
        for (Block crop : new Block[] {new BlockCrops() { }, new ModCrop(), new BlockNetherWart() { },
            new BlockCocoa(), new BlockStem(null) { }}) {
            for (int stage = 0; stage < 16; stage++) {
                assertTrue(crop.getClass().getName() + "@" + stage, rules.matches(target(crop, stage)));
            }
        }
    }

    @Test
    public void optionalCropFruitAndBerryFamiliesMatchWithoutLoadingTheirMods() throws Exception {
        MatcherRegistry rules = MatcherRegistry.builtIns(configuration());
        for (Block crop : new Block[] {
            new com.pam.harvestcraft.BlockPamFruit(), new FruitVariant(),
            new com.pam.harvestcraft.BlockPamFruitingLog(),
            new com.gtnewhorizon.cropsnh.blocks.BlockCropSticks(),
            new ic2.core.crop.BlockCrop(), new mods.natura.blocks.crops.CropBlock(),
            new mods.natura.blocks.crops.BerryBush(), new mods.natura.blocks.crops.NetherBerryBush()}) {
            for (int stage = 0; stage < 16; stage++) {
                assertTrue(crop.getClass().getName() + "@" + stage, rules.matches(target(crop, stage)));
            }
        }
    }

    @Test
    public void namesAndBroadPlantInterfacesDoNotAcceptFlowersGrassSaplingsOrMachines() throws Exception {
        CropMatcher matcher = matcher(configuration());
        for (Block other : new Block[] {new BlockFlower(0) { }, new BlockTallGrass() { },
            new BlockSapling() { }, new Block(Material.rock) { }}) {
            TestTarget target = target(other, 7);
            for (String id : new String[] {"example:crop", "harvestcraft:cropMachine", "minecraft:wheat"}) {
                target.blockId = id;
                assertFalse(matcher.matches(target));
            }
        }
    }

    @Test
    public void noHitEntitiesAndMissingBlocksNeverMatch() throws Exception {
        CropMatcher matcher = matcher(configuration());
        assertFalse(matcher.matches(target(null, 7)));
        TestTarget target = target(new ModCrop(), 7);
        target.hit.typeOfHit = MovingObjectPosition.MovingObjectType.MISS;
        assertFalse(matcher.matches(target));
        target.hit.typeOfHit = MovingObjectPosition.MovingObjectType.ENTITY;
        assertFalse(matcher.matches(target));
        target.hit = null;
        assertFalse(matcher.matches(target));
    }

    @Test
    public void matchingDoesNotReadMaturityOrActivateTheCrop() throws Exception {
        CropMatcher matcher = matcher(configuration());
        TestTarget crop = new TestTarget() {
            @Override public boolean isBlock() { return true; }
            @Override public Block block() { return new ModCrop(); }
            @Override public int blockMetadata() { throw new AssertionError("Must not inspect maturity"); }
            @Override public World world() { throw new AssertionError("Must not inspect world state"); }
        };
        assertTrue(matcher.matches(crop));
        assertTrue(matcher.matches(crop));
    }

    @Test
    public void upgradeEnablesCropsWithoutChangingExistingListsAndSupportsIndependentDisable() throws Exception {
        Configuration file = configuration();
        file.get("matchers.vanilla_blocks", "blocks", new String[] {"custom:box"});
        MatcherRegistry rules = MatcherRegistry.builtIns(file);
        assertTrue(rules.matches(target(new ModCrop(), 7)));
        assertArrayEquals(new String[] {"custom:box"},
            file.getCategory("matchers.vanilla_blocks").get("blocks").getStringList());
        file.get("matchers.crops", "enabled", true).set(false);
        file.save();
        MatcherRegistry disabled = MatcherRegistry.builtIns(new Configuration(file.getConfigFile()));
        assertFalse(disabled.matches(target(new ModCrop(), 7)));
        assertFalse(disabled.matches(target(new FruitVariant(), 7)));
        assertTrue(disabled.matches(block("custom:box", 0)));
    }

    @Test
    public void configuredFamiliesReplaceDefaultsAndEmptyListsRemainEmptyAfterReload() throws Exception {
        Configuration file = configuration();
        file.get("matchers.crops", "standardCrops", true).set(false);
        file.get("matchers.crops", "classes", new String[] {CustomCrop.class.getName()});
        CropMatcher custom = matcher(file);
        assertTrue(custom.matches(target(new CustomCropVariant(), 0)));
        assertFalse(custom.matches(target(new ModCrop(), 7)));
        assertFalse(custom.matches(target(new FruitVariant(), 7)));
        file.get("matchers.crops", "classes", new String[0]).set(new String[0]);
        file.save();
        CropMatcher empty = matcher(new Configuration(file.getConfigFile()));
        assertFalse(empty.matches(target(new CustomCropVariant(), 0)));
        assertFalse(empty.matches(target(new ModCrop(), 7)));
        assertFalse(empty.matches(target(new FruitVariant(), 7)));
    }

    @Test
    public void manualDisableTakesPriorityOverTheNewCropDefaultAndPersists() throws Exception {
        Configuration file = configuration();
        MatcherRegistry rules = MatcherRegistry.builtIns(file);
        TestTarget crop = target(new ModCrop(), 7);
        assertTrue(rules.matches(crop));
        assertFalse(rules.toggleTarget(crop).match);
        MatcherRegistry reloaded = MatcherRegistry.builtIns(new Configuration(file.getConfigFile()));
        assertFalse(reloaded.matches(crop));
        assertTrue(reloaded.toggleTarget(crop).match);
        assertTrue(reloaded.matches(crop));
    }

    /** Pam's field crops share this vanilla base; the real RC2 jar was checked separately. */
    private static final class ModCrop extends BlockCrops {
        @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
            throw new AssertionError("Must not activate crops while matching");
        }
    }
    private static final class FruitVariant extends com.pam.harvestcraft.BlockPamFruit { }
    private static class CustomCrop extends Block {
        CustomCrop() { super(Material.plants); }
    }
    private static final class CustomCropVariant extends CustomCrop { }
}
