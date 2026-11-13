package com.quickchat.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Custom on-screen floating notification popup (top-center of the screen,
 * completely independent of Windows bottom-right toast notifications).
 * Never steals focus from the active application.
 */
public class NotificationPopup extends JWindow {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 72;
    private static final int DISPLAY_MS = 4500;

    private final JLabel titleLabel = new JLabel();
    private final JLabel messageLabel = new JLabel();
    private final Timer hideTimer;
    private Runnable clickAction;

    public NotificationPopup() {
        setAlwaysOnTop(true);
        setFocusableWindowState(false);
        setSize(WIDTH, HEIGHT);
        setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, 14, 14));

        JPanel root = new JPanel(new BorderLayout(10, 0));
        root.setBackground(Theme.BG_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.ACCENT, 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 10)
        ));
        root.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel textPanel = new JPanel(new BorderLayout(0, 4));
        textPanel.setOpaque(false);

        titleLabel.setFont(Theme.FONT_BOLD);
        titleLabel.setForeground(Theme.TEXT_PRIMARY);

        messageLabel.setFont(Theme.FONT_BODY);
        messageLabel.setForeground(Theme.TEXT_MUTED);

        textPanel.add(titleLabel, BorderLayout.NORTH);
        textPanel.add(messageLabel, BorderLayout.CENTER);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(Theme.FONT_TITLE);
        closeBtn.setForeground(Theme.TEXT_MUTED);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setPreferredSize(new Dimension(24, 24));
        closeBtn.addActionListener(e -> setVisible(false));

        root.add(textPanel, BorderLayout.CENTER);
        root.add(closeBtn, BorderLayout.EAST);

        MouseAdapter clickHandler = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setVisible(false);
                Runnable action = clickAction;
                if (action != null) {
                    action.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                root.setBackground(Theme.BG_CARD_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                root.setBackground(Theme.BG_HEADER);
            }
        };
        root.addMouseListener(clickHandler);
        textPanel.addMouseListener(clickHandler);

        setContentPane(root);

        hideTimer = new Timer(DISPLAY_MS, e -> setVisible(false));
        hideTimer.setRepeats(false);
    }

    /**
     * Shows a notification with a title, a short preview, and an optional
     * action that runs when the notification is clicked.
     */
    public void showNotification(String title, String preview, Runnable onClick) {
        this.clickAction = onClick;
        titleLabel.setText(title);

        String text = preview == null ? "" : preview.replace("\n", " ").trim();
        if (text.length() > 44) {
            text = text.substring(0, 41) + "...";
        }
        messageLabel.setText(text);

        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int x = bounds.x + (bounds.width - WIDTH) / 2;
        int y = bounds.y + 22;
        setLocation(x, y);

        setVisible(true);
        toFront();
        hideTimer.restart();
    }
}
