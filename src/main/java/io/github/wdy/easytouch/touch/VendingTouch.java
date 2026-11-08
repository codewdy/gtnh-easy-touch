package io.github.wdy.easytouch.touch;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Optional vending integration; purchases always use the original server-validated request. */
public final class VendingTouch {
    private static final VendingTouch INSTANCE = new VendingTouch();
    private static final String VM = "com.cubefury.vendingmachine.";
    private GuiScreen owner;
    private Object widget, panel, scroll;
    private boolean held, moved, open, failed;
    private long finger, device;
    private int detailOffset;
    private int sx, sy, lastY, left, top, width, bottom;
    public static void install() { MinecraftForge.EVENT_BUS.register(INSTANCE); }
    public static boolean accept(TouchSample s) { return INSTANCE.touch(s); }
    private static Object call(Object o, String n, Object... args) throws ReflectiveOperationException {
        for (Method m : o.getClass().getMethods()) if (m.getName().equals(n) && m.getParameterTypes().length == args.length) {
            try { return m.invoke(o, args); } catch (IllegalArgumentException mismatch) { /* overload */ }
        }
        throw new NoSuchMethodException(n);
    }
    private static Object field(Object o, String n) throws ReflectiveOperationException {
        for (Class<?> c=o.getClass(); c!=null; c=c.getSuperclass()) try {
            Field f=c.getDeclaredField(n); f.setAccessible(true); return f.get(o);
        } catch (NoSuchFieldException absent) { }
        throw new NoSuchFieldException(n);
    }
    private Object display() throws ReflectiveOperationException { return call(widget, "getDisplay"); }
    private boolean available(Object d) throws ReflectiveOperationException {
        return (Boolean) field(d,"enabled") && (Boolean) call(d,"isTradeableNow",call(panel,"getWalletMode"))
            && (Boolean) call(field(widget,"vm"),"getActive");
    }
    private boolean touch(TouchSample s) {
        if (s.kind==TouchSample.Kind.RESET) { held=open=false; owner=null; return false; }
        GuiScreen gui=Minecraft.getMinecraft().currentScreen;
        if (gui!=owner) { held=open=false; owner=gui; }
        if (gui==null || failed) return false;
        int x=(int)(s.x*gui.width), y=(int)(s.y*gui.height);
        try {
            if (s.kind==TouchSample.Kind.DOWN) {
                if (held) return true;
                if (!open) {
                    if (!gui.getClass().getName().startsWith("com.cleanroommc.modularui.")) return false;
                    Object screen=Class.forName("com.cleanroommc.modularui.screen.ClientScreenHandler").getMethod("getMuiScreen").invoke(null);
                    if (screen==null || call(call(screen,"getScreenWrapper"),"getGuiScreen")!=gui) return false;
                    Object ctx=call(screen,"getContext");
                    call(ctx,"updateState",x,y,0f);
                    for (Object p : (Iterable<?>)call(call(screen,"getPanelManager"),"getOpenPanels")) {
                        Method m=Class.forName("com.cleanroommc.modularui.screen.ModularPanel").getDeclaredMethod("findHoveredWidgets");
                        m.setAccessible(true); m.invoke(p);
                    }
                    // Publish the newly hit-tested panel lists to the context before reading them.
                    call(ctx,"onFrameUpdate");
                    widget=null; scroll=null; detailOffset=0;
                    // MUI 2.3.92 getAllHovered() recurses forever at iterator exhaustion.
                    // Snapshot the underlying finite hit list instead.
                    for (Object hit:new ArrayList<>((List<?>)field(ctx,"hovered"))) {
                        Object w=call(hit,"getElement");
                        if (w!=null && w.getClass().getName().equals(VM+"blocks.gui.TradeItemDisplayWidget")) { widget=w; break; }
                    }
                    if (widget!=null) {
                        panel=field(widget,"rootPanel");
                        for(Object w=widget; w!=null; w=call(w,"getParent")) {
                            try { call(w,"getScrollData"); scroll=w; break; } catch(NoSuchMethodException absent) { }
                        }
                    } else {
                        // Backgrounds are below the pointer but need not be hoverable widgets.
                        // Only capture the vending GUI's own trade lists, never wallet/inventory lists.
                        panel=null;
                        List<?> below=new ArrayList<>((List<?>)field(ctx,"belowMouse"));
                        for(Object p:(Iterable<?>)call(call(screen,"getPanelManager"),"getOpenPanels")) {
                            if (!p.getClass().getName().equals(VM+"blocks.gui.TradeMainPanel")) continue;
                            for(Object list:(Iterable<?>)field(field(p,"gui"),"tradeLists")) {
                                for(Object hit:below) {
                                    if(call(hit,"getElement")==list) { scroll=list; panel=p; break; }
                                }
                                if(scroll!=null) break;
                            }
                            if(scroll!=null) break;
                        }
                        if(scroll==null) return false;
                    }
                }
                held=true; moved=false; finger=s.fingerId; device=s.deviceId;
                sx=x; sy=lastY=y; return true;
            }
            if (!held) return open;
            if (finger!=s.fingerId || device!=s.deviceId) return true;
            if (Math.abs(x-sx)>6 || Math.abs(y-sy)>6) moved=true;
            if (s.kind==TouchSample.Kind.MOVE && moved && open) {
                if(Math.abs(lastY-y)>=14) { detailOffset=Math.max(0,detailOffset+(lastY-y)/14); lastY=y; }
            }
            if (s.kind==TouchSample.Kind.MOVE && moved && !open && scroll!=null) {
                call(call(scroll,"getScrollData"),"scrollBy",call(scroll,"getScrollArea"),lastY-y);
                lastY=y;
            }
            if (s.kind==TouchSample.Kind.UP) {
                held=false;
                // A tap opens details on release; the same release cannot also buy.
                if (!moved && !open && widget!=null) {
                    open=true;
                } else if (open && !moved) {
                    if(x>=left+8 && x<left+width-8 && y>=bottom-28 && y<bottom-6) {
                        Object d=display(); if (available(d)) { call(panel,"attemptPurchase",d); open=false; }
                    } else if (x<left || x>=left+width || y<top || y>=bottom || y<top+22) open=false;
                }
            }
            if(s.kind==TouchSample.Kind.CANCEL) held=open=false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed=true; held=open=false;
            cpw.mods.fml.common.FMLLog.log(org.apache.logging.log4j.Level.WARN,e,"[GTNH Easy Touch] Vending integration unavailable");
            return true;
        }
    }
    private void items(List<String> lines,Object d,String key,String label) throws ReflectiveOperationException {
        for(Object b:(Iterable<?>)field(d,key)) {
            ItemStack stack=(ItemStack)call(b,"getBaseStack");
            lines.add(label+stack.getDisplayName()+" × "+field(b,"stackSize"));
        }
    }
    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Post e) {
        if(e.gui!=owner || Minecraft.getMinecraft().currentScreen!=owner || failed) return;
        if(!open) return;
        try {
            Object d=display(), mode=call(panel,"getWalletMode");
            List<String> lines=new ArrayList<>();
            items(lines,d,"toItems","获得：");
            Object manager=Class.forName(VM+"trade.TradeManager").getField("INSTANCE").get(null);
            Object names=Class.forName(VM+"storage.NameCache").getField("INSTANCE").get(null);
            Object wallet=call(manager,"getWallet",call(names,"getUUIDFromPlayer",Minecraft.getMinecraft().thePlayer),mode);
            lines.add("钱包："+call(mode,"getLocalizedName"));
            for(Object c:(Iterable<?>)field(d,"fromCurrency")) {
                ItemStack coin=(ItemStack)call(c,"getItemRepresentation");
                lines.add(coin.getDisplayName()+"：需要 "+field(c,"value")+" / 拥有 "+(wallet==null?"未知":call(wallet,"getCount",field(c,"type"))));
            }
            items(lines,d,"fromItems","消耗："); items(lines,d,"ncItems","需持有：");
            lines.add("当前冷却："+field(d,"cooldownText"));
            boolean enabled=available(d);
            width=Math.min(320,owner.width-8); left=(owner.width-width)/2;
            top=Math.max(4,(owner.height-(lines.size()*14+60))/2); bottom=Math.min(owner.height-4,top+lines.size()*14+60);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); GL11.glPushMatrix();
            try {
                GL11.glTranslatef(0,0,600); GL11.glDisable(GL11.GL_DEPTH_TEST);
                Gui.drawRect(left,top,left+width,bottom,0xF018202B);
                text("商品详情                       ×",left+8,top+7,0xFFFFFF);
                int y=top+26;
                detailOffset=Math.min(detailOffset,Math.max(0,lines.size()-Math.max(1,(bottom-top-60)/14)));
                for(int i=detailOffset;i<lines.size();i++) { if(y>bottom-42) break; text(lines.get(i),left+8,y,0xEEEEEE); y+=14; }
                Gui.drawRect(left+8,bottom-28,left+width-8,bottom-6,enabled?0xFF367D48:0xFF555555);
                text(enabled?"购买":"暂不可购买",left+16,bottom-21,enabled?0xFFFFFF:0xAAAAAA);
            } finally { GL11.glPopMatrix(); GL11.glPopAttrib(); }
        } catch(ReflectiveOperationException | RuntimeException error) {
            open=false; cpw.mods.fml.common.FMLLog.log(org.apache.logging.log4j.Level.WARN,error,"[GTNH Easy Touch] Vending details unavailable");
        }
    }
    private void text(String s,int x,int y,int color) {
        Minecraft.getMinecraft().fontRenderer.drawString(Minecraft.getMinecraft().fontRenderer.trimStringToWidth(s,width-16),x,y,color);
    }
}
