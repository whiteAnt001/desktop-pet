package com.whiteant.pet.client;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Emote {
    HEART("♥"),
    LOL("ㅋㅋㅋ"),
    HI("안녕~"),
    SLEEPY("졸려"),
    HUNGRY("배고파");

    private final String text;

    public static String textOf(String code) {
        try {
            return valueOf(code).text;
        } catch (IllegalArgumentException e) {
            return "?";
        }
    }
}