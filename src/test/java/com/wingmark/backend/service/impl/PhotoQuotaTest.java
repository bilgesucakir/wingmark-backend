package com.wingmark.backend.service.impl;

import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "wingmark.uploads.max-photos-per-user=2")
class PhotoQuotaTest {

    @Autowired
    private GridFsFileStorageServiceImpl storage;

    private static MockMultipartFile photo() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return new MockMultipartFile("file", "p.jpg", "image/jpeg", out.toByteArray());
    }

    private static String filenameOf(String url) {
        return url.substring("/uploads/".length());
    }

    @Test
    void aUserCannotStoreMorePhotosThanTheLimitAndOthersAreNotAffected() throws Exception {
        UUID owner = UUID.randomUUID();

        String first = storage.store(photo(), owner);
        storage.store(photo(), owner);

        assertThat(storage.countPhotosOwnedBy(owner)).isEqualTo(2); // thumbnails are not counted
        assertThatThrownBy(() -> storage.store(photo(), owner))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PHOTO_QUOTA_EXCEEDED));
        assertThat(storage.store(photo(), UUID.randomUUID())).startsWith("/uploads/");

        // Deleting one frees a place.
        storage.delete(filenameOf(first));
        assertThat(storage.countPhotosOwnedBy(owner)).isEqualTo(1);
        assertThat(storage.store(photo(), owner)).startsWith("/uploads/");
    }

    @Test
    void uploadsWithoutAnOwnerAreNotLimited() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(storage.store(photo())).startsWith("/uploads/");
        }
    }

    @Test
    void listFilesReportsEachStoredFileWithItsNameTimeAndSize() throws Exception {
        String url = storage.store(photo(), UUID.randomUUID());

        var files = storage.listFiles();

        assertThat(files).anySatisfy(f -> {
            assertThat(f.filename()).isEqualTo(filenameOf(url));
            assertThat(f.sizeBytes()).isPositive();
            assertThat(f.uploadedAt()).isNotNull();
        });
        assertThat(files).anyMatch(f -> f.filename().equals(filenameOf(url).replaceAll("\\.jpg$", "_thumb.jpg")));
    }
}
