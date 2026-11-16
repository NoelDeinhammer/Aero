package com.quickchat.ui;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Adds bottom-right grip and edge resizing support to undecorated rounded Swing windows.
 */
public final class WindowResizer {
    private static final int CORNER_SIZE = 16;
    private static final int SW_SIZE = 12;
    private static final int SIDE_THICKNESS = 4;
    private static final int BOTTOM_THICKNESS = 5;

    private WindowResizer() {
    }

    public static <T extends Window & RootPaneContainer> void install(
            T window,
            int cornerArc,
            int minWidth,
            int minHeight,
            int topSafeAreaHeight,
            Runnable onResizeFinished) {
        JLayeredPane layeredPane = window.getLayeredPane();
        int safeTop = Math.max(0, topSafeAreaHeight);

        ResizeHandle seGrip = new ResizeHandle(
                window, cornerArc, minWidth, minHeight,
                false, true, true, true,
                Cursor.SE_RESIZE_CURSOR, onResizeFinished);
        ResizeHandle swCorner = new ResizeHandle(
                window, cornerArc, minWidth, minHeight,
                true, false, true, false,
                Cursor.SW_RESIZE_CURSOR, onResizeFinished);
        ResizeHandle sEdge = new ResizeHandle(
                window, cornerArc, minWidth, minHeight,
                false, false, true, false,
                Cursor.S_RESIZE_CURSOR, onResizeFinished);
        ResizeHandle eEdge = new ResizeHandle(
                window, cornerArc, minWidth, minHeight,
                false, true, false, false,
                Cursor.E_RESIZE_CURSOR, onResizeFinished);
        ResizeHandle wEdge = new ResizeHandle(
                window, cornerArc, minWidth, minHeight,
                true, false, false, false,
                Cursor.W_RESIZE_CURSOR, onResizeFinished);

        layeredPane.add(seGrip, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(swCorner, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(sEdge, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(eEdge, JLayeredPane.PALETTE_LAYER);
        layeredPane.add(wEdge, JLayeredPane.PALETTE_LAYER);

        Runnable layoutHandles = () -> {
            int w = layeredPane.getWidth();
            int h = layeredPane.getHeight();
            if (w <= 0 || h <= 0) {
                w = window.getWidth();
                h = window.getHeight();
            }
            if (w <= 0 || h <= 0) {
                return;
            }
            seGrip.setBounds(w - CORNER_SIZE, h - CORNER_SIZE, CORNER_SIZE, CORNER_SIZE);
            swCorner.setBounds(0, h - SW_SIZE, SW_SIZE, SW_SIZE);
            sEdge.setBounds(SW_SIZE, h - BOTTOM_THICKNESS,
                    Math.max(0, w - SW_SIZE - CORNER_SIZE), BOTTOM_THICKNESS);
            eEdge.setBounds(w - SIDE_THICKNESS, safeTop,
                    SIDE_THICKNESS, Math.max(0, h - safeTop - CORNER_SIZE));
            wEdge.setBounds(0, safeTop,
                    SIDE_THICKNESS, Math.max(0, h - safeTop - SW_SIZE));
        };

        layeredPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layoutHandles.run();
            }
        });

        window.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateRoundShape(window, cornerArc);
                layoutHandles.run();
            }
        });

        updateRoundShape(window, cornerArc);
        layoutHandles.run();
    }

    public static Dimension usableScreenSize(Window window, int minWidth, int minHeight) {
        Rectangle bounds = null;
        Insets insets = new Insets(0, 0, 0, 0);
        GraphicsConfiguration gc = window != null ? window.getGraphicsConfiguration() : null;
        if (gc != null) {
            bounds = gc.getBounds();
            insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        } else {
            bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        }
        int maxW = Math.max(minWidth, bounds.width - insets.left - insets.right);
        int maxH = Math.max(minHeight, bounds.height - insets.top - insets.bottom);
        return new Dimension(maxW, maxH);
    }

    public static void updateRoundShape(Window window, int cornerArc) {
        if (window == null || cornerArc <= 0) {
            return;
        }
        int w = window.getWidth();
        int h = window.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (window.getShape() instanceof RoundRectangle2D current
                && Math.round(current.getWidth()) == w
                && Math.round(current.getHeight()) == h) {
            return;
        }
        window.setShape(new RoundRectangle2D.Double(0, 0, w, h, cornerArc, cornerArc));
    }

    private static final class ResizeHandle extends JComponent {
        private final boolean paintGrip;
        private boolean hovered;
        private boolean dragging;
        private Point dragStartScreen;
        private Rectangle dragStartBounds;
        private Dimension maxDragSize;

        private <T extends Window & RootPaneContainer> ResizeHandle(
                T window,
                int cornerArc,
                int minWidth,
                int minHeight,
                boolean west,
                boolean east,
                boolean south,
                boolean paintGrip,
                int cursorType,
                Runnable onResizeFinished) {
            this.paintGrip = paintGrip;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(cursorType));

            MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    if (paintGrip) {
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    if (paintGrip) {
                        repaint();
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) {
                        return;
                    }
                    dragging = true;
                    dragStartScreen = e.getLocationOnScreen();
                    dragStartBounds = window.getBounds();
                    maxDragSize = usableScreenSize(window, minWidth, minHeight);
                    if (paintGrip) {
                        repaint();
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (!dragging || dragStartScreen == null || dragStartBounds == null) {
                        return;
                    }
                    Point current = e.getLocationOnScreen();
                    int dx = current.x - dragStartScreen.x;
                    int dy = current.y - dragStartScreen.y;

                    int newX = dragStartBounds.x;
                    int newY = dragStartBounds.y;
                    int newW = dragStartBounds.width;
                    int newH = dragStartBounds.height;

                    if (east) {
                        newW = clamp(dragStartBounds.width + dx, minWidth, maxDragSize.width);
                    } else if (west) {
                        newW = clamp(dragStartBounds.width - dx, minWidth, maxDragSize.width);
                        newX = dragStartBounds.x + (dragStartBounds.width - newW);
                    }

                    if (south) {
                        newH = clamp(dragStartBounds.height + dy, minHeight, maxDragSize.height);
                    }

                    Rectangle currentBounds = window.getBounds();
                    if (newX == currentBounds.x && newY == currentBounds.y
                            && newW == currentBounds.width && newH == currentBounds.height) {
                        return;
                    }

                    window.setBounds(newX, newY, newW, newH);
                    updateRoundShape(window, cornerArc);
                    window.validate();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!dragging) {
                        return;
                    }
                    dragging = false;
                    if (paintGrip) {
                        repaint();
                    }
                    Rectangle endBounds = window.getBounds();
                    boolean sizeChanged = dragStartBounds != null
                            && (endBounds.width != dragStartBounds.width
                            || endBounds.height != dragStartBounds.height);
                    dragStartScreen = null;
                    dragStartBounds = null;
                    if (sizeChanged && onResizeFinished != null) {
                        onResizeFinished.run();
                    }
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!paintGrip) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int alpha = dragging ? 235 : (hovered ? 195 : 115);
            g2.setColor(new Color(Theme.TEXT_MUTED.getRed(),
                    Theme.TEXT_MUTED.getGreen(),
                    Theme.TEXT_MUTED.getBlue(), alpha));
            g2.setStroke(new BasicStroke(1.25f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Keep lines strictly inside the rounded corner curve.
            g2.drawLine(4, 12, 12, 4);
            g2.drawLine(7, 12, 12, 7);
            g2.drawLine(10, 12, 12, 10);
            g2.dispose();
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
