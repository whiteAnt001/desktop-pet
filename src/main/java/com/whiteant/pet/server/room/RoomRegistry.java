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

    public Optional<Room> findOrCreate(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        if (!CODE.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(rooms.computeIfAbsent(normalized, Room::new));
    }

    public Collection<Room> all() {
        return rooms.values();
    }

    public void removeIfEmpty(Room room) {
        if (room.isEmpty()) {
            rooms.remove(room.getCode(), room);
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}