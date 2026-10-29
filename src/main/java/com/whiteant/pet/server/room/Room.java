package com.whiteant.pet.server.room;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
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

    // 선물상자 상태 (synchronized 메서드로만 접근)
    private String giftId;
    private long giftExpiresAt;
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

    /** 투하 시간이 됐으면 새 선물 id 반환, 아니면 null */
    public synchronized String dropGiftIfDue(long now, long lifetimeMs) {
        if (giftId != null || isEmpty() || nextGiftAt == 0 || now < nextGiftAt) {
            return null;
        }
        giftId = UUID.randomUUID().toString().substring(0, 8);
        giftExpiresAt = now + lifetimeMs;
        nextGiftAt = 0;
        return giftId;
    }

    /** 선착순 판정. 처음 요청한 사람만 true */
    public synchronized boolean claimGift(String id) {
        if (giftId == null || !giftId.equals(id)) {
            return false;
        }
        giftId = null;
        return true;
    }

    /** 만료됐으면 만료된 선물 id 반환, 아니면 null */
    public synchronized String expireGiftIfDue(long now) {
        if (giftId != null && now >= giftExpiresAt) {
            String expired = giftId;
            giftId = null;
            return expired;
        }
        return null;
    }
}