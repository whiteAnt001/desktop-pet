package com.whiteant.pet.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whiteant.pet.server.gift.GiftItem;
import com.whiteant.pet.server.room.Member;
import com.whiteant.pet.server.room.Room;
import com.whiteant.pet.server.room.RoomRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetSocketHandler extends TextWebSocketHandler {

    private static final int MAX_MEMBERS = 10;
    private static final int MAX_NAME_LENGTH = 10;
    private static final int MAX_CHAT_LENGTH = 50;
    private static final long CHAT_COOLDOWN_MS = 500;
    private static final long SNACK_COOLDOWN_MS = 3000;
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> EMOTES = Set.of("HEART", "LOL", "HI", "SLEEPY", "HUNGRY");

    private final RoomRegistry roomRegistry;
    private final ObjectMapper mapper;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Room> joinedRooms = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // sendMessage는 스레드 안전하지 않아서 데코레이터로 감쌈
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024));
    }

    @Override
    protected void handleTextMessage(WebSocketSession raw, TextMessage message) throws IOException {
        WebSocketSession session = sessions.get(raw.getId());
        if (session == null) {
            return;
        }

        JsonNode msg = mapper.readTree(message.getPayload());
        switch (msg.path("type").asText()) {
            case "JOIN" -> join(session, msg);
            case "EMOTE" -> emote(session, msg);
            case "CHAT" -> chat(session, msg);
            case "MOVE" -> move(session, msg);
            case "SNACK" -> snack(session, msg);
            case "STATUS" -> status(session, msg);
            case "EQUIP" -> equip(session, msg);
            case "COLOR" -> color(session, msg);
            case "GIFT_CLAIM" -> claimGift(session, msg);
            default -> sendError(session, "UNKNOWN_TYPE");
        }
    }

    private void join(WebSocketSession session, JsonNode msg) {
        if (joinedRooms.containsKey(session.getId())) {
            return;
        }

        Optional<Room> found = roomRegistry.findOrCreate(msg.path("code").asText(null));
        if (found.isEmpty()) {
            sendError(session, "INVALID_CODE");
            return;
        }
        Room room = found.get();

        String name = msg.path("name").asText("").trim();
        String color = msg.path("color").asText("");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || !COLOR.matcher(color).matches()) {
            sendError(session, "INVALID_PROFILE");
            return;
        }
        if (room.size() >= MAX_MEMBERS) {
            sendError(session, "ROOM_FULL");
            return;
        }

        Member member = new Member(session.getId(), name, color, validItem(msg.path("item").asText("")), false);
        room.add(session, member);
        joinedRooms.put(session.getId(), room);
        log.info("[{}] {} 입장 ({}명)", room.getCode(), name, room.size());

        send(session, Map.of(
                "type", "WELCOME",
                "selfId", member.getId(),
                "code", room.getCode(),
                "members", room.members()));

        broadcast(room, Map.of("type", "MEMBER_JOINED", "member", member), member.getId());
    }

    private void emote(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        String emote = msg.path("emote").asText("");
        if (room == null || !EMOTES.contains(emote)) {
            return;
        }
        broadcast(room, Map.of("type", "EMOTE", "memberId", session.getId(), "emote", emote), null);
    }

    private void chat(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        if (room == null) {
            return;
        }

        String text = msg.path("text").asText("").replaceAll("\\p{Cntrl}", " ").trim();
        if (text.isEmpty() || onCooldown(session.getId(), "CHAT", CHAT_COOLDOWN_MS)) {
            return;
        }
        if (text.length() > MAX_CHAT_LENGTH) {
            text = text.substring(0, MAX_CHAT_LENGTH);
        }

        broadcast(room, Map.of("type", "CHAT", "memberId", session.getId(), "text", text), null);
    }

    private void move(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        if (room == null) {
            return;
        }

        // targetId 없으면 내 개미 (예전 클라이언트 호환)
        String targetId = msg.path("targetId").asText(session.getId());
        if (room.member(targetId) == null) {
            return;
        }

        double nx = msg.path("nx").asDouble(Double.NaN);
        double ny = msg.path("ny").asDouble(Double.NaN);
        if (!Double.isFinite(nx) || !Double.isFinite(ny)) {
            return;
        }

        // 옮긴 사람 화면은 이미 움직였으니까 제외
        broadcast(room, Map.of(
                "type", "MOVE",
                "memberId", targetId,
                "by", session.getId(),
                "nx", clamp(nx),
                "ny", clamp(ny),
                "released", msg.path("released").asBoolean(false)), session.getId());
    }

    private void snack(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        if (room == null || onCooldown(session.getId(), "SNACK", SNACK_COOLDOWN_MS)) {
            return;
        }

        double nx = msg.path("nx").asDouble(Double.NaN);
        if (!Double.isFinite(nx)) {
            return;
        }

        broadcast(room, Map.of("type", "SNACK", "memberId", session.getId(), "nx", clamp(nx)), null);
    }

    private void status(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        Member member = room == null ? null : room.member(session.getId());
        if (member == null) {
            return;
        }

        boolean away = msg.path("away").asBoolean(false);
        member.setAway(away);
        broadcast(room, Map.of("type", "STATUS", "memberId", member.getId(), "away", away), member.getId());
    }

    private void equip(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        Member member = room == null ? null : room.member(session.getId());
        if (member == null) {
            return;
        }

        String item = validItem(msg.path("item").asText(""));
        member.setItem(item);
        broadcast(room, Map.of("type", "EQUIP", "memberId", member.getId(), "item", item), member.getId());
    }

    private void color(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        Member member = room == null ? null : room.member(session.getId());
        String color = msg.path("color").asText("");
        if (member == null || !COLOR.matcher(color).matches()) {
            return;
        }

        member.setColor(color);
        broadcast(room, Map.of("type", "COLOR", "memberId", member.getId(), "color", color), member.getId());
    }

    private void claimGift(WebSocketSession session, JsonNode msg) {
        Room room = joinedRooms.get(session.getId());
        if (room == null) {
            return;
        }

        String giftId = msg.path("giftId").asText("");
        if (!room.claimGift(giftId)) {
            return;
        }

        GiftItem item = GiftItem.random();
        Member member = room.member(session.getId());
        log.info("[{}] {} 선물 획득: {}", room.getCode(), member == null ? "?" : member.getName(), item);

        broadcast(room, Map.of(
                "type", "GIFT_CLAIMED",
                "giftId", giftId,
                "memberId", session.getId(),
                "item", item.name()), null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String id = session.getId();
        sessions.remove(id);
        cooldowns.keySet().removeIf(key -> key.startsWith(id + ":"));

        Room room = joinedRooms.remove(id);
        if (room == null) {
            return;
        }
        room.remove(id);
        log.info("[{}] 퇴장 ({}명 남음)", room.getCode(), room.size());
        broadcast(room, Map.of("type", "MEMBER_LEFT", "memberId", id), null);
        roomRegistry.removeIfEmpty(room);
    }

    public void broadcastToRoom(Room room, Object payload) {
        broadcast(room, payload, null);
    }

    private String validItem(String item) {
        return item != null && GiftItem.isValid(item) ? item : "";
    }

    private boolean onCooldown(String sessionId, String type, long cooldownMs) {
        String key = sessionId + ":" + type;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(key);
        if (last != null && now - last < cooldownMs) {
            return true;
        }
        cooldowns.put(key, now);
        return false;
    }

    private double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void broadcast(Room room, Object payload, String excludeId) {
        for (WebSocketSession s : room.sessions()) {
            if (!s.getId().equals(excludeId)) {
                send(s, payload);
            }
        }
    }

    private void sendError(WebSocketSession session, String code) {
        send(session, Map.of("type", "ERROR", "code", code));
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
            }
        } catch (IOException e) {
            // 끊긴 세션은 afterConnectionClosed에서 정리됨
            log.debug("send 실패 session={}", session.getId(), e);
        }
    }
}