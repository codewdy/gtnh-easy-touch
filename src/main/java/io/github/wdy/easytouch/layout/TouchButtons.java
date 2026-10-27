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
    private final MappingMenu mappingMenu;
    private final Runnable reset;
    private final Minecraft mc = Minecraft.getMinecraft();
    private final KeyStateMerger.Source source;
    private final VirtualKeyboard keyboard = new VirtualKeyboard();
    private final VirtualMouse mouse = new VirtualMouse(keyboard);
    private final ChordState chords;
    private final MouseControl mouseControl;
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
        mouseControl = new MouseControl((code, down) -> {
            if (down) chords.press(Collections.singletonList(code));
            else chords.release(Collections.singletonList(code));
        }, mouse::motion);
        reset = () -> { cancel(); resetInputs.run(); TouchInput.cancelGuiTouch(); };
        mappingMenu = new MappingMenu(option -> {
            List<Integer> combination = keys(option);
            chords.press(combination);
            chords.release(combination);
        });
        menu = new GlobalTouchMenu(() -> {
            if (mouseControl.isOpen()) mouseControl.releaseInputs();
            else reset.run();
        }, item -> {
            boolean wasMouse = mouseControl.isOpen();
            reset.run();
            if (item == 1) config.toggle();
            else if (item == 2) config.toggleButtons();
            else if (item == 3 && !(mc.currentScreen instanceof LayoutEditor)) openRequested = true;
            else if (item == 5) config.toggleSecondaryTap();
            else if (item == 4) {
                if (wasMouse) mouseControl.reset();
                else if (available()) mouseControl.open();
            }
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
        return enabled && Display.isActive() && (!(mc.currentScreen instanceof LayoutEditor) || mouseControl.isOpen())
            && (mc.currentScreen != null || (mc.thePlayer != null && !mc.thePlayer.isDead && mc.inGameHasFocus && !mc.gameSettings.hideGUI));
    }
    public void syncKeyboard() { if (mc.currentScreen == null && mc.inGameHasFocus && Display.isActive()) keyboard.syncBindings(); }
    public boolean buttonsVisible() { return config.buttonsVisible; }
    public boolean mouseControlActive() { return mouseControl.isOpen(); }
    private boolean menuAvailable() {
        return enabled && Display.isActive() && (mc.currentScreen != null || mc.thePlayer != null && !mc.thePlayer.isDead && mc.inGameHasFocus);
    }
    public boolean visible(TouchLayout.Button b) { return mc.currentScreen == null ? b.world : b.other && (b.mode == 3 || !b.smartSecondary); }
    public boolean accept(TouchSample s) {
        if (s.kind == TouchSample.Kind.RESET || !menuAvailable()) { menu.reset(); cancel(); return false; }
        ScaledResolution r = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        sizeMenus(r.getScaledWidth(), r.getScaledHeight());
        if (available() && config.buttonsVisible && mappingMenu.accept(s, r.getScaledWidth(), r.getScaledHeight())) return true;
        if (menu.accept(s, r.getScaledWidth(), r.getScaledHeight())) return true;
        if (!available() || !config.buttonsVisible && !mouseControl.isOpen()) { cancel(); return false; }
        if (s.simulated() || !s.valid()) return false;
        String id = s.deviceId + ":" + s.fingerId;
        TouchLayout.Button b = contacts.get(id);
        if (b != null) {
            if (s.kind == TouchSample.Kind.UP || s.kind == TouchSample.Kind.CANCEL) {
                contacts.remove(id);
                if (b.mode == 3 && s.kind == TouchSample.Kind.UP
                        && b.hit((int)(s.x*r.getScaledWidth()), (int)(s.y*r.getScaledHeight()), r.getScaledWidth(), r.getScaledHeight())) {
                    reset.run();
                    mappingMenu.open(b);
                    return true;
                }
                if ((b.mode == 1 && !contacts.containsValue(b)) || s.kind == TouchSample.Kind.CANCEL) release(b);
            }
            return true;
        }
        // A contact stays with its button or touchpad until released.
        if (s.kind != TouchSample.Kind.DOWN)
            return mouseControl.isOpen() && mouseControl.accept(s, r.getScaledWidth(), r.getScaledHeight());
        if (buttonsVisible()) for (int i = layout.buttons.size() - 1; i >= 0; i--) {
            b = layout.buttons.get(i);
            if (!visible(b) || !b.hit((int)(s.x*r.getScaledWidth()), (int)(s.y*r.getScaledHeight()), r.getScaledWidth(), r.getScaledHeight())) continue;
            contacts.put(id, b);
            if (b.mode == 3) return true;
            if (b.mode == 2 && held.containsKey(b)) { release(b); return true; }
            if (!held.containsKey(b)) {
                List<Integer> combination = keys(b);
                if (!combination.isEmpty()) { held.put(b, combination); chords.press(combination); }
            }
            return true;
        }
        return mouseControl.isOpen() && mouseControl.accept(s, r.getScaledWidth(), r.getScaledHeight());
    }
    private void release(TouchLayout.Button b) {
        List<Integer> combination = held.remove(b);
        if (combination != null) chords.release(combination);
    }

    public int mouseFlags() { return mouse.flags(); }
    public boolean mouseHeld(int button) { return mouse.held(button); }
    public void flushMouse() { mouse.flush(); }
    public void flushMouseMotion() { mouse.flushMotion(); }
    public void cancel() {
        mappingMenu.reset();
        // Clear gesture/button ownership without changing the selected input mode.
        mouseControl.releaseInputs();
        mouse.cancel();
        for (int code : keyboard.cancel()) {
            KeyBinding.setKeyBindState(code, mc.currentScreen == null && Display.isActive() && (code < 0 ? TouchInput.mouseButtonDown(code+100) : VirtualKeyboard.physicalDown(code)));
            for (KeyBinding binding : mc.gameSettings.keyBindings) if (binding.getKeyCode() == code) while (binding.isPressed()) { }
        }
        chords.clear(); held.clear(); contacts.clear(); source.clear();
    }
    public void draw(int w, int h) {
        sizeMenus(w,h);
        if (available() && buttonsVisible())
        for (TouchLayout.Button b : layout.buttons) if (visible(b)) {
            drawButton(mc, b, w, h, held.containsKey(b), false);
            if (b.mode == 3 ? b.options.isEmpty() : keys(b).isEmpty()) mc.fontRenderer.drawStringWithShadow("!", b.left(w)+2, b.top(h)+2, 0xFF8888);
        }
        if (menuAvailable()) drawMenu(w, h);
        if (available() && config.buttonsVisible && mappingMenu.isOpen()) drawMappingMenu(w,h);
        if (available() && mouseControl.isOpen()) mouseControl.draw(w,h);
    }
    private static final int MENU_PADDING = 8;
    private String[] menuLabels() {
        return new String[]{"Smart: " + (config.enabled ? "开启" : "关闭"),
            config.buttonsVisible ? "隐藏所有按键" : "显示所有按键",
            mc.currentScreen instanceof LayoutEditor ? "正在配置按键映射" : "配置按键映射"};
    }
    private int textWidth(String label) { return (int)Math.ceil(mc.fontRenderer.getStringWidth(label)*.8f); }
    private int fittedMenuWidth(int textWidth, int screenWidth) {
        return Math.max(1,Math.min(screenWidth-12,Math.max(32,textWidth+MENU_PADDING*2)));
    }
    /** Use the same font measurements before drawing and hit testing, including after GUI resize. */
    private void sizeMenus(int w, int h) {
        menu.touchpadAvailable(available());
        int longest=0;
        for (String label : menuLabels()) longest=Math.max(longest,textWidth(label));
        menu.panelWidth(fittedMenuWidth(longest,w));
        if (!mappingMenu.isOpen()) return;
        longest=0;
        for (TouchLayout.Button option : mappingMenu.button().options) longest=Math.max(longest,textWidth(option.label));
        if (mappingMenu.button().options.isEmpty()) longest=textWidth("请先添加子选项");
        if (mappingMenu.pages(h)>1) longest=Math.max(longest,2*Math.max(textWidth("上一页"),textWidth("下一页"))+MENU_PADDING*2);
        mappingMenu.panelWidth(fittedMenuWidth(longest,w));
    }
    private void menuBackground(int x, int y, int width, int height) {
        Gui.drawRect(x,y,x+width,y+height,0xF0222E3B);
        Gui.drawRect(x,y,x+width,y+1,0xFF71879A);
        Gui.drawRect(x,y+height-1,x+width,y+height,0xFF71879A);
        Gui.drawRect(x,y,x+1,y+height,0xFF71879A);
        Gui.drawRect(x+width-1,y,x+width,y+height,0xFF71879A);
    }
    private void menuDivider(int x, int y, int width) {
        Gui.drawRect(x+1,y-1,x+width-1,y+1,0xFF8298AA);
    }
    private void drawMappingMenu(int w, int h) {
        int x=mappingMenu.left(w), width=mappingMenu.panelWidth(), rows=mappingMenu.rows(h);
        List<TouchLayout.Button> options=mappingMenu.button().options;
        menuBackground(x,mappingMenu.top(h),width,mappingMenu.panelHeight(h));
        for (int row=0; row<mappingMenu.visibleRows(h); row++) {
            int index=mappingMenu.page()*rows+row;
            if (index >= options.size()) break;
            int y=mappingMenu.top(h)+row*MappingMenu.ROW;
            if (row > 0) menuDivider(x,y,width);
            TouchLayout.Button option=options.get(index);
            Circle.text(mc,option.label,x+MENU_PADDING,y+7,width-MENU_PADDING*2,false,keys(option).isEmpty() ? 0xFF8888 : 0xECF4FA);
        }
        if (options.isEmpty()) Circle.text(mc,"请先添加子选项",x+MENU_PADDING,mappingMenu.top(h)+7,width-MENU_PADDING*2,false,0xFF8888);
        int y=mappingMenu.top(h)+mappingMenu.visibleRows(h)*MappingMenu.ROW;
        if (mappingMenu.pages(h)>1) {
            menuDivider(x,y,width);
            Gui.drawRect(x+width/2,y+1,x+width/2+1,y+MappingMenu.ROW-1,0xFF8298AA);
            String[] labels={"上一页", "下一页"};
            for (int i=0;i<2;i++) {
                int color=(i == 0 && mappingMenu.page() == 0 || i == 1 && mappingMenu.page()+1 == mappingMenu.pages(h)) ? 0x888888 : 0xECF4FA;
                Circle.text(mc,labels[i],x+i*width/2+MENU_PADDING,y+7,width/2-MENU_PADDING*2,false,color);
            }
        }
    }

    private void drawMenu(int w, int h) {
        int x = menu.left(w), y = menu.top(h);
        if (available()) {
            drawTouchpadToggle(menu.touchpadLeft(w),y);
            int sx = menu.secondaryLeft(w);
            Circle.button(sx,y,GlobalTouchMenu.SIZE,config.secondaryTap ? 0xC028594F : 0x40253747,config.secondaryTap,false);
            Circle.text(mc,config.secondaryTap ? "副" : "主",sx+GlobalTouchMenu.SIZE/2f,y+GlobalTouchMenu.SIZE/2f-3,GlobalTouchMenu.SIZE-4,true,0xECF4FA);
        }
        Circle.button(x, y, GlobalTouchMenu.SIZE, 0x40253747,menu.isOpen(),false);
        for (int i=0; i<3; i++) Gui.drawRect(x+6, y+6+i*5, x+18, y+8+i*5, 0xFFE0E8EF);
        if (!menu.isOpen()) return;
        String[] labels = menuLabels();
        menuBackground(menu.panelLeft(w),menu.panelTop(h),menu.panelWidth(),labels.length*GlobalTouchMenu.ROW);
        for (int i=0; i<labels.length; i++) {
            x = menu.panelLeft(w); y = menu.panelTop(h)+i*GlobalTouchMenu.ROW;
            if (i > 0) menuDivider(x,y,menu.panelWidth());
            Circle.text(mc,labels[i],x+MENU_PADDING,y+7,menu.panelWidth()-MENU_PADDING*2,false,0xECF4FA);
        }
    }
    private void drawTouchpadToggle(int x,int y) {
        boolean active=mouseControl.isOpen();
        Circle.button(x,y,GlobalTouchMenu.SIZE,active ? 0xC028594F : 0x40253747,active,false);
        int color=active ? 0xFF88FFBB : 0xFFE0E8EF;
        // Touch surface outline and the divided click strip below it.
        Gui.drawRect(x+5,y+5,x+19,y+6,color);
        Gui.drawRect(x+5,y+5,x+6,y+19,color);
        Gui.drawRect(x+18,y+5,x+19,y+19,color);
        Gui.drawRect(x+5,y+18,x+19,y+19,color);
        Gui.drawRect(x+6,y+14,x+18,y+15,color);
        Gui.drawRect(x+11,y+15,x+12,y+18,color);
    }
    public static void drawButton(Minecraft mc, TouchLayout.Button b, int w, int h, boolean active, boolean selected) {
        int x = b.left(w), y = b.top(h), alpha = b.opacity * 255 / 100;
        Circle.button(x,y,b.size,(alpha << 24) | (active ? 0x28594F : 0x253747),active,selected);
        Circle.text(mc,b.label,x+b.size/2f,y+b.size/2f-3,b.size-4,true,0xECF4FA);
    }
    @SubscribeEvent public void render(TickEvent.RenderTickEvent e) { keyboard.refresh(); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void gui(GuiOpenEvent e) {
        menu.reset();
        cancel();
        // A non-null destination is another GUI, including the layout editor.
        // The following queued RESET must also only clear inputs, not the mode.
        if (e.gui == null) mouseControl.reset();
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!menuAvailable()) menu.reset();
        if (!available() || !config.buttonsVisible && !mouseControl.isOpen()) cancel();
        keyboard.mouseModifiers(Collections.emptySet());
        if (openRequested) { openRequested = false; cancel(); mc.displayGuiScreen(new LayoutEditor(this, mc.currentScreen)); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void guiDraw(GuiScreenEvent.DrawScreenEvent.Post e) { draw(e.gui.width, e.gui.height); }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void hud(RenderGameOverlayEvent.Post e) {
        if (e.type == RenderGameOverlayEvent.ElementType.ALL && mc.currentScreen == null)
            draw(e.resolution.getScaledWidth(), e.resolution.getScaledHeight());
    }
}
