package com.whiteant.pet.client;

import javax.swing.*;
import java.awt.*;

public class SnackWindow extends JWindow {

    private static final int SIZE = 24;
    private static final int FLOOR_OFFSET = 20;
    private static final int LIFETIME_TICKS = 6000 / 33;
    private static final int FADE_TICKS = 1000 / 33;

    private final Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    private final Timer timer;

    private final double x;
    private double y;
    private double vy;
    private int landedTicks;

    public SnackWindow(double nx) {
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
        setContentPane(panel);

        timer = new Timer(33, e -> tick());
    }

    public int getCenterX() {
        return (int) x + SIZE / 2;
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
        if (y < groundY()) {
            vy += 1.0;
            y = Math.min(groundY(), y + vy);
        } else {
            landedTicks++;
            if (landedTicks >= LIFETIME_TICKS) {
                dispose();
                return;
            }
        }
        setLocation((int) x, (int) y);
        repaint();
    }

    private void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int remain = LIFETIME_TICKS - landedTicks;
        if (remain < FADE_TICKS) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, remain / (float) FADE_TICKS)));
        }

        g.setColor(new Color(0xc68642));
        g.fillOval(2, 2, 20, 20);
        g.setColor(new Color(0x5b3a1e));
        g.fillOval(7, 7, 3, 3);
        g.fillOval(13, 9, 3, 3);
        g.fillOval(9, 14, 3, 3);
        g.fillOval(15, 15, 2, 2);
    }
}