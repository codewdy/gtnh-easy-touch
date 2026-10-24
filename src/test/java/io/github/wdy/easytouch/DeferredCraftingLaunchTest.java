package io.github.wdy.easytouch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.github.wdy.easytouch.nei.DeferredCraftingLaunch;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeferredCraftingLaunchTest {
    private final FakeContext context = new FakeContext();
    private final DeferredCraftingLaunch launch = new DeferredCraftingLaunch(context, 10000, true);

    @Test
    public void keepsButtonReleaseInTreeAndWaitsForInventoryBeforeCrafting() {
        launch.tick(0);
        launch.tick(500);
        assertTrue(context.calls.isEmpty());
        context.released = true;
        launch.tick(600);
        assertEquals(Arrays.asList("restore"), context.calls);
        launch.tick(900);
        launch.tick(3000); // A slow server must not be replaced with a guessed delay.
        assertEquals(Arrays.asList("restore"), context.calls);
        context.synced = true;
        launch.tick(3100);
        launch.tick(3200);
        assertEquals(Arrays.asList("restore", "close", "craft", "removeGroup"), context.calls);
        assertTrue(launch.done());
    }

    @Test
    public void immediateReplyStillCannotStartInsideScreenSwitch() {
        context.released = true;
        context.synced = true;
        launch.tick(0);
        assertEquals(Arrays.asList("restore"), context.calls);
        launch.tick(50);
        assertEquals(Arrays.asList("restore", "close", "craft", "removeGroup"), context.calls);
    }

    @Test
    public void leavingTreeOrLosingFocusBeforeReleaseCancelsLaunch() {
        context.source = false;
        context.released = true;
        launch.tick(0);
        assertEquals(Arrays.asList("close"), context.calls);
        assertTrue(launch.done());
    }

    @Test
    public void replacingContainerWhileWaitingCancelsEvenIfReplyArrives() {
        context.released = true;
        launch.tick(0);
        context.target = false;
        context.synced = true;
        launch.tick(50);
        assertEquals(Arrays.asList("restore", "close"), context.calls);
        assertTrue(launch.done());
    }

    @Test
    public void missingReplyTimesOutWithoutMovingItems() {
        context.released = true;
        launch.tick(0);
        launch.tick(9999);
        assertFalse(launch.done());
        launch.tick(10000);
        context.synced = true;
        launch.tick(10050);
        assertEquals(Arrays.asList("restore", "close", "timeout"), context.calls);
    }

    @Test
    public void manualActionCancelsPendingCraftAndLateReplyCannotRestartIt() {
        context.released = true;
        launch.tick(0);
        launch.cancel();
        launch.cancel();
        context.synced = true;
        launch.tick(50);
        assertEquals(Arrays.asList("restore", "close"), context.calls);
    }

    @Test
    public void vetoedScreenSwitchNeverStartsOrWaitsForSync() {
        context.released = true;
        context.target = false;
        launch.tick(0);
        assertTrue(launch.done());
        assertEquals(Arrays.asList("restore", "close"), context.calls);
    }

    @Test
    public void refusedCraftLeavesGroupBookmarked() {
        context.released = true;
        context.synced = true;
        context.acceptCraft = false;
        launch.tick(0);
        launch.tick(50);
        launch.tick(100);
        assertTrue(launch.done());
        assertEquals(Arrays.asList("restore", "close", "craft"), context.calls);
    }

    @Test
    public void craftOnlyStartsOnceAndNeverRemovesTheGroup() {
        DeferredCraftingLaunch keep = new DeferredCraftingLaunch(context, 10000, false);
        context.released = true;
        keep.tick(0);
        keep.tick(500);
        assertEquals(Arrays.asList("restore"), context.calls);
        context.synced = true;
        keep.tick(550);
        keep.tick(600);
        assertTrue(keep.done());
        assertEquals(Arrays.asList("restore", "close", "craft"), context.calls);
    }

    @Test
    public void exceptionStartingCraftLeavesGroupBookmarked() {
        context.released = true;
        context.synced = true;
        context.failCraft = true;
        launch.tick(0);
        try {
            launch.tick(50);
            fail("Expected the simulated NEI startup failure");
        } catch (IllegalStateException expected) {
            assertEquals("NEI startup failed", expected.getMessage());
        }
        launch.tick(100);
        assertTrue(launch.done());
        assertEquals(Arrays.asList("restore", "close", "craft"), context.calls);
    }

    private static final class FakeContext implements DeferredCraftingLaunch.Context {
        boolean source = true, target = true, released, synced;
        boolean acceptCraft = true, failCraft;
        final List<String> calls = new ArrayList<>();
        @Override public boolean sourceCurrent() { return source; }
        @Override public boolean targetCurrent() { return target; }
        @Override public boolean inputReleased() { return released; }
        @Override public void restoreAndRequestSync() { calls.add("restore"); }
        @Override public boolean syncApplied() { return synced; }
        @Override public void closeSync() { calls.add("close"); }
        @Override public boolean startCrafting() {
            calls.add("craft");
            if (failCraft) throw new IllegalStateException("NEI startup failed");
            return acceptCraft;
        }
        @Override public void removeCraftingGroup() { calls.add("removeGroup"); }
        @Override public void syncTimedOut() { calls.add("timeout"); }
    }
}
