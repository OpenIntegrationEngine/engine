// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.client.ui.components.tag;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

/**
 * A single rounded tag "chip" with a delete affordance. Used by {@link MirthTagField}.
 */
public class TagChip extends JPanel {
    private static final Font FONT = new Font("Tahoma", Font.PLAIN, 11);
    private static final Font CLOSE_FONT = new Font("Tahoma", Font.BOLD, 12);
    private static final int HEIGHT = 20;
    private static final int ARC = 6;
    private static final float OPACITY = 0.9f;
    private static final Color BORDER_COLOR = new Color(0xD9, 0xD9, 0xD9);

    private JLabel closeLabel;

    public TagChip(String name, Color background, Color foreground, final Runnable onDelete) {
        setLayout(new MigLayout("insets 0 5 0 3, gap 3, novisualpadding, fill"));
        setOpaque(false);
        setFocusable(false);
        setBackground(background);
        setForeground(foreground);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(FONT);
        nameLabel.setForeground(foreground);
        add(nameLabel);

        closeLabel = new JLabel("×");
        closeLabel.setFont(CLOSE_FONT);
        closeLabel.setForeground(foreground);
        closeLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isEnabled()) {
                    onDelete.run();
                }
            }
        });
        add(closeLabel);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        closeLabel.setVisible(enabled);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, Math.max(size.height, HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // The chips this replaced were composited at 90% over the white row and outlined in grey.
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACITY));
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.setColor(BORDER_COLOR);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        g2.dispose();

        super.paintComponent(g);
    }
}
