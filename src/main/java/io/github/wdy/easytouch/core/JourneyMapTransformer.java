package io.github.wdy.easytouch.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Optional integration: only the verified JourneyMap 5.2 method shapes are patched. */
public final class JourneyMapTransformer implements IClassTransformer {
    private static final String HOOK = "io/github/wdy/easytouch/map/JourneyMapScale";
    private static final String TEX = "journeymap/client/render/texture/TextureImpl";
    private static final String DRAW = "journeymap/client/render/draw/DrawUtil";
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        boolean entity = "journeymap.client.render.draw.DrawUtil".equals(transformedName);
        boolean waypoint = "journeymap.client.render.draw.DrawWayPointStep".equals(transformedName);
        boolean navigator = "com.gtnewhorizons.navigator.api.model.steps.UniversalRenderStep".equals(transformedName);
        boolean node = "com.dyonovan.tcnodetracker.integration.navigator.ThaumcraftNodeRenderStep".equals(transformedName);
        boolean mini = "journeymap.client.ui.minimap.DisplayVars".equals(transformedName);
        if (bytes == null || (!entity && !waypoint && !navigator && !node && !mini)) return bytes;
        ClassNode cls = new ClassNode();
        new ClassReader(bytes).accept(cls, 0);
        int changes = 0;
        for (Object item : cls.methods) {
            MethodNode method = (MethodNode) item;
            if (entity && method.name.equals("drawEntity")
                    && method.desc.equals("(DDDZL" + TEX + ";FFDZ)V")) {
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.FLOAD, 9));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "scale", "(F)F", false));
                code.add(new VarInsnNode(Opcodes.FSTORE, 9));
                method.instructions.insert(code);
                changes++;
            }
            if (navigator && method.name.equals("drawJourneyMap") && method.desc.equals("(DDFDDDD)V")) {
                // Both preRender (size/offset/hitbox) and draw receive the same scaled size.
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.FLOAD, 5));
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "overlayScale", "(FLjava/lang/Object;)F", false));
                code.add(new VarInsnNode(Opcodes.FSTORE, 5));
                code.add(new VarInsnNode(Opcodes.DLOAD, 10));
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "overlayFontScale", "(DLjava/lang/Object;)D", false));
                code.add(new VarInsnNode(Opcodes.DSTORE, 10));
                method.instructions.insert(code);
                changes += 2;
            }
            if (node && method.name.equals("draw") && method.desc.equals("(DDFD)V")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn instanceof LdcInsnNode && Double.valueOf(32.0).equals(((LdcInsnNode) insn).cst)) {
                        InsnList code = new InsnList();
                        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        code.add(new FieldInsnNode(Opcodes.GETFIELD, cls.name, "isJourneyMap", "Z"));
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "nodeAspectScale", "(DZ)D", false));
                        method.instructions.insert(insn, code);
                        changes++;
                    }
                }
            }
            if (mini && method.name.equals("<init>")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (!(insn instanceof FieldInsnNode)) continue;
                    FieldInsnNode field = (FieldInsnNode) insn;
                    if (field.getOpcode() == Opcodes.PUTFIELD && field.owner.equals(cls.name)
                        && (field.name.equals("minimapWidth") || field.name.equals("minimapHeight"))) {
                        InsnList code = new InsnList();
                        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        code.add(new FieldInsnNode(Opcodes.GETFIELD, cls.name, "displayWidth", "I"));
                        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        code.add(new FieldInsnNode(Opcodes.GETFIELD, cls.name, "displayHeight", "I"));
                        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "minimapDimension", "(III)I", false));
                        method.instructions.insertBefore(insn, code);
                        changes++;
                    }
                }
            }
            if (!waypoint) continue;
            // Scale dimensions at their source: centering, label offsets and hit testing agree.
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals(TEX) && call.desc.equals("()I")
                        && (call.name.equals("getWidth") || call.name.equals("getHeight"))) {
                    method.instructions.insert(call,
                        new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "dimension", "(I)I", false));
                    changes++;
                } else if (call.owner.equals(DRAW) && call.name.equals("drawColoredImage")
                        && call.desc.equals("(L" + TEX + ";ILjava/lang/Integer;DDD)V")) {
                    int angle = method.maxLocals;
                    method.maxLocals += 2;
                    InsnList code = new InsnList();
                    code.add(new VarInsnNode(Opcodes.DSTORE, angle));
                    code.add(new InsnNode(Opcodes.FCONST_1));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "scale", "(F)F", false));
                    code.add(new VarInsnNode(Opcodes.DLOAD, angle));
                    method.instructions.insertBefore(call, code);
                    call.desc = "(L" + TEX + ";ILjava/lang/Integer;DDFD)V";
                    changes++;
                }
            }
        }
        if (changes == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cls.accept(writer);
        System.out.println("[GTNH Easy Touch] JourneyMap icon scale hooks: " + transformedName + " (" + changes + ")");
        return writer.toByteArray();
    }
}
