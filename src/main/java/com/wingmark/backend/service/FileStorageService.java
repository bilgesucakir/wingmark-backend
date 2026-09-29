package com.wingmark.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/** Stores uploaded images in MongoDB GridFS (see GridFsFileStorageServiceImpl), served at /uploads/{filename}. */
public interface FileStorageService {

    /**
     * Stores the file and returns a public-facing relative URL (e.g. /uploads/xxx.jpg).
     * Only JPEG/PNG images are accepted (415 otherwise); they're re-encoded to strip EXIF.
     */
    String store(MultipartFile file);

    /** Whether a previously stored file with this name still exists. */
    boolean exists(String filename);

    /**
     * Returns the stored filename a URL points at, if it's one of this service's own uploads
     * (either the relative /uploads/xxx.jpg form or an absolute URL ending in it); empty for
     * anything else, e.g. an external image URL.
     */
    Optional<String> storedFilename(String url);

    /** The stored bytes and content type of a file, or empty if there's no such file. */
    Optional<StoredFile> load(String filename);

    /** Deletes a previously stored file by name. Missing files are ignored; failures are logged, not thrown. */
    void delete(String filename);

    record StoredFile(byte[] content, String contentType) {
    }
}
