package com.whiteant.pet.server.admin;

import com.whiteant.pet.server.gift.GiftScheduler;
import com.whiteant.pet.server.room.Member;
import com.whiteant.pet.server.room.Room;
import com.whiteant.pet.server.room.RoomRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 관리자 장난용. pet.admin-token 설정 안 하면 전부 막힘
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_DROP = 20;

    private final RoomRegistry roomRegistry;
    private final GiftScheduler giftScheduler;

    @Value("${pet.admin-token:}")
    private String adminToken;

    @GetMapping("/rooms")
    public ResponseEntity<?> rooms(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<Map<String, Object>> list = roomRegistry.all().stream()
                .map(room -> Map.<String, Object>of(
                        "code", room.getCode(),
                        "members", room.members().stream().map(Member::getName).toList()))
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/rooms/{code}/gifts")
    public ResponseEntity<?> dropGifts(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                       @PathVariable String code,
                                       @RequestParam(defaultValue = "1") int count) {
        if (!authorized(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<Room> found = roomRegistry.find(code);
        if (found.isEmpty() || found.get().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "방이 없거나 아무도 없어"));
        }

        int n = Math.max(1, Math.min(count, MAX_DROP));
        giftScheduler.dropNow(found.get(), n);
        return ResponseEntity.ok(Map.of("room", found.get().getCode(), "dropped", n));
    }

    private boolean authorized(String token) {
        if (adminToken == null || adminToken.isBlank() || token == null) {
            return false;
        }
        // 타이밍 공격 방지용 비교
        return MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}