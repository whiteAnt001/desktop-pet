package com.whiteant.pet.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;

@Getter
@RequiredArgsConstructor
public enum Item {
    // 머리
    RIBBON("리본", Rarity.COMMON, Slot.HEAD),
    FLOWER("꽃", Rarity.COMMON, Slot.HEAD),
    STRAW_HAT("밀짚모자", Rarity.COMMON, Slot.HEAD),
    SPROUT("새싹", Rarity.COMMON, Slot.HEAD),
    PARTY_HAT("파티 고깔", Rarity.COMMON, Slot.HEAD),
    BEANIE("비니", Rarity.RARE, Slot.HEAD),
    HEADPHONES("헤드폰", Rarity.RARE, Slot.HEAD),
    CAT_EARS("고양이 귀", Rarity.RARE, Slot.HEAD),
    CHEF_HAT("요리사 모자", Rarity.RARE, Slot.HEAD),
    WIZARD_HAT("마법사 모자", Rarity.EPIC, Slot.HEAD),
    HORNS("악마 뿔", Rarity.EPIC, Slot.HEAD),
    CROWN("왕관", Rarity.LEGENDARY, Slot.HEAD),
    HALO("천사 고리", Rarity.LEGENDARY, Slot.HEAD),

    // 얼굴
    BLUSH("볼터치", Rarity.COMMON, Slot.FACE),
    MUSTACHE("콧수염", Rarity.COMMON, Slot.FACE),
    MASK("마스크", Rarity.COMMON, Slot.FACE),
    SUNGLASSES("선글라스", Rarity.RARE, Slot.FACE),
    MONOCLE("외눈 안경", Rarity.RARE, Slot.FACE),
    EYEPATCH("안대", Rarity.RARE, Slot.FACE),

    // 목
    SCARF("목도리", Rarity.RARE, Slot.NECK),

    // 등
    BACKPACK("책가방", Rarity.COMMON, Slot.BACK),
    CAPE_RED("빨간 망토", Rarity.RARE, Slot.BACK),
    DEVIL_WINGS("악마 날개", Rarity.EPIC, Slot.BACK),
    CAPE_ROYAL("왕실 망토", Rarity.LEGENDARY, Slot.BACK),
    ANGEL_WINGS("천사 날개", Rarity.LEGENDARY, Slot.BACK),

    // 오라
    AURA_SPARKLE("반짝이 오라", Rarity.EPIC, Slot.AURA),
    AURA_HEART("하트 오라", Rarity.EPIC, Slot.AURA),
    AURA_SHADOW("그림자 오라", Rarity.EPIC, Slot.AURA),
    AURA_FIRE("불꽃 오라", Rarity.LEGENDARY, Slot.AURA),
    AURA_ELECTRIC("번개 오라", Rarity.LEGENDARY, Slot.AURA),
    AURA_RAINBOW("무지개 오라", Rarity.LEGENDARY, Slot.AURA);

    @Getter
    @RequiredArgsConstructor
    public enum Rarity {
        COMMON("일반"), RARE("레어"), EPIC("영웅"), LEGENDARY("전설");

        private final String label;
    }

    /** 선언 순서 = 그리는 순서 (오라가 제일 뒤) */
    @Getter
    @RequiredArgsConstructor
    public enum Slot {
        AURA("오라"), BACK("등"), NECK("목"), HEAD("머리"), FACE("얼굴");

        private final String label;
    }

    /** 그릴 때 필요한 개미 자세. 좌표는 개미 기준 (몸통 중심 대략 40, 52) */
    public record Pose(int headX, int headY, int bob, int dir, int frame) {
    }

    private final String label;
    private final Rarity rarity;
    private final Slot slot;

    public String displayName() {
        return "[" + rarity.getLabel() + "] " + label;
    }

    public static Item fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** "CROWN,CAPE_RED" → [CROWN, CAPE_RED]. 모르는 코드는 무시 */
    public static List<Item> fromCodes(String codes) {
        List<Item> items = new ArrayList<>();
        if (codes == null || codes.isBlank()) {
            return items;
        }
        for (String code : codes.split(",")) {
            Item item = fromCode(code);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    public static String toCodes(List<Item> items) {
        StringJoiner joiner = new StringJoiner(",");
        items.forEach(i -> joiner.add(i.name()));
        return joiner.toString();
    }

    public void draw(Graphics2D g0, Pose p) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int hx = p.headX();
        int hy = p.headY();
        int dir = p.dir();
        int f = p.frame();
        int back = -dir;
        int cx = hx + 12;
        int eyeX = dir > 0 ? hx + 13 : hx + 5;
        int bodyTop = 44 + p.bob();

        switch (this) {
            // ===== 오라 =====
            case AURA_SPARKLE -> {
                g.setColor(new Color(0xffe066));
                for (int i = 0; i < 7; i++) {
                    double a = i * Math.PI * 2 / 7 + f * 0.02;
                    int sx = (int) (40 + Math.cos(a) * 34);
                    int sy = (int) (50 + Math.sin(a) * 30);
                    float alpha = (float) (0.5 + 0.5 * Math.sin(f * 0.25 + i * 1.7));
                    g.setComposite(alpha(alpha));
                    star(g, sx, sy, 3 + alpha * 3);
                }
            }
            case AURA_HEART -> {
                g.setColor(new Color(0xff6b9d));
                for (int i = 0; i < 5; i++) {
                    int t = (f + i * 17) % 80;
                    int x = 14 + i * 13 + (int) (Math.sin((f + i * 10) * 0.1) * 4);
                    g.setComposite(alpha((1f - t / 80f) * 0.85f));
                    heart(g, x, 70 - t, 8);
                }
            }
            case AURA_SHADOW -> {
                int r = (int) (42 * (0.85 + 0.15 * Math.sin(f * 0.08)));
                glow(g, r, new Color(60, 20, 90, 160));
                g.setColor(new Color(90, 40, 130));
                for (int i = 0; i < 4; i++) {
                    int t = (f + i * 25) % 60;
                    g.setComposite(alpha(1f - t / 60f));
                    g.fillOval(18 + i * 14, 62 - t, 6, 10);
                }
            }
            case AURA_FIRE -> {
                int r = (int) (40 * (0.9 + 0.1 * Math.sin(f * 0.5)));
                glow(g, r, new Color(255, 140, 0, 150));
                for (int i = 0; i < 7; i++) {
                    int t = (f * 2 + i * 11) % 40;
                    int x = 18 + i * 7 + (int) (Math.sin((f + i * 7) * 0.3) * 2);
                    int size = Math.max(2, 9 - t / 5);
                    g.setComposite(alpha(1f - t / 40f));
                    g.setColor(new Color(i % 2 == 0 ? 0xff8c00 : 0xffd23f));
                    g.fillOval(x - size / 2, 66 - t - size, size, (int) (size * 1.6));
                }
            }
            case AURA_ELECTRIC -> {
                glow(g, 40, new Color(80, 160, 255, 130));
                g.setColor(new Color(0xbfe6ff));
                g.setStroke(new BasicStroke(1.6f));
                Random rnd = new Random((f / 3) * 7919L);
                for (int i = 0; i < 3; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double px = 40 + Math.cos(a) * 14;
                    double py = 52 + Math.sin(a) * 12;
                    Path2D bolt = new Path2D.Double();
                    bolt.moveTo(px, py);
                    for (int k = 0; k < 4; k++) {
                        px += Math.cos(a) * 7 + (rnd.nextDouble() - 0.5) * 8;
                        py += Math.sin(a) * 7 + (rnd.nextDouble() - 0.5) * 8;
                        bolt.lineTo(px, py);
                    }
                    g.draw(bolt);
                }
            }
            case AURA_RAINBOW -> {
                g.setStroke(new BasicStroke(3f));
                for (int k = 0; k < 6; k++) {
                    Color c = Color.getHSBColor((f * 0.01f + k / 6f) % 1f, 0.8f, 1f);
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 170));
                    int start = (f * 4 + k * 60) % 360;
                    g.drawArc(4, 18, 72, 66, start, 50);
                }
            }

            // ===== 등 =====
            case BACKPACK -> {
                int px = back > 0 ? 50 : 18;
                g.setColor(new Color(0x2f81f7));
                g.fillRoundRect(px, bodyTop, 12, 16, 5, 5);
                g.setColor(new Color(0x1f5fbf));
                g.fillRoundRect(px + 2, bodyTop + 8, 8, 6, 3, 3);
            }
            case CAPE_RED -> cape(g, p, new Color(0xcf222e), new Color(0x8c1620), null, 26);
            case CAPE_ROYAL -> cape(g, p, new Color(0x6f42c1), new Color(0x4b2a85), new Color(0xf2c94c), 32);
            case ANGEL_WINGS -> {
                double flap = Math.sin(f * 0.3) * 0.25;
                int bx = 40 + back * 6;
                int by = bodyTop + 2;
                wing(g, bx, by, back, -0.7 + flap, false);
                wing(g, bx + back * 3, by + 3, back, -0.25 + flap, false);
            }
            case DEVIL_WINGS -> {
                double flap = Math.sin(f * 0.35) * 0.2;
                wing(g, 40 + back * 6, bodyTop + 2, back, -0.5 + flap, true);
            }

            // ===== 목 =====
            case SCARF -> {
                int y = hy + 16;
                g.setColor(new Color(0xcf222e));
                g.fillRoundRect(hx - 1, y, 26, 6, 6, 6);
                int tailX = back < 0 ? hx - 3 : hx + 21;
                int wave = (int) (Math.sin(f * 0.25) * 2);
                g.fillRoundRect(tailX, y + 3, 6, 12 + wave, 3, 3);
                g.setColor(Color.WHITE);
                for (int i = 0; i < 3; i++) {
                    g.fillRect(hx + 4 + i * 7, y, 2, 6);
                }
            }

            // ===== 머리 =====
            case RIBBON -> {
                g.setColor(new Color(0xe85d9a));
                int bx = dir > 0 ? hx + 5 : hx + 19;
                int by = hy + 1;
                g.fillPolygon(new int[]{bx, bx - 7, bx - 7}, new int[]{by, by - 5, by + 5}, 3);
                g.fillPolygon(new int[]{bx, bx + 7, bx + 7}, new int[]{by, by - 5, by + 5}, 3);
                g.fillOval(bx - 2, by - 2, 5, 5);
            }
            case FLOWER -> {
                int fx = dir > 0 ? hx + 5 : hx + 19;
                int fy = hy + 1;
                g.setColor(new Color(0xffb3d1));
                for (int i = 0; i < 5; i++) {
                    double a = i * Math.PI * 2 / 5;
                    g.fillOval((int) (fx + Math.cos(a) * 4) - 3, (int) (fy + Math.sin(a) * 4) - 3, 6, 6);
                }
                g.setColor(new Color(0xf2c94c));
                g.fillOval(fx - 2, fy - 2, 5, 5);
            }
            case STRAW_HAT -> {
                g.setColor(new Color(0xe3c16f));
                g.fillOval(hx - 6, hy - 1, 36, 7);
                g.fillRoundRect(hx + 3, hy - 9, 18, 11, 6, 6);
                g.setColor(new Color(0xb5432f));
                g.fillRect(hx + 3, hy - 1, 18, 2);
            }
            case SPROUT -> {
                g.setColor(new Color(0x3fb950));
                g.setStroke(new BasicStroke(2));
                g.drawLine(cx, hy + 2, cx, hy - 7);
                g.fillOval(cx - 8, hy - 11, 8, 5);
                g.fillOval(cx, hy - 11, 8, 5);
            }
            case PARTY_HAT -> {
                g.setColor(new Color(0x8250df));
                g.fillPolygon(new int[]{cx - 8, cx + 8, cx}, new int[]{hy + 3, hy + 3, hy - 15}, 3);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(cx - 5, hy - 2, cx + 4, hy - 4);
                g.drawLine(cx - 2, hy - 8, cx + 2, hy - 9);
                g.setColor(new Color(0xf2c94c));
                g.fillOval(cx - 3, hy - 18, 6, 6);
            }
            case BEANIE -> {
                g.setColor(new Color(0x2f81f7));
                g.fillArc(hx - 1, hy - 5, 26, 22, 0, 180);
                g.fillRect(hx - 1, hy + 4, 26, 4);
                g.setColor(Color.WHITE);
                g.fillOval(cx - 3, hy - 10, 7, 7);
            }
            case HEADPHONES -> {
                g.setColor(new Color(0x2d333b));
                g.setStroke(new BasicStroke(3));
                g.drawArc(hx, hy - 5, 24, 24, 20, 140);
                g.setColor(new Color(0xcf222e));
                int cupX = dir > 0 ? hx + 4 : hx + 12;
                g.fillRoundRect(cupX, hy + 6, 8, 11, 4, 4);
            }
            case CAT_EARS -> {
                g.setColor(new Color(0x2d333b));
                g.fillPolygon(new int[]{hx + 2, hx + 10, hx + 3}, new int[]{hy + 5, hy + 1, hy - 7}, 3);
                g.fillPolygon(new int[]{hx + 14, hx + 22, hx + 21}, new int[]{hy + 1, hy + 5, hy - 7}, 3);
                g.setColor(new Color(0xffb3d1));
                g.fillPolygon(new int[]{hx + 4, hx + 8, hx + 4}, new int[]{hy + 3, hy + 1, hy - 3}, 3);
                g.fillPolygon(new int[]{hx + 16, hx + 20, hx + 20}, new int[]{hy + 1, hy + 3, hy - 3}, 3);
            }
            case CHEF_HAT -> {
                g.setColor(Color.WHITE);
                g.fillRect(cx - 8, hy - 4, 16, 7);
                g.fillOval(cx - 11, hy - 14, 10, 10);
                g.fillOval(cx - 5, hy - 17, 10, 10);
                g.fillOval(cx + 1, hy - 14, 10, 10);
                g.setColor(new Color(0xd0d7de));
                g.drawRect(cx - 8, hy - 4, 16, 7);
            }
            case WIZARD_HAT -> {
                g.setColor(new Color(0x1f3a7a));
                g.fillOval(hx - 5, hy - 1, 34, 7);
                g.fillPolygon(new int[]{cx - 9, cx + 9, cx + (dir > 0 ? -10 : 10)},
                        new int[]{hy + 2, hy + 2, hy - 22}, 3);
                g.setColor(new Color(0xf2c94c));
                star(g, cx, hy - 6, 3.5);
            }
            case HORNS -> {
                g.setColor(new Color(0xa40e26));
                g.fillPolygon(new int[]{hx + 3, hx + 9, hx + 1}, new int[]{hy + 4, hy + 2, hy - 7}, 3);
                g.fillPolygon(new int[]{hx + 15, hx + 21, hx + 23}, new int[]{hy + 2, hy + 4, hy - 7}, 3);
            }
            case CROWN -> {
                g.setColor(new Color(0xf2c94c));
                g.fillPolygon(
                        new int[]{hx + 3, hx + 3, hx + 8, cx, hx + 16, hx + 21, hx + 21},
                        new int[]{hy + 3, hy - 7, hy - 2, hy - 10, hy - 2, hy - 7, hy + 3}, 7);
                g.setColor(new Color(0xcf222e));
                g.fillOval(cx - 2, hy - 2, 4, 4);
            }
            case HALO -> {
                g.setColor(new Color(0xffd84d));
                g.setStroke(new BasicStroke(2.5f));
                g.drawOval(hx + 1, hy - 14 + (int) (Math.sin(f * 0.1) * 1.5), 22, 6);
            }

            // ===== 얼굴 =====
            case BLUSH -> {
                g.setColor(new Color(255, 120, 150, 150));
                g.fillOval(dir > 0 ? hx + 11 : hx + 5, hy + 13, 8, 4);
            }
            case MUSTACHE -> {
                int front = dir > 0 ? hx + 16 : hx + 2;
                g.setColor(new Color(0x1f2328));
                g.fillOval(front - 1, hy + 14, 5, 3);
                g.fillOval(front + 3, hy + 14, 5, 3);
            }
            case MASK -> {
                int mx = dir > 0 ? hx + 10 : hx + 1;
                g.setColor(Color.WHITE);
                g.fillRoundRect(mx, hy + 12, 13, 8, 4, 4);
                g.setColor(new Color(0xd0d7de));
                g.drawLine(dir > 0 ? mx : mx + 13, hy + 14, dir > 0 ? hx + 3 : hx + 21, hy + 10);
            }
            case SUNGLASSES -> {
                g.setColor(new Color(0x111111));
                g.fillRoundRect(eyeX - 3, hy + 6, 12, 7, 4, 4);
                g.setStroke(new BasicStroke(1.5f));
                int sideX = dir > 0 ? hx + 2 : hx + 22;
                g.drawLine(sideX, hy + 8, dir > 0 ? eyeX - 3 : eyeX + 9, hy + 8);
            }
            case MONOCLE -> {
                g.setColor(new Color(0xd4a72c));
                g.setStroke(new BasicStroke(1.5f));
                g.drawOval(eyeX - 2, hy + 5, 10, 10);
                g.drawLine(eyeX + 3, hy + 15, eyeX + 1, hy + 24);
            }
            case EYEPATCH -> {
                g.setColor(new Color(0x111111));
                g.fillOval(eyeX - 1, hy + 6, 8, 8);
                g.setStroke(new BasicStroke(1.5f));
                g.drawLine(hx + 2, hy + 4, hx + 22, hy + 10);
            }
        }

        g.dispose();
    }

    // ===== 그리기 도우미 =====

    private static AlphaComposite alpha(float a) {
        return AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, a)));
    }

    /** 개미 중심에서 퍼지는 은은한 빛 */
    private static void glow(Graphics2D g, int radius, Color color) {
        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
        g.setPaint(new RadialGradientPaint(new Point(40, 52), Math.max(1, radius),
                new float[]{0f, 1f}, new Color[]{color, clear}));
        g.fillOval(40 - radius, 52 - radius, radius * 2, radius * 2);
    }

    private static void star(Graphics2D g, double cx, double cy, double r) {
        double s = r / 3;
        Path2D path = new Path2D.Double();
        path.moveTo(cx, cy - r);
        path.lineTo(cx + s, cy - s);
        path.lineTo(cx + r, cy);
        path.lineTo(cx + s, cy + s);
        path.lineTo(cx, cy + r);
        path.lineTo(cx - s, cy + s);
        path.lineTo(cx - r, cy);
        path.lineTo(cx - s, cy - s);
        path.closePath();
        g.fill(path);
    }

    private static void heart(Graphics2D g, int x, int y, int size) {
        int r = size / 2;
        g.fillOval(x - r, y - r / 2, r + 1, r + 1);
        g.fillOval(x, y - r / 2, r + 1, r + 1);
        g.fillPolygon(new int[]{x - r, x + r + 1, x}, new int[]{y + r / 4, y + r / 4, y + r + 2}, 3);
    }

    private static void cape(Graphics2D g, Pose p, Color fill, Color shade, Color trim, int length) {
        int back = -p.dir();
        int top = 44 + p.bob();
        double wave = Math.sin(p.frame() * 0.25) * 3;

        Path2D path = new Path2D.Double();
        path.moveTo(40 - back * 2, top - 2);
        path.lineTo(40 + back * 8, top);
        path.quadTo(40 + back * length * 0.8, top + 14, 40 + back * length + wave, top + 30);
        path.lineTo(40 + back * 4, top + 30);
        path.closePath();

        g.setColor(fill);
        g.fill(path);
        g.setStroke(new BasicStroke(trim == null ? 1.2f : 2f));
        g.setColor(trim == null ? shade : trim);
        g.draw(path);
    }

    /** back 방향으로 뻗는 날개. angle 음수면 위로 들림 */
    private static void wing(Graphics2D g0, int bx, int by, int back, double angle, boolean devil) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(bx, by);
        g.scale(back, 1);
        g.rotate(angle);

        if (devil) {
            Path2D path = new Path2D.Double();
            path.moveTo(0, 0);
            path.lineTo(8, -12);
            path.lineTo(14, -8);
            path.lineTo(22, -16);
            path.lineTo(27, -2);
            path.lineTo(19, -4);
            path.lineTo(13, 2);
            path.closePath();
            g.setColor(new Color(0x7a1f2b));
            g.fill(path);
            g.setColor(new Color(0x4a0f18));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(path);
        } else {
            g.setColor(Color.WHITE);
            g.fillOval(0, -6, 24, 12);
            g.setColor(new Color(0xd0d7de));
            g.setStroke(new BasicStroke(1f));
            g.drawOval(0, -6, 24, 12);
            g.drawLine(8, -3, 18, -2);
        }
        g.dispose();
    }
}