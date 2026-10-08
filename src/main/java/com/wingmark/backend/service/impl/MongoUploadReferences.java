package com.wingmark.backend.service.impl;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.SpeciesImage;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.UploadReferences;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Reads the photo fields of bird logs, users and species images, loading only those fields. */
@Component
@RequiredArgsConstructor
public class MongoUploadReferences implements UploadReferences {

    private final MongoTemplate mongoTemplate;
    private final FileStorageService fileStorageService;

    @Override
    public Set<String> referencedFilenames() {
        Set<String> filenames = new HashSet<>();
        for (BirdLog log : mongoTemplate.find(projected("photoUrl"), BirdLog.class)) {
            fileStorageService.storedFilename(log.getPhotoUrl()).ifPresent(filenames::add);
        }
        for (User user : mongoTemplate.find(projected("profilePicture"), User.class)) {
            fileStorageService.storedFilename(user.getProfilePicture()).ifPresent(filenames::add);
        }
        for (SpeciesImage image : mongoTemplate.find(projected("imageUrl"), SpeciesImage.class)) {
            fileStorageService.storedFilename(image.getImageUrl()).ifPresent(filenames::add);
        }
        return filenames;
    }

    private static Query projected(String field) {
        Query query = new Query(Criteria.where(field).ne(null));
        query.fields().include(field);
        return query;
    }
}
