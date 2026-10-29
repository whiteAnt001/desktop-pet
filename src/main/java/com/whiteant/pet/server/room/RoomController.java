package com.whiteant.pet.server.room;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomRegistry roomRegistry;

    @PostMapping
    public Map<String, String> create() {
        return Map.of("code", roomRegistry.create().getCode());
    }
}
