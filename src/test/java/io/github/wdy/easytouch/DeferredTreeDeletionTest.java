package io.github.wdy.easytouch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import io.github.wdy.easytouch.nei.DeferredTreeDeletion;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeferredTreeDeletionTest {
    private final FakeContext context = new FakeContext();
    private final DeferredTreeDeletion deletion = new DeferredTreeDeletion(context);

    @Test
    public void waitsForClickReleaseThenExitsAndDeletesOnlyOnce() {
        deletion.tick();
        deletion.tick();
        assertTrue(context.calls.isEmpty());
        context.released = true;
        deletion.tick();
        deletion.tick();
        assertEquals(Arrays.asList("exit", "removeGroup"), context.calls);
        assertTrue(deletion.done());
    }

    @Test
    public void vetoedExitPreservesTheDisplayedGroup() {
        context.released = true;
        context.exitAccepted = false;
        deletion.tick();
        assertEquals(Arrays.asList("exit"), context.calls);
        assertTrue(deletion.done());
    }

    @Test
    public void leavingTreeOrReplacingGroupBeforeReleaseCancelsDeletion() {
        context.sourceCurrent = false;
        context.released = true;
        deletion.tick();
        assertTrue(context.calls.isEmpty());
        assertTrue(deletion.done());
    }

    @Test
    public void cancelledActionCannotDeleteOnLaterRelease() {
        deletion.cancel();
        context.released = true;
        deletion.tick();
        assertTrue(context.calls.isEmpty());
        assertTrue(deletion.done());
    }

    private static final class FakeContext implements DeferredTreeDeletion.Context {
        boolean sourceCurrent = true, released, exitAccepted = true;
        final List<String> calls = new ArrayList<>();
        @Override public boolean sourceCurrent() { return sourceCurrent; }
        @Override public boolean inputReleased() { return released; }
        @Override public boolean exitTree() { calls.add("exit"); return exitAccepted; }
        @Override public void removeGroup() { calls.add("removeGroup"); }
    }
}
