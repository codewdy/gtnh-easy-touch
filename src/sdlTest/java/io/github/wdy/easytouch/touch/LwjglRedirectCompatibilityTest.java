package io.github.wdy.easytouch.touch;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import com.gtnewhorizons.retrofuturabootstrap.api.ClassNodeHandle;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer.Context;
import me.eigenraven.lwjgl3ify.rfb.transformers.LwjglRedirectTransformer;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import static org.junit.Assert.*;

/** Runs the RC2 redirect transformer without initializing Minecraft or a display. */
public class LwjglRedirectCompatibilityTest {
    private static final String ROOT = "io.github.wdy.easytouch.";

    private byte[] classBytes(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toByteArray();
        }
    }

    private boolean redirect(String name, ClassNodeHandle handle) {
        LwjglRedirectTransformer transformer = new LwjglRedirectTransformer();
        if (!transformer.shouldTransformClass(null, Context.LCL_WITH_TRANSFORMS, null, name, handle)) return false;
        boolean changed = transformer.transformClassIfNeeded(null, Context.LCL_WITH_TRANSFORMS, null, name, handle);
        if (changed) handle.markDirty();
        return changed;
    }

    private Set<String> references(byte[] bytes) {
        final Set<String> names = new HashSet<>();
        new ClassReader(bytes).accept(new ClassRemapper(new ClassWriter(0), new Remapper() {
            @Override public String map(String name) {
                names.add(name);
                return name;
            }
        }), 0);
        return names;
    }

    @Test public void sdlCallsKeepTheirRealPackageAfterTheRuntimeTransformer() throws Exception {
        for (String simpleName : new String[] { "TouchInput", "SdlTouchSource" }) {
            String name = ROOT + "touch." + simpleName;
            byte[] original = classBytes(name);
            ClassNodeHandle handle = new ClassNodeHandle(original);
            assertFalse(name + " must bypass legacy LWJGL redirection", redirect(name, handle));
            assertArrayEquals(original, handle.computeBytes());
            Set<String> names = references(handle.computeBytes());
            assertTrue(names.toString(), names.stream().anyMatch(n -> n.startsWith("org/lwjgl/sdl/")));
            for (String ref : names) {
                assertFalse(ref, ref.startsWith("org/lwjglx/sdl/"));
                assertFalse("An aware class must explicitly use the lwjgl3ify input shim: " + ref,
                    ref.startsWith("org/lwjgl/input/") || ref.equals("org/lwjgl/opengl/Display"));
            }
            if (simpleName.equals("TouchInput")) {
                assertTrue(names.contains("org/lwjglx/input/Keyboard"));
                assertTrue(names.contains("org/lwjglx/opengl/Display"));
            }
        }
    }

    @Test public void removingTheMarkerReproducesTheReportedMissingSdlClass() throws Exception {
        String name = ROOT + "touch.TouchInput";
        ClassNode node = new ClassNode();
        new ClassReader(classBytes(name)).accept(node, 0);
        if (node.visibleAnnotations != null) {
            node.visibleAnnotations.removeIf(a -> a.desc.equals("Lme/eigenraven/lwjgl3ify/api/Lwjgl3Aware;"));
        }
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        ClassNodeHandle handle = new ClassNodeHandle(writer.toByteArray());
        assertTrue(redirect(name, handle));
        assertTrue(references(handle.computeBytes()).contains("org/lwjglx/sdl/SDLInit"));
    }

    @Test public void ordinaryMouseControlsStillReceiveLegacyRedirection() throws Exception {
        String name = ROOT + "input.SmartMouseInput";
        ClassNodeHandle handle = new ClassNodeHandle(classBytes(name));
        assertTrue(redirect(name, handle));
        Set<String> names = references(handle.computeBytes());
        assertTrue(names.contains("org/lwjglx/input/Mouse"));
        assertFalse(names.contains("org/lwjgl/input/Mouse"));
    }
}
