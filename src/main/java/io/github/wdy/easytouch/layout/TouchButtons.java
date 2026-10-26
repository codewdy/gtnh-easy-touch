package io.github.wdy.easytouch.layout;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;
import io.github.wdy.easytouch.Action;
import io.github.wdy.easytouch.TouchConfig;
import io.github.wdy.easytouch.touch.TouchInput;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.wdy.easytouch.input.KeyStateMerger;
import io.github.wdy.easytouch.touch.TouchSample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.apache.logging.log4j.LogManager;

public final class TouchButtons {
    public static TouchButtons instance;
    public TouchLayout layout;
    private final File file;
    private final boolean enabled;
    private final TouchConfig config;
    private final Supplier<Action> secondary;
    private final GlobalTouchMenu menu;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final KeyStateMerger.Source source;
    private final VirtualKeyboard keyboard = new VirtualKeyboard();
    private final VirtualMouse mouse = new VirtualMouse(keyboard);
    private final ChordState chords;
    private final Map<String, TouchLayout.Button> contacts = new HashMap<String, TouchLayout.Button>();
    private final Map<TouchLayout.Button, List<Integer>> held = new IdentityHashMap<TouchLayout.Button, List<Integer>>();
    public boolean openRequested;
    public String loadError;

    public TouchButtons(TouchConfig config, KeyStateMerger.Source source, Runnable resetInputs, Supplier<Action> secondary) {
        this.secondary = secondary;
        this.config = config;
        this.file = config.layoutFile; this.enabled = config.touchEnabled; this.source = source;
        chords = new ChordState((code, down) -> {
            if (code < 0) mouse.event(code, down); else keyboard.event(code, down);
            if (down && mc.currentScreen == null) source.holdCode(code);
            else if (!down) source.releaseCode(code);
        });
        Runnable reset = () -> { cancel(); resetInputs.run(); TouchInput.cancelGuiTouch(); };
        menu = new GlobalTouchMenu(reset, item -> {
            reset.run();
            if (item == 1) config.toggle();
            else if (item == 2) config.toggleButtons();
            else if (item == 3 && !(mc.currentScreen instanceof LayoutEditor)) openRequested = true;
        });
        try { layout = TouchLayout.load(file); }
        catch (IOException ex) {
            layout = new TouchLayout(); loadError = "布局读取失败；保存将覆盖原文件";
            LogManager.getLogger("GTNH Easy Touch").warn("Cannot load touch layout", ex);
        }
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }
    public void save(TouchLayout next) throws IOException { next.save(file); cancel(); layout = next.copy(); loadError = null; }
    public List<Integer> keys(TouchLayout.Button b) {
        if (b.smartSecondary) {
            if (mc.currentScreen != null || mc.thePlayer == null || mc.theWorld == null) return Collections.emptyList();
            Set<Integer> queued = keyboard.queuedKeys();
            boolean shift = queued.contains(42) || queued.contains(54) || queued.contains(mc.gameSettings.keyBindSneak.getKeyCode());
            int code = ((!shift && secondary.get() == Action.ATTACK) ? mc.gameSettings.keyBindAttack : mc.gameSettings.keyBindUseItem).getKeyCode();
            return ChordState.valid(code) ? Collections.singletonList(code) : Collections.emptyList();
        }
        if (!b.keys.isEmpty()) return ChordState.normalize(b.keys);
        int code = key(b);
        return ChordState.valid(code) ? Collections.singletonList(code) : Collections.emptyList();
    }
    public int key(TouchLayout.Button b) {
        if (b.binding.isEmpty()) return b.key;
        for (KeyBinding binding : mc.gameSettings.keyBindings)
            if (binding.getKeyDescription().equals(b.binding)) return binding.getKeyCode();
        return 0;
    }
    private boolean available() {
        return enabled && Display.isActive() && !(mc.currentScreen instanceof LayoutEditor)
            && (mc.currentScreen != null || (mc.thePlayer != null && !mc.thePlayer.isDead && mc.inGameHasFocus && !mc.gameSettings.hideGUI));
    }
    public void syncKeyboard() { if (mc.currentScreen == null && mc.inGameHasFocus && Display.isActive()) keyboard.syncBindings(); }
    public boolean buttonsVisible() { return config.buttonsVisible; }
    private boolean menuAvailable() {
        return enabled && Display.isActive() && (mc.currentScreen != null || mc.thePlayer != null && !mc.thePlayer.isDead && mc.inGameHasFocus);
    }
    public boolean visible(TouchLayout.Button b) { return mc.currentScreen == null ? b.world : b.other && !b.smartSecondary; }
    public boolean accept(TouchSample s) {
        if (s.kind == TouchSample.Kind.RESET || !menuAvailable()) { menu.reset(); cancel(); return false; }
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        if (menu.accept(s, r.getScaledWidth(), r.getScaledHeight())) return true;
        if (!available() || !config.buttonsVisible) { cancel(); return false; }
        if (s.simulated() || !s.valid()) return false;
        String id = s.deviceId + ":" + s.fingerId;
        TouchLayout.Button b = contacts.get(id);
        if (b != null) {
            if (s.kind == TouchSample.Kind.UP || s.kind == TouchSample.Kind.CANCEL) {
                contacts.remove(id);
                if ((b.mode == 1 && !contacts.containsValue(b)) || s.kind == TouchSample.Kind.CANCEL) release(b);
            }
            return true;
        }
        if (s.kind != TouchSample.Kind.DOWN) return false;
        for (int i = layout.buttons.size() - 1; i >= 0; i--) {
            b = layout.buttons.get(i);
            if (!visible(b) || !b.hit((int)(s.x*r.getScaledWidth()), (int)(s.y*r.getScaledHeight()), r.getScaledWidth(), r.getScaledHeight())) continue;
            contacts.put(id, b);
            if (b.mode == 2 && held.containsKey(b)) { release(b); return true; }
            if (!held.containsKey(b)) {
                List<Integer> combination = keys(b);
                if (!combination.isEmpty()) { held.put(b, combination); chords.press(combination); }
            }
            return true;
        }
        return false;
    }
    private void release(TouchLayout.Button b) {
        List<Integer> combination = held.remove(b);
        if (combination != null) chords.release(combination);
    }

    public int mouseFlags() { return mouse.flags(); }
    public boolean mouseHeld(int button) { return mouse.held(button); }
    public void flushMouse() { mouse.flush(); }
    public void cancel() {
        mouse.cancel();
        for (int code : keyboard.cancel()) {
            KeyBinding.setKeyBindState(code, mc.currentScreen == null && Display.isActive() && (code < 0 ? TouchInput.mouseButtonDown(code+100) : VirtualKeyboard.physicalDown(code)));
            for (KeyBinding binding : mc.gameSettings.keyBindings) if (binding.getKeyCode() == code) while (binding.isPressed()) { }
        }
        chords.clear(); held.clear(); contacts.clear(); source.clear();
    }
    public void draw(int w, int h) {
        if (available() && config.buttonsVisible)
        for (TouchLayout.Button b : layout.buttons) if (visible(b)) {
            drawButton(mc, b, w, h, held.containsKey(b), false);
            if (keys(b).isEmpty()) mc.fontRenderer.drawStringWithShadow("!", b.left(w)+2, b.top(h)+2, 0xFF8888);
        }
        if (menuAvailable()) drawMenu(w, h);
    }
    private void drawMenu(int w, int h) {
        int x = menu.left(w), y = menu.top(h);
        Circle.button(x, y, GlobalTouchMenu.SIZE, 0x40253747,menu.isOpen(),false);
        for (int i=0; i<3; i++) Gui.drawRect(x+6, y+6+i*5, x+18, y+8+i*5, 0xFFE0E8EF);
        if (!menu.isOpen()) return;
        String[] labels = {"Smart: " + (config.enabled ? "开启" : "关闭"),
            config.buttonsVisible ? "隐藏所有按键" : "显示所有按键",
            mc.currentScreen instanceof LayoutEditor ? "正在配置按键映射" : "配置按键映射"};
        for (int i=0; i<labels.length; i++) {
            x = menu.panelLeft(w); y = menu.panelTop(h)+i*GlobalTouchMenu.ROW;
            Circle.button(x+GlobalTouchMenu.WIDTH-GlobalTouchMenu.SIZE, y, GlobalTouchMenu.SIZE, 0x40253747, i == 0 ? config.enabled : i == 1 && config.buttonsVisible,false);
            String icon = new String[]{"S", "V", "K"}[i];
            Circle.text(mc,icon,x+GlobalTouchMenu.WIDTH-12,y+9,18,true,0xECF4FA);
            Circle.text(mc,labels[i],x,y+9,GlobalTouchMenu.WIDTH-28,false,0xECF4FA);
        }
    }
    public static void drawButton(Minecraft mc, TouchLayout.Button b, int w, int h, boolean active, boolean selected) {
        int x = b.left(w), y = b.top(h), alpha = b.opacity * 255 / 100;
        Circle.button(x,y,b.size,(alpha << 24) | (active ? 0x28594F : 0x253747),active,selected);
        Circle.text(mc,b.label,x+b.size/2f,y+b.size/2f-3,b.size-4,true,0xECF4FA);
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e) { keyboard.refresh(); }
    @SubscribeEvent public void gui(GuiOpenEvent e) { menu.reset(); cancel(); }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!menuAvailable()) menu.reset();
        if (!available() || !config.buttonsVisible) cancel();
        keyboard.mouseModifiers(Collections.emptySet());
        if (openRequested) { openRequested = false; cancel(); mc.displayGuiScreen(new LayoutEditor(this, mc.currentScreen)); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void guiDraw(GuiScreenEvent.DrawScreenEvent.Post e) { draw(e.gui.width, e.gui.height); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void hud(RenderGameOverlayEvent.Post e) {
        if (e.type == RenderGameOverlayEvent.ElementType.ALL && mc.currentScreen == null)
            draw(e.resolution.getScaledWidth(), e.resolution.getScaledHeight());
    }
}
