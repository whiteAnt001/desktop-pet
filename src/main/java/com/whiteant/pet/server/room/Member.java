package com.whiteant.pet.server.room;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Member {
    private final String id;
    private final String name;

    // 재접속 판별용. 다른 사람한테는 안 보냄
    @JsonIgnore
    private final String clientId;

    private volatile String color;
    private volatile String item;
    private volatile boolean away;
}