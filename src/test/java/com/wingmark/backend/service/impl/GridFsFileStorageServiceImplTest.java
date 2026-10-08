package com.wingmark.backend.service.impl;

import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class GridFsFileStorageServiceImplTest {

    @Autowired
    private GridFsFileStorageServiceImpl storage;

    private static byte[] image(int width, int height, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static String filenameOf(String url) {
        return url.substring("/uploads/".length());
    }

    @Test
    void storesAnImageInGridFsAndLoadsItBackUnderItsUploadsUrl() throws Exception {
        String url = storage.store(new MockMultipartFile("file", "p.jpg", "image/jpeg", image(4, 4, "jpg")));

        assertThat(url).startsWith("/uploads/").endsWith(".jpg");
        assertThat(storage.exists(filenameOf(url))).isTrue();
        var loaded = storage.load(filenameOf(url)).orElseThrow();
        assertThat(loaded.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageIO.read(new ByteArrayInputStream(loaded.content()))).isNotNull();
    }

    @Test
    void downscalesLargeImagesToTheMaxDimensionKeepingAspectRatio() throws Exception {
        String url = storage.store(new MockMultipartFile("file", "big.png", "image/png", image(4000, 2000, "png")));

        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(storage.load(filenameOf(url)).orElseThrow().content()));
        assertThat(stored.getWidth()).isEqualTo(GridFsFileStorageServiceImpl.MAX_DIMENSION);
        assertThat(stored.getHeight()).isEqualTo(GridFsFileStorageServiceImpl.MAX_DIMENSION / 2);
    }

    @Test
    void generatesARandomFilenameSoRepeatUploadsCannotCollide() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "p.jpg", "image/jpeg", image(4, 4, "jpg"));

        assertThat(storage.store(file)).isNotEqualTo(storage.store(file));
    }

    @Test
    void rejectsEmptyAndUndecodableFilesWith400() {
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[0])))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_FILE));
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("file", "p.jpg", "image/jpeg", "not an image".getBytes())))
                .isInstanceOfSatisfying(BadRequestException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_FILE));
    }

    @Test
    void rejectsNonImageContentTypesWith415() {
        for (String type : new String[]{"text/plain", "text/html", "image/svg+xml", "image/webp", "image/heic"}) {
            MockMultipartFile file = new MockMultipartFile("file", "x", type, "<script>alert(1)</script>".getBytes());
            assertThatThrownBy(() -> storage.store(file))
                    .isInstanceOfSatisfying(ApiException.class, ex -> {
                        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
                    });
        }
    }

    @Test
    void deleteRemovesTheFile() throws Exception {
        String filename = filenameOf(storage.store(new MockMultipartFile("file", "p.png", "image/png", image(2, 2, "png"))));

        storage.delete(filename);

        assertThat(storage.exists(filename)).isFalse();
        assertThat(storage.load(filename)).isEmpty();
    }

    @Test
    void storedFilenameOnlyAcceptsUploadsUrlsWithoutPathTricks() {
        assertThat(storage.storedFilename("/uploads/abc.jpg")).contains("abc.jpg");
        assertThat(storage.storedFilename("https://wingmark-backend.onrender.com/uploads/abc.jpg")).contains("abc.jpg");
        assertThat(storage.storedFilename("https://example.com/me.png")).isEmpty();
        assertThat(storage.storedFilename("/uploads/../secret")).isEmpty();
        assertThat(storage.storedFilename(null)).isEmpty();
    }

    private static String thumbnailNameOf(String url) {
        return filenameOf(url).replaceAll("\\.(jpg|png)$", "") + GridFsFileStorageServiceImpl.THUMBNAIL_SUFFIX;
    }

    @Test
    void everyUploadGetsASmallJpegThumbnailNextToIt() throws Exception {
        String url = storage.store(new MockMultipartFile("file", "big.jpg", "image/jpeg", image(3000, 1500, "jpg")));

        var thumbnail = storage.load(thumbnailNameOf(url)).orElseThrow();
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail.content()));
        assertThat(thumbnail.contentType()).isEqualTo("image/jpeg");
        assertThat(decoded.getWidth()).isEqualTo(GridFsFileStorageServiceImpl.THUMBNAIL_DIMENSION);
        assertThat(decoded.getHeight()).isEqualTo(GridFsFileStorageServiceImpl.THUMBNAIL_DIMENSION / 2);
    }

    @Test
    void aPngUploadStillGetsAJpegThumbnail() throws Exception {
        String url = storage.store(new MockMultipartFile("file", "p.png", "image/png", image(800, 600, "png")));

        var thumbnail = storage.load(thumbnailNameOf(url)).orElseThrow();
        assertThat(thumbnail.contentType()).isEqualTo("image/jpeg");
        assertThat(thumbnailNameOf(url)).endsWith("_thumb.jpg");
    }

    @Test
    void smallImagesAreNotEnlargedForTheThumbnail() throws Exception {
        String url = storage.store(new MockMultipartFile("file", "s.jpg", "image/jpeg", image(100, 50, "jpg")));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(storage.load(thumbnailNameOf(url)).orElseThrow().content()));
        assertThat(decoded.getWidth()).isEqualTo(100);
    }

    @Test
    void aPhotoWithoutAThumbnailGetsItTheFirstTimeItIsAskedFor() throws Exception {
        String url = storage.store(new MockMultipartFile("file", "old.jpg", "image/jpeg", image(1000, 1000, "jpg")));
        String thumbnailName = thumbnailNameOf(url);
        storage.delete(thumbnailName); // as for a photo uploaded before thumbnails existed
        assertThat(storage.exists(thumbnailName)).isFalse();

        var created = storage.load(thumbnailName).orElseThrow();

        assertThat(created.contentType()).isEqualTo("image/jpeg");
        assertThat(ImageIO.read(new ByteArrayInputStream(created.content())).getWidth()).isEqualTo(GridFsFileStorageServiceImpl.THUMBNAIL_DIMENSION);
        assertThat(storage.exists(thumbnailName)).isTrue();
    }

    @Test
    void aThumbnailForAPhotoThatDoesNotExistIsNotFound() {
        assertThat(storage.load("00000000-0000-0000-0000-000000000000_thumb.jpg")).isEmpty();
    }

    @Test
    void deletingAPhotoAlsoDeletesItsThumbnail() throws Exception {
        String url = storage.store(new MockMultipartFile("file", "p.jpg", "image/jpeg", image(600, 600, "jpg")));

        storage.delete(filenameOf(url));

        assertThat(storage.exists(filenameOf(url))).isFalse();
        assertThat(storage.exists(thumbnailNameOf(url))).isFalse();
    }

    @Test
    void thumbnailUrlsAreDerivedOnlyForOwnPhotos() {
        assertThat(storage.thumbnailUrl("/uploads/abc.jpg")).contains("/uploads/abc_thumb.jpg");
        assertThat(storage.thumbnailUrl("https://wingmark-backend.onrender.com/uploads/abc.png")).contains("/uploads/abc_thumb.jpg");
        assertThat(storage.thumbnailUrl("/uploads/abc_thumb.jpg")).isEmpty();
        assertThat(storage.thumbnailUrl("https://example.com/me.png")).isEmpty();
        assertThat(storage.thumbnailUrl(null)).isEmpty();
    }
}
