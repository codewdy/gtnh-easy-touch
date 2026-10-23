package io.github.wdy.easytouch;

import appeng.api.parts.IPartHost;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import io.github.wdy.easytouch.matching.MatcherRegistry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.config.Configuration;
import org.junit.Test;
import static org.junit.Assert.*;

public class OptionalMatchersTest extends MatcherTestSupport {
    private MatcherRegistry rules() throws Exception {
        Configuration file = configuration();
        file.get("matchers.gregtech", "machines", new String[] {ApprovedMachine.class.getName()});
        file.get("matchers.applied_energistics", "parts", new String[] {Terminal.class.getName()});
        return MatcherRegistry.builtIns(file);
    }

    @Test
    public void gtMatcherRequiresApprovedFamilyInsideAGtMachineHost() throws Exception {
        MatcherRegistry rules = rules();
        assertTrue(rules.matches(tile(new BaseMetaTileEntity(new ApprovedMachineVariant()))));
        assertFalse(rules.matches(tile(new BaseMetaTileEntity(new Cable()))));
        assertFalse(rules.matches(tile(new BaseMetaTileEntity(null))));
        assertFalse(rules.matches(tile(new UnrelatedPipeHost())));
    }

    @Test
    public void aeMatcherOwnsLocalCoordinatesAndRejectsTheContainingCable() throws Exception {
        MatcherRegistry rules = rules();
        Host host = new Host();
        TestTarget target = tile(host);
        target.hit = new MovingObjectPosition(-12, 64, 200, 2, Vec3.createVectorHelper(-11.25, 64.5, 200.125));
        host.selected = new Selected(new Terminal());
        assertTrue(rules.matches(target));
        assertEquals(0.75, host.local.xCoord, 0.0001);
        assertEquals(0.5, host.local.yCoord, 0.0001);
        assertEquals(0.125, host.local.zCoord, 0.0001);
        host.selected = new Selected(new Cable());
        assertFalse(rules.matches(target));
        host.selected = new Selected(null);
        assertFalse(rules.matches(target));
        host.selected = null;
        assertFalse(rules.matches(target));
    }

    public static class ApprovedMachine { }
    public static class ApprovedMachineVariant extends ApprovedMachine { }
    public static class Terminal { }
    public static class Cable { }
    public static class UnrelatedPipeHost extends TileEntity {
        public Object getMetaTileEntity() { throw new AssertionError("Must not probe an unrelated host"); }
    }
    public static class Selected {
        public final Object part;
        public Selected(Object part) { this.part = part; }
    }
    public static class Host extends TileEntity implements IPartHost {
        Selected selected;
        Vec3 local;
        public Object selectPart(Vec3 position) { local = position; return selected; }
    }
}
