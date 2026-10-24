package io.github.wdy.easytouch.nei;

/** Keeps the opening input and the server's inventory snapshot out of the crafting operation. */
public final class DeferredCraftingLaunch {
    public interface Context {
        boolean sourceCurrent();
        boolean targetCurrent();
        boolean inputReleased();
        void restoreAndRequestSync();
        boolean syncApplied();
        void closeSync();
        boolean startCrafting();
        void removeCraftingGroup();
        void syncTimedOut();
    }

    private enum State { WAIT_RELEASE, WAIT_SYNC, DONE }

    private final Context context;
    private final long timeoutMillis;
    private final boolean removeGroup;
    private State state = State.WAIT_RELEASE;
    private long deadline;

    public DeferredCraftingLaunch(Context context, long timeoutMillis, boolean removeGroup) {
        this.context = context;
        this.timeoutMillis = timeoutMillis;
        this.removeGroup = removeGroup;
    }

    /** Called only at client tick END, after GUI input and incoming packets have been processed. */
    public void tick(long now) {
        if (state == State.WAIT_RELEASE) {
            if (!context.sourceCurrent()) {
                cancel();
            } else if (context.inputReleased()) {
                state = State.WAIT_SYNC;
                deadline = now + timeoutMillis;
                context.restoreAndRequestSync();
                if (!context.targetCurrent()) cancel();
            }
            // Never start inside the screen switch, even when a local server responds immediately.
            return;
        }
        if (state != State.WAIT_SYNC) return;
        if (!context.targetCurrent()) {
            cancel();
        } else if (!context.syncApplied()) {
            if (now >= deadline) {
                cancel();
                context.syncTimedOut();
            }
        } else if (context.inputReleased()) {
            cancel();
            if (context.startCrafting() && removeGroup) context.removeCraftingGroup();
        }
    }

    public boolean done() { return state == State.DONE; }

    public void cancel() {
        if (state == State.DONE) return;
        state = State.DONE;
        context.closeSync();
    }
}
