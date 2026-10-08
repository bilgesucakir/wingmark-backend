package com.wingmark.backend.service.impl;

import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.storage.ObjectStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application the way it runs with UPLOAD_STORAGE=r2, but with dummy settings and the bucket replaced by a
 * mock, so it proves the wiring without any connection to R2.
 */
@SpringBootTest(properties = {
        "wingmark.uploads.storage=r2",
        "wingmark.r2.endpoint=https://account.eu.r2.example.invalid",
        "wingmark.r2.bucket=test-bucket",
        "wingmark.r2.access-key-id=dummy-key-id",
        "wingmark.r2.secret-access-key=dummy-secret"})
class R2ModeWiringTest {

    @MockBean
    private ObjectStore objectStore;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private UploadMigrationServiceImpl migration;

    @Test
    void inR2ModeTheR2StorageIsTheOneThatIsUsedAndTheMigrationExists() {
        assertThat(fileStorageService).isInstanceOf(R2FileStorageServiceImpl.class);
        assertThat(migration).isNotNull();
    }
}
