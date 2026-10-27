package io.github.wdy.easytouch.input;

import io.github.wdy.easytouch.touch.TouchSample;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

/** Select on touch-down and reserve the contact until release, including outside the hotbar. */
public final class TouchHotbar {
    private final Set<String> contacts = new HashSet<>();
    private final IntConsumer select;

    public TouchHotbar(IntConsumer select) { this.select = select; }

    public void cancel() { contacts.clear(); }

    public boolean accept(TouchSample sample, int width, int height, boolean enabled) {
        if (!enabled || sample.kind == TouchSample.Kind.RESET) {
            cancel();
            return false;
        }
        if (sample.simulated()) return false;
        String id = sample.deviceId + ":" + sample.fingerId;
        if (contacts.contains(id)) {
            if (sample.kind == TouchSample.Kind.UP || sample.kind == TouchSample.Kind.CANCEL) contacts.remove(id);
            return true;
        }
        if (sample.kind != TouchSample.Kind.DOWN || !sample.valid()) return false;
        // Vanilla's nine 20-pixel slots, excluding the one-pixel outer frame.
        float x = sample.x * width - (width / 2 - 90);
        float y = sample.y * height;
        if (x < 0 || x >= 180 || y < height - 21 || y >= height - 1) return false;
        contacts.add(id);
        select.accept((int) (x / 20));
        return true;
    }
}
