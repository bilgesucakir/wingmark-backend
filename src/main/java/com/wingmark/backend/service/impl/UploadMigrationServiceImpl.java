package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.R2MigrationProperties;
import com.wingmark.backend.entity.UploadedFile;
import com.wingmark.backend.repository.UploadedFileRepository;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.FileStorageService.StoredFile;
import com.wingmark.backend.service.FileStorageService.StoredFileInfo;
import com.wingmark.backend.service.UploadMigrationService;
import com.wingmark.backend.storage.ObjectStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Copies each database photo, with its thumbnail, to R2 under the same file name, so no URL or database row changes.
 * Each copy is checked by size, and a photo is recorded in the index only when everything of it is verified in R2.
 * A photo already in the index costs no R2 request, and the number of photos looked at per run is capped, so the
 * number of requests per run is bounded. Nothing is ever deleted from the database here; removing those copies
 * would be a separate, explicit step.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "wingmark.uploads.storage", havingValue = "r2")
public class UploadMigrationServiceImpl implements UploadMigrationService {

    private final GridFsFileStorageServiceImpl gridFs;
    private final ObjectStore objectStore;
    private final UploadedFileRepository uploadedFiles;
    private final R2MigrationProperties properties;

    public UploadMigrationServiceImpl(GridFsFileStorageServiceImpl gridFs, ObjectStore objectStore,
                                      UploadedFileRepository uploadedFiles, R2MigrationProperties properties) {
        this.gridFs = gridFs;
        this.objectStore = objectStore;
        this.uploadedFiles = uploadedFiles;
        this.properties = properties;
    }

    @Override
    public Result migrate() {
        Set<String> doneBases = new HashSet<>();
        uploadedFiles.findAll().forEach(file -> doneBases.add(PhotoImages.baseOf(file.getFilename())));

        Map<String, List<StoredFileInfo>> byBase = new LinkedHashMap<>();
        for (StoredFileInfo file : gridFs.listFiles()) {
            byBase.computeIfAbsent(PhotoImages.baseOf(file.filename()), base -> new ArrayList<>()).add(file);
        }

        int copied = 0;
        int alreadyThere = 0;
        int failed = 0;
        long bytes = 0;
        int examined = 0;
        for (Map.Entry<String, List<StoredFileInfo>> entry : byBase.entrySet()) {
            if (doneBases.contains(entry.getKey())) {
                continue;
            }
            if (examined >= properties.maxPerRun()) {
                break;
            }
            examined++;
            Unit unit = moveUnit(entry.getValue());
            if (unit.failed()) {
                failed++;
            } else if (unit.bytesCopied() > 0 || unit.wouldCopy()) {
                copied++;
                bytes += unit.bytesCopied();
            } else {
                alreadyThere++;
            }
        }
        log.info("R2 migration {}: {} photo(s) copied ({} bytes), {} already in R2, {} failed", properties.dryRun() ? "(dry run)" : "done",
                copied, bytes, alreadyThere, failed);
        return new Result(copied, alreadyThere, failed, bytes, properties.dryRun());
    }

    /** The outcome for one photo and its thumbnail. */
    private record Unit(boolean failed, boolean wouldCopy, long bytesCopied) {
    }

    private Unit moveUnit(List<StoredFileInfo> files) {
        boolean wouldCopy = false;
        long bytesCopied = 0;
        for (StoredFileInfo file : files) {
            String key = R2FileStorageServiceImpl.KEY_PREFIX + file.filename();
            try {
                Optional<Long> existing = objectStore.size(key);
                if (existing.isPresent() && existing.get() == file.sizeBytes()) {
                    continue;
                }
                if (properties.dryRun()) {
                    wouldCopy = true;
                    bytesCopied += file.sizeBytes();
                    continue;
                }
                if (!copy(file, key)) {
                    return new Unit(true, false, 0);
                }
                bytesCopied += file.sizeBytes();
            } catch (RuntimeException e) {
                log.error("R2 copy of {} failed; left for the next run", file.filename(), e);
                return new Unit(true, false, 0);
            }
        }
        if (!properties.dryRun()) {
            files.stream().filter(file -> !file.filename().endsWith(PhotoImages.THUMBNAIL_SUFFIX)).findFirst().ifPresent(this::index);
        }
        return new Unit(false, wouldCopy, bytesCopied);
    }

    /** Copies one file and checks the copy by size. */
    private boolean copy(StoredFileInfo file, String key) {
        Optional<StoredFile> source = gridFs.load(file.filename());
        if (source.isEmpty()) {
            return false;
        }
        objectStore.put(key, source.get().content(), source.get().contentType());
        Optional<Long> stored = objectStore.size(key);
        if (stored.isEmpty() || stored.get() != source.get().content().length) {
            log.error("R2 copy of {} did not verify (size mismatch); left for the next run", file.filename());
            return false;
        }
        return true;
    }

    /** Records a photo in the index with its recorded owner, if it is not there yet. */
    private void index(StoredFileInfo photo) {
        if (uploadedFiles.findByFilename(photo.filename()).isPresent()) {
            return;
        }
        uploadedFiles.save(UploadedFile.builder().filename(photo.filename()).ownerId(gridFs.ownerOf(photo.filename()).orElse(null))
                .sizeBytes(photo.sizeBytes()).build());
    }
}
