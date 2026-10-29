package com.whiteant.pet.client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class PetWindow extends JWindow {

    public interface MenuOpener {
        void open(Component invoker, int x, int y);
    }

    public interface MoveListener {
        void onMove(double nx, double ny, boolean released);
    }

    private static final int TICK_MS = 33;

    private static final int W = 180;
    private static final int BUBBLE_AREA = 64;
    private static final int H = BUBBLE_AREA + 100;
    private static final int ANT_X = (W - 80) / 2;
    private static final int ANT_Y = BUBBLE_AREA;

    // 개미 몸통 + 이름표가 그려지는 영역 (창 기준)
    private static final Rectangle ANT_HIT = new Rectangle(ANT_X + 14, ANT_Y + 12, 56, 88);
    // 말풍선이 그려질 수 있는 영역 (창 기준)
    private static final Rectangle BUBBLE_HIT = new Rectangle(4, 0, W - 8, ANT_Y);

    private static final double WALK_SPEED = 1.5;
    private static final double RUN_SPEED = 3.5;

    private static final int BUBBLE_MAX_WIDTH = 150;
    private static final int BUBBLE_MAX_LINES = 3;
    private static final int BUBBLE_MIN_MS = 4000;
    private static final int BUBBLE_MS_PER_CHAR = 300;
    private static final int BUBBLE_MAX_MS = 15000;
    private static final int FADE_TICKS = 1000 / TICK_MS;
    private static final long MOVE_SEND_INTERVAL_MS = 50;

    private static final Font BUBBLE_FONT = new Font("Malgun Gothic", Font.BOLD, 12);
    private static final Font NAME_FONT = new Font("Malgun Gothic", Font.BOLD, 11);
    private static final Font ZZZ_FONT = new Font("Malgun Gothic", Font.BOLD, 11);

    private final String name;
    private final boolean me;
    private final MoveListener moveListener;
    private final Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    private final Timer timer;

    private Color color;
    private Runnable onDoubleClick;

    private double x;
    private double y;
    private double vy;
    private int dir = Math.random() < 0.5 ? 1 : -1;
    private int frame;
    private boolean dragging;
    private boolean moved;
    private boolean hovering;
    private boolean sleeping;
    private Item item;
    private Double targetX;
    private Point dragOffset;
    private long lastMoveSentAt;

    private String bubbleText;
    private int bubbleTicks;

    public PetWindow(String name, Color color, boolean me, MenuOpener menuOpener, MoveListener moveListener) {
        this.name = name;
        this.color = color;
        this.me = me;
        this.moveListener = moveListener;

        setAlwaysOnTop(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(W, H);

        x = area.x + Math.random() * (area.width - W);
        y = area.y; // 위에서 떨어지면서 등장

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

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (menuOpener != null) {
                        menuOpener.open(panel, e.getX(), e.getY());
                    }
                    return;
                }
                dragging = true;
                moved = false;
                targetX = null;
                dragOffset = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging || dragOffset == null) {
                    return;
                }
                Point p = e.getLocationOnScreen();
                x = p.x - dragOffset.x;
                y = p.y - dragOffset.y;
                moved = true;
                setLocation((int) x, (int) y);

                long now = System.currentTimeMillis();
                if (now - lastMoveSentAt >= MOVE_SEND_INTERVAL_MS) {
                    lastMoveSentAt = now;
                    notifyMove(false);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || !dragging) {
                    return;
                }
                dragging = false;
                dragOffset = null;
                vy = 0;
                // 그냥 클릭은 안 보냄
                if (moved) {
                    notifyMove(true);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2 && onDoubleClick != null) {
                    onDoubleClick.run();
                }
            }
        };
        panel.addMouseListener(mouse);
        panel.addMouseMotionListener(mouse);

        timer = new Timer(TICK_MS, e -> tick());
    }

    public String getPetName() {
        return name;
    }

    public void setOnDoubleClick(Runnable onDoubleClick) {
        this.onDoubleClick = onDoubleClick;
    }

    /** 채팅 입력칸 띄울 위치 (개미 머리 위, 화면 좌표) */
    public Point getChatAnchor() {
        return new Point((int) x + W / 2, (int) y + ANT_Y + 10);
    }

    public void start() {
        setLocation((int) x, (int) y);
        setVisible(true);
        timer.start();
    }

    public void showBubble(String text) {
        bubbleText = text;
        int ms = Math.min(BUBBLE_MAX_MS, BUBBLE_MIN_MS + text.length() * BUBBLE_MS_PER_CHAR);
        bubbleTicks = ms / TICK_MS;
        repaint();
    }

    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    public void setItem(Item item) {
        this.item = item;
        repaint();
    }

    public void setSleeping(boolean sleeping) {
        this.sleeping = sleeping;
        if (sleeping) {
            targetX = null;
        }
        repaint();
    }

    /** 화면 x좌표(개미 중심 기준)로 달려가기. 자고 있으면 무시 */
    public void goTo(int screenCenterX) {
        if (sleeping) {
            return;
        }
        double tx = screenCenterX - W / 2.0;
        targetX = Math.max(area.x, Math.min(area.x + area.width - W, tx));
    }

    public double getNx() {
        return clamp((x - area.x) / Math.max(1, area.width - W));
    }

    /** 다른 사람이 이 개미를 드래그했을 때 */
    public void remoteMove(double nx, double ny, boolean released) {
        x = area.x + clamp(nx) * (area.width - W);
        y = area.y + clamp(ny) * (groundY() - area.y);
        dragging = !released;
        dragOffset = null;
        targetX = null;
        vy = 0;
        setLocation((int) x, (int) y);
        repaint();
    }

    @Override
    public void dispose() {
        timer.stop();
        super.dispose();
    }

    private void notifyMove(boolean released) {
        if (moveListener == null) {
            return;
        }
        double nx = (x - area.x) / Math.max(1, area.width - W);
        double ny = (y - area.y) / Math.max(1, groundY() - area.y);
        moveListener.onMove(clamp(nx), clamp(ny), released);
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private int groundY() {
        return area.y + area.height - H;
    }

    /** enter/exit 이벤트가 가끔 안 와서 커서 위치를 직접 확인 */
    private boolean isPointerOver() {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            return false;
        }
        Point p = info.getLocation();
        int lx = p.x - (int) x;
        int ly = p.y - (int) y;

        if (ANT_HIT.contains(lx, ly)) {
            return true;
        }
        return bubbleTicks > 0 && BUBBLE_HIT.contains(lx, ly);
    }

    private void tick() {
        hovering = !dragging && isPointerOver();

        // 마우스 올려두면 말풍선 유지
        if (bubbleTicks > 0 && !hovering) {
            bubbleTicks--;
        }

        if (dragging) {
            repaint();
            return;
        }

        if (y < groundY()) {
            frame++;
            vy += 1.2;
            y = Math.min(groundY(), y + vy);
        } else {
            vy = 0;
            y = groundY();

            if (sleeping) {
                frame++;
            } else if (!hovering) {
                frame++;
                if (targetX != null) {
                    double dx = targetX - x;
                    if (Math.abs(dx) <= RUN_SPEED) {
                        x = targetX;
                        targetX = null;
                        showBubble("냠냠");
                    } else {
                        dir = dx > 0 ? 1 : -1;
                        x += dir * RUN_SPEED;
                    }
                } else {
                    if (Math.random() < 0.01) {
                        dir = -dir;
                    }
                    x += dir * WALK_SPEED;
                }

                if (x < area.x) {
                    x = area.x;
                    dir = 1;
                }
                if (x > area.x + area.width - W) {
                    x = area.x + area.width - W;
                    dir = -1;
                }
            }
        }

        setLocation((int) x, (int) y);
        repaint();
    }

    private void draw(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (bubbleTicks > 0 && bubbleText != null) {
            drawBubble(g);
        }

        g.translate(ANT_X, ANT_Y);
        drawAnt(g);
        drawNameTag(g);
    }

    private void drawBubble(Graphics2D g) {
        Composite original = g.getComposite();
        if (!hovering && bubbleTicks < FADE_TICKS) {
            float alpha = Math.max(0f, bubbleTicks / (float) FADE_TICKS);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        g.setFont(BUBBLE_FONT);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = wrap(bubbleText, fm);

        int lineH = fm.getHeight();
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, fm.stringWidth(line));
        }

        int bw = textW + 16;
        int bh = lines.size() * lineH + 8;
        int bx = (W - bw) / 2;
        int bottom = ANT_Y - 6;
        int by = bottom - bh;
        int cx = W / 2;

        g.setColor(Color.WHITE);
        g.fillRoundRect(bx, by, bw, bh, 12, 12);
        g.fillPolygon(new int[]{cx - 5, cx + 5, cx}, new int[]{bottom - 1, bottom - 1, bottom + 5}, 3);
        g.setColor(new Color(0x8c959f));
        g.drawRoundRect(bx, by, bw, bh, 12, 12);

        g.setColor(new Color(0x1f2328));
        int ty = by + 4 + fm.getAscent();
        for (String line : lines) {
            g.drawString(line, bx + 8, ty);
            ty += lineH;
        }

        g.setComposite(original);
    }

    private List<String> wrap(String text, FontMetrics fm) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();

        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            i += Character.charCount(cp);

            if (cur.length() > 0 && fm.stringWidth(cur + ch) > BUBBLE_MAX_WIDTH) {
                lines.add(cur.toString());
                cur.setLength(0);

                if (lines.size() == BUBBLE_MAX_LINES) {
                    String last = lines.get(BUBBLE_MAX_LINES - 1);
                    lines.set(BUBBLE_MAX_LINES - 1, last.substring(0, Math.max(0, last.length() - 1)) + "…");
                    return lines;
                }
            }
            cur.append(ch);
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    private void drawAnt(Graphics2D g) {
        boolean walking = !dragging && !hovering && !sleeping && vy == 0;
        int bob = walking ? (int) (Math.sin(frame * 0.4) * 2) : 0;
        int legSwing = walking ? (int) (Math.sin(frame * 0.4) * 4) : 0;

        g.setColor(color);
        g.setStroke(new BasicStroke(2));

        g.drawLine(30, 62, 24 + legSwing, 76);
        g.drawLine(40, 64, 40 - legSwing, 78);
        g.drawLine(50, 62, 56 + legSwing, 76);

        g.fillOval(26, 44 + bob, 28, 24);

        int headX = dir > 0 ? 40 : 18;
        int headY = 26 + bob;
        g.fillOval(headX, headY, 24, 22);

        g.drawLine(headX + 8, headY + 2, headX + 2, headY - 10);
        g.drawLine(headX + 16, headY + 2, headX + 22, headY - 10);

        g.setColor(Color.WHITE);
        int eyeX = dir > 0 ? headX + 13 : headX + 5;
        if (sleeping) {
            g.drawLine(eyeX, headY + 10, eyeX + 6, headY + 10);
        } else {
            g.fillOval(eyeX, headY + 7, 6, 6);
        }

        if (dragging) {
            g.drawArc(headX + 6, headY + 14, 10, 6, 0, 180);
        }

        if (item != null) {
            item.draw(g, headX, headY, dir);
        }

        if (sleeping) {
            g.setFont(ZZZ_FONT);
            g.setColor(new Color(0x8c959f));
            int zy = headY - 6 - (frame / 10) % 6;
            g.drawString("Zzz", dir > 0 ? headX + 22 : headX - 18, zy);
        }
    }

    private void drawNameTag(Graphics2D g) {
        g.setFont(NAME_FONT);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(name);
        int tagX = (80 - tw) / 2 - 4;

        g.setColor(me ? new Color(9, 105, 218, 200) : new Color(0, 0, 0, 140));
        g.fillRoundRect(tagX, 82, tw + 8, 15, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString(name, tagX + 4, 94);
    }
}