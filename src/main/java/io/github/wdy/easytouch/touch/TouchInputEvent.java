package io.github.wdy.easytouch.touch;

import cpw.mods.fml.common.eventhandler.Event;

/** Posted on MinecraftForge.EVENT_BUS on the client thread; not cancelable; consumed marks contacts owned by custom buttons.
 * RESET releases all contacts (focus loss, world change, overflow or backend failure).
 * Coordinates are SDL window-relative, with the origin at the top left, usually in 0..1.
 * Check sample.simulated() before using a contact as evidence of real touch input.
 */
public final class TouchInputEvent extends Event {
    public boolean consumed;
    public final TouchSample sample;
    public TouchInputEvent(TouchSample sample) { this.sample = sample; }
}
