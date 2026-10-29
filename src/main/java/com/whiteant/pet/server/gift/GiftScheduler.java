package com.whiteant.pet.server.gift;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.whiteant.pet.server.PetSocketHandler;
import com.whiteant.pet.server.room.Room;
import com.whiteant.pet.server.room.RoomRegistry;

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
            String expired = room.expireGiftIfDue(now);
            if (expired != null) {
                socketHandler.broadcastToRoom(room, Map.of("type", "GIFT_EXPIRED", "giftId", expired));
            }

            room.ensureNextGift(now, randomDelayMs());

            String giftId = room.dropGiftIfDue(now, lifetimeSeconds * 1000);
            if (giftId != null) {
                double nx = ThreadLocalRandom.current().nextDouble(0.1, 0.9);
                log.info("[{}] 선물상자 투하 {}", room.getCode(), giftId);
                socketHandler.broadcastToRoom(room, Map.of("type", "GIFT_DROP", "giftId", giftId, "nx", nx));
            }
        }
    }

    private long randomDelayMs() {
        long min = Math.max(1, minSeconds) * 1000;
        long max = Math.max(minSeconds, maxSeconds) * 1000;
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}