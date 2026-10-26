package io.github.wdy.easytouch.layout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** User layout is saved atomically; the editor works on a separate copy. */
public final class TouchLayout {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public int version = 3;
    public List<Button> buttons = new ArrayList<Button>();
    public float wheelX = -1, wheelY = -1, wheelScale = 1;
    public boolean wheelVisible = true;
    public static final class Button {
        public String label = "按键", binding = "";
        public int key = 0, mode = 1, size = 24, opacity = 30;
        public List<Integer> keys = new ArrayList<Integer>();
        public boolean world = true, other = false;
        public boolean smartSecondary = false;
        public float x = .8f, y = .6f;
        public int left(int w) { return Math.round(x * Math.max(0, w - size)); }
        public int top(int h) { return Math.round(y * Math.max(0, h - size)); }
        public boolean hit(int px, int py, int w, int h) {
            return Circle.hit(px, py, left(w), top(h), size);
        }
    }
    private void addSecondary() {
        for (Button b : buttons) if (b.smartSecondary) return;
        Button b = new Button(); b.smartSecondary = true; b.label = "副"; b.x = .9f; b.y = .65f;
        buttons.add(b);
    }
    public TouchLayout copy() { return JSON.fromJson(JSON.toJson(this), TouchLayout.class); }
    public static TouchLayout load(File path) throws IOException {
        if (!path.exists()) {
            TouchLayout layout = new TouchLayout(); layout.addSecondary(); return layout;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject object = new com.google.gson.JsonParser().parse(reader).getAsJsonObject();
            boolean legacy = !object.has("version");
            boolean needsSecondary = legacy || object.get("version").getAsInt() < 3;
            TouchLayout layout = JSON.fromJson(object, TouchLayout.class);
            if (layout == null || layout.buttons == null) throw new IOException("Invalid touch layout");
            layout.buttons.removeAll(Collections.singleton(null));
            for (Button b : layout.buttons) {
                b.keys = ChordState.normalize(b.keys);
                if (legacy && b.opacity == 55) b.opacity = 30;
                b.x = clamp(b.x); b.y = clamp(b.y);
                b.size = Math.max(24, Math.min(100, b.size));
                b.opacity = Math.max(10, Math.min(100, b.opacity));
                b.mode = b.mode == 2 ? 2 : 1;
                if (!ChordState.valid(b.key)) b.key = 0;
                if (b.label == null) b.label = "按键";
                if (b.binding == null) b.binding = "";
            }
            if (needsSecondary) layout.addSecondary();
            layout.version = 3;
            layout.wheelScale = Float.isFinite(layout.wheelScale) ? Math.max(.5f, Math.min(2, layout.wheelScale)) : 1;
            layout.wheelX = layout.wheelX < 0 ? -1 : clamp(layout.wheelX);
            layout.wheelY = layout.wheelY < 0 ? -1 : clamp(layout.wheelY);
            return layout;
        } catch (RuntimeException ex) { throw new IOException("Invalid touch layout", ex); }
    }
    public void save(File path) throws IOException {
        Path temp = Files.createTempFile(path.getParentFile().toPath(), "touch-layout-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { JSON.toJson(this, writer); }
            try { Files.move(temp, path.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path.toPath(), StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    public static float clamp(float n) { return Float.isFinite(n) ? Math.max(0, Math.min(1, n)) : .5f; }
}
