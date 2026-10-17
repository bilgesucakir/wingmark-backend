package com.wingmark.backend.controller;

import com.wingmark.backend.dto.appicon.AppIconResponseDto;
import com.wingmark.backend.service.AppIconService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** The alternate app icons users may pick. */
@Tag(name = "App icons", description = "Alternate app icons made available by the admin")
@RestController
@RequestMapping("/api/app-icons")
@RequiredArgsConstructor
public class AppIconController {

    private final AppIconService appIconService;

    /** Lists the available icons. Any signed-in user. */
    @Operation(summary = "List available app icons", description = "Requires login (any user). No personal data. Returns the alternate app icons the admin has made available, " +
            "oldest first. The app shows an icon only if its iconId is in this list and matches an icon built into the app; unknown ids are ignored. " +
            "Cacheable for 5 minutes.")
    @GetMapping
    public ResponseEntity<List<AppIconResponseDto>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate()).body(appIconService.list());
    }
}
