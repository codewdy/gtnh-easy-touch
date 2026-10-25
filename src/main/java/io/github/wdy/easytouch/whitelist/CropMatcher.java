package io.github.wdy.easytouch.whitelist;

import java.util.Set;
import io.github.wdy.easytouch.matching.MatchSupport;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.TargetContext;
import io.github.wdy.easytouch.matching.TargetMatcher;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockStem;

/** Crop families at every growth stage; harvesting remains the original mod's responsibility. */
public final class CropMatcher implements TargetMatcher {
    private final boolean standardCrops;
    private final Set<String> classes;

    public CropMatcher(MatcherSettings settings) {
        standardCrops = settings.flag("standardCrops", true,
            "Match BlockCrops (including Pam's crops), stems, nether wart and cocoa, at every growth stage. "
                + "This only swaps buttons; it does not add right-click harvesting.");
        classes = settings.strings("classes", "Additional crop block class families, including subclasses. "
                + "These optional mods do not need to be installed.",
            "com.pam.harvestcraft.BlockPamFruit",
            "com.pam.harvestcraft.BlockPamFruitingLog",
            "com.gtnewhorizon.cropsnh.blocks.BlockCropSticks",
            "ic2.core.crop.BlockCrop",
            "mods.natura.blocks.crops.CropBlock",
            "mods.natura.blocks.crops.BerryBush",
            "mods.natura.blocks.crops.NetherBerryBush");
    }

    @Override
    public boolean matches(TargetContext context) {
        if (!context.isBlock()) return false;
        Block block = context.block();
        // Use Java types for vanilla classes so this also works after production reobfuscation.
        if (standardCrops && (block instanceof BlockCrops || block instanceof BlockStem
            || block instanceof BlockNetherWart || block instanceof BlockCocoa)) return true;
        // IPlantable / IGrowable / BlockBush also include ordinary flowers, grass and saplings.
        // Do not query maturity or call activation: harvesting must not change the next click's mapping.
        return MatchSupport.classFamily(classes, block);
    }
}
