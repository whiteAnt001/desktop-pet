package com.whiteant.pet.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.*;

@Getter
@RequiredArgsConstructor
public enum Item {
    RIBBON("리본", Rarity.COMMON),
    FLOWER("꽃", Rarity.COMMON),
    STRAW_HAT("밀짚모자", Rarity.COMMON),
    SUNGLASSES("선글라스", Rarity.RARE),
    HEADPHONES("헤드폰", Rarity.RARE),
    BEANIE("비니", Rarity.RARE),
    CROWN("왕관", Rarity.LEGENDARY),
    HALO("천사 고리", Rarity.LEGENDARY);

    @Getter
    @RequiredArgsConstructor
    public enum Rarity {
        COMMON("일반"), RARE("레어"), LEGENDARY("전설");

        private final String label;
    }

    private final String label;
    private final Rarity rarity;

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

    /** hx, hy = 머리 왼쪽 위 (머리 크기 24x22) */
    public void draw(Graphics2D g0, int hx, int hy, int dir) {
        Graphics2D g = (Graphics2D) g0.create();
        int cx = hx + 12;
        int eyeX = dir > 0 ? hx + 13 : hx + 5;

        switch (this) {
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
            case SUNGLASSES -> {
                g.setColor(new Color(0x111111));
                g.fillRoundRect(eyeX - 3, hy + 6, 12, 7, 4, 4);
                g.setStroke(new BasicStroke(1.5f));
                int sideX = dir > 0 ? hx + 2 : hx + 22;
                g.drawLine(sideX, hy + 8, dir > 0 ? eyeX - 3 : eyeX + 9, hy + 8);
            }
            case HEADPHONES -> {
                g.setColor(new Color(0x2d333b));
                g.setStroke(new BasicStroke(3));
                g.drawArc(hx, hy - 5, 24, 24, 20, 140);
                g.setColor(new Color(0xcf222e));
                int cupX = dir > 0 ? hx + 4 : hx + 12;
                g.fillRoundRect(cupX, hy + 6, 8, 11, 4, 4);
            }
            case BEANIE -> {
                g.setColor(new Color(0x2f81f7));
                g.fillArc(hx - 1, hy - 5, 26, 22, 0, 180);
                g.fillRect(hx - 1, hy + 4, 26, 4);
                g.setColor(Color.WHITE);
                g.fillOval(cx - 3, hy - 10, 7, 7);
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
                g.drawOval(hx + 1, hy - 14, 22, 6);
            }
        }

        g.dispose();
    }
}