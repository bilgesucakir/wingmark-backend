package com.wingmark.backend.storage;

import com.wingmark.backend.config.R2Properties;
import com.wingmark.backend.exception.FileStorageException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Uses a mocked S3 client: nothing here can reach a real bucket. */
class S3ObjectStoreTest {

    private final S3Client client = mock(S3Client.class);
    private final S3ObjectStore store = new S3ObjectStore(client, "test-bucket");

    @Test
    void putSendsTheBucketKeyContentTypeAndALongImmutableCacheHeader() {
        store.put("photos/a.jpg", new byte[]{1, 2, 3}, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo("photos/a.jpg");
        assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(request.getValue().cacheControl()).isEqualTo("public, max-age=31536000, immutable");
    }

    @Test
    void aFailedPutIsReportedAsAStorageFailure() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(S3Exception.builder().statusCode(500).build());

        assertThatThrownBy(() -> store.put("photos/a.jpg", new byte[]{1}, "image/jpeg")).isInstanceOf(FileStorageException.class);
    }

    @Test
    void getReturnsTheBytesAndContentType() {
        GetObjectResponse response = GetObjectResponse.builder().contentType("image/png").build();
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(ResponseBytes.fromByteArray(response, new byte[]{9, 8}));

        Optional<ObjectStore.StoredObject> object = store.get("photos/a.png");

        assertThat(object).isPresent();
        assertThat(object.get().content()).containsExactly(9, 8);
        assertThat(object.get().contentType()).isEqualTo("image/png");
    }

    @Test
    void aMissingObjectIsEmptyForGetAndSizeWhetherTheErrorIsNoSuchKeyOrA404() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());

        assertThat(store.get("photos/x.jpg")).isEmpty();
        assertThat(store.size("photos/x.jpg")).isEmpty();
    }

    @Test
    void otherReadErrorsAreNotHiddenAsMissing() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(503).build());
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());

        assertThatThrownBy(() -> store.get("photos/x.jpg")).isInstanceOf(FileStorageException.class);
        assertThatThrownBy(() -> store.size("photos/x.jpg")).isInstanceOf(FileStorageException.class);
    }

    @Test
    void sizeReportsTheContentLength() {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().contentLength(1234L).build());

        assertThat(store.size("photos/a.jpg")).contains(1234L);
    }

    @Test
    void deleteSendsTheKeyAndNeverThrows() {
        store.delete("photos/a.jpg");
        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("photos/a.jpg");

        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(500).build());
        assertThatCode(() -> store.delete("photos/b.jpg")).doesNotThrowAnyException();
    }

    @Test
    void theRealClientCanBeBuiltFromSettingsWithoutAnyNetworkCall() {
        R2Properties dummy = new R2Properties("https://account.eu.r2.example.invalid", "bucket", "auto", "dummy-key-id", "dummy-secret");

        assertThatCode(() -> new S3ObjectStore(dummy)).doesNotThrowAnyException();
    }
}
