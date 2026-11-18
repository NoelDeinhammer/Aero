package com.quickchat.ui;

import com.quickchat.config.AppSettings;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

/**
 * Shared dynamic color palette and typography for Aero's lightweight UI.
 * Derives readable surfaces, borders, and text colors automatically from
 * four user-customizable base colors.
 */
public final class Theme {
    private Theme() {}

    public static final Color DEFAULT_BG_WINDOW = new Color(24, 25, 30);
    public static final Color DEFAULT_ACCENT = new Color(88, 101, 242);
    public static final Color DEFAULT_BUBBLE_OUT = new Color(88, 101, 242);
    public static final Color DEFAULT_BUBBLE_IN = new Color(45, 48, 58);

    public record Preset(String id, Color bgWindow, Color accent, Color bubbleOut, Color bubbleIn) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("aero", DEFAULT_BG_WINDOW, DEFAULT_ACCENT, DEFAULT_BUBBLE_OUT, DEFAULT_BUBBLE_IN),
            new Preset("ocean", new Color(16, 22, 32), new Color(46, 152, 240), new Color(33, 114, 214), new Color(32, 43, 59)),
            new Preset("emerald", new Color(17, 24, 21), new Color(45, 168, 107), new Color(35, 134, 86), new Color(33, 45, 40)),
            new Preset("rose", new Color(25, 20, 27), new Color(224, 86, 126), new Color(196, 63, 103), new Color(44, 35, 47)),
            new Preset("graphite", new Color(22, 23, 27), new Color(128, 137, 158), new Color(74, 82, 99), new Color(39, 42, 49)),
            new Preset("light", new Color(242, 244, 248), new Color(79, 93, 245), new Color(79, 93, 245), new Color(255, 255, 255))
    );

    public static volatile Color BG_WINDOW = DEFAULT_BG_WINDOW;
    public static volatile Color BG_HEADER = new Color(32, 34, 41);
    public static volatile Color BG_CARD = new Color(36, 38, 46);
    public static volatile Color BG_CARD_HOVER = new Color(46, 49, 60);
    public static volatile Color BG_INPUT = new Color(40, 42, 50);

    public static volatile Color ACCENT = DEFAULT_ACCENT;
    public static volatile Color ACCENT_HOVER = new Color(108, 120, 255);
    public static volatile Color BTN_DARK = new Color(26, 28, 35);
    public static volatile Color BTN_DARK_HOVER = new Color(40, 43, 54);
    public static volatile Color BTN_DARK_BORDER = new Color(54, 57, 70);
    public static volatile Color BTN_ACCENT = DEFAULT_ACCENT;
    public static volatile Color BTN_ACCENT_HOVER = new Color(108, 120, 255);
    public static volatile Color BTN_ACCENT_TEXT = Color.WHITE;
    public static volatile Color BTN_DANGER = new Color(168, 52, 62);
    public static volatile Color BTN_DANGER_HOVER = new Color(196, 64, 76);
    public static volatile Color ONLINE_DOT = new Color(59, 165, 93);
    public static volatile Color DANGER = new Color(235, 95, 95);
    public static volatile Color BORDER = new Color(56, 59, 71);

    public static volatile Color BUBBLE_OUT = DEFAULT_BUBBLE_OUT;
    public static volatile Color BUBBLE_OUT_TEXT = new Color(246, 247, 252);
    public static volatile Color BUBBLE_OUT_META = new Color(228, 232, 245, 185);
    public static volatile Color BUBBLE_IN = DEFAULT_BUBBLE_IN;
    public static volatile Color BUBBLE_IN_TEXT = new Color(240, 242, 248);
    public static volatile Color BUBBLE_IN_META = new Color(190, 196, 212, 185);

    public static volatile Color TEXT_PRIMARY = new Color(240, 242, 248);
    public static volatile Color TEXT_MUTED = new Color(155, 160, 175);
    public static volatile Color SCROLL_THUMB = new Color(255, 255, 255, 48);
    public static volatile Color SCROLL_THUMB_HOVER = new Color(255, 255, 255, 88);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);

    static {
        applyPalette(DEFAULT_BG_WINDOW, DEFAULT_ACCENT, DEFAULT_BUBBLE_OUT, DEFAULT_BUBBLE_IN);
    }

    public static void applyFromSettings(AppSettings settings) {
        if (settings == null) {
            resetToDefaults();
            return;
        }
        applyPalette(
                parseHex(settings.getThemeBgWindow(), DEFAULT_BG_WINDOW),
                parseHex(settings.getThemeAccent(), DEFAULT_ACCENT),
                parseHex(settings.getThemeBubbleOut(), DEFAULT_BUBBLE_OUT),
                parseHex(settings.getThemeBubbleIn(), DEFAULT_BUBBLE_IN)
        );
    }

    public static void resetToDefaults() {
        applyPalette(DEFAULT_BG_WINDOW, DEFAULT_ACCENT, DEFAULT_BUBBLE_OUT, DEFAULT_BUBBLE_IN);
    }

    public static void applyPalette(Color bgWindow, Color accent, Color bubbleOut, Color bubbleIn) {
        Color bg = bgWindow != null ? bgWindow : DEFAULT_BG_WINDOW;
        Color ac = accent != null ? accent : DEFAULT_ACCENT;
        Color bOut = bubbleOut != null ? bubbleOut : DEFAULT_BUBBLE_OUT;
        Color bIn = bubbleIn != null ? bubbleIn : DEFAULT_BUBBLE_IN;

        BG_WINDOW = bg;
        ACCENT = ac;
        BUBBLE_OUT = bOut;
        BUBBLE_IN = bIn;

        boolean lightWindow = isLight(bg);
        if (lightWindow) {
            double lum = luminance(bg);
            if (lum > 235) {
                BG_HEADER = mix(bg, Color.BLACK, 0.045);
                BG_CARD = mix(bg, Color.BLACK, 0.028);
                BG_CARD_HOVER = mix(bg, Color.BLACK, 0.07);
                BG_INPUT = Color.WHITE;
            } else {
                BG_HEADER = mix(bg, Color.WHITE, 0.30);
                BG_CARD = mix(bg, Color.WHITE, 0.55);
                BG_CARD_HOVER = mix(bg, Color.WHITE, 0.72);
                BG_INPUT = mix(bg, Color.WHITE, 0.65);
            }
            BORDER = mix(bg, Color.BLACK, 0.14);
            TEXT_PRIMARY = new Color(24, 26, 34);
            TEXT_MUTED = new Color(96, 102, 118);
            BTN_DARK = mix(bg, Color.BLACK, 0.055);
            BTN_DARK_HOVER = mix(bg, Color.BLACK, 0.11);
            BTN_DARK_BORDER = mix(bg, Color.BLACK, 0.18);
            ONLINE_DOT = new Color(34, 138, 72);
            DANGER = new Color(204, 52, 62);
            SCROLL_THUMB = new Color(20, 24, 32, 52);
            SCROLL_THUMB_HOVER = new Color(20, 24, 32, 95);
        } else {
            BG_HEADER = mix(bg, Color.WHITE, 0.048);
            BG_CARD = mix(bg, Color.WHITE, 0.068);
            BG_CARD_HOVER = mix(bg, Color.WHITE, 0.115);
            BG_INPUT = mix(bg, Color.WHITE, 0.085);
            BORDER = mix(bg, Color.WHITE, 0.16);
            TEXT_PRIMARY = new Color(240, 242, 248);
            TEXT_MUTED = new Color(158, 164, 180);
            BTN_DARK = mix(bg, Color.WHITE, 0.045);
            BTN_DARK_HOVER = mix(bg, Color.WHITE, 0.115);
            BTN_DARK_BORDER = mix(bg, Color.WHITE, 0.19);
            ONLINE_DOT = new Color(59, 165, 93);
            DANGER = new Color(235, 95, 95);
            SCROLL_THUMB = new Color(255, 255, 255, 48);
            SCROLL_THUMB_HOVER = new Color(255, 255, 255, 92);
        }

        ACCENT_HOVER = isLight(ac) ? mix(ac, Color.BLACK, 0.12) : mix(ac, Color.WHITE, 0.16);
        BTN_ACCENT = ac;
        BTN_ACCENT_HOVER = ACCENT_HOVER;
        BTN_ACCENT_TEXT = isLight(ac) ? new Color(20, 22, 28) : Color.WHITE;

        BUBBLE_OUT_TEXT = isLight(bOut) ? new Color(22, 24, 30) : new Color(248, 249, 253);
        BUBBLE_OUT_META = isLight(bOut)
                ? new Color(56, 62, 76, 195)
                : new Color(232, 236, 248, 185);
        BUBBLE_IN_TEXT = isLight(bIn) ? new Color(22, 24, 30) : new Color(240, 242, 248);
        BUBBLE_IN_META = isLight(bIn)
                ? new Color(82, 88, 104, 195)
                : new Color(188, 195, 212, 185);
    }

    public static boolean isLight(Color c) {
        return luminance(c) >= 145.0;
    }

    public static double luminance(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }

    public static Color mix(Color a, Color b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() * (1.0 - clamped) + b.getRed() * clamped);
        int g = (int) Math.round(a.getGreen() * (1.0 - clamped) + b.getGreen() * clamped);
        int bl = (int) Math.round(a.getBlue() * (1.0 - clamped) + b.getBlue() * clamped);
        return new Color(clampByte(r), clampByte(g), clampByte(bl));
    }

    public static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), clampByte(alpha));
    }

    public static String toHex(Color c) {
        if (c == null) {
            return "#000000";
        }
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color parseHex(String value, Color fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1);
        }
        if (!trimmed.matches("[0-9A-Fa-f]{6}")) {
            return fallback;
        }
        try {
            return new Color(Integer.parseInt(trimmed, 16));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
