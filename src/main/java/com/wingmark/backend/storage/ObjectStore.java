package com.wingmark.backend.storage;

import java.util.Optional;

/** The few object-storage operations the photo storage needs. The only implementation talks S3 to Cloudflare R2. */
public interface ObjectStore {

    /** An object's bytes, content type and size. */
    record StoredObject(byte[] content, String contentType) {
        public long size() {
            return content.length;
        }
    }

    /** Stores the bytes under the key with the given content type and a one-year immutable cache header. */
    void put(String key, byte[] content, String contentType);

    /** Returns the object, or empty if there is none under the key. */
    Optional<StoredObject> get(String key);

    /** Returns the size in bytes of the object under the key, or empty if there is none. */
    Optional<Long> size(String key);

    /** Deletes the object; a missing object is not an error. */
    void delete(String key);
}
