package com.wingmark.backend.controller;

import com.wingmark.backend.security.UserPrincipal;
import com.wingmark.backend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/photo")
    public ResponseEntity<Map<String, String>> uploadPhoto(@AuthenticationPrincipal UserPrincipal principal,
                                                             @RequestParam("file") MultipartFile file) {
        String url = fileStorageService.store(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }
}
