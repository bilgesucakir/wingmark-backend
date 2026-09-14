package com.wingmark.backend.service;

import org.springframework.web.multipart.MultipartFile;

/** Stores uploaded files (currently local disk; see LocalFileStorageServiceImpl). */
public interface FileStorageService {

    /** Stores the file and returns a public-facing relative URL (e.g. /uploads/xxx.jpg). */
    String store(MultipartFile file);
}
