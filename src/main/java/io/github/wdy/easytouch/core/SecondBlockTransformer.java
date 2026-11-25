package io.github.wdy.easytouch.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Delay second-block START packets when its natural mining time is below 500ms. */
public final class SecondBlockTransformer implements IClassTransformer {
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !"net.minecraft.client.multiplayer.PlayerControllerMP".equals(transformedName)) return bytes;
        ClassNode cls = new ClassNode();
        new ClassReader(bytes).accept(cls, 0);
        String hookClass = "io/github/wdy/easytouch/input/SecondBlockGuard";
        String cooldownField = null;
        for (Object entry : cls.fields) {
            FieldNode field = (FieldNode) entry;
            if (field.desc.equals("I") && (field.name.equals("blockHitDelay") || field.name.equals("field_78781_i")
                    || (cls.name.equals("bje") && field.name.equals("i")))) cooldownField = field.name;
        }
        if (cooldownField == null) throw new IllegalStateException("Easy Touch blockHitDelay field did not match");
        int matches = 0;
        for (Object entry : cls.methods) {
            MethodNode method = (MethodNode) entry;
            if ((method.name.equals("clickBlock") || method.name.equals("func_78743_b")
                    || method.name.equals("onPlayerDamageBlock") || method.name.equals("func_78759_c")
                    || (cls.name.equals("bje") && (method.name.equals("b") || method.name.equals("c")))) && method.desc.equals("(IIII)V")) {
                InsnList hook = new InsnList();
                LabelNode proceed = new LabelNode();
                if (method.name.equals("onPlayerDamageBlock") || method.name.equals("func_78759_c")
                        || (cls.name.equals("bje") && method.name.equals("c"))) {
                    // Keep vanilla's cooldown ticking; it already prevents damage while positive.
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new FieldInsnNode(Opcodes.GETFIELD, cls.name, cooldownField, "I"));
                    hook.add(new JumpInsnNode(Opcodes.IFGT, proceed));
                }
                for (int slot = 1; slot <= 3; slot++) hook.add(new VarInsnNode(Opcodes.ILOAD, slot));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hookClass, "allowClick", "(III)Z", false));
                hook.add(new JumpInsnNode(Opcodes.IFNE, proceed));
                hook.add(new InsnNode(Opcodes.RETURN));
                hook.add(proceed);
                hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(hook);
                matches++;
            }
            if ((method.name.equals("onPlayerDestroyBlock") || method.name.equals("func_78751_a") || (cls.name.equals("bje") && method.name.equals("a"))) && method.desc.equals("(IIII)Z")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() != Opcodes.IRETURN) continue;
                    InsnList hook = new InsnList();
                    hook.add(new InsnNode(Opcodes.DUP));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hookClass, "onDestroyed", "(Z)V", false));
                    method.instructions.insertBefore(insn, hook);
                }
                matches++;
            }
        }
        if (matches != 3) {
            throw new IllegalStateException("Easy Touch second-block hooks did not match: expected 3, found "
                + matches + " in " + cls.name);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        return writer.toByteArray();
    }
}
