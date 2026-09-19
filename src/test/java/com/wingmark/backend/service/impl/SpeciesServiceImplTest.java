package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequestDto;
import com.wingmark.backend.dto.species.CreateSpeciesRequestDto;
import com.wingmark.backend.dto.species.SpeciesImageResponseDto;
import com.wingmark.backend.dto.species.SpeciesResponseDto;
import com.wingmark.backend.dto.species.UpdateSpeciesRequestDto;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.SpeciesImage;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import com.wingmark.backend.exception.DuplicateResourceException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeciesServiceImplTest {

    @Mock
    private SpeciesRepository speciesRepository;
    @Mock
    private SpeciesImageRepository speciesImageRepository;

    private SpeciesServiceImpl speciesService;

    @BeforeEach
    void setUp() {
        speciesService = new SpeciesServiceImpl(speciesRepository, speciesImageRepository);
    }

    @Test
    void getAllWithoutSearchReturnsEverySpecies() {
        Species species = Species.builder().id(UUID.randomUUID()).commonName(Map.of("en", "House Sparrow")).scientificName("Passer domesticus").build();
        when(speciesRepository.findAll()).thenReturn(List.of(species));
        when(speciesImageRepository.findBySpeciesId(species.getId())).thenReturn(List.of());

        List<SpeciesResponseDto> result = speciesService.getAll(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).commonName()).containsEntry("en", "House Sparrow");
    }

    @Test
    void getAllWithSearchFiltersByCommonName() {
        when(speciesRepository.findByCommonNameContainingIgnoreCase("sparrow")).thenReturn(List.of());

        speciesService.getAll("sparrow");

        verify(speciesRepository).findByCommonNameContainingIgnoreCase("sparrow");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(speciesRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> speciesService.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsDuplicateScientificName() {
        when(speciesRepository.existsByScientificNameIgnoreCase("Passer domesticus")).thenReturn(true);

        CreateSpeciesRequestDto request = new CreateSpeciesRequestDto(
                Map.of("en", "House Sparrow"), "Passer domesticus", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> speciesService.create(request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createSavesNewSpecies() {
        when(speciesRepository.existsByScientificNameIgnoreCase(any())).thenReturn(false);
        when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> {
            Species s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(speciesImageRepository.findBySpeciesId(any())).thenReturn(List.of());

        CreateSpeciesRequestDto request = new CreateSpeciesRequestDto(
                Map.of("en", "European Robin"), "Erithacus rubecula", "Muscicapidae", "Passeriformes",
                Map.of("en", "desc"), Map.of("en", "2 years"), Map.of("en", "insects"), Map.of("en", "gardens"),
                Map.of("en", "12cm"), Map.of("en", "LC"), Map.of("en", "Europe"));

        SpeciesResponseDto response = speciesService.create(request);

        assertThat(response.commonName()).containsEntry("en", "European Robin");
        assertThat(response.scientificName()).isEqualTo("Erithacus rubecula");
    }

    @Test
    void updateAllowsKeepingTheSameScientificName() {
        UUID id = UUID.randomUUID();
        Species existing = Species.builder().id(id).commonName(Map.of("en", "Old Name")).scientificName("Passer domesticus").build();
        when(speciesRepository.findById(id)).thenReturn(Optional.of(existing));
        when(speciesRepository.save(any(Species.class))).thenAnswer(inv -> inv.getArgument(0));
        when(speciesImageRepository.findBySpeciesId(id)).thenReturn(List.of());

        UpdateSpeciesRequestDto request = new UpdateSpeciesRequestDto(
                Map.of("en", "New Name"), "Passer domesticus", null, null, null, null, null, null, null, null, null);

        SpeciesResponseDto response = speciesService.update(id, request);

        assertThat(response.commonName()).containsEntry("en", "New Name");
    }

    @Test
    void updateRejectsRenamingToAnotherSpeciesScientificName() {
        UUID id = UUID.randomUUID();
        Species existing = Species.builder().id(id).commonName(Map.of("en", "Old Name")).scientificName("Passer domesticus").build();
        when(speciesRepository.findById(id)).thenReturn(Optional.of(existing));
        when(speciesRepository.existsByScientificNameIgnoreCase("Erithacus rubecula")).thenReturn(true);

        UpdateSpeciesRequestDto request = new UpdateSpeciesRequestDto(
                Map.of("en", "Old Name"), "Erithacus rubecula", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> speciesService.update(id, request)).isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteThrowsWhenSpeciesDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(speciesRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> speciesService.delete(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesImagesThenSpecies() {
        UUID id = UUID.randomUUID();
        when(speciesRepository.existsById(id)).thenReturn(true);

        speciesService.delete(id);

        verify(speciesImageRepository).deleteBySpeciesId(id);
        verify(speciesRepository).deleteById(id);
    }

    @Test
    void addImageThrowsWhenSpeciesDoesNotExist() {
        UUID speciesId = UUID.randomUUID();
        when(speciesRepository.existsById(speciesId)).thenReturn(false);

        CreateSpeciesImageRequestDto request = new CreateSpeciesImageRequestDto(
                LifeStageImage.ADULT, ImageGender.MALE, "https://example.com/photo.jpg", null);

        assertThatThrownBy(() -> speciesService.addImage(speciesId, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addImageSavesWhenSpeciesExists() {
        UUID speciesId = UUID.randomUUID();
        when(speciesRepository.existsById(speciesId)).thenReturn(true);
        when(speciesImageRepository.save(any(SpeciesImage.class))).thenAnswer(inv -> {
            SpeciesImage img = inv.getArgument(0);
            img.setId(UUID.randomUUID());
            return img;
        });

        CreateSpeciesImageRequestDto request = new CreateSpeciesImageRequestDto(
                LifeStageImage.ADULT, ImageGender.MALE, "https://example.com/photo.jpg", "caption");

        SpeciesImageResponseDto response = speciesService.addImage(speciesId, request);

        assertThat(response.imageUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void deleteImageRejectsImageBelongingToAnotherSpecies() {
        UUID speciesId = UUID.randomUUID();
        UUID otherSpeciesId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        SpeciesImage image = SpeciesImage.builder().id(imageId).speciesId(otherSpeciesId).build();
        when(speciesImageRepository.findById(imageId)).thenReturn(Optional.of(image));

        assertThatThrownBy(() -> speciesService.deleteImage(speciesId, imageId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
