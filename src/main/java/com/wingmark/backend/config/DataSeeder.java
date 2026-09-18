package com.wingmark.backend.config;

import com.wingmark.backend.entity.Badge;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.SpeciesImage;
import com.wingmark.backend.enums.BadgeCriteriaType;
import com.wingmark.backend.enums.BadgeTier;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import com.wingmark.backend.repository.BadgeRepository;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seeds badges and a handful of starter species on first startup against an empty
 * database, replacing what used to be Flyway seed migrations (V2/V3). Fixed UUIDs are
 * reused from those migrations so species_images keep pointing at the right species.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final BadgeRepository badgeRepository;
    private final SpeciesRepository speciesRepository;
    private final SpeciesImageRepository speciesImageRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedBadges();
        seedSpecies();
    }

    private void seedBadges() {
        if (badgeRepository.count() > 0) {
            return;
        }

        badgeRepository.saveAll(List.of(
                badge("11111111-1111-1111-1111-111111111101", "First Sighting", "Log your first bird", "egg",
                        BadgeCriteriaType.TOTAL_LOGS, 1, null, BadgeTier.BRONZE),
                badge("11111111-1111-1111-1111-111111111102", "Getting Started", "Log 10 birds", "binoculars",
                        BadgeCriteriaType.TOTAL_LOGS, 10, null, BadgeTier.SILVER),
                badge("11111111-1111-1111-1111-111111111103", "Dedicated Birder", "Log 100 birds", "trophy",
                        BadgeCriteriaType.TOTAL_LOGS, 100, null, BadgeTier.GOLD),

                badge("11111111-1111-1111-1111-111111111201", "Species Spotter", "Log 5 different species", "feather",
                        BadgeCriteriaType.UNIQUE_SPECIES, 5, null, BadgeTier.BRONZE),
                badge("11111111-1111-1111-1111-111111111202", "Species Collector", "Log 25 different species", "feather",
                        BadgeCriteriaType.UNIQUE_SPECIES, 25, null, BadgeTier.SILVER),
                badge("11111111-1111-1111-1111-111111111203", "Master Birder", "Log 75 different species", "feather",
                        BadgeCriteriaType.UNIQUE_SPECIES, 75, null, BadgeTier.GOLD),

                badge("11111111-1111-1111-1111-111111111301", "Nest Watcher", "Log 5 baby birds", "chick",
                        BadgeCriteriaType.BABY_LOGS, 5, null, BadgeTier.BRONZE),

                badge("11111111-1111-1111-1111-111111111401", "Mystery Bird", "Log 5 birds you could not identify", "question-mark",
                        BadgeCriteriaType.UNKNOWN_SPECIES_LOGS, 5, null, BadgeTier.BRONZE),

                badge("11111111-1111-1111-1111-111111111501", "Proud Pet Parent", "Log your pet for the first time", "paw",
                        BadgeCriteriaType.PET_LOGS, 1, null, BadgeTier.BRONZE),

                badge("11111111-1111-1111-1111-111111111601", "Local Explorer", "Spot 10 different species within 5km of one spot", "map-pin",
                        BadgeCriteriaType.SPECIES_IN_RADIUS, 10, Map.of("radiusMeters", 5000), BadgeTier.SILVER)
        ));
    }

    private void seedSpecies() {
        if (speciesRepository.count() > 0) {
            return;
        }

        UUID houseSparrowId = UUID.fromString("22222222-2222-2222-2222-222222222201");
        UUID europeanRobinId = UUID.fromString("22222222-2222-2222-2222-222222222202");
        UUID mallardId = UUID.fromString("22222222-2222-2222-2222-222222222203");

        speciesRepository.saveAll(List.of(
                Species.builder().id(houseSparrowId)
                        .commonName("House Sparrow").scientificName("Passer domesticus")
                        .family("Passeridae").order("Passeriformes")
                        .description("A small, stocky bird with a thick bill for eating seeds. Males have a grey crown and black bib; females are plain brown and buff.")
                        .lifespan("3-5 years").diet("Seeds, grains, and insects (especially when feeding young)")
                        .habitat("Urban and suburban areas, farmland, close to human settlement")
                        .sizeDescription("14-18cm, wingspan 19-25cm").conservationStatus("Least Concern")
                        .nativeRange("Native to Europe, Asia and North Africa; introduced worldwide")
                        .build(),
                Species.builder().id(europeanRobinId)
                        .commonName("European Robin").scientificName("Erithacus rubecula")
                        .family("Muscicapidae").order("Passeriformes")
                        .description("Small insectivorous bird best known for its bright orange-red breast and face.")
                        .lifespan("2 years (up to 8-11 in the wild)").diet("Insects, worms, seeds and berries")
                        .habitat("Gardens, woodlands, hedgerows and parks")
                        .sizeDescription("12.5-14cm, wingspan 20-22cm").conservationStatus("Least Concern")
                        .nativeRange("Europe, western Siberia and parts of North Africa")
                        .build(),
                Species.builder().id(mallardId)
                        .commonName("Mallard").scientificName("Anas platyrhynchos")
                        .family("Anatidae").order("Anseriformes")
                        .description("The most common and widespread dabbling duck. Males have an iridescent green head and yellow bill; females are mottled brown.")
                        .lifespan("5-10 years").diet("Aquatic plants, seeds, insects, small fish")
                        .habitat("Lakes, ponds, rivers, wetlands and urban parks")
                        .sizeDescription("50-65cm, wingspan 81-98cm").conservationStatus("Least Concern")
                        .nativeRange("Native across the Northern Hemisphere")
                        .build()
        ));

        speciesImageRepository.saveAll(List.of(
                speciesImage("33333333-3333-3333-3333-333333333301", houseSparrowId, LifeStageImage.ADULT, ImageGender.MALE,
                        "https://upload.wikimedia.org/wikipedia/commons/4/48/Passer_domesticus_adult%2Cwinter-male.jpg",
                        "Adult male in winter plumage, grey crown, chestnut nape, black bib"),
                speciesImage("33333333-3333-3333-3333-333333333302", houseSparrowId, LifeStageImage.ADULT, ImageGender.FEMALE,
                        "https://upload.wikimedia.org/wikipedia/commons/b/b3/Female_house_sparrow_at_Kodai.jpg",
                        "Adult female, plain brown and buff"),
                speciesImage("33333333-3333-3333-3333-333333333303", europeanRobinId, LifeStageImage.ADULT, ImageGender.NOT_APPLICABLE,
                        "https://upload.wikimedia.org/wikipedia/commons/f/f3/Erithacus_rubecula_with_cocked_head.jpg",
                        "Adult, sexes look alike")
        ));
    }

    private Badge badge(String id, String name, String description, String icon,
                         BadgeCriteriaType criteriaType, int criteriaValue,
                         Map<String, Object> criteriaMetadata, BadgeTier tier) {
        return Badge.builder()
                .id(UUID.fromString(id))
                .name(name)
                .description(description)
                .icon(icon)
                .criteriaType(criteriaType)
                .criteriaValue(criteriaValue)
                .criteriaMetadata(criteriaMetadata)
                .tier(tier)
                .build();
    }

    private SpeciesImage speciesImage(String id, UUID speciesId, LifeStageImage lifeStage, ImageGender gender,
                                       String imageUrl, String caption) {
        return SpeciesImage.builder()
                .id(UUID.fromString(id))
                .speciesId(speciesId)
                .lifeStage(lifeStage)
                .gender(gender)
                .imageUrl(imageUrl)
                .caption(caption)
                .build();
    }
}
