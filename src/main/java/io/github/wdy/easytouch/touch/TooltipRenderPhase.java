package io.github.wdy.easytouch.touch;

import java.util.function.Consumer;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.GuiScreenEvent;

/** Registered during construction, before tooltip mods register their pre-init render listeners. */
public final class TooltipRenderPhase {
    private static Consumer<GuiScreenEvent.DrawScreenEvent.Post> renderer;
    public static void setRenderer(Consumer<GuiScreenEvent.DrawScreenEvent.Post> callback) { renderer = callback; }
    public static void registerEarly() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new TooltipRenderPhase());
    }
    private TooltipRenderPhase() { }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (renderer != null) renderer.accept(event);
    }
}
