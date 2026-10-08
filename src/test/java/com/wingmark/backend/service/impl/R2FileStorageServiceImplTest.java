package com.wingmark.backend.service.impl;

import com.wingmark.backend.config.UploadProperties;
import com.wingmark.backend.entity.UploadedFile;
import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.FileStorageException;
import com.wingmark.backend.repository.UploadedFileRepository;
import com.wingmark.backend.service.FileStorageService.StoredFile;
import com.wingmark.backend.service.FileStorageService.StoredFileInfo;
import com.wingmark.backend.storage.ObjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An in-memory object store stands in for R2, so nothing here can reach a real bucket. */
@ExtendWith(MockitoExtension.class)
class R2FileStorageServiceImplTest {

    /** A fake bucket kept in memory. */
    private static final class MemoryStore implements ObjectStore {
        final Map<String, StoredObject> objects = new HashMap<>();
        boolean failPuts;

        @Override
        public void put(String key, byte[] content, String contentType) {
            if (failPuts) {
                throw new FileStorageException("boom");
            }
            objects.put(key, new StoredObject(content, contentType));
        }

        @Override
        public Optional<StoredObject> get(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public Optional<Long> size(String key) {
            return Optional.ofNullable(objects.get(key)).map(StoredObject::size);
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }
    }

    @Mock
    private UploadedFileRepository uploadedFiles;
    @Mock
    private GridFsFileStorageServiceImpl gridFs;

    private final MemoryStore bucket = new MemoryStore();
    private R2FileStorageServiceImpl storage;
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        storage = new R2FileStorageServiceImpl(bucket, uploadedFiles, gridFs, new UploadProperties(2, "r2"));
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private static MockMultipartFile upload(byte[] bytes) {
        return new MockMultipartFile("file", "p.jpg", "image/jpeg", bytes);
    }

    private static String nameOf(String url) {
        return url.substring("/uploads/".length());
    }

    @Test
    void anUploadStoresThePhotoAndItsThumbnailInTheBucketAndIndexesIt() throws Exception {
        String url = storage.store(upload(jpeg(2000, 1000)), owner);

        String name = nameOf(url);
        assertThat(url).startsWith("/uploads/").endsWith(".jpg");
        assertThat(bucket.objects).containsKeys("photos/" + name, "photos/" + name.replace(".jpg", "_thumb.jpg"));
        assertThat(bucket.objects.get("photos/" + name).contentType()).isEqualTo("image/jpeg");
        BufferedImage photo = ImageIO.read(new ByteArrayInputStream(bucket.objects.get("photos/" + name).content()));
        assertThat(photo.getWidth()).isEqualTo(1280);
        BufferedImage thumb = ImageIO.read(new ByteArrayInputStream(bucket.objects.get("photos/" + name.replace(".jpg", "_thumb.jpg")).content()));
        assertThat(thumb.getWidth()).isEqualTo(400);

        ArgumentCaptor<UploadedFile> indexed = ArgumentCaptor.forClass(UploadedFile.class);
        verify(uploadedFiles).save(indexed.capture());
        assertThat(indexed.getValue().getFilename()).isEqualTo(name);
        assertThat(indexed.getValue().getOwnerId()).isEqualTo(owner);
        assertThat(indexed.getValue().getSizeBytes()).isEqualTo(bucket.objects.get("photos/" + name).size());
    }

    @Test
    void aFileThatIsNotAnImageIsRefusedAndNothingIsStored() {
        MockMultipartFile html = new MockMultipartFile("file", "x.html", "text/html", "<script>".getBytes());

        assertThatThrownBy(() -> storage.store(html, owner)).isInstanceOf(ApiException.class);

        assertThat(bucket.objects).isEmpty();
        verify(uploadedFiles, never()).save(any());
    }

    @Test
    void theLimitCountsPhotosInBothStoresOnceEachAndRefusesTheNextUpload() throws Exception {
        UploadedFile inR2 = UploadedFile.builder().filename("a.jpg").ownerId(owner).build();
        when(uploadedFiles.findByOwnerId(owner)).thenReturn(List.of(inR2));
        when(gridFs.photoFilenamesOwnedBy(owner)).thenReturn(Set.of("a.jpg", "old.jpg")); // a.jpg is a migrated copy

        assertThat(storage.countPhotosOwnedBy(owner)).isEqualTo(2);
        assertThatThrownBy(() -> storage.store(upload(jpeg(10, 10)), owner))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PHOTO_QUOTA_EXCEEDED));
        assertThat(bucket.objects).isEmpty();
    }

    @Test
    void uploadsWithoutAnOwnerAreNotLimited() throws Exception {
        for (int i = 0; i < 4; i++) {
            storage.store(upload(jpeg(10, 10)));
        }

        assertThat(bucket.objects).hasSize(8); // four photos and four thumbnails
    }

    @Test
    void aFailedUploadLeavesNothingBehindAndIsNotIndexed() throws Exception {
        bucket.failPuts = true;

        assertThatThrownBy(() -> storage.store(upload(jpeg(10, 10)), owner)).isInstanceOf(FileStorageException.class);

        assertThat(bucket.objects).isEmpty();
        verify(uploadedFiles, never()).save(any());
    }

    @Test
    void loadReadsFromTheBucketFirst() {
        bucket.put("photos/a.png", new byte[]{1, 2}, "image/png");

        Optional<StoredFile> file = storage.load("a.png");

        assertThat(file).isPresent();
        assertThat(file.get().contentType()).isEqualTo("image/png");
        verify(gridFs, never()).load(any());
    }

    @Test
    void loadFallsBackToTheDatabaseForOlderPhotos() {
        StoredFile older = new StoredFile(new byte[]{5}, "image/jpeg");
        when(gridFs.load("old.jpg")).thenReturn(Optional.of(older));

        assertThat(storage.load("old.jpg")).contains(older);
    }

    @Test
    void aMissingThumbnailOfAPhotoInTheBucketIsBuiltAndStored() throws Exception {
        bucket.put("photos/p.jpg", jpeg(1000, 1000), "image/jpeg");
        when(gridFs.load("p_thumb.jpg")).thenReturn(Optional.empty());

        Optional<StoredFile> thumbnail = storage.load("p_thumb.jpg");

        assertThat(thumbnail).isPresent();
        assertThat(ImageIO.read(new ByteArrayInputStream(thumbnail.get().content())).getWidth()).isEqualTo(400);
        assertThat(bucket.objects).containsKey("photos/p_thumb.jpg");
    }

    @Test
    void nothingIsFoundForAnUnknownFile() {
        when(gridFs.load("nope.jpg")).thenReturn(Optional.empty());

        assertThat(storage.load("nope.jpg")).isEmpty();
        assertThat(storage.exists("nope.jpg")).isFalse();
    }

    @Test
    void existsLooksInBothStores() {
        bucket.put("photos/a.jpg", new byte[]{1}, "image/jpeg");
        when(gridFs.exists("old.jpg")).thenReturn(true);

        assertThat(storage.exists("a.jpg")).isTrue();
        assertThat(storage.exists("old.jpg")).isTrue();
    }

    @Test
    void deletingAPhotoRemovesItFromTheBucketTheThumbnailTheIndexAndTheOlderStore() {
        bucket.put("photos/a.jpg", new byte[]{1}, "image/jpeg");
        bucket.put("photos/a_thumb.jpg", new byte[]{1}, "image/jpeg");

        storage.delete("a.jpg");

        assertThat(bucket.objects).isEmpty();
        verify(uploadedFiles).deleteByFilename("a.jpg");
        verify(gridFs).delete("a.jpg");
    }

    @Test
    void listFilesMergesTheIndexWithTheOlderStoreWithoutDuplicates() {
        Instant now = Instant.parse("2026-10-09T10:00:00Z");
        UploadedFile migrated = UploadedFile.builder().filename("a.jpg").sizeBytes(100).build();
        migrated.setCreatedAt(now);
        when(uploadedFiles.findAll()).thenReturn(List.of(migrated));
        when(gridFs.listFiles()).thenReturn(List.of(
                new StoredFileInfo("a.jpg", now, 100), new StoredFileInfo("a_thumb.jpg", now, 10), // migrated copies
                new StoredFileInfo("old.jpg", now, 50), new StoredFileInfo("old_thumb.jpg", now, 5)));

        List<String> names = storage.listFiles().stream().map(StoredFileInfo::filename).toList();

        assertThat(names).containsExactlyInAnyOrder("a.jpg", "old.jpg", "old_thumb.jpg");
    }

    @Test
    void nameHelpersWorkWithoutTouchingEitherStore() {
        assertThat(storage.thumbnailUrl("/uploads/a.jpg")).contains("/uploads/a_thumb.jpg");
        assertThat(storage.storedFilename("https://x.example/uploads/a.jpg")).contains("a.jpg");
        assertThat(bucket.objects).isEmpty();
    }
}
