package com.whiteant.pet.client;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import java.util.prefs.Preferences;

/** 서버/닉네임/색/방 코드 기억 */
@Getter
@Setter
public class Settings {

    public static final String DEFAULT_SERVER = "https://pet.nckval.com";
    public static final String DEFAULT_COLOR = "#57606a";

    private static final Preferences PREFS = Preferences.userNodeForPackage(Settings.class);

    /** 이 PC 고유 ID. 재접속할 때 서버가 같은 사람인지 알아보는 용도 */
    private String clientId;
    private String server;
    private String name;
    private String color;
    private String roomCode;
    private boolean autoJoin;

    public static Settings load() {
        Settings s = new Settings();
        s.clientId = PREFS.get("settings.clientId", "");
        if (s.clientId.isBlank()) {
            s.clientId = UUID.randomUUID().toString();
            PREFS.put("settings.clientId", s.clientId);
        }
        s.server = PREFS.get("settings.server", DEFAULT_SERVER);
        s.name = PREFS.get("settings.name", "");
        s.color = PREFS.get("settings.color", DEFAULT_COLOR);
        s.roomCode = PREFS.get("settings.roomCode", "");
        s.autoJoin = PREFS.getBoolean("settings.autoJoin", false);
        return s;
    }

    public void save() {
        PREFS.put("settings.clientId", clientId);
        PREFS.put("settings.server", server);
        PREFS.put("settings.name", name);
        PREFS.put("settings.color", color);
        PREFS.put("settings.roomCode", roomCode);
        PREFS.putBoolean("settings.autoJoin", autoJoin);
    }

    public boolean canAutoJoin() {
        return autoJoin && !server.isBlank() && !name.isBlank() && !roomCode.isBlank();
    }
}