package com.whiteant.pet.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GiftWindow extends JWindow {

    private static final int SIZE = 44;
    private static final int FLOOR_OFFSET = 20;

    private final Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    private final Timer timer;

    private final double x;
    private double y;
    private double vy;
    private int frame;
    private boolean claimed;

    public GiftWindow(double nx, Runnable onClaim) {
        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(SIZE, SIZE);

        x = area.x + Math.max(0, Math.min(1, nx)) * (area.width - SIZE);
        y = area.y - SIZE;

        JPanel panel = new JPanel() {
            {
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                draw((Graphics2D) g);
            }
        };
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || claimed) {
                    return;
                }
                claimed = true;
                repaint();
                onClaim.run();
            }
        });
        setContentPane(panel);

        timer = new Timer(33, e -> tick());
    }

    public void start() {
        setLocation((int) x, (int) y);
        setVisible(true);
        timer.start();
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }

    private int groundY() {
        return area.y + area.height - SIZE - FLOOR_OFFSET;
    }

    private void tick() {
        frame++;
        if (y < groundY()) {
            // 천천히 떨어지게 최대 속도 제한
            vy = Math.min(4, vy + 0.3);
            y = Math.min(groundY(), y + vy);
        }
        setLocation((int) x, (int) y);
        repaint();
    }

    private void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean landed = y >= groundY();
        int wobble = landed && frame % 90 < 20 ? (int) (Math.sin(frame * 0.8) * 3) : 0;

        if (claimed) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }
        g.translate(wobble, 0);

        g.setColor(new Color(0xe5534b));
        g.fillRoundRect(6, 18, 32, 24, 4, 4);
        g.setColor(new Color(0xf47067));
        g.fillRoundRect(3, 11, 38, 9, 4, 4);

        g.setColor(new Color(0xf2c94c));
        g.fillRect(19, 11, 6, 31);
        g.fillOval(11, 3, 11, 9);
        g.fillOval(22, 3, 11, 9);
        g.setColor(new Color(0xd4a72c));
        g.fillOval(19, 6, 6, 6);
    }
}