package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * uploadDir is only read by LegacyUploadImporter now: uploads are stored in MongoDB GridFS,
 * and this is where pre-GridFS uploads may still sit on disk to be imported once.
 */
@ConfigurationProperties(prefix = "wingmark.storage")
public record StorageProperties(String uploadDir) {
}
