package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits for uploaded photos.
 *
 * @param maxPhotosPerUser most photos one user may have stored at once; more are refused with 403 PHOTO_QUOTA_EXCEEDED. 0 means no limit.
 * @param storage          where new photos are stored: {@code gridfs} (the database, default) or {@code r2}
 *                         (Cloudflare R2, with the database as a fallback for older photos)
 */
@ConfigurationProperties(prefix = "wingmark.uploads")
public record UploadProperties(@DefaultValue("200") int maxPhotosPerUser, @DefaultValue("gridfs") String storage) {
}
