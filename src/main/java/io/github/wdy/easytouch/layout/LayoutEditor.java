package io.github.wdy.easytouch.layout;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;

/** One draft spans the canvas, properties and paginated mapping picker. */
public final class LayoutEditor extends GuiScreen {
    private final TouchButtons owner;
    private final GuiScreen parent;
    private final TouchLayout draft;
    private int selected = -1, screen, option = -1, optionPage;
    private int toolbarX = 4, toolbarY = 4;
    private boolean other, dragging, keypad, smartChoice;
    private int dragX, dragY;
    private GuiTextField name;
    private String message = "";
    private final List<Integer> selectedKeys = new ArrayList<Integer>();
    public LayoutEditor(TouchButtons owner, GuiScreen parent) {
        this.owner = owner; this.parent = parent; draft = owner.layout.copy();
        if (owner.loadError != null) message = owner.loadError;
    }
    private TouchLayout.Button rootButton() { return selected >= 0 && selected < draft.buttons.size() ? draft.buttons.get(selected) : null; }
    private TouchLayout.Button current() {
        TouchLayout.Button root = rootButton();
        return root != null && option >= 0 ? root.options.get(option) : root;
    }
    private int optionRows() { return Math.max(1, (height - 100) / 26); }
    private void button(int id, int x, int y, int w, String text) { buttonList.add(new CircleControlButton(id, x, y, w, text)); }
    private void toolbarButton(int id, int width, String label) {
        if (toolbarX + width > this.width - 4) { toolbarX = 4; toolbarY += 24; }
        button(id, toolbarX, toolbarY, width, label);
        toolbarX += width + 4;
    }
    @Override public void initGui() {
        buttonList.clear(); dragging = false; toolbarX = toolbarY = 4;
        if (screen == 0) {
            toolbarButton(1, 64, "新增"); toolbarButton(2, 64, "编辑"); toolbarButton(3, 64, "删除");
            toolbarButton(4, 100, "预览: " + (other ? "其他" : "世界"));
            toolbarButton(5, 80, "选择下一个");
            toolbarButton(6, 68, "保存"); toolbarButton(7, 72, "取消");
            return;
        }
        int x = width/2-145, y = 52;
        if (screen == 1) toolbarButton(18, 100, option >= 0 ? "返回子选项" : "返回布局");
        TouchLayout.Button b = current();
        if (screen == 1 && b != null) {
            name = new GuiTextField(fontRendererObj, x, y, 290, 20); name.setMaxStringLength(40); name.setText(b.label);
            button(10, x, y+26, 290, b.mode == 3 ? "配置子选项 (" + b.options.size() + ")" : "映射: " + target(b));
            if (option >= 0) { button(34, x, y+50, 290, "删除此子选项"); return; }
            button(11, x, y+50, 290, "触发: " + (b.mode == 3 ? "弹出菜单" : b.mode == 2 ? "点击切换" : "按住触发"));
            button(12, x, y+74, 142, "世界: " + (b.world ? "显示" : "隐藏"));
            button(13, x+148, y+74, 142, "其他: " + (b.other ? "显示" : "隐藏"));
            button(14, x, y+98, 142, "大小 -"); button(15, x+148, y+98, 142, "大小 + ("+b.size+")");
            button(16, x, y+122, 142, "不透明度 -"); button(17, x+148, y+122, 142, "不透明度 + ("+b.opacity+"%)");
        } else if (screen == 1 && selected == -2) {
            button(19, x, y+26, 290, "轮盘: " + (draft.wheelVisible ? "显示" : "隐藏"));
            button(14, x, y+54, 142, "缩小"); button(15, x+148, y+54, 142, "放大");
        } else if (screen == 3 && b != null) {
            toolbarButton(30, 100, "返回按键");
            toolbarButton(31, 100, "新增子选项");
            toolbarButton(32, 72, "上一页"); toolbarButton(33, 72, "下一页");
            optionPage = Math.min(optionPage, Math.max(0, (b.options.size()-1)/optionRows()));
            for (int i=optionPage*optionRows(); i<Math.min(b.options.size(), (optionPage+1)*optionRows()); i++)
                button(2000+i, x, 74+(i%optionRows())*26, 290, b.options.get(i).label + " / " + target(b.options.get(i)));
        } else if (screen == 2) {
            int[][] main = {
                {1,59,60,61,62,63,64,65,66,67,68,87,88,-200},
                {41,2,3,4,5,6,7,8,9,10,11,12,13,14},
                {15,16,17,18,19,20,21,22,23,24,25,26,27,43},
                {58,30,31,32,33,34,35,36,37,38,39,40,28},
                {42,44,45,46,47,48,49,50,51,52,53,54},
                {29,219,56,57,184,220,157,203,200,208,205,-100,-99,-98}
            };
            int[][] extra = {
                {183,70,197,210,199,201,211,207,209,-100,-99,-98,-200},
                {69,181,55,74}, {71,72,73,78}, {75,76,77}, {79,80,81,156}, {82,83}
            };
            int size=Math.min(32,Math.min((width-20)/14,(height-82)/6)), gap=2;
            int[][] rows=keypad ? extra : main;
            for (int row=0;row<rows.length;row++) {
                int start=(width-rows[row].length*size)/2;
                for (int col=0;col<rows[row].length;col++) {
                    int code=rows[row][col];
                    buttonList.add(new KeyboardKeyButton(code,start+col*size,68+row*size,size-gap,keyLabel(code),code == -200 ? smartChoice : selectedKeys.contains(code)));
                }
            }
            toolbarButton(20, 80, keypad ? "主键盘" : "数字/导航");
            toolbarButton(21, 58, "清空");
            toolbarButton(22, 58, "确定");
            toolbarButton(23, 58, "取消");
        }
    }
    private String keyLabel(int key) {
        switch(key) {
            case -200: return "副";
            case -100: return "左"; case -99: return "右"; case -98: return "中";
            case 1: return "Esc"; case 14: return "Bk"; case 15: return "Tab";
            case 28: case 156: return "Ent"; case 29: return "Ctrl"; case 157: return "RC";
            case 42: return "Sh"; case 54: return "RS"; case 56: return "Alt"; case 184: return "RA";
            case 57: return "Sp"; case 58: return "Cap"; case 219: case 220: return "Win";
            case 200: return "↑"; case 203: return "←"; case 205: return "→"; case 208: return "↓";
            case 210: return "Ins"; case 211: return "Del"; case 199: return "Hm"; case 207: return "End";
            case 201: return "Pu"; case 209: return "Pd"; case 183: return "Prt"; case 197: return "Pau";
            case 69: return "Num"; case 70: return "Scr";
            default: return Keyboard.getKeyName(key).replace("NUMPAD", "N");
        }
    }
    private String chordLabel(List<Integer> keys) {
        StringBuilder text=new StringBuilder();
        for(int key:ChordState.normalize(keys)) { if(text.length()>0) text.append("+"); text.append(key < 0 ? "鼠标"+keyLabel(key)+"键" : Keyboard.getKeyName(key)); }
        return text.length() == 0 ? "未设置" : text.toString();
    }
    private String target(TouchLayout.Button b) {
        if (b.mode == 3) return "菜单 (" + b.options.size() + " 项)";
        if (b.smartSecondary) return "Smart 副键（Shift 时使用/右键）";
        return !b.keys.isEmpty() ? chordLabel(b.keys) : b.binding.isEmpty() ? (b.key == 0 ? "未设置" : b.key < 0 ? "鼠标"+keyLabel(b.key)+"键" : Keyboard.getKeyName(b.key)) : I18n.format(b.binding);
    }
    private void captureName() { if (screen == 1 && current() != null && name != null) current().label = name.getText(); }
    @Override protected void actionPerformed(GuiButton btn) {
        captureName();
        TouchLayout.Button b = current();
        switch (btn.id) {
            case 1: b = new TouchLayout.Button(); b.world = !other; b.other = other; draft.buttons.add(b); selected = draft.buttons.size()-1; screen = 1; break;
            case 2: if (b != null || selected == -2) screen = 1; break;
            case 3: if (b != null) { draft.buttons.remove(selected); selected = -1; } else if (selected == -2) draft.wheelVisible = false; break;
            case 4: other = !other; break;
            case 5: selected = selected == -2 ? (draft.buttons.isEmpty() ? -2 : 0) : selected+1 >= draft.buttons.size() ? -2 : selected+1; break;
            case 6:
                try { owner.save(draft); mc.displayGuiScreen(parent); }
                catch (IOException ex) { message = "保存失败: " + ex.getMessage(); }
                return;
            case 7: mc.displayGuiScreen(parent); return;
            case 10: if (b.mode == 3) { optionPage=0; screen=3; break; } selectedKeys.clear(); smartChoice=b.smartSecondary; if (!smartChoice) selectedKeys.addAll(owner.keys(b)); screen = 2; break;
            case 11: b.mode = b.mode % 3 + 1; break;
            case 12: b.world = !b.world; break;
            case 13: b.other = !b.other; break;
            case 14: if (b != null) b.size = Math.max(24,b.size-4); else draft.wheelScale = Math.max(.5f,draft.wheelScale-.1f); break;
            case 15: if (b != null) b.size = Math.min(100,b.size+4); else draft.wheelScale = Math.min(2,draft.wheelScale+.1f); break;
            case 16: b.opacity = Math.max(10,b.opacity-5); break;
            case 17: b.opacity = Math.min(100,b.opacity+5); break;
            case 18: screen = option >= 0 ? 3 : 0; option=-1; break;
            case 19: draft.wheelVisible = !draft.wheelVisible; break;
            case 20: keypad = !keypad; break;
            case 21: selectedKeys.clear(); smartChoice=false; break;
            case 22:
                b.smartSecondary=smartChoice; b.keys=ChordState.normalize(selectedKeys); b.binding=""; b.key=b.keys.isEmpty() ? 0 : b.keys.get(b.keys.size()-1);
                b.label=smartChoice ? "副" : b.keys.isEmpty() ? "按键" : chordLabel(b.keys);
                if (smartChoice) { b.world=true; b.other=false; }
                screen=1;
                break;
            case 23: screen = 1; break;
            case 30: screen=1; break;
            case 31: b.options.add(new TouchLayout.Button()); option=b.options.size()-1; screen=1; break;
            case 32: optionPage=Math.max(0,optionPage-1); break;
            case 33: optionPage++; break;
            case 34: rootButton().options.remove(option); option=-1; screen=3; break;
            case 800: smartChoice=!smartChoice; selectedKeys.clear(); break;
            default:
                if (btn.id >= 2000 && screen == 3) { option=btn.id-2000; screen=1; }
                else if (btn.id >= 900 && screen == 2) {
                    smartChoice=false;
                    Integer key=btn.id-1000;
                    if (!selectedKeys.remove(key)) selectedKeys.add(key);
                }
        }
        initGui();
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        drawDefaultBackground();
        if (screen == 0) {
            for (int i=0; i<draft.buttons.size(); i++) {
                TouchLayout.Button b = draft.buttons.get(i);
                if ((other ? b.other : b.world) || selected == i) TouchButtons.drawButton(mc,b,width,height,false,selected==i);
            }
            if (!other && (draft.wheelVisible || selected == -2)) {
                int r = Math.round(wheelRadius()), x = Math.round(wheelX()), y = Math.round(wheelY());
                Circle.draw(x-r,y-r,r*2,selected == -2 ? 0x66706030 : 0x33405060);
                drawCenteredString(fontRendererObj,"WASD",x,y-4,0xFFFFFF);
            }
            String status = selected == -2 ? "已选: WASD 轮盘" : current() == null ? "选择按键后拖动；编辑设置映射和显示模式" : "已选: " + current().label + " / " + target(current());
            drawRect(0, 0, width, toolbarY+36, 0x88202730);
            drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(status,width-8),width/2,toolbarY+24,0xFFFFFF);
        } else {
            drawCenteredString(fontRendererObj,screen == 3 ? "子选项：点选编辑；选择后只按一次" : screen == 2 ? "点选多个键组成组合键；点圆形按钮确认" : option >= 0 ? "子选项属性（执行一次）" : "按键属性",width/2,screen == 3 ? 56 : 32,0xFFFFFF);
            if (screen == 2) drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(smartChoice ? "Smart 副键：反向操作；Shift 时右键" : chordLabel(selectedKeys),width-16),width/2,46,0x88FFBB);
            if (screen == 1 && current() != null) name.drawTextBox();
        }
        super.drawScreen(mx,my,partial);
        if (!message.isEmpty()) drawCenteredString(fontRendererObj,message,width/2,height-50,0xFF8888);
    }
    private float wheelRadius() { return Math.min(48f, Math.min(width*.18f,height*.22f))*draft.wheelScale; }
    private float wheelX() { float r=wheelRadius(); return draft.wheelX < 0 ? r+12 : r+draft.wheelX*Math.max(0,width-2*r); }
    private float wheelY() { float r=wheelRadius(); return draft.wheelY < 0 ? height-r-36 : r+draft.wheelY*Math.max(0,height-2*r); }
    @Override protected void mouseClicked(int x,int y,int button) {
        if (screen == 1 && current() != null) name.mouseClicked(x,y,button);
        for (Object obj : buttonList) { GuiButton b = (GuiButton)obj; if (b.mousePressed(mc,x,y)) { super.mouseClicked(x,y,button); return; } }
        if (screen != 0 || button != 0) return;
        for (int i=draft.buttons.size()-1;i>=0;i--) {
            TouchLayout.Button b=draft.buttons.get(i);
            if (((other ? b.other : b.world) || selected==i) && b.hit(x,y,width,height)) {
                selected=i; dragging=true; dragX=x-b.left(width); dragY=y-b.top(height); return;
            }
        }
        if (!other && Math.abs(x-wheelX())<=wheelRadius() && Math.abs(y-wheelY())<=wheelRadius()) {
            selected=-2; dragging=true; dragX=x-Math.round(wheelX()); dragY=y-Math.round(wheelY());
        }
    }
    @Override protected void mouseClickMove(int x,int y,int button,long elapsed) {
        if (!dragging || button != 0 || screen != 0) return;
        TouchLayout.Button b=current();
        if (b != null) { b.x=TouchLayout.clamp((float)(x-dragX)/Math.max(1,width-b.size)); b.y=TouchLayout.clamp((float)(y-dragY)/Math.max(1,height-b.size)); }
        else if (selected == -2) { float r=wheelRadius(); draft.wheelX=TouchLayout.clamp((x-dragX-r)/Math.max(1,width-2*r)); draft.wheelY=TouchLayout.clamp((y-dragY-r)/Math.max(1,height-2*r)); }
    }
    @Override protected void mouseMovedOrUp(int x,int y,int button) { if (button == 0) dragging=false; super.mouseMovedOrUp(x,y,button); }
    @Override protected void keyTyped(char c,int key) {
        if (key == Keyboard.KEY_ESCAPE) { captureName(); if (screen == 0) mc.displayGuiScreen(parent); else { screen=screen == 2 ? 1 : option >= 0 ? 3 : screen == 3 ? 1 : 0; if (screen == 3) option=-1; initGui(); } return; }
        if (screen == 1 && current() != null) name.textboxKeyTyped(c,key);
    }
    @Override public void updateScreen() { if (screen == 1 && name != null) name.updateCursorCounter(); }
    @Override public void onGuiClosed() { owner.cancel(); }
}
