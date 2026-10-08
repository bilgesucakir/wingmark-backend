package com.wingmark.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wingmark.backend.dto.species.CreateSpeciesRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks scripts/species-popular.json, the species imported through the admin API, so a typo or a missing translation fails the build. */
class PopularSpeciesSeedTest {

    /** Species already in production when the file was made; the import skips existing ones, but the file should not repeat them. */
    private static final Set<String> ALREADY_IN_PRODUCTION = Set.of("passer domesticus", "anas platyrhynchos", "erithacus rubecula", "forpus coelestis");

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private List<CreateSpeciesRequestDto> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        return List.of(mapper.readValue(new File("scripts/species-popular.json"), CreateSpeciesRequestDto[].class));
    }

    @Test
    void theFileHoldsAReasonableNumberOfSpeciesAndEachPassesTheApiValidation() throws Exception {
        List<CreateSpeciesRequestDto> species = load();

        assertThat(species).hasSizeGreaterThanOrEqualTo(50);
        species.forEach(s -> assertThat(validator.validate(s)).as(s.scientificName()).isEmpty());
    }

    @Test
    void scientificNamesAreUniqueTwoWordNamesAndNotAlreadyInProduction() throws Exception {
        Set<String> seen = new HashSet<>();
        for (CreateSpeciesRequestDto s : load()) {
            String name = s.scientificName();
            assertThat(name.trim().split("\\s+")).as(name).hasSize(2);
            assertThat(seen.add(name.toLowerCase())).as("duplicate " + name).isTrue();
            assertThat(ALREADY_IN_PRODUCTION).doesNotContain(name.toLowerCase());
        }
    }

    @Test
    void everyTextFieldHasBothEnglishAndTurkish() throws Exception {
        for (CreateSpeciesRequestDto s : load()) {
            List<Map<String, String>> localized = List.of(s.commonName(), s.description(), s.lifespan(), s.diet(), s.habitat(),
                    s.sizeDescription(), s.conservationStatus(), s.nativeRange());
            for (Map<String, String> field : localized) {
                assertThat(field).as(s.scientificName()).isNotNull();
                assertThat(field.get("en")).as(s.scientificName() + " en").isNotBlank();
                assertThat(field.get("tr")).as(s.scientificName() + " tr").isNotBlank();
            }
            assertThat(s.family()).as(s.scientificName()).isNotBlank();
            assertThat(s.order()).as(s.scientificName()).isNotBlank();
        }
    }

    @Test
    void conservationStatusesAreKnownIucnCategoriesWhoseTwoLanguagesMatch() throws Exception {
        Map<String, String> turkishByEnglish = Map.of("Least Concern", "Az Endişe Verici", "Near Threatened", "Tehdide Yakın",
                "Vulnerable", "Hassas", "Endangered", "Tehlike Altında", "Critically Endangered", "Çok Tehlikede");
        for (CreateSpeciesRequestDto s : load()) {
            assertThat(turkishByEnglish).as(s.scientificName()).containsKey(s.conservationStatus().get("en"));
            assertThat(s.conservationStatus().get("tr")).as(s.scientificName())
                    .isEqualTo(turkishByEnglish.get(s.conservationStatus().get("en")));
        }
    }

    @Test
    void turkishLifespanAndSizeUseTurkishUnits() throws Exception {
        for (CreateSpeciesRequestDto s : load()) {
            assertThat(s.lifespan().get("tr")).as(s.scientificName()).endsWith("yıl");
            assertThat(s.sizeDescription().get("tr")).as(s.scientificName()).contains(" cm");
            assertThat(s.sizeDescription().get("en")).as(s.scientificName()).contains("cm").doesNotContain(" cm");
        }
    }
}
