package io.github.wdy.easytouch.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Skip only the optional login book block; retain toolkit/bauble synchronization. */
public final class ProgrammableHatchesTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"reobf.proghatches.main.MyMod".equals(transformedName)) return bytes;
        ClassNode cls = new ClassNode();
        new ClassReader(bytes).accept(cls, 0);
        for (Object entry : cls.methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals("join") || !method.desc.equals(
                    "(Lcpw/mods/fml/common/gameevent/PlayerEvent$PlayerLoggedInEvent;)V")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                AbstractInsnNode before = executable(call.getPrevious(), false);
                AbstractInsnNode after = executable(call.getNext(), true);
                if (!call.name.equals("getExtendedProperties")
                        || !call.desc.equals("(Ljava/lang/String;)Lnet/minecraftforge/common/IExtendedEntityProperties;")
                        || !(before instanceof LdcInsnNode)
                        || !"GET_PROGHATCHBOOK".equals(((LdcInsnNode) before).cst)
                        || !(after instanceof JumpInsnNode) || after.getOpcode() != Opcodes.IFNULL) continue;
                // Force the existing null branch. Stack types and all existing frames stay valid.
                InsnList skip = new InsnList();
                skip.add(new InsnNode(Opcodes.POP));
                skip.add(new InsnNode(Opcodes.ACONST_NULL));
                method.instructions.insert(call, skip);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                cls.accept(writer);
                System.out.println("[GTNH Easy Touch] Disabled ProgrammableHatches login tutorial book");
                return writer.toByteArray();
            }
        }
        System.err.println("[GTNH Easy Touch] WARNING: ProgrammableHatches login book patch did not match");
        return bytes;
    }

    private static AbstractInsnNode executable(AbstractInsnNode node, boolean forward) {
        while (node != null && node.getOpcode() < 0) node = forward ? node.getNext() : node.getPrevious();
        return node;
    }
}
