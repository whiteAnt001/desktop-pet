package com.whiteant.pet.server.gift;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@RequiredArgsConstructor
public enum GiftItem {
    RIBBON(Rarity.COMMON),
    FLOWER(Rarity.COMMON),
    STRAW_HAT(Rarity.COMMON),
    SUNGLASSES(Rarity.RARE),
    HEADPHONES(Rarity.RARE),
    BEANIE(Rarity.RARE),
    CROWN(Rarity.LEGENDARY),
    HALO(Rarity.LEGENDARY);

    private final Rarity rarity;

    @Getter
    @RequiredArgsConstructor
    public enum Rarity {
        COMMON(70), RARE(25), LEGENDARY(5);

        private final int weight;
    }

    public static GiftItem random() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        Rarity picked = Rarity.LEGENDARY;
        int acc = 0;
        for (Rarity r : Rarity.values()) {
            acc += r.getWeight();
            if (roll < acc) {
                picked = r;
                break;
            }
        }

        Rarity target = picked;
        List<GiftItem> pool = Arrays.stream(values()).filter(i -> i.rarity == target).toList();
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public static boolean isValid(String code) {
        return Arrays.stream(values()).anyMatch(i -> i.name().equals(code));
    }
}