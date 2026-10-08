package com.wingmark.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/** Stores uploaded images in MongoDB GridFS (see GridFsFileStorageServiceImpl), served at /uploads/{filename}. */
public interface FileStorageService {

    /** Stores a JPEG or PNG (415 otherwise), re-encoded to strip EXIF and shrunk (at most 1280 px), plus a 400 px JPEG thumbnail next to it. Returns the photo's relative URL such as {@code /uploads/xxx.jpg}. */
    String store(MultipartFile file);

    /**
     * Returns the thumbnail URL for one of this service's own photo URLs, or empty for anything else (external URLs,
     * thumbnails themselves). Photos uploaded before thumbnails existed get theirs on first request.
     */
    Optional<String> thumbnailUrl(String photoUrl);

    /** Whether a previously stored file with this name still exists. */
    boolean exists(String filename);

    /** Returns the stored filename for one of this service's own upload URLs; empty for anything else. */
    Optional<String> storedFilename(String url);

    /** The stored bytes and content type of a file, or empty if there's no such file. */
    Optional<StoredFile> load(String filename);

    /** Deletes a previously stored file by name, and its thumbnail. Missing files are ignored; failures are logged, not thrown. */
    void delete(String filename);

    record StoredFile(byte[] content, String contentType) {
    }
}
