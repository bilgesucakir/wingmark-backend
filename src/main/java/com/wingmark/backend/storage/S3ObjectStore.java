package com.wingmark.backend.storage;

import com.wingmark.backend.config.R2Properties;
import com.wingmark.backend.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Talks S3 to a Cloudflare R2 bucket. Every call has a timeout and at most two retries, so a slow or failing R2
 * cannot hold requests or loop. Exists only when {@code UPLOAD_STORAGE=r2}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "wingmark.uploads.storage", havingValue = "r2")
public class S3ObjectStore implements ObjectStore {

    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client client;
    private final String bucket;

    @Autowired
    public S3ObjectStore(R2Properties properties) {
        this(buildClient(properties), properties.bucket());
    }

    S3ObjectStore(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    private static S3Client buildClient(R2Properties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey())))
                .forcePathStyle(true)
                // R2 does not accept the newer default checksum headers; only send them where S3 requires them.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(15))
                        .apiCallAttemptTimeout(Duration.ofSeconds(8))
                        .retryStrategy(RetryMode.STANDARD))
                .build();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(key)
                    .contentType(contentType)
                    .cacheControl(CACHE_CONTROL)
                    .build(), RequestBody.fromBytes(content));
        } catch (RuntimeException e) {
            log.error("R2 put failed for {}", key, e);
            throw new FileStorageException("Failed to store file in object storage", e);
        }
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredObject(object.asByteArray(), object.response().contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            log.error("R2 get failed for {}", key, e);
            throw new FileStorageException("Failed to read file from object storage", e);
        }
    }

    @Override
    public Optional<Long> size(String key) {
        try {
            return Optional.ofNullable(client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (RuntimeException e) {
            if (isNotFound(e)) {
                return Optional.empty();
            }
            log.error("R2 head failed for {}", key, e);
            throw new FileStorageException("Failed to check file in object storage", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            // Failures are logged and not thrown, like the database store: a leftover object is found by the orphan cleanup.
            log.error("R2 delete failed for {}", key, e);
        }
    }

    private static boolean isNotFound(RuntimeException e) {
        return e instanceof S3Exception s3 && s3.statusCode() == 404;
    }
}
