package com.whiteant.pet.server.update;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

//클라이언트 자동 업데이트용. publishClient로 만든 파일을 client-dir에 올려두면 됨
@RestController
@RequestMapping("/api/client")
public class ClientUpdateController {

    private static final Set<String> FILES = Set.of("desktop-pet-client.jar", "DesktopPet.zip");

    @Value("${pet.client-dir:./client}")
    private String clientDir;

    @GetMapping("/latest")
    public ResponseEntity<Resource> latest() {
        return serve("latest.json", MediaType.APPLICATION_JSON);
    }

    @GetMapping("/files/{name:.+}")
    public ResponseEntity<Resource> file(@PathVariable String name) {
        if (!FILES.contains(name)) {
            return ResponseEntity.notFound().build();
        }
        return serve(name, MediaType.APPLICATION_OCTET_STREAM);
    }

    private ResponseEntity<Resource> serve(String name, MediaType type) {
        Path path = Path.of(clientDir, name);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(path));
    }
}