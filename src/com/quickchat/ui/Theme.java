package com.quickchat.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Shared dark-mode color palette and typography for QuickChat's floating UI.
 */
public final class Theme {
    private Theme() {}

    public static final Color BG_WINDOW = new Color(24, 25, 30);
    public static final Color BG_HEADER = new Color(32, 34, 41);
    public static final Color BG_CARD = new Color(36, 38, 46);
    public static final Color BG_CARD_HOVER = new Color(46, 49, 60);
    public static final Color BG_INPUT = new Color(40, 42, 50);

    public static final Color ACCENT = new Color(88, 101, 242);
    public static final Color ACCENT_HOVER = new Color(108, 120, 255);
    public static final Color ONLINE_DOT = new Color(59, 165, 93);
    public static final Color DANGER = new Color(235, 95, 95);
    public static final Color BORDER = new Color(56, 59, 71);

    public static final Color BUBBLE_OUT = new Color(88, 101, 242);
    public static final Color BUBBLE_IN = new Color(45, 48, 58);

    public static final Color TEXT_PRIMARY = new Color(240, 242, 248);
    public static final Color TEXT_MUTED = new Color(155, 160, 175);

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);
}
