package com.xixka.topstatusbar.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUIScale;
import com.xixka.topstatusbar.model.StatusItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * A single compact, New UI-style status cell: icon + text, rounded hover
 * background, tooltip and click handling. Its preferred height stays within
 * the Main Toolbar row so the toolbar does not grow.
 */
public final class StatusCell extends JComponent {

    private static final JBColor HOVER_BACKGROUND = new JBColor(new Color(0, 0, 0, 22), new Color(255, 255, 255, 34));
    private static final JBColor PRESSED_BACKGROUND = new JBColor(new Color(0, 0, 0, 44), new Color(255, 255, 255, 60));
    private static final JBColor WARNING_FOREGROUND = new JBColor(0xB45309, 0xE08734);
    private static final JBColor ERROR_FOREGROUND = new JBColor(0xC13438, 0xE5545B);
    private static final JBColor INFO_FOREGROUND = new JBColor(0x2C6FD1, 0x7CA9F5);
    private static final JBColor FALLBACK_FOREGROUND = new JBColor(0x22272E, 0xB6BAC0);

    private final StatusItem item;
    @Nullable
    private final Project project;
    private boolean hover;
    private boolean pressed;

    public StatusCell(@NotNull StatusItem item, @Nullable Project project) {
        this.item = item;
        this.project = project;
        setOpaque(false);
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setAlignmentY(CENTER_ALIGNMENT);
        setFont(JBFont.label());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                StatusCell.this.item.onClick(project);
            }
        });
        setToolTipText(item.getTooltip());
    }

    public @NotNull StatusItem item() {
        return item;
    }

    public void refresh() {
        setToolTipText(item.getTooltip());
        setFont(JBFont.label());
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        Icon icon = item.getIcon();
        String text = item.getText();
        int gap = JBUIScale.scale(4);
        int padding = JBUIScale.scale(8);
        int width = padding * 2;
        if (icon != null) {
            width += icon.getIconWidth();
            if (!text.isEmpty()) {
                width += gap;
            }
        }
        if (!text.isEmpty()) {
            width += metrics.stringWidth(text);
        }
        int height = Math.max(icon == null ? 0 : icon.getIconHeight(), metrics.getHeight()) + JBUIScale.scale(4);
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (hover) {
                float arc = JBUIScale.scale(6f);
                g2.setColor(pressed ? PRESSED_BACKGROUND : HOVER_BACKGROUND);
                g2.fill(new RoundRectangle2D.Float(0f, 0f, getWidth() - 1f, getHeight() - 1f, arc, arc));
            }
            Icon icon = item.getIcon();
            String text = item.getText();
            FontMetrics metrics = g2.getFontMetrics();
            int padding = JBUIScale.scale(8);
            int gap = JBUIScale.scale(4);
            int x = padding;
            if (icon != null) {
                int iconY = Math.max(0, (getHeight() - icon.getIconHeight()) / 2);
                icon.paintIcon(this, g2, x, iconY);
                x += icon.getIconWidth();
                if (!text.isEmpty()) {
                    x += gap;
                }
            }
            if (!text.isEmpty()) {
                g2.setColor(foregroundColor());
                int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.drawString(text, x, textY);
            }
        } finally {
            g2.dispose();
        }
    }

    private Color foregroundColor() {
        switch (item.getSeverity()) {
            case WARNING:
                return WARNING_FOREGROUND;
            case ERROR:
                return ERROR_FOREGROUND;
            case INFO:
                return INFO_FOREGROUND;
            default:
                Color label = UIManager.getColor("Label.foreground");
                return label == null ? FALLBACK_FOREGROUND : label;
        }
    }
}
