package com.quickchat.ui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/**
 * Lightweight, theme-aware custom Swing components for Aero.
 */
public final class UiComponents {
    private UiComponents() {
    }

    /**
     * Panel with rounded corners and dynamic theme-aware background/border suppliers.
     */
    public static class RoundedPanel extends JPanel {
        private final int arc;
        private Supplier<Color> bgSupplier;
        private Supplier<Color> borderSupplier;

        public RoundedPanel(LayoutManager layout, int arc,
                            Supplier<Color> bgSupplier, Supplier<Color> borderSupplier) {
            super(layout);
            this.arc = arc;
            this.bgSupplier = bgSupplier;
            this.borderSupplier = borderSupplier;
            setOpaque(false);
        }

        public void setBgSupplier(Supplier<Color> bgSupplier) {
            this.bgSupplier = bgSupplier;
            repaint();
        }

        public void setBorderSupplier(Supplier<Color> borderSupplier) {
            this.borderSupplier = borderSupplier;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (bgSupplier != null) {
                Color bg = bgSupplier.get();
                if (bg != null) {
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                }
            }
            if (borderSupplier != null) {
                Color border = borderSupplier.get();
                if (border != null) {
                    g2.setColor(border);
                    g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Compact pill-shaped badge label (unread count, shortcut tag, etc.).
     */
    public static final class PillLabel extends JLabel {
        private final int arc;
        private final Supplier<Color> bgSupplier;
        private final Supplier<Color> fgSupplier;
        private final Supplier<Color> borderSupplier;

        public PillLabel(String text, int arc, Supplier<Color> bgSupplier,
                         Supplier<Color> fgSupplier, Supplier<Color> borderSupplier) {
            super(text, SwingConstants.CENTER);
            this.arc = arc;
            this.bgSupplier = bgSupplier;
            this.fgSupplier = fgSupplier;
            this.borderSupplier = borderSupplier;
            setOpaque(false);
            setFont(Theme.FONT_SMALL);
            setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (bgSupplier != null) {
                Color bg = bgSupplier.get();
                if (bg != null) {
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                }
            }
            if (borderSupplier != null) {
                Color border = borderSupplier.get();
                if (border != null) {
                    g2.setColor(border);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                }
            }
            g2.dispose();
            if (fgSupplier != null) {
                setForeground(fgSupplier.get());
            }
            super.paintComponent(g);
        }
    }

    /**
     * Circular initial avatar with optional online status dot.
     */
    public static final class AvatarBadge extends JComponent {
        private String initial;
        private final boolean showOnlineDot;
        private final int size;

        public AvatarBadge(String name, int size, boolean showOnlineDot) {
            this.size = size;
            this.showOnlineDot = showOnlineDot;
            setNameText(name);
            setOpaque(false);
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        public void setNameText(String name) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                this.initial = "?";
            } else {
                int cp = trimmed.codePointAt(0);
                this.initial = new String(Character.toChars(Character.toUpperCase(cp)));
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color fill = Theme.withAlpha(Theme.ACCENT, Theme.isLight(Theme.BG_WINDOW) ? 38 : 65);
            g2.setColor(fill);
            g2.fillOval(0, 0, size - 1, size - 1);

            g2.setColor(Theme.withAlpha(Theme.ACCENT, 140));
            g2.drawOval(0, 0, size - 1, size - 1);

            g2.setColor(Theme.ACCENT_HOVER);
            Font font = Theme.FONT_BOLD.deriveFont(Math.max(11f, size * 0.42f));
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (size - fm.stringWidth(initial)) / 2;
            int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(initial, tx, ty);

            if (showOnlineDot) {
                int dot = Math.max(8, Math.round(size * 0.28f));
                int dx = size - dot;
                int dy = size - dot;
                g2.setColor(Theme.BG_CARD);
                g2.fillOval(dx - 1, dy - 1, dot + 2, dot + 2);
                g2.setColor(Theme.ONLINE_DOT);
                g2.fillOval(dx, dy, dot, dot);
            }
            g2.dispose();
        }
    }

    /**
     * Clean pill toggle switch implemented as a JCheckBox so native keyboard/state behavior works.
     */
    public static final class ToggleSwitch extends JCheckBox {
        public ToggleSwitch() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.FONT_BODY);
            setIconTextGap(10);
            setIcon(new ToggleIcon());
        }

        @Override
        protected void paintComponent(Graphics g) {
            setForeground(Theme.TEXT_PRIMARY);
            super.paintComponent(g);
        }

        private static final class ToggleIcon implements Icon {
            private static final int W = 34;
            private static final int H = 18;

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                boolean selected = c instanceof JCheckBox cb && cb.isSelected();
                boolean enabled = c.isEnabled();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color track = selected ? Theme.ACCENT : Theme.BG_INPUT;
                if (!enabled) {
                    track = Theme.withAlpha(track, 120);
                }
                g2.setColor(track);
                g2.fillRoundRect(x, y, W, H, H, H);

                if (!selected) {
                    g2.setColor(Theme.BORDER);
                    g2.drawRoundRect(x, y, W - 1, H - 1, H, H);
                }

                int knob = H - 4;
                int kx = selected ? x + W - knob - 2 : x + 2;
                int ky = y + 2;
                g2.setColor(selected ? Theme.BTN_ACCENT_TEXT : Theme.TEXT_MUTED);
                g2.fillOval(kx, ky, knob, knob);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return W;
            }

            @Override
            public int getIconHeight() {
                return H;
            }
        }
    }

    /**
     * Theme-aware radio button that avoids native Windows light chrome.
     */
    public static final class ThemedRadioButton extends JRadioButton {
        public ThemedRadioButton(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(Theme.FONT_BODY);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIconTextGap(8);
            setIcon(new RadioIcon());
        }

        @Override
        protected void paintComponent(Graphics g) {
            setForeground(Theme.TEXT_PRIMARY);
            super.paintComponent(g);
        }

        private static final class RadioIcon implements Icon {
            private static final int SIZE = 16;

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                boolean selected = c instanceof JRadioButton rb && rb.isSelected();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(selected ? Theme.ACCENT : Theme.BG_INPUT);
                g2.fillOval(x + 1, y + 1, SIZE - 2, SIZE - 2);
                g2.setColor(selected ? Theme.ACCENT : Theme.BORDER);
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawOval(x + 1, y + 1, SIZE - 2, SIZE - 2);
                if (selected) {
                    g2.setColor(Theme.BTN_ACCENT_TEXT);
                    g2.fillOval(x + 5, y + 5, SIZE - 10, SIZE - 10);
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return SIZE;
            }

            @Override
            public int getIconHeight() {
                return SIZE;
            }
        }
    }

    /**
     * Slim, rounded custom scrollbar matching the active Theme.
     */
    public static final class SlimScrollBarUI extends BasicScrollBarUI {
        private static final int BAR_WIDTH = 8;

        @Override
        protected void configureScrollBarColors() {
            thumbColor = Theme.SCROLL_THUMB;
            trackColor = Theme.BG_WINDOW;
        }

        @Override
        public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == JScrollBar.VERTICAL
                    ? new Dimension(BAR_WIDTH, super.getPreferredSize(c).height)
                    : new Dimension(super.getPreferredSize(c).width, BAR_WIDTH);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // Transparent track lets parent surface show through cleanly.
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() || isDragging
                    ? Theme.SCROLL_THUMB_HOVER
                    : Theme.SCROLL_THUMB);
            int pad = 1;
            int w = Math.max(3, thumbBounds.width - pad * 2);
            int h = Math.max(12, thumbBounds.height - pad * 2);
            g2.fill(new RoundRectangle2D.Float(
                    thumbBounds.x + pad, thumbBounds.y + pad, w, h, w, w));
            g2.dispose();
        }

        private static JButton zeroButton() {
            JButton btn = new JButton();
            Dimension zero = new Dimension(0, 0);
            btn.setPreferredSize(zero);
            btn.setMinimumSize(zero);
            btn.setMaximumSize(zero);
            return btn;
        }
    }

    public static void styleScrollPane(JScrollPane scroll, Supplier<Color> bgSupplier) {
        scroll.setBorder(null);
        scroll.setOpaque(false);
        Color bg = bgSupplier != null ? bgSupplier.get() : Theme.BG_WINDOW;
        scroll.getViewport().setBackground(bg);
        JScrollBar vBar = scroll.getVerticalScrollBar();
        vBar.setOpaque(false);
        vBar.setUnitIncrement(14);
        vBar.setUI(new SlimScrollBarUI());
        JScrollBar hBar = scroll.getHorizontalScrollBar();
        hBar.setOpaque(false);
        hBar.setUnitIncrement(14);
        hBar.setUI(new SlimScrollBarUI());
    }

    public static JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.setUI(new BasicPopupMenuUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                g.setColor(Theme.BG_CARD);
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
        });
        menu.setOpaque(true);
        menu.setBackground(Theme.BG_CARD);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        return menu;
    }

    public static void addPopupSeparator(JPopupMenu menu) {
        JPanel wrap = new JPanel(null) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, 7);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 7);
            }

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(6, 3, Math.max(0, getWidth() - 12), 1);
            }
        };
        wrap.setOpaque(false);
        menu.add(wrap);
    }

    public static JMenuItem createMenuItem(String text, Icon icon, Color fgOverride) {
        JMenuItem item = new JMenuItem(text, icon);
        item.setFont(Theme.FONT_SMALL);
        item.setOpaque(false);
        item.setContentAreaFilled(false);
        item.setBorderPainted(false);
        item.setFocusPainted(false);
        item.setBorder(BorderFactory.createEmptyBorder());
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.setUI(new BasicMenuItemUI() {
            private static final int PAD_LEFT = 10;
            private static final int PAD_RIGHT = 14;
            private static final int ICON_GAP = 9;

            @Override
            public Dimension getPreferredSize(JComponent c) {
                JMenuItem mi = (JMenuItem) c;
                Font font = mi.getFont() != null ? mi.getFont() : Theme.FONT_SMALL;
                FontMetrics fm = mi.getFontMetrics(font);
                Icon ic = mi.getIcon();
                int iconW = ic != null ? ic.getIconWidth() : 0;
                int iconH = ic != null ? ic.getIconHeight() : 0;
                String label = mi.getText() != null ? mi.getText() : "";
                int textW = fm.stringWidth(label);
                int w = PAD_LEFT + iconW + (iconW > 0 ? ICON_GAP : 0) + textW + PAD_RIGHT;
                int h = Math.max(28, Math.max(iconH, fm.getHeight()) + 10);
                return new Dimension(w, h);
            }

            @Override
            public void paint(Graphics g, JComponent c) {
                JMenuItem mi = (JMenuItem) c;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = mi.getWidth();
                int h = mi.getHeight();
                if (mi.isArmed() || mi.getModel().isSelected()) {
                    g2.setColor(Theme.BG_CARD_HOVER);
                    g2.fillRoundRect(0, 0, w, h, 6, 6);
                }

                int x = PAD_LEFT;
                Icon ic = mi.getIcon();
                if (ic != null) {
                    int iy = (h - ic.getIconHeight()) / 2;
                    ic.paintIcon(mi, g2, x, iy);
                    x += ic.getIconWidth() + ICON_GAP;
                }

                String label = mi.getText();
                if (label != null && !label.isEmpty()) {
                    Font font = mi.getFont() != null ? mi.getFont() : Theme.FONT_SMALL;
                    g2.setFont(font);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.setColor(fgOverride != null ? fgOverride : Theme.TEXT_PRIMARY);
                    int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                    g2.drawString(label, x, ty);
                }
                g2.dispose();
            }
        });
        return item;
    }
}
