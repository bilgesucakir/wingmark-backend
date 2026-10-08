package com.wingmark.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection settings for the Cloudflare R2 bucket that holds uploaded photos when {@code UPLOAD_STORAGE=r2}.
 * The access keys are secrets: set them only in the host's environment, never in files, and they are never logged.
 *
 * @param endpoint        S3 endpoint, e.g. {@code https://<account id>.eu.r2.cloudflarestorage.com} (use the EU one for an EU bucket)
 * @param bucket          bucket name
 * @param region          R2 uses {@code auto}
 * @param accessKeyId     access key id (secret)
 * @param secretAccessKey secret access key (secret)
 */
@ConfigurationProperties(prefix = "wingmark.r2")
public record R2Properties(String endpoint, String bucket, @DefaultValue("auto") String region,
                           String accessKeyId, String secretAccessKey) {

    /** Returns the names of the settings that are missing, empty when R2 is fully configured. */
    public java.util.List<String> missing() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (isBlank(endpoint)) {
            missing.add("R2_ENDPOINT");
        }
        if (isBlank(bucket)) {
            missing.add("R2_BUCKET");
        }
        if (isBlank(accessKeyId)) {
            missing.add("R2_ACCESS_KEY_ID");
        }
        if (isBlank(secretAccessKey)) {
            missing.add("R2_SECRET_ACCESS_KEY");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Never prints the keys. */
    @Override
    public String toString() {
        return "R2Properties[endpoint=" + endpoint + ", bucket=" + bucket + ", region=" + region + ", keys=hidden]";
    }
}
