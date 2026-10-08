package com.wingmark.backend.repository;

import com.wingmark.backend.entity.UploadedFile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Index of photos kept in object storage. */
public interface UploadedFileRepository extends MongoRepository<UploadedFile, UUID> {

    Optional<UploadedFile> findByFilename(String filename);

    List<UploadedFile> findByOwnerId(UUID ownerId);

    void deleteByFilename(String filename);
}
