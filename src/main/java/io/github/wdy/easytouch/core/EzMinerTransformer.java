package io.github.wdy.easytouch.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Route both preview and harvest founders without changing the player's selected mode. */
public final class EzMinerTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"com.czqwq.EZMiner.chain.planning.LegacyFounderPlanningFactory".equals(transformedName)) return bytes;
        ClassNode cls = new ClassNode();
        new ClassReader(bytes).accept(cls, 0);
        String base = "com/czqwq/EZMiner/core/founder/BasePositionFounder";
        String descriptor = "(Lcom/czqwq/EZMiner/core/MinerModeState;Lorg/joml/Vector3i;Ljava/util/concurrent/LinkedBlockingQueue;Lnet/minecraft/entity/player/EntityPlayer;Lcom/czqwq/EZMiner/core/MinerConfig;)L" + base + ";";
        for (Object entry : cls.methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals("createFounder") || !method.desc.equals(descriptor)) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn.getOpcode() != Opcodes.ARETURN) continue;
                InsnList hook = new InsnList();
                for (int slot = 1; slot <= 5; slot++) hook.add(new VarInsnNode(Opcodes.ALOAD, slot));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "io/github/wdy/easytouch/mining/EzMinerAutoVein", "route",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayer;Ljava/lang/Object;)Ljava/lang/Object;", false));
                hook.add(new TypeInsnNode(Opcodes.CHECKCAST, base));
                method.instructions.insertBefore(insn, hook);
            }
            // No new branches or locals; preserve original frames.
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cls.accept(writer);
            System.out.println("[GTNH Easy Touch] Enabled EZMiner automatic GT vein routing");
            return writer.toByteArray();
        }
        System.err.println("[GTNH Easy Touch] WARNING: EZMiner auto vein patch did not match");
        return bytes;
    }
}
