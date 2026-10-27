package io.github.wdy.easytouch.whitelist;

import io.github.wdy.easytouch.matching.MatchSupport;
import io.github.wdy.easytouch.matching.MatcherSettings;
import io.github.wdy.easytouch.matching.TargetContext;
import io.github.wdy.easytouch.matching.TargetMatcher;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.item.ItemStack;

/** GT 机器名单和读取内部设备类型的适配器；不依赖 GT 编译库。 */
public final class GregTechMatcher implements TargetMatcher {
    private final Set<String> machines;
    private final Map<Class<?>, Method> methods = new HashMap<Class<?>, Method>();
    private final Set<Class<?>> incompatible = new HashSet<Class<?>>();

    public GregTechMatcher(MatcherSettings settings) {
        // 允许这些明确的设备家族及其子类。不要添加普通 tank、hatch、tiered-block 基类：
        // 它们还包含没有操作界面的设备。更不能放行整个 gt.blockmachines。
        machines = settings.strings("machines", "GT machine class families. Pipes are excluded.",
            "gregtech.api.metatileentity.implementations.MTEBasicMachine", // 含蒸汽加工机
            "gregtech.api.metatileentity.implementations.MTEBasicGenerator",
            "gregtech.api.metatileentity.implementations.MTEMultiBlockBase",
            "gregtech.api.metatileentity.implementations.MTEHatchInput",
            "gregtech.api.metatileentity.implementations.MTEHatchInputBus",
            "gregtech.api.metatileentity.implementations.MTEHatchOutput",
            "gregtech.api.metatileentity.implementations.MTEHatchOutputBus",
            "gregtech.api.metatileentity.implementations.MTEBasicBatteryBuffer",
            "gregtech.common.tileentities.boilers.MTEBoiler",
            "gregtech.common.tileentities.storage.MTEDigitalTankBase", // 含量子储罐
            "gregtech.common.tileentities.storage.MTEQuantumChest");
    }

    /** Tool-on-GT-block interaction keeps vanilla actions, even with a user allow rule. */
    public static boolean suppressesSmart(TargetContext context) {
        if (!context.isBlock()) return false;
        ItemStack held = context.heldItem();
        if (held == null || held.getItem() == null
            || !MatchSupport.hasType(held.getItem().getClass(), "gregtech.api.items.MetaGeneratedTool")) return false;
        String blockId = context.blockId();
        return blockId != null && blockId.startsWith("gregtech:");
    }

    @Override
    public boolean matches(TargetContext context) {
        Object tile = context.tileEntity();
        if (tile == null) return false;
        Class<?> type = tile.getClass();
        if (machines.isEmpty() || incompatible.contains(type)
            || !MatchSupport.hasType(type, "gregtech.api.metatileentity.BaseMetaTileEntity")) return false;

        // BaseMetaPipeEntity 也有 getMetaTileEntity，必须先检查宿主类型以排除管线。
        try {
            Method method = methods.get(type);
            if (method == null) {
                method = type.getMethod("getMetaTileEntity");
                methods.put(type, method);
            }
            Object machine = method.invoke(tile);
            return machine != null && MatchSupport.classFamily(machines, machine);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            incompatible.add(type);
            FMLLog.warning("[GTNH Easy Touch] Cannot inspect %s (%s); keeping vanilla buttons.",
                type.getName(), e.getClass().getSimpleName());
            return false;
        }
    }
}
