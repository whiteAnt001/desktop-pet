package com.whiteant.pet.server.gift;

import com.whiteant.pet.server.PetSocketHandler;
import com.whiteant.pet.server.room.Room;
import com.whiteant.pet.server.room.RoomRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class GiftScheduler {

    private final RoomRegistry roomRegistry;
    private final PetSocketHandler socketHandler;

    @Value("${pet.gift.min-seconds:300}")
    private long minSeconds;

    @Value("${pet.gift.max-seconds:900}")
    private long maxSeconds;

    @Value("${pet.gift.lifetime-seconds:60}")
    private long lifetimeSeconds;

    @Scheduled(fixedDelay = 5000)
    public void tick() {
        long now = System.currentTimeMillis();

        for (Room room : roomRegistry.all()) {
            for (String expired : room.expireGifts(now)) {
                socketHandler.broadcastToRoom(room, Map.of("type", "GIFT_EXPIRED", "giftId", expired));
            }

            room.ensureNextGift(now, randomDelayMs());

            String giftId = room.dropGiftIfDue(now, lifetimeSeconds * 1000);
            if (giftId != null) {
                log.info("[{}] 선물상자 투하 {}", room.getCode(), giftId);
                broadcastDrop(room, giftId);
            }
        }
    }

    /** 관리자 투하: 바로 count개 */
    public void dropNow(Room room, int count) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            broadcastDrop(room, room.addGift(now, lifetimeSeconds * 1000));
        }
        log.info("[{}] 관리자 선물상자 {}개 투하", room.getCode(), count);
    }

    private void broadcastDrop(Room room, String giftId) {
        double nx = ThreadLocalRandom.current().nextDouble(0.05, 0.95);
        socketHandler.broadcastToRoom(room, Map.of("type", "GIFT_DROP", "giftId", giftId, "nx", nx));
    }

    private long randomDelayMs() {
        long min = Math.max(1, minSeconds) * 1000;
        long max = Math.max(minSeconds, maxSeconds) * 1000;
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}