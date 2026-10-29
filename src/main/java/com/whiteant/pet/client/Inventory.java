package com.whiteant.pet.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.prefs.Preferences;

/** 아이템은 각자 PC에 저장 (윈도우는 레지스트리). 부위별로 하나씩 장착 */
public class Inventory {

    private static final String KEY_OWNED = "inventory.owned";
    private static final String KEY_EQUIPPED = "inventory.equipped";

    private final Preferences prefs = Preferences.userNodeForPackage(Inventory.class);
    private final EnumSet<Item> owned = EnumSet.noneOf(Item.class);
    private final EnumMap<Item.Slot, Item> equipped = new EnumMap<>(Item.Slot.class);

    public Inventory() {
        owned.addAll(Item.fromCodes(prefs.get(KEY_OWNED, "")));

        // 예전 버전은 하나만 저장돼 있어도 그대로 읽힘
        for (Item item : Item.fromCodes(prefs.get(KEY_EQUIPPED, ""))) {
            if (owned.contains(item)) {
                equipped.put(item.getSlot(), item);
            }
        }
    }

    /** 새로 얻었으면 true, 이미 있으면 false */
    public boolean add(Item item) {
        boolean added = owned.add(item);
        if (added) {
            save();
        }
        return added;
    }

    public Set<Item> getOwned() {
        return Collections.unmodifiableSet(owned);
    }

    /** 장착 중인 것 전부 (그리는 순서대로) */
    public List<Item> getEquipped() {
        return new ArrayList<>(equipped.values());
    }

    public Item getEquipped(Item.Slot slot) {
        return equipped.get(slot);
    }

    public String equippedCodes() {
        return Item.toCodes(getEquipped());
    }

    public void equip(Item item) {
        equipped.put(item.getSlot(), item);
        save();
    }

    public void unequip(Item.Slot slot) {
        equipped.remove(slot);
        save();
    }

    private void save() {
        StringJoiner joiner = new StringJoiner(",");
        owned.forEach(i -> joiner.add(i.name()));
        prefs.put(KEY_OWNED, joiner.toString());
        prefs.put(KEY_EQUIPPED, equippedCodes());
    }
}