package com.quickchat.ui;

import com.quickchat.i18n.I18n;
import com.quickchat.model.FileAttachment;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Full-size / large modal preview window for screenshots and images sent in chat.
 * Supports Ctrl+C to copy the image, Esc to close, and direct Download.
 */
public class ImagePreviewDialog extends JDialog {
    private Point dragOffset;

    public ImagePreviewDialog(Window owner, FileAttachment attachment,
                              Consumer<FileAttachment> downloadAction) {
        super(owner, I18n.t(I18n.Key.IMAGE_PREVIEW_TITLE, attachment.getFileName()),
                ModalityType.MODELESS);
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int maxImgW = Math.max(320, (int) (screen.width * 0.80));
        int maxImgH = Math.max(240, (int) (screen.height * 0.78));

        BufferedImage image = null;
        Path path = attachment.getLocalPath();
        if (path != null) {
            try {
                image = UiIcons.loadScaledImage(path, maxImgW, maxImgH);
            } catch (IOException ignored) {
            }
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.BG_WINDOW);
        root.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));

        // Header
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(Theme.BG_HEADER);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(8, 12, 8, 8)
        ));

        JLabel title = new JLabel(attachment.getFileName() + " · "
                + FileAttachment.formatSize(attachment.getFileSize()));
        title.setFont(Theme.FONT_BOLD);
        title.setForeground(Theme.TEXT_PRIMARY);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);

        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(Theme.FONT_SMALL);
        statusLabel.setForeground(Theme.ONLINE_DOT);

        JButton copyBtn = UiIcons.darkButton(null, UiIcons.copy(Theme.TEXT_PRIMARY, 14),
                Theme.BTN_DARK, Theme.BTN_DARK_HOVER, Theme.BTN_DARK_BORDER, Theme.TEXT_PRIMARY, 8);
        copyBtn.setPreferredSize(new Dimension(30, 28));
        copyBtn.setToolTipText(I18n.t(I18n.Key.COPY_IMAGE));

        final BufferedImage finalImage = image;
        Runnable doCopy = () -> {
            boolean ok = finalImage != null
                    ? UiIcons.copyImageToClipboard(finalImage)
                    : UiIcons.copyImageToClipboard(path);
            if (ok) {
                statusLabel.setText(I18n.t(I18n.Key.COPIED_NOTICE));
                Timer t = new Timer(1800, e -> statusLabel.setText(" "));
                t.setRepeats(false);
                t.start();
            }
        };
        copyBtn.addActionListener(e -> doCopy.run());

        JButton downloadBtn = UiIcons.darkButton(
                I18n.t(I18n.Key.DOWNLOAD_BUTTON),
                UiIcons.download(Color.WHITE, 14),
                Theme.BTN_ACCENT, Theme.BTN_ACCENT_HOVER, null, Color.WHITE, 8);
        downloadBtn.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        downloadBtn.setToolTipText(I18n.t(I18n.Key.DOWNLOAD_TOOLTIP));
        downloadBtn.addActionListener(e -> {
            if (downloadAction != null) {
                downloadAction.accept(attachment);
            }
        });

        JButton closeBtn = UiIcons.darkButton(null, UiIcons.cancel(Theme.TEXT_MUTED, 14),
                Theme.BTN_DARK, Theme.BTN_DARK_HOVER, Theme.BTN_DARK_BORDER, Theme.TEXT_PRIMARY, 8);
        closeBtn.setPreferredSize(new Dimension(28, 28));
        closeBtn.addActionListener(e -> dispose());

        actions.add(statusLabel);
        actions.add(copyBtn);
        actions.add(downloadBtn);
        actions.add(closeBtn);

        header.add(title, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);

        MouseAdapter dragger = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                Point p = e.getLocationOnScreen();
                dragOffset = new Point(p.x - getX(), p.y - getY());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset != null) {
                    Point p = e.getLocationOnScreen();
                    setLocation(p.x - dragOffset.x, p.y - dragOffset.y);
                }
            }
        };
        header.addMouseListener(dragger);
        header.addMouseMotionListener(dragger);
        title.addMouseListener(dragger);
        title.addMouseMotionListener(dragger);

        JLabel imageLabel;
        if (image != null) {
            imageLabel = new JLabel(new ImageIcon(image));
            imageLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        } else {
            imageLabel = new JLabel(attachment.getFileName());
            imageLabel.setForeground(Theme.TEXT_MUTED);
            imageLabel.setFont(Theme.FONT_BODY);
            imageLabel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        }
        imageLabel.setHorizontalAlignment(JLabel.CENTER);

        JScrollPane scroll = new JScrollPane(imageLabel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG_WINDOW);

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        setContentPane(root);

        root.registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        root.registerKeyboardAction(
                e -> doCopy.run(),
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        pack();
        int w = Math.min(screen.width - 40, Math.max(360, getWidth()));
        int h = Math.min(screen.height - 40, Math.max(220, getHeight()));
        setSize(new Dimension(w, h));
        setShape(new RoundRectangle2D.Double(0, 0, w, h, 14, 14));
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(root::requestFocusInWindow);
    }
}
