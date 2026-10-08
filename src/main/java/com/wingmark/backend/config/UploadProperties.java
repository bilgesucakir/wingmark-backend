package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limits for uploaded photos.
 *
 * @param maxPhotosPerUser most photos one user may have stored at once; more are refused with 403 PHOTO_QUOTA_EXCEEDED. 0 means no limit.
 */
@ConfigurationProperties(prefix = "wingmark.uploads")
public record UploadProperties(@DefaultValue("200") int maxPhotosPerUser) {
}
