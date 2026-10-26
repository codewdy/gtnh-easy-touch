package io.github.wdy.easytouch.touch;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.wdy.easytouch.TouchConfig;
import io.github.wdy.easytouch.input.TouchMouse;
import static org.lwjgl.sdl.SDLMouse.SDL_BUTTON_LMASK;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;
import static org.lwjgl.sdl.SDLTimer.SDL_GetTicksNS;
import org.lwjglx.opengl.Display;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_EVENTS;
import static org.lwjgl.sdl.SDLInit.SDL_WasInit;

/** Owns the native source and forwards copied events on the client tick. */
// Keep SDL3 references intact; legacy input/window calls must explicitly use the lwjgl3ify shim.
@Lwjgl3Aware
public final class TouchInput {
    private static TouchInput installed;
    private SdlTouchMouseFilter mouseFilter;
    private static final Logger LOG = LogManager.getLogger("GTNH Easy Touch");
    private final Minecraft mc = Minecraft.getMinecraft();
    private final KeyBinding debug = new KeyBinding("key.easytouch.touchDebug", Keyboard.KEY_NONE,
        "key.categories.easytouch");
    final TouchQueue queue = new TouchQueue(1024);
    final TouchTracker tracker = new TouchTracker();
    SdlTouchSource source;
    String status;
    private final boolean logEvents;
    private boolean openRequested;
    private final TouchMouse guiMouse = new TouchMouse(new GuiTouchMouse(mc));

    private TouchInput(TouchConfig config) {
        logEvents = config.touchLogEvents;
        try {
            if (SDL_WasInit(SDL_INIT_EVENTS) == 0 || Display.sdlWindowId == 0) {
                throw new IllegalStateException("SDL3 events or the lwjgl3ify SDL window are not initialized");
            }
            if (!config.touchEnabled) {
                status = "disabled";
                return;
            }
            source = new SdlTouchSource(queue, () -> Display.sdlWindowId);
            mouseFilter = new SdlTouchMouseFilter(() -> Display.sdlWindowId);
            status = "ready";
            LOG.info("[Touch] SDL3 event watch ready; {}. Open /easytouch touch to inspect contacts.",
                source.enabledTypes);
        } catch (RuntimeException | LinkageError error) {
            throw new IllegalStateException("GTNH Easy Touch requires lwjgl3ify 3.0.37+ with a working SDL3 backend "
                + "(GTNH 2.9.0 RC2 modern Java edition). Cannot initialize touch input.", error);
        }
    }

    public static void install(TouchConfig config) {
        final TouchInput input = new TouchInput(config);
        installed = input;
        ClientRegistry.registerKeyBinding(input.debug);
        MinecraftForge.EVENT_BUS.register(input);
        FMLCommonHandler.instance().bus().register(input);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "easytouch"; }
            @Override public String getCommandUsage(ICommandSender sender) { return "/easytouch touch"; }
            @Override public int getRequiredPermissionLevel() { return 0; }
            @Override public boolean canCommandSenderUseCommand(ICommandSender sender) { return true; }
            @Override public void processCommand(ICommandSender sender, String[] args) {
                if (args.length != 1 || !"touch".equalsIgnoreCase(args[0])) {
                    throw new WrongUsageException(getCommandUsage(sender));
                }
                // Chat closes its GUI after submitting a command; open on the following tick.
                input.openRequested = true;
            }
        });
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        if (debug.getKeyCode() > 0 && Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent()
            && Keyboard.getEventKey() == debug.getKeyCode() && mc.currentScreen == null && Display.isActive()) {
            openRequested = true;
        }
    }

    public static long nanoTime() { return installed == null ? 0 : SDL_GetTicksNS(); }

    public static boolean mouseButtonDown(int button) {
        TouchInput input = installed;
        return input != null && input.mouseFilter != null && "ready".equals(input.status)
            ? input.mouseFilter.physicalButtonDown(button) : Mouse.isButtonDown(button);
    }

    private void syncMouseButtons() {
        if (mouseFilter != null && "ready".equals(status)) {
            // SDL's aggregate includes the synthetic mouse we filtered. Publish only real buttons + our GUI owner.
            Mouse.sdlMouseButtonFlags = mouseFilter.physicalButtons() | (guiMouse.held() ? SDL_BUTTON_LMASK : 0);
        }
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (!Display.isActive() || mc.currentScreen == null) guiMouse.cancel();
        syncMouseButtons();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        while (debug.isPressed()) { }
        queue.focus(Display.isActive());
        if (source != null && "ready".equals(status)
            && (source.failure() != null || mouseFilter.failure() != null)) {
            status = "failed";
            LOG.warn("[Touch] SDL3 input stopped after a callback error.",
                source.failure() != null ? source.failure() : mouseFilter.failure());
            guiMouse.cancel();
            mouseFilter.close();
            try { source.close(); }
            catch (RuntimeException | LinkageError error) { LOG.warn("[Touch] Cannot unregister event watch.", error); }
        }
        for (TouchSample sample : queue.drain()) {
            tracker.accept(sample);
            guiMouse.accept(sample);
            MinecraftForge.EVENT_BUS.post(new TouchInputEvent(sample));
            if (logEvents && sample.kind != TouchSample.Kind.MOVE) {
                LOG.info("[Touch] {} device={} finger={} simulated={} window={} x={} y={}",
                    sample.kind, sample.deviceId, sample.fingerId, sample.simulated(), sample.windowId, sample.x, sample.y);
            }
        }
        if (!Display.isActive() || mc.currentScreen == null) guiMouse.cancel();
        syncMouseButtons();
        if (openRequested) {
            openRequested = false;
            if (Display.isActive()) mc.displayGuiScreen(new TouchDebugScreen(this, mc.currentScreen));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void gui(GuiOpenEvent event) {
        // Drop queued GUI contacts before returning to gameplay (and vice versa).
        queue.reset();
        guiMouse.cancel();
        syncMouseButtons();
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            queue.reset();
            guiMouse.cancel();
            syncMouseButtons();
        }
    }
}
