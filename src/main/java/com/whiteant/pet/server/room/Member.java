package com.whiteant.pet.server.room;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Member {
    private final String id;
    private final String name;
    private volatile String color;
    private volatile String item;
    private volatile boolean away;
}