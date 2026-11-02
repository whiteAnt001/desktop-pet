package com.whiteant.pet.server.room;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class Room {

    @Getter
    private final String code;

    private final Map<String, Member> members = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // 떠 있는 선물상자들: giftId → 만료 시각 (synchronized 메서드로만 접근)
    private final Map<String, Long> gifts = new HashMap<>();
    private long nextGiftAt;

    public void add(WebSocketSession session, Member member) {
        members.put(member.getId(), member);
        sessions.put(member.getId(), session);
    }

    public void remove(String memberId) {
        members.remove(memberId);
        sessions.remove(memberId);
    }

    public Member member(String memberId) {
        return members.get(memberId);
    }

    public WebSocketSession session(String memberId) {
        return sessions.get(memberId);
    }

    public Member findByClientId(String clientId) {
        return members.values().stream()
                .filter(m -> clientId.equals(m.getClientId()))
                .findFirst()
                .orElse(null);
    }

    public Member findByName(String name) {
        return members.values().stream()
                .filter(m -> name.equals(m.getName()))
                .findFirst()
                .orElse(null);
    }

    public List<Member> members() {
        return new ArrayList<>(members.values());
    }

    public Collection<WebSocketSession> sessions() {
        return sessions.values();
    }

    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public synchronized void ensureNextGift(long now, long delayMs) {
        if (nextGiftAt == 0) {
            nextGiftAt = now + delayMs;
        }
    }

    /** 자동 투하: 떠 있는 상자 없고 시간 됐으면 새 선물 id, 아니면 null */
    public synchronized String dropGiftIfDue(long now, long lifetimeMs) {
        if (!gifts.isEmpty() || isEmpty() || nextGiftAt == 0 || now < nextGiftAt) {
            return null;
        }
        nextGiftAt = 0;
        return addGift(now, lifetimeMs);
    }

    /** 조건 없이 상자 하나 추가 (관리자 투하용) */
    public synchronized String addGift(long now, long lifetimeMs) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        gifts.put(id, now + lifetimeMs);
        return id;
    }

    /** 선착순 판정. 처음 요청한 사람만 true */
    public synchronized boolean claimGift(String id) {
        return gifts.remove(id) != null;
    }

    /** 만료된 상자 id들 */
    public synchronized List<String> expireGifts(long now) {
        List<String> expired = new ArrayList<>();
        gifts.entrySet().removeIf(e -> {
            if (now >= e.getValue()) {
                expired.add(e.getKey());
                return true;
            }
            return false;
        });
        return expired;
    }
}