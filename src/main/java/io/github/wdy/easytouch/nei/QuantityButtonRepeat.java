package io.github.wdy.easytouch.nei;

/** Time-based repeat without bursts after stalls; the caller handles the initial press. */
final class QuantityButtonRepeat {
    private boolean active;
    private long next;

    void begin(long now) { active = true; next = now + 400; }
    void cancel() { active = false; }

    boolean poll(long now) {
        if (!active || now < next) return false;
        next = now + 100;
        return true;
    }
}
