package com.whiteant.pet.server.room;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class RoomRegistry {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final Pattern CODE = Pattern.compile("^[" + CHARS + "]{" + CODE_LENGTH + "}$");

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room create() {
        while (true) {
            Room room = new Room(randomCode());
            if (rooms.putIfAbsent(room.getCode(), room) == null) {
                return room;
            }
        }
    }

    // 코드 형식만 맞으면 없던 방도 그 코드로 만들어줌 (방 유지용)
    public Optional<Room> findOrCreate(String code) {
        String normalized = normalize(code);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.of(rooms.computeIfAbsent(normalized, Room::new));
    }

    // 있는 방만 찾기 (관리자용)
    public Optional<Room> find(String code) {
        String normalized = normalize(code);
        return normalized == null ? Optional.empty() : Optional.ofNullable(rooms.get(normalized));
    }

    public Collection<Room> all() {
        return rooms.values();
    }

    public void removeIfEmpty(Room room) {
        if (room.isEmpty()) {
            rooms.remove(room.getCode(), room);
        }
    }

    private String normalize(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        return CODE.matcher(normalized).matches() ? normalized : null;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}