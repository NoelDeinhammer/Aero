package com.quickchat.ui;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Crisp vector Swing icons and memory-safe image/clipboard utilities.
 */
public final class UiIcons {
    private UiIcons() {
    }

    public static Icon paperclip(Color color, int size) {
        return paperclip(() -> color, size);
    }

    public static Icon paperclip(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float s = w / 16f;
            Path2D p = new Path2D.Float();
            p.moveTo(11.5f * s, 5.2f * s);
            p.lineTo(6.2f * s, 10.5f * s);
            p.curveTo(5.1f * s, 11.6f * s, 3.4f * s, 11.6f * s, 2.4f * s, 10.6f * s);
            p.curveTo(1.4f * s, 9.6f * s, 1.4f * s, 7.9f * s, 2.5f * s, 6.8f * s);
            p.lineTo(8.5f * s, 0.9f * s);
            p.curveTo(9.9f * s, -0.4f * s + 1.5f * s, 12.1f * s, 1.1f * s, 13.3f * s, 2.3f * s);
            p.curveTo(14.5f * s, 3.5f * s, 14.5f * s, 5.7f * s, 13.1f * s, 7.1f * s);
            p.lineTo(7.1f * s, 13.1f * s);
            p.curveTo(5.3f * s, 14.9f * s, 2.4f * s, 14.9f * s, 0.7f * s + 1.2f * s, 13.2f * s);
            g.draw(p);
        });
    }

    public static Icon download(Color color, int size) {
        return download(() -> color, size);
    }

    public static Icon download(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = w / 2f;
            float top = h * 0.16f;
            float arrowBottom = h * 0.66f;
            float arrowWing = w * 0.24f;

            g.drawLine(Math.round(cx), Math.round(top), Math.round(cx), Math.round(arrowBottom));
            g.drawLine(Math.round(cx - arrowWing), Math.round(arrowBottom - arrowWing),
                    Math.round(cx), Math.round(arrowBottom));
            g.drawLine(Math.round(cx + arrowWing), Math.round(arrowBottom - arrowWing),
                    Math.round(cx), Math.round(arrowBottom));

            float left = w * 0.18f;
            float right = w * 0.82f;
            float trayY = h * 0.84f;
            float trayLip = h * 0.70f;
            Path2D tray = new Path2D.Float();
            tray.moveTo(left, trayLip);
            tray.lineTo(left, trayY);
            tray.lineTo(right, trayY);
            tray.lineTo(right, trayLip);
            g.draw(tray);
        });
    }

    public static Icon fileBadge(Color color, int size) {
        return fileBadge(() -> color, size);
    }

    public static Icon fileBadge(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float l = w * 0.2f;
            float r = w * 0.8f;
            float t = h * 0.12f;
            float b = h * 0.88f;
            float fold = w * 0.22f;

            Path2D doc = new Path2D.Float();
            doc.moveTo(l, t);
            doc.lineTo(r - fold, t);
            doc.lineTo(r, t + fold);
            doc.lineTo(r, b);
            doc.lineTo(l, b);
            doc.closePath();
            g.draw(doc);

            g.drawLine(Math.round(r - fold), Math.round(t), Math.round(r - fold), Math.round(t + fold));
            g.drawLine(Math.round(r - fold), Math.round(t + fold), Math.round(r), Math.round(t + fold));
        });
    }

    public static Icon copy(Color color, int size) {
        return copy(() -> color, size);
    }

    public static Icon copy(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int s = Math.max(8, size);
            int off = Math.round(s * 0.18f);
            int boxW = Math.round(s * 0.54f);
            int boxH = Math.round(s * 0.62f);
            g.drawRoundRect(off, off, boxW, boxH, 3, 3);
            g.drawRoundRect(off + Math.round(s * 0.2f), off + Math.round(s * 0.18f), boxW, boxH, 3, 3);
        });
    }

    public static Icon cancel(Color color, int size) {
        return cancel(() -> color, size);
    }

    public static Icon cancel(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = Math.max(3, Math.round(w * 0.26f));
            g.drawLine(pad, pad, w - pad, h - pad);
            g.drawLine(w - pad, pad, pad, h - pad);
        });
    }

    public static Icon check(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Float();
            p.moveTo(w * 0.20f, h * 0.54f);
            p.lineTo(w * 0.42f, h * 0.76f);
            p.lineTo(w * 0.82f, h * 0.26f);
            g.draw(p);
        });
    }

    public static Icon moreDots(Color color, int size) {
        return moreDots(() -> color, size);
    }

    public static Icon moreDots(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            float cy = h / 2f;
            float r = Math.max(1.1f, w * 0.085f);
            float d = r * 2f;
            float spacing = w * 0.27f;
            float cx = w / 2f;
            for (int i = -1; i <= 1; i++) {
                float x = cx + i * spacing - r;
                float y = cy - r;
                g.fillOval(Math.round(x), Math.round(y), Math.round(d), Math.round(d));
            }
        });
    }

    public static Icon backArrow(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cy = h / 2f;
            float left = w * 0.22f;
            float right = w * 0.78f;
            float wing = w * 0.26f;
            g.drawLine(Math.round(right), Math.round(cy), Math.round(left), Math.round(cy));
            g.drawLine(Math.round(left + wing), Math.round(cy - wing), Math.round(left), Math.round(cy));
            g.drawLine(Math.round(left + wing), Math.round(cy + wing), Math.round(left), Math.round(cy));
        });
    }

    public static Icon settings(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = w / 2f;
            float cy = h / 2f;
            float rOuter = w * 0.34f;
            float rInner = w * 0.15f;
            g.draw(new Ellipse2D.Float(cx - rInner, cy - rInner, rInner * 2f, rInner * 2f));
            g.draw(new Ellipse2D.Float(cx - rOuter, cy - rOuter, rOuter * 2f, rOuter * 2f));
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0;
                float x1 = (float) (cx + Math.cos(ang) * (rOuter - 0.5f));
                float y1 = (float) (cy + Math.sin(ang) * (rOuter - 0.5f));
                float x2 = (float) (cx + Math.cos(ang) * (w * 0.46f));
                float y2 = (float) (cy + Math.sin(ang) * (h * 0.46f));
                g.drawLine(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2));
            }
        });
    }

    public static Icon members(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float r1 = w * 0.14f;
            float cx1 = w * 0.38f;
            float cy1 = h * 0.34f;
            g.draw(new Ellipse2D.Float(cx1 - r1, cy1 - r1, r1 * 2f, r1 * 2f));
            Path2D p1 = new Path2D.Float();
            p1.moveTo(w * 0.14f, h * 0.80f);
            p1.curveTo(w * 0.14f, h * 0.56f, w * 0.62f, h * 0.56f, w * 0.62f, h * 0.80f);
            g.draw(p1);

            float r2 = w * 0.11f;
            float cx2 = w * 0.69f;
            float cy2 = h * 0.37f;
            g.draw(new Ellipse2D.Float(cx2 - r2, cy2 - r2, r2 * 2f, r2 * 2f));
            Path2D p2 = new Path2D.Float();
            p2.moveTo(w * 0.64f, h * 0.58f);
            p2.curveTo(w * 0.80f, h * 0.60f, w * 0.86f, h * 0.70f, w * 0.86f, h * 0.80f);
            g.draw(p2);
        });
    }

    public static Icon edit(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D pencil = new Path2D.Float();
            pencil.moveTo(w * 0.20f, h * 0.80f);
            pencil.lineTo(w * 0.25f, h * 0.60f);
            pencil.lineTo(w * 0.68f, h * 0.17f);
            pencil.lineTo(w * 0.83f, h * 0.32f);
            pencil.lineTo(w * 0.40f, h * 0.75f);
            pencil.closePath();
            g.draw(pencil);
            g.drawLine(Math.round(w * 0.59f), Math.round(h * 0.26f),
                    Math.round(w * 0.74f), Math.round(h * 0.41f));
        });
    }

    public static Icon trash(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(Math.round(w * 0.20f), Math.round(h * 0.27f),
                    Math.round(w * 0.80f), Math.round(h * 0.27f));
            g.drawLine(Math.round(w * 0.38f), Math.round(h * 0.16f),
                    Math.round(w * 0.62f), Math.round(h * 0.16f));
            Path2D bin = new Path2D.Float();
            bin.moveTo(w * 0.28f, h * 0.28f);
            bin.lineTo(w * 0.32f, h * 0.83f);
            bin.lineTo(w * 0.68f, h * 0.83f);
            bin.lineTo(w * 0.72f, h * 0.28f);
            g.draw(bin);
            g.drawLine(Math.round(w * 0.44f), Math.round(h * 0.42f),
                    Math.round(w * 0.44f), Math.round(h * 0.70f));
            g.drawLine(Math.round(w * 0.56f), Math.round(h * 0.42f),
                    Math.round(w * 0.56f), Math.round(h * 0.70f));
        });
    }

    public static Icon send(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D plane = new Path2D.Float();
            plane.moveTo(w * 0.14f, h * 0.22f);
            plane.lineTo(w * 0.86f, h * 0.50f);
            plane.lineTo(w * 0.14f, h * 0.78f);
            plane.lineTo(w * 0.30f, h * 0.50f);
            plane.closePath();
            g.fill(plane);
        });
    }

    public static Icon lock(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.45f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawRoundRect(Math.round(w * 0.22f), Math.round(h * 0.44f),
                    Math.round(w * 0.56f), Math.round(h * 0.42f), 3, 3);
            Path2D shackle = new Path2D.Float();
            shackle.moveTo(w * 0.34f, h * 0.44f);
            shackle.lineTo(w * 0.34f, h * 0.30f);
            shackle.curveTo(w * 0.34f, h * 0.16f, w * 0.66f, h * 0.16f, w * 0.66f, h * 0.30f);
            shackle.lineTo(w * 0.66f, h * 0.44f);
            g.draw(shackle);
        });
    }

    public static Icon group(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float r = w * 0.15f;
            g.draw(new Ellipse2D.Float(w * 0.50f - r, h * 0.22f, r * 2f, r * 2f));
            Path2D arc = new Path2D.Float();
            arc.moveTo(w * 0.22f, h * 0.80f);
            arc.curveTo(w * 0.22f, h * 0.56f, w * 0.78f, h * 0.56f, w * 0.78f, h * 0.80f);
            g.draw(arc);
        });
    }

    public static Icon plus(Supplier<Color> color, int size) {
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int pad = Math.round(w * 0.22f);
            int cx = w / 2;
            int cy = h / 2;
            g.drawLine(cx, pad, cx, h - pad);
            g.drawLine(pad, cy, w - pad, cy);
        });
    }

    /**
     * Creates a dark rounded Swing button that bypasses light native Windows L&F chrome.
     */
    public static JButton darkButton(String text, Icon icon,
                                     Color bg, Color hoverBg, Color borderColor,
                                     Color fg, int arc) {
        return themedButton(text, icon, () -> bg, () -> hoverBg,
                borderColor == null ? null : () -> borderColor, () -> fg, arc);
    }

    public static JButton themedButton(String text, Icon icon,
                                       Supplier<Color> bg, Supplier<Color> hoverBg,
                                       Supplier<Color> borderColor, Supplier<Color> fg, int arc) {
        JButton btn = new JButton(text, icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = bg != null ? bg.get() : Theme.BTN_DARK;
                Color hover = hoverBg != null ? hoverBg.get() : null;
                Color border = borderColor != null ? borderColor.get() : null;
                Color fill = base;
                if (!isEnabled()) {
                    fill = new Color(base.getRed(), base.getGreen(), base.getBlue(), 130);
                } else if (getModel().isPressed()) {
                    fill = base.darker();
                } else if (getModel().isRollover() && hover != null) {
                    fill = hover;
                }
                if (fg != null) {
                    setForeground(fg.get());
                }
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                if (border != null) {
                    g2.setColor(border);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setUI(new BasicButtonUI());
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setRolloverEnabled(true);
        btn.setForeground(fg != null ? fg.get() : Theme.TEXT_PRIMARY);
        btn.setFont(Theme.FONT_SMALL);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(4, 8, 4, 8));
        btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        return btn;
    }

    public static JButton iconButton(Icon icon, Icon hoverIcon, int size, int arc) {
        JButton btn = new JButton(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getModel().isRollover() || getModel().isPressed()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color fill = getModel().isPressed() ? Theme.BG_CARD_HOVER.darker() : Theme.BG_CARD_HOVER;
                    g2.setColor(fill);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setUI(new BasicButtonUI());
        if (hoverIcon != null) {
            btn.setRolloverIcon(hoverIcon);
        }
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setRolloverEnabled(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(0, 0, 0, 0));
        btn.setBorder(BorderFactory.createEmptyBorder());
        btn.setPreferredSize(new Dimension(size, size));
        return btn;
    }

    /**
     * Loads and scales an image from disk using source subsampling so very large images
     * stay well within tight heap limits.
     */
    public static BufferedImage loadScaledImage(Path path, int maxWidth, int maxHeight) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int origW = reader.getWidth(0);
                int origH = reader.getHeight(0);
                if (origW <= 0 || origH <= 0) {
                    return null;
                }

                double scale = Math.min(1.0, Math.min((double) maxWidth / origW, (double) maxHeight / origH));
                int targetW = Math.max(1, (int) Math.round(origW * scale));
                int targetH = Math.max(1, (int) Math.round(origH * scale));

                int subsample = Math.max(1, Math.min(origW / targetW, origH / targetH));
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsample > 1) {
                    param.setSourceSubsampling(subsample, subsample, 0, 0);
                }
                BufferedImage decoded = reader.read(0, param);
                if (decoded == null) {
                    return null;
                }
                if (decoded.getWidth() == targetW && decoded.getHeight() == targetH) {
                    return decoded;
                }
                return resizeSmooth(decoded, targetW, targetH);
            } finally {
                reader.dispose();
            }
        }
    }

    public static BufferedImage resizeSmooth(BufferedImage src, int targetW, int targetH) {
        int type = src.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage dest = new BufferedImage(targetW, targetH, type);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, targetW, targetH, null);
        g.dispose();
        return dest;
    }

    /**
     * Converts any AWT Image (including HiDPI MultiResolutionImage) to a BufferedImage.
     */
    public static BufferedImage toBufferedImage(Image img) {
        if (img == null) {
            return null;
        }
        Image resolved = img;
        if (img instanceof MultiResolutionImage) {
            List<Image> variants = ((MultiResolutionImage) img).getResolutionVariants();
            Image best = null;
            int bestWidth = -1;
            for (Image variant : variants) {
                int vw = variant.getWidth(null);
                if (vw > bestWidth) {
                    bestWidth = vw;
                    best = variant;
                }
            }
            if (best != null) {
                resolved = best;
            }
        }
        if (resolved instanceof BufferedImage) {
            return (BufferedImage) resolved;
        }
        ImageIcon loaded = new ImageIcon(resolved);
        int w = loaded.getIconWidth();
        int h = loaded.getIconHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.drawImage(loaded.getImage(), 0, 0, null);
        g.dispose();
        return bi;
    }

    public static boolean copyImageToClipboard(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        try {
            BufferedImage img = loadScaledImage(path, 3840, 2160);
            if (img == null) {
                return false;
            }
            return copyImageToClipboard(img);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean copyImageToClipboard(Image image) {
        if (image == null) {
            return false;
        }
        Transferable transferable = new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{DataFlavor.imageFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.imageFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!DataFlavor.imageFlavor.equals(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return image;
            }
        };
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @FunctionalInterface
    private interface Painter {
        void paint(Graphics2D g, int w, int h);
    }

    private static final class VectorIcon implements Icon {
        private final int width;
        private final int height;
        private final Supplier<Color> color;
        private final Painter painter;

        private VectorIcon(int width, int height, Supplier<Color> color, Painter painter) {
            this.width = width;
            this.height = height;
            this.color = color;
            this.painter = painter;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.translate(x, y);
            g2.setColor(color.get());
            painter.paint(g2, width, height);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
