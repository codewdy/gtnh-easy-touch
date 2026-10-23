package io.github.wdy.easytouch.input;

/** Holds sneak through a normal player update before allowing a native use action on the following tick. */
public final class SneakUseGesture {
    private enum Phase { IDLE, SNEAKING, READY, USING, BLOCKED }

    private static final int MAX_SNEAK_TICKS = 10;
    private Phase phase = Phase.IDLE;
    private boolean keyDown;
    private boolean firstUseTick;
    private int waitingTicks;

    /** A queued press preserves quick taps even if the physical key was already released. */
    public void updateKey(boolean pressed, boolean down) {
        boolean newPress = pressed || (down && !keyDown);
        keyDown = down;
        if (phase == Phase.BLOCKED) {
            if (!down) phase = Phase.IDLE;
            return;
        }
        if (phase == Phase.IDLE && newPress) {
            phase = Phase.SNEAKING;
            waitingTicks = 0;
        } else if (phase == Phase.USING && !down && !firstUseTick) {
            phase = Phase.IDLE;
        }
    }

    /** Returns true exactly when the caller should enqueue the initial vanilla use click. */
    public boolean beginTick(boolean playerSneaking) {
        if (phase != Phase.READY) return false;
        if (!playerSneaking) {
            phase = Phase.SNEAKING;
            return false;
        }
        phase = Phase.USING;
        firstUseTick = true;
        return true;
    }

    /** Called after the player update and its normal sneak packet have completed. */
    public void endTick(boolean sneakUpdated) {
        if (phase == Phase.SNEAKING) {
            if (sneakUpdated) phase = Phase.READY;
            else if (++waitingTicks >= MAX_SNEAK_TICKS) cancel();
        } else if (phase == Phase.USING) {
            firstUseTick = false;
            if (!keyDown) phase = Phase.IDLE;
            else if (!sneakUpdated) phase = Phase.SNEAKING;
        }
    }

    public boolean holdsSneak() {
        return phase == Phase.SNEAKING || phase == Phase.READY || phase == Phase.USING;
    }

    public boolean holdsUse() { return phase == Phase.USING; }

    /** GUI/focus/world boundaries discard pending taps and require a release before restarting. */
    public void cancel() {
        phase = Phase.BLOCKED;
        firstUseTick = false;
    }
}
