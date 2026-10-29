package com.whiteant.pet.client;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.prefs.Preferences;

/** 아이템은 각자 PC에 저장 (윈도우는 레지스트리) */
public class Inventory {

    private static final String KEY_OWNED = "inventory.owned";
    private static final String KEY_EQUIPPED = "inventory.equipped";

    private final Preferences prefs = Preferences.userNodeForPackage(Inventory.class);
    private final EnumSet<Item> owned = EnumSet.noneOf(Item.class);

    @Getter
    private Item equipped;

    public Inventory() {
        for (String code : prefs.get(KEY_OWNED, "").split(",")) {
            Item item = Item.fromCode(code);
            if (item != null) {
                owned.add(item);
            }
        }
        Item saved = Item.fromCode(prefs.get(KEY_EQUIPPED, ""));
        equipped = saved != null && owned.contains(saved) ? saved : null;
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

    public void setEquipped(Item item) {
        equipped = item;
        save();
    }

    private void save() {
        StringJoiner joiner = new StringJoiner(",");
        owned.forEach(i -> joiner.add(i.name()));
        prefs.put(KEY_OWNED, joiner.toString());
        prefs.put(KEY_EQUIPPED, equipped == null ? "" : equipped.name());
    }
}