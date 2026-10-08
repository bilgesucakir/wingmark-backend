package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.UploadCleanupProperties;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.FileStorageService.StoredFileInfo;
import com.wingmark.backend.service.OrphanedUploadService;
import com.wingmark.backend.service.UploadReferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Removes abandoned uploads. A photo and its thumbnail are treated as one; logs name files, not people. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanedUploadServiceImpl implements OrphanedUploadService {

    private static final String THUMBNAIL_SUFFIX = "_thumb.jpg";

    private final FileStorageService fileStorageService;
    private final UploadReferences uploadReferences;
    private final UploadCleanupProperties properties;

    @Override
    public Result cleanup(Instant now) {
        Instant cutoff = now.minus(Duration.ofDays(properties.graceDays()));
        Set<String> referencedBases = uploadReferences.referencedFilenames().stream()
                .map(OrphanedUploadServiceImpl::baseOf).collect(Collectors.toSet());

        Map<String, List<StoredFileInfo>> byBase = new LinkedHashMap<>();
        for (StoredFileInfo file : fileStorageService.listFiles()) {
            byBase.computeIfAbsent(baseOf(file.filename()), base -> new ArrayList<>()).add(file);
        }

        int photos = 0;
        long bytes = 0;
        for (Map.Entry<String, List<StoredFileInfo>> entry : byBase.entrySet()) {
            List<StoredFileInfo> group = entry.getValue();
            boolean referenced = referencedBases.contains(entry.getKey());
            boolean oldEnough = group.stream().allMatch(file -> file.uploadedAt().isBefore(cutoff));
            if (referenced || !oldEnough) {
                continue;
            }
            if (photos >= properties.maxPerRun()) {
                break;
            }
            photos++;
            bytes += group.stream().mapToLong(StoredFileInfo::sizeBytes).sum();
            if (properties.dryRun()) {
                log.info("Dry run: would delete unreferenced upload {} ({} file(s))", entry.getKey(), group.size());
                continue;
            }
            // Deleting the photo also deletes its thumbnail; a thumbnail left alone is deleted by its own name.
            group.stream().filter(file -> !file.filename().endsWith(THUMBNAIL_SUFFIX)).findFirst()
                    .ifPresentOrElse(photo -> fileStorageService.delete(photo.filename()),
                            () -> group.forEach(file -> fileStorageService.delete(file.filename())));
        }
        log.info("Upload cleanup {}: {} unreferenced photo(s), {} byte(s)", properties.dryRun() ? "(dry run)" : "done", photos, bytes);
        return new Result(photos, bytes, properties.dryRun());
    }

    /** Returns the filename without its thumbnail suffix or extension, so a photo and its thumbnail share a key. */
    static String baseOf(String filename) {
        if (filename.endsWith(THUMBNAIL_SUFFIX)) {
            return filename.substring(0, filename.length() - THUMBNAIL_SUFFIX.length());
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? filename : filename.substring(0, dot);
    }
}
