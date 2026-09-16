package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.StorageProperties;
import com.wingmark.backend.exception.FileStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceImplTest {

    @TempDir
    Path uploadDir;

    private LocalFileStorageServiceImpl service(Path dir) {
        return new LocalFileStorageServiceImpl(new StorageProperties(dir.toString()));
    }

    private byte[] jpegBytes() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    void storesAnUploadedImageAndReturnsAUploadsUrl() throws Exception {
        LocalFileStorageServiceImpl storage = service(uploadDir);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());

        String url = storage.store(file);

        assertThat(url).startsWith("/uploads/").endsWith(".jpg");
        Path stored = uploadDir.resolve(url.substring("/uploads/".length()));
        assertThat(Files.exists(stored)).isTrue();
    }

    @Test
    void rejectsAnEmptyFile() {
        LocalFileStorageServiceImpl storage = service(uploadDir);
        MockMultipartFile empty = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> storage.store(empty)).isInstanceOf(FileStorageException.class);
    }

    @Test
    void rejectsAFileThatIsNotActuallyAnImage() {
        LocalFileStorageServiceImpl storage = service(uploadDir);
        MockMultipartFile fake = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> storage.store(fake)).isInstanceOf(FileStorageException.class);
    }

    @Test
    void nonImageContentTypesAreStoredAsIsWithoutReencoding() throws Exception {
        LocalFileStorageServiceImpl storage = service(uploadDir);
        byte[] content = "just some bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", content);

        String url = storage.store(file);

        Path stored = uploadDir.resolve(url.substring("/uploads/".length()));
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    void generatesARandomFilenameSoConcurrentUploadsCannotCollide() throws Exception {
        LocalFileStorageServiceImpl storage = service(uploadDir);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes());

        String first = storage.store(file);
        String second = storage.store(file);

        assertThat(first).isNotEqualTo(second);
    }
}
