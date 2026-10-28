package io.github.wdy.easytouch.text;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import cpw.mods.fml.common.FMLLog;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import me.eigenraven.lwjgl3ify.client.MainThreadExec;
import org.apache.logging.log4j.Level;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjglx.opengl.Display;
import static org.lwjgl.sdl.SDLProperties.SDL_GetPointerProperty;
import static org.lwjgl.sdl.SDLVideo.*;
import static org.lwjgl.system.JNI.*;

/** Small C ABI; the DLL and COM providers stay loaded until process exit. */
@Lwjgl3Aware
final class WindowsUiaBridge {
    private static SharedLibrary library;
    private final long install, update, take, detach;
    private final ByteBuffer packet = ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder());
    private final ByteBuffer pending = ByteBuffer.allocateDirect(8 + 32768 * 2).order(ByteOrder.nativeOrder());
    private ByteBuffer value = ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder());
    private long hwnd;

    WindowsUiaBridge() {
        if (org.lwjgl.system.Pointer.POINTER_SIZE != 8) throw new IllegalStateException("UIA bridge requires Windows x64");
        if (library == null) library = load();
        if (invokeI(function("et_uia_version")) != 1) throw new IllegalStateException("UIA bridge ABI mismatch");
        install = function("et_uia_install");
        update = function("et_uia_update");
        take = function("et_uia_take");
        detach = function("et_uia_detach");
        MainThreadExec.runOnMainThread((Runnable) () -> {
            hwnd = SDL_GetPointerProperty(SDL_GetWindowProperties(Display.sdlWindow), SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0);
            if (hwnd == 0 || invokePI(hwnd, install) != 0) throw new IllegalStateException("Cannot attach UIA provider to Minecraft HWND");
        });
        FMLLog.log(Level.INFO, "[GTNH Easy Touch] [UIA text] bridge installed on Minecraft HWND; no editor popup.");
    }

    void publish(int token, TextState text, TextGeometry geometry, int limit, double width, double height) {
        String contents = text == null ? "" : text.text;
        int bytes = (contents.length() + 1) * 2;
        if (value.capacity() < bytes) value = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        for (int i = 0; i < contents.length(); i++) value.putChar(i * 2, contents.charAt(i));
        value.putChar(contents.length() * 2, '\0');
        packet.putInt(0, token).putInt(4, limit).putInt(8, contents.length()).putInt(12, 0);
        if (token != 0) {
            packet.putDouble(16, geometry.left / width).putDouble(24, geometry.top / height)
                .putDouble(32, geometry.right / width).putDouble(40, geometry.bottom / height)
                .putDouble(48, geometry.caretX / width).putDouble(56, geometry.caretY / height)
                .putDouble(64, 1 / width).putDouble(72, geometry.caretHeight / height);
        }
        MainThreadExec.runOnMainThread((Runnable) () -> {
            long current = SDL_GetPointerProperty(SDL_GetWindowProperties(Display.sdlWindow), SDL_PROP_WINDOW_WIN32_HWND_POINTER, 0);
            if (current != hwnd) {
                invokeI(detach);
                if (current == 0 || invokePI(current, install) != 0) throw new IllegalStateException("Cannot reattach UIA after window recreation");
                hwnd = current;
            }
            int status = invokePPI(MemoryUtil.memAddress(packet), MemoryUtil.memAddress(value), update);
            if (status != 0) throw new IllegalStateException("UIA snapshot failed: " + status);
        });
    }

    Edit poll() {
        int length = MainThreadExec.runOnMainThread((java.util.function.IntSupplier)
            () -> invokePI(MemoryUtil.memAddress(pending), pending.capacity(), take));
        if (length < 0) return null;
        if (length > 32767) throw new IllegalStateException("Invalid UIA value length");
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) chars[i] = pending.getChar(8 + i * 2);
        return new Edit(pending.getInt(0), new String(chars));
    }

    void close() { MainThreadExec.runOnMainThread((Runnable) () -> invokeI(detach)); }

    private long function(String name) {
        long address = library.getFunctionAddress(name);
        if (address == 0) throw new IllegalStateException("Missing UIA export " + name);
        return address;
    }

    private static SharedLibrary load() {
        // Content-addressed cache: never replace an in-use Windows DLL; support Unicode paths.
        try (InputStream input = WindowsUiaBridge.class.getResourceAsStream("/natives/windows-x86_64/gtnheasytouch_uia.dll")) {
            if (input == null) throw new IOException("Bundled UIA DLL is missing");
            Path temporary = Files.createTempFile("gtnheasytouch-uia-", ".dll");
            try {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(temporary));
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format("%02x", b & 255));
                Path directory = Paths.get(System.getProperty("java.io.tmpdir"), "gtnheasytouch-uia", hex.toString());
                Files.createDirectories(directory);
                Path dll = directory.resolve("gtnheasytouch_uia.dll");
                if (!Files.exists(dll)) Files.copy(temporary, dll);
                if (!java.util.Arrays.equals(hash, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dll)))) {
                    throw new IOException("UIA DLL cache checksum mismatch");
                }
                return Library.loadNative(WindowsUiaBridge.class, "gtnheasytouch", dll.toAbsolutePath().toString());
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Cannot load bundled Windows UIA bridge", error);
        }
    }

    static final class Edit {
        final int token;
        final String text;
        Edit(int token, String text) { this.token = token; this.text = text; }
    }
}
