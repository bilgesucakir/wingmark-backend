package com.wingmark.backend.service.impl;

import com.wingmark.backend.entity.BirdLog;
import com.wingmark.backend.entity.SpeciesImage;
import com.wingmark.backend.entity.User;
import com.wingmark.backend.enums.Gender;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStage;
import com.wingmark.backend.enums.LifeStageImage;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MongoUploadReferencesTest {

    @Autowired
    private MongoUploadReferences references;
    @Autowired
    private BirdLogRepository birdLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SpeciesImageRepository speciesImageRepository;

    @Test
    void collectsTheOwnUploadFilenamesUsedByLogsProfilePicturesAndSpeciesImages() {
        String id = UUID.randomUUID().toString();
        birdLogRepository.save(BirdLog.builder().userId(UUID.randomUUID()).lifeStage(LifeStage.ADULT).gender(Gender.UNKNOWN)
                .latitude(1.0).longitude(1.0).photoUrl("/uploads/log-" + id + ".jpg").build());
        userRepository.save(User.builder().email("ref-" + id + "@example.com").username("ref" + id.substring(0, 8))
                .passwordHash("x").profilePicture("https://backend.example/uploads/pic-" + id + ".png").build());
        speciesImageRepository.save(SpeciesImage.builder().speciesId(UUID.randomUUID()).lifeStage(LifeStageImage.ADULT)
                .gender(ImageGender.NOT_APPLICABLE).imageUrl("/uploads/species-" + id + ".jpg").build());
        // Not our uploads: ignored.
        birdLogRepository.save(BirdLog.builder().userId(UUID.randomUUID()).lifeStage(LifeStage.ADULT).gender(Gender.UNKNOWN)
                .latitude(1.0).longitude(1.0).photoUrl("https://example.com/other-" + id + ".jpg").build());

        assertThat(references.referencedFilenames())
                .contains("log-" + id + ".jpg", "pic-" + id + ".png", "species-" + id + ".jpg")
                .noneMatch(name -> name.contains("other-" + id));
    }
}
