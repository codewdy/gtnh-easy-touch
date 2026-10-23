package io.github.wdy.easytouch.whitelist;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import cpw.mods.fml.common.FMLLog;
import io.github.wdy.easytouch.matching.AnyOfMatcher;
import io.github.wdy.easytouch.matching.MatchSupport;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.SimpleMatcher;
import io.github.wdy.easytouch.matching.TargetContext;
import io.github.wdy.easytouch.matching.TargetMatcher;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** AE2 方块和面板名单；只读取准星选中的部件，不执行交互来探测。 */
public final class AppliedEnergisticsMatcher extends AnyOfMatcher {
    public AppliedEnergisticsMatcher(MatcherSettings settings) {
        super(SimpleMatcher.builder().blocks(settings.strings("blocks", "Exact AE2 block IDs, optionally @metadata.",
            "appliedenergistics2:tile.BlockSkyChest",
            "appliedenergistics2:tile.BlockChest",
            "appliedenergistics2:tile.BlockDrive",
            "appliedenergistics2:tile.BlockInterface",
            "appliedenergistics2:tile.BlockCellWorkbench",
            "appliedenergistics2:tile.BlockIOPort",
            "appliedenergistics2:tile.BlockCondenser",
            "appliedenergistics2:tile.BlockGrinder",
            "appliedenergistics2:tile.BlockInscriber",
            "appliedenergistics2:tile.BlockAdvancedInscriber",
            "appliedenergistics2:tile.BlockSecurity",
            "appliedenergistics2:tile.BlockSpatialIOPort")).build(), new SelectedPartMatcher(settings));
    }

    private static final class SelectedPartMatcher implements TargetMatcher {
        private final Set<String> parts;
        private final Map<Class<?>, Method> methods = new HashMap<Class<?>, Method>();
        private final Set<Class<?>> incompatible = new HashSet<Class<?>>();

        private SelectedPartMatcher(MatcherSettings settings) {
            // 部件按精确类名匹配，不包含 BlockCableBus / TileCableBus 或线缆部件。
            parts = settings.strings("parts", "Exact selected-part class names; never the containing cable host.",
                "appeng.parts.reporting.PartTerminal",
                "appeng.parts.reporting.PartCraftingTerminal",
                "appeng.parts.reporting.PartPatternTerminal",
                "appeng.parts.reporting.PartPatternTerminalEx",
                "appeng.parts.reporting.PartInterfaceTerminal",
                "appeng.parts.reporting.PartCraftingDiagnosticTerminal",
                "appeng.parts.misc.PartInterface");
        }

        @Override
        public boolean matches(TargetContext context) {
            Object tile = context.tileEntity();
            if (tile == null) return false;
            MovingObjectPosition hit = context.hit();
            Class<?> type = tile.getClass();
            if (parts.isEmpty() || incompatible.contains(type)
                || !MatchSupport.hasType(type, "appeng.api.parts.IPartHost") || hit == null || hit.hitVec == null) return false;

            try {
                Method method = methods.get(type);
                if (method == null) {
                    method = type.getMethod("selectPart", Vec3.class);
                    methods.put(type, method);
                }
                // IPartHost 使用相对方块的坐标，不能直接传世界坐标。
                Vec3 local = Vec3.createVectorHelper(hit.hitVec.xCoord - hit.blockX,
                    hit.hitVec.yCoord - hit.blockY, hit.hitVec.zCoord - hit.blockZ);
                Object selected = method.invoke(tile, local);
                if (selected == null) return false;
                Object part = selected.getClass().getField("part").get(selected);
                return part != null && MatchSupport.exactClass(parts, part);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                incompatible.add(type);
                FMLLog.warning("[GTNH Easy Touch] Cannot inspect %s (%s); keeping vanilla buttons.",
                    type.getName(), e.getClass().getSimpleName());
                return false;
            }
        }
    }
}
