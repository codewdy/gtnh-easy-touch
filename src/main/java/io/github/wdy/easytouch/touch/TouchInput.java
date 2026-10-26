package io.github.wdy.easytouch.touch;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.wdy.easytouch.TouchConfig;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjglx.input.Keyboard;
import org.lwjglx.opengl.Display;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_EVENTS;
import static org.lwjgl.sdl.SDLInit.SDL_WasInit;

/** Owns the native source and forwards copied events on the client tick. */
// Keep SDL3 references intact; legacy input/window calls must explicitly use the lwjgl3ify shim.
@Lwjgl3Aware
public final class TouchInput {
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

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (debug.isPressed()) { }
        queue.focus(Display.isActive());
        if (source != null && "ready".equals(status) && source.failure() != null) {
            status = "failed";
            LOG.warn("[Touch] SDL3 event forwarding stopped after a callback error.", source.failure());
            try { source.close(); }
            catch (RuntimeException | LinkageError error) { LOG.warn("[Touch] Cannot unregister event watch.", error); }
        }
        for (TouchSample sample : queue.drain()) {
            tracker.accept(sample);
            MinecraftForge.EVENT_BUS.post(new TouchInputEvent(sample));
            if (logEvents && sample.kind != TouchSample.Kind.MOVE) {
                LOG.info("[Touch] {} device={} finger={} simulated={} window={} x={} y={}",
                    sample.kind, sample.deviceId, sample.fingerId, sample.simulated(), sample.windowId, sample.x, sample.y);
            }
        }
        if (openRequested) {
            openRequested = false;
            if (Display.isActive()) mc.displayGuiScreen(new TouchDebugScreen(this, mc.currentScreen));
        }
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) queue.reset();
    }
}
