package com.quickchat.hotkey;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * Registers a system-wide global hotkey on Windows without external native
 * libraries by running a lightweight hidden PowerShell message-loop helper.
 *
 * <p>The helper is supervised: if it ever exits unexpectedly it is started
 * again. It also watches the Java process and kills itself when Aero exits,
 * so a stale helper can never keep the hotkey registered forever.</p>
 */
public class GlobalHotkeyListener {
    private final Runnable onHotkey;
    private final Consumer<String> onBoundShortcut;

    private volatile boolean running = false;
    private volatile Process process;
    private Thread supervisorThread;

    public GlobalHotkeyListener(Runnable onHotkey, Consumer<String> onBoundShortcut) {
        this.onHotkey = onHotkey;
        this.onBoundShortcut = onBoundShortcut;
    }

    public void start() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            onBoundShortcut.accept("Tray-Klick (nur Windows)");
            return;
        }
        if (running) return;

        running = true;
        supervisorThread = new Thread(this::supervise, "aero-hotkey");
        supervisorThread.setDaemon(true);
        supervisorThread.start();
    }

    public void stop() {
        running = false;
        Process current = process;
        if (current != null && current.isAlive()) {
            current.destroyForcibly();
        }
        Thread thread = supervisorThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void supervise() {
        while (running) {
            boolean bound = false;
            try {
                bound = runHelperOnce();
            } catch (Exception e) {
                if (running) {
                    System.err.println("[Hotkey] Helfer-Fehler: " + e.getMessage());
                }
            }
            if (!running) break;

            try {
                // Without a registered hotkey there is no point in retrying
                // every couple of seconds; wait longer instead.
                Thread.sleep(bound ? 1200L : 8000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Runs one helper process until it exits. Returns true when a global
     * hotkey was actually bound (as opposed to the tray-click fallback).
     */
    private boolean runHelperOnce() throws IOException {
        String script = buildScript(ProcessHandle.current().pid());
        String encodedCommand = Base64.getEncoder().encodeToString(
                script.getBytes(StandardCharsets.UTF_16LE));

        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden",
                "-EncodedCommand", encodedCommand
        );
        builder.redirectErrorStream(true);
        Process helper = builder.start();
        process = helper;

        boolean bound = false;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while (running && (line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if ("HOTKEY".equals(trimmed)) {
                    SwingUtilities.invokeLater(onHotkey);
                } else if (trimmed.startsWith("BOUND:")) {
                    String label = trimmed.substring("BOUND:".length());
                    bound = !label.startsWith("Tray-Klick");
                    SwingUtilities.invokeLater(() -> onBoundShortcut.accept(label));
                } else if (!trimmed.isEmpty()
                        && !trimmed.startsWith("#< CLIXML")
                        && !trimmed.startsWith("ERROR:")) {
                    System.err.println("[Hotkey] " + trimmed);
                } else if (trimmed.startsWith("ERROR:")) {
                    System.err.println("[Hotkey] " + trimmed);
                }
            }
        } finally {
            if (process == helper) {
                process = null;
            }
            if (helper.isAlive()) {
                helper.destroyForcibly();
            }
        }
        return bound;
    }

    private static String buildScript(long parentPid) {
        return String.join("\r\n",
                "$ErrorActionPreference = 'Stop'",
                "try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch { }",
                "$code = @'",
                "using System;",
                "using System.Diagnostics;",
                "using System.Runtime.InteropServices;",
                "using System.Threading;",
                "public class AeroHotkey {",
                "    [DllImport(\"user32.dll\")] public static extern bool RegisterHotKey(IntPtr hWnd, int id, uint fsModifiers, uint vk);",
                "    [DllImport(\"user32.dll\")] public static extern bool UnregisterHotKey(IntPtr hWnd, int id);",
                "    [DllImport(\"user32.dll\")] public static extern int GetMessage(out MSG lpMsg, IntPtr hWnd, uint wMsgFilterMin, uint wMsgFilterMax);",
                "    [StructLayout(LayoutKind.Sequential)] public struct MSG { public IntPtr hwnd; public uint message; public IntPtr wParam; public IntPtr lParam; public uint time; public int ptX; public int ptY; }",
                "    public static void WatchParent(int parentPid, int selfPid) {",
                "        Thread thread = new Thread(() => {",
                "            while (true) {",
                "                Thread.Sleep(2000);",
                "                try { Process.GetProcessById(parentPid); }",
                "                catch {",
                "                    try { Process.GetProcessById(selfPid).Kill(); } catch { }",
                "                    return;",
                "                }",
                "            }",
                "        });",
                "        thread.IsBackground = true;",
                "        thread.Start();",
                "    }",
                "}",
                "'@",
                "try { Add-Type -TypeDefinition $code -Language CSharp } catch { [Console]::WriteLine('ERROR:' + $_.Exception.Message); exit 1 }",
                "[AeroHotkey]::WatchParent(" + parentPid + ", $PID)",
                "function Write-Line([string]$text) { [Console]::WriteLine($text); [Console]::Out.Flush() }",
                "$noRepeat = 0x4000",
                "$bound = $false",
                "if ([AeroHotkey]::RegisterHotKey([IntPtr]::Zero, 1, (0x0003 -bor $noRepeat), 0x20)) {",
                "    Write-Line 'BOUND:Ctrl+Alt+Space'",
                "    $bound = $true",
                "} elseif ([AeroHotkey]::RegisterHotKey([IntPtr]::Zero, 1, (0x0006 -bor $noRepeat), 0x20)) {",
                "    Write-Line 'BOUND:Ctrl+Shift+Space'",
                "    $bound = $true",
                "} elseif ([AeroHotkey]::RegisterHotKey([IntPtr]::Zero, 1, (0x0006 -bor $noRepeat), 0x4B)) {",
                "    Write-Line 'BOUND:Ctrl+Shift+K'",
                "    $bound = $true",
                "} else {",
                "    Write-Line 'BOUND:Tray-Klick'",
                "}",
                "if ($bound) {",
                "    $msg = New-Object AeroHotkey+MSG",
                "    while ($true) {",
                "        $result = [AeroHotkey]::GetMessage([ref]$msg, [IntPtr]::Zero, 0, 0)",
                "        if ($result -le 0) { break }",
                "        if ($msg.message -eq 0x0312) { Write-Line 'HOTKEY' }",
                "    }",
                "    [AeroHotkey]::UnregisterHotKey([IntPtr]::Zero, 1) | Out-Null",
                "    Write-Line 'EXIT'",
                "}"
        );
    }
}
