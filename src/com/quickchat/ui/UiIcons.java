package com.quickchat.ui;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/**
 * Crisp vector Swing icons and memory-safe image/clipboard utilities.
 */
public final class UiIcons {
    private UiIcons() {
    }

    public static Icon paperclip(Color color, int size) {
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
        return new VectorIcon(size, size, color, (g, w, h) -> {
            g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = w / 2f;
            float top = h * 0.16f;
            float arrowBottom = h * 0.66f;
            float arrowWing = w * 0.24f;

            // Vertical stem
            g.drawLine(Math.round(cx), Math.round(top), Math.round(cx), Math.round(arrowBottom));
            // Arrow head
            g.drawLine(Math.round(cx - arrowWing), Math.round(arrowBottom - arrowWing),
                    Math.round(cx), Math.round(arrowBottom));
            g.drawLine(Math.round(cx + arrowWing), Math.round(arrowBottom - arrowWing),
                    Math.round(cx), Math.round(arrowBottom));

            // Tray at bottom
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
        private final Color color;
        private final Painter painter;

        private VectorIcon(int width, int height, Color color, Painter painter) {
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
            g2.setColor(color);
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
