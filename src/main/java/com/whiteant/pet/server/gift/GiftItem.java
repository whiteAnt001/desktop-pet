package com.whiteant.pet.server.gift;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum GiftItem {
    // 일반
    RIBBON(Rarity.COMMON),
    FLOWER(Rarity.COMMON),
    STRAW_HAT(Rarity.COMMON),
    SPROUT(Rarity.COMMON),
    PARTY_HAT(Rarity.COMMON),
    BLUSH(Rarity.COMMON),
    MUSTACHE(Rarity.COMMON),
    MASK(Rarity.COMMON),
    BACKPACK(Rarity.COMMON),
    BOWTIE(Rarity.COMMON),
    TIE(Rarity.COMMON),
    BELL_COLLAR(Rarity.COMMON),

    // 레어
    BEANIE(Rarity.RARE),
    HEADPHONES(Rarity.RARE),
    CAT_EARS(Rarity.RARE),
    CHEF_HAT(Rarity.RARE),
    SUNGLASSES(Rarity.RARE),
    MONOCLE(Rarity.RARE),
    EYEPATCH(Rarity.RARE),
    SCARF(Rarity.RARE),
    CAPE_RED(Rarity.RARE),
    PEARL_NECKLACE(Rarity.RARE),
    HEART_PENDANT(Rarity.RARE),

    // 영웅
    WIZARD_HAT(Rarity.EPIC),
    HORNS(Rarity.EPIC),
    DEVIL_WINGS(Rarity.EPIC),
    AURA_SPARKLE(Rarity.EPIC),
    AURA_HEART(Rarity.EPIC),
    AURA_SHADOW(Rarity.EPIC),
    GOLD_CHAIN(Rarity.EPIC),
    MEDAL(Rarity.EPIC),

    // 전설
    CROWN(Rarity.LEGENDARY),
    HALO(Rarity.LEGENDARY),
    CAPE_ROYAL(Rarity.LEGENDARY),
    ANGEL_WINGS(Rarity.LEGENDARY),
    AURA_FIRE(Rarity.LEGENDARY),
    AURA_ELECTRIC(Rarity.LEGENDARY),
    AURA_RAINBOW(Rarity.LEGENDARY),
    DIAMOND_NECKLACE(Rarity.LEGENDARY);

    /** 부위 수 (오라/등/목/머리/얼굴) */
    private static final int MAX_EQUIPPED = 5;

    private static final Set<String> NAMES = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    private final Rarity rarity;

    @Getter
    @RequiredArgsConstructor
    public enum Rarity {
        COMMON(60), RARE(28), EPIC(9), LEGENDARY(3);

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

    /** "CROWN,CAPE_RED,AURA_FIRE" 처럼 쉼표로 여러 개. 전부 유효하고 5개 이하일 때만 true */
    public static boolean isValid(String codes) {
        if (codes == null || codes.isBlank()) {
            return false;
        }
        String[] parts = codes.split(",");
        if (parts.length > MAX_EQUIPPED) {
            return false;
        }
        for (String part : parts) {
            if (!NAMES.contains(part.trim())) {
                return false;
            }
        }
        return true;
    }
}