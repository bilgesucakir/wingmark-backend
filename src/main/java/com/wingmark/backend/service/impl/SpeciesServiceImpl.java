package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.species.CreateSpeciesImageRequestDto;
import com.wingmark.backend.dto.species.CreateSpeciesRequestDto;
import com.wingmark.backend.dto.species.SpeciesImageResponseDto;
import com.wingmark.backend.dto.species.SpeciesResponseDto;
import com.wingmark.backend.dto.species.UpdateSpeciesRequestDto;
import com.wingmark.backend.entity.Species;
import com.wingmark.backend.entity.SpeciesImage;
import com.wingmark.backend.exception.DuplicateResourceException;
import com.wingmark.backend.exception.ResourceNotFoundException;
import com.wingmark.backend.repository.SpeciesImageRepository;
import com.wingmark.backend.repository.SpeciesRepository;
import com.wingmark.backend.service.FileStorageService;
import com.wingmark.backend.service.CommonsService;
import com.wingmark.backend.repository.BirdLogRepository;
import com.wingmark.backend.repository.SpeciesSearchRepository;
import com.wingmark.backend.service.SeenFilter;
import com.wingmark.backend.service.SpeciesFilter;
import com.wingmark.backend.service.SpeciesService;
import com.wingmark.backend.util.TaxonNames;
import com.wingmark.backend.dto.species.SpeciesFacetsResponseDto;
import com.wingmark.backend.exception.ApiException;
import com.wingmark.backend.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import com.wingmark.backend.util.SearchPattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Species guide entries and images. */
@Service
@RequiredArgsConstructor
public class SpeciesServiceImpl implements SpeciesService {

    private final SpeciesRepository speciesRepository;
    private final SpeciesImageRepository speciesImageRepository;
    private final CommonsService commonsService;
    private final FileStorageService fileStorageService;
    private final UploadedFileCleaner uploadedFileCleaner;
    private final BirdLogRepository birdLogRepository;
    private final TaxonNames taxonNames;

    @Override
    public Page<SpeciesResponseDto> getAll(String search, Pageable pageable) {
        return getAll(SpeciesFilter.searching(search), pageable);
    }

    @Override
    public Page<SpeciesResponseDto> getAll(SpeciesFilter filter, Pageable pageable) {
        if (filter.hasTaxonomyOrSeen()) {
            Set<UUID> seenIds = seenIds(filter);
            return speciesRepository.search(nameRegex(filter.search()), filter.orders(), filter.families(),
                    seenIds, seenFlag(filter), pageable).map(this::toResponse);
        }
        String search = filter.search();
        // The repository query feeds this into $regex, so it is escaped by SearchPattern: a public search box
        // must match the text literally (apart from accent folding), never run a caller-supplied pattern.
        Page<Species> species = StringUtils.hasText(search)
                ? speciesRepository.findByNameMatching(SearchPattern.accentInsensitive(search.trim()), pageable)
                : speciesRepository.findAll(pageable);
        return species.map(this::toResponse);
    }

    @Override
    public SpeciesFacetsResponseDto getFacets(SpeciesFilter filter, Locale locale) {
        String regex = nameRegex(filter.search());
        Set<UUID> seenIds = seenIds(filter);
        Boolean seen = seenFlag(filter);
        // Each menu counts under every filter except its own, so the choices a user can still make show their numbers.
        List<SpeciesSearchRepository.FacetRow> forOrders = speciesRepository.facetRows(regex, null, filter.families(), seenIds, seen);
        List<SpeciesSearchRepository.FacetRow> forFamilies = speciesRepository.facetRows(regex, filter.orders(), null, seenIds, seen);

        Map<String, long[]> orderCounts = new LinkedHashMap<>();
        Map<String, String> orderValues = new HashMap<>();
        for (SpeciesSearchRepository.FacetRow row : forOrders) {
            if (StringUtils.hasText(row.order())) {
                String key = key(row.order());
                orderValues.putIfAbsent(key, tidy(row.order()));
                orderCounts.computeIfAbsent(key, k -> new long[1])[0] += row.count();
            }
        }
        Map<String, long[]> familyCounts = new LinkedHashMap<>();
        Map<String, String[]> familyValues = new HashMap<>();
        for (SpeciesSearchRepository.FacetRow row : forFamilies) {
            if (StringUtils.hasText(row.family()) && StringUtils.hasText(row.order())) {
                String key = key(row.order()) + "|" + key(row.family());
                familyValues.putIfAbsent(key, new String[] {tidy(row.family()), tidy(row.order())});
                familyCounts.computeIfAbsent(key, k -> new long[1])[0] += row.count();
            }
        }

        addSelectedWithoutSpecies(filter, orderCounts, orderValues, familyCounts, familyValues);

        List<SpeciesFacetsResponseDto.OrderFacet> orders = orderCounts.entrySet().stream()
                .map(entry -> new SpeciesFacetsResponseDto.OrderFacet(orderValues.get(entry.getKey()),
                        taxonNames.order(orderValues.get(entry.getKey()), locale), entry.getValue()[0]))
                .sorted(Comparator.comparingLong(SpeciesFacetsResponseDto.OrderFacet::count).reversed()
                        .thenComparing(SpeciesFacetsResponseDto.OrderFacet::name))
                .toList();
        List<SpeciesFacetsResponseDto.FamilyFacet> families = familyCounts.entrySet().stream()
                .map(entry -> {
                    String[] value = familyValues.get(entry.getKey());
                    return new SpeciesFacetsResponseDto.FamilyFacet(value[0], value[1], taxonNames.family(value[0], locale), entry.getValue()[0]);
                })
                .sorted(Comparator.comparingLong(SpeciesFacetsResponseDto.FamilyFacet::count).reversed()
                        .thenComparing(SpeciesFacetsResponseDto.FamilyFacet::name))
                .toList();
        return new SpeciesFacetsResponseDto(orders, families);
    }

    /**
     * A selected order or family that has no species under the other filters is still listed, with count 0, so a menu never
     * drops a choice the user made. A selected family's order is looked up among all species; an unknown family is skipped.
     */
    private void addSelectedWithoutSpecies(SpeciesFilter filter, Map<String, long[]> orderCounts, Map<String, String> orderValues,
                                           Map<String, long[]> familyCounts, Map<String, String[]> familyValues) {
        if (filter.orders() != null) {
            for (String selected : filter.orders()) {
                if (StringUtils.hasText(selected) && !orderCounts.containsKey(key(selected))) {
                    orderValues.put(key(selected), tidy(selected));
                    orderCounts.put(key(selected), new long[1]);
                }
            }
        }
        if (filter.families() == null) {
            return;
        }
        Map<String, String[]> known = null;
        for (String selected : filter.families()) {
            if (!StringUtils.hasText(selected) || familyCounts.keySet().stream().anyMatch(k -> k.endsWith("|" + key(selected)))) {
                continue;
            }
            if (known == null) {
                known = new HashMap<>();
                for (SpeciesSearchRepository.FacetRow row : speciesRepository.facetRows(null, null, null, null, null)) {
                    if (StringUtils.hasText(row.family()) && StringUtils.hasText(row.order())) {
                        known.putIfAbsent(key(row.family()), new String[] {tidy(row.family()), tidy(row.order())});
                    }
                }
            }
            String[] value = known.get(key(selected));
            if (value != null) {
                String familyKey = key(value[1]) + "|" + key(value[0]);
                familyValues.putIfAbsent(familyKey, value);
                familyCounts.putIfAbsent(familyKey, new long[1]);
            }
        }
    }

    private static String nameRegex(String search) {
        return StringUtils.hasText(search) ? SearchPattern.accentInsensitive(search.trim()) : null;
    }

    private static Boolean seenFlag(SpeciesFilter filter) {
        return filter.seen() == null ? null : filter.seen() == SeenFilter.SEEN;
    }

    /** Returns the ids of the species the user has logged (pet logs included), or null when no seen filter is set. */
    private Set<UUID> seenIds(SpeciesFilter filter) {
        if (filter.seen() == null) {
            return null;
        }
        if (filter.userId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "Sign in to filter by seen or unseen species");
        }
        Set<UUID> ids = new HashSet<>();
        birdLogRepository.findByUserIdAndSpeciesIdIsNotNull(filter.userId()).forEach(birdLog -> ids.add(birdLog.getSpeciesId()));
        return ids;
    }

    /** Folds case and spaces so "Passeriformes" and "passeriformes " count as one. */
    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns the name trimmed and written as a Latin taxon name: first letter upper case, the rest lower case. */
    private static String tidy(String value) {
        String trimmed = value.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.ROOT) + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    public SpeciesResponseDto getById(UUID id) {
        return toResponse(findSpecies(id));
    }

    @Override
    public SpeciesResponseDto create(CreateSpeciesRequestDto request) {
        validateCommonName(request.commonName());

        if (speciesRepository.existsByScientificNameIgnoreCase(request.scientificName())) {
            throw new DuplicateResourceException("A species with this scientific name already exists");
        }

        Species species = Species.builder()
                .commonName(request.commonName())
                .scientificName(request.scientificName())
                .family(request.family())
                .order(request.order())
                .description(request.description())
                .lifespan(request.lifespan())
                .diet(request.diet())
                .habitat(request.habitat())
                .sizeDescription(request.sizeDescription())
                .conservationStatus(request.conservationStatus())
                .nativeRange(request.nativeRange())
                .build();

        return toResponse(speciesRepository.save(species));
    }

    @Override
    public SpeciesResponseDto update(UUID id, UpdateSpeciesRequestDto request) {
        validateCommonName(request.commonName());

        Species species = findSpecies(id);

        if (!species.getScientificName().equalsIgnoreCase(request.scientificName())
                && speciesRepository.existsByScientificNameIgnoreCase(request.scientificName())) {
            throw new DuplicateResourceException("A species with this scientific name already exists");
        }

        species.setCommonName(request.commonName());
        species.setScientificName(request.scientificName());
        species.setFamily(request.family());
        species.setOrder(request.order());
        species.setDescription(request.description());
        species.setLifespan(request.lifespan());
        species.setDiet(request.diet());
        species.setHabitat(request.habitat());
        species.setSizeDescription(request.sizeDescription());
        species.setConservationStatus(request.conservationStatus());
        species.setNativeRange(request.nativeRange());

        return toResponse(speciesRepository.save(species));
    }

    @Override
    public void delete(UUID id) {
        if (!speciesRepository.existsById(id)) {
            throw ResourceNotFoundException.of("Species", id);
        }
        List<String> imageUrls = speciesImageRepository.findBySpeciesId(id).stream().map(SpeciesImage::getImageUrl).toList();
        speciesImageRepository.deleteBySpeciesId(id);
        speciesRepository.deleteById(id);
        imageUrls.forEach(uploadedFileCleaner::deleteIfUnreferenced);
    }

    @Override
    public SpeciesImageResponseDto addImage(UUID speciesId, CreateSpeciesImageRequestDto request) {
        if (!speciesRepository.existsById(speciesId)) {
            throw ResourceNotFoundException.of("Species", speciesId);
        }

        // A Commons file: the licence, credit line and source page come from Commons itself, never from the request.
        CommonsService.CommonsFile commons = StringUtils.hasText(request.commonsFileUrl())
                ? commonsService.describe(request.commonsFileUrl()) : null;

        SpeciesImage image = SpeciesImage.builder()
                .speciesId(speciesId)
                .lifeStage(request.lifeStage())
                .gender(request.gender())
                .imageUrl(request.imageUrl())
                .caption(request.caption())
                .licenseCode(commons != null ? commons.licenseCode() : request.licenseCode())
                .attribution(commons != null ? commons.attribution() : request.attribution())
                .sourceUrl(commons != null ? commons.sourceUrl() : request.sourceUrl())
                .build();

        image = speciesImageRepository.save(image);
        return toImageResponse(image);
    }

    @Override
    public void deleteImage(UUID speciesId, UUID imageId) {
        SpeciesImage image = speciesImageRepository.findById(imageId)
                .orElseThrow(() -> ResourceNotFoundException.of("SpeciesImage", imageId));

        if (!image.getSpeciesId().equals(speciesId)) {
            throw ResourceNotFoundException.of("SpeciesImage", imageId);
        }

        speciesImageRepository.delete(image);
        uploadedFileCleaner.deleteIfUnreferenced(image.getImageUrl());
    }

    private void validateCommonName(Map<String, String> commonName) {
        if (!StringUtils.hasText(commonName.get("en"))) {
            throw new IllegalArgumentException("commonName must include a non-blank 'en' translation");
        }
    }

    private Species findSpecies(UUID id) {
        return speciesRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Species", id));
    }

    private SpeciesResponseDto toResponse(Species species) {
        List<SpeciesImageResponseDto> images = speciesImageRepository.findBySpeciesId(species.getId()).stream()
                .map(this::toImageResponse)
                .toList();

        return new SpeciesResponseDto(
                species.getId(),
                species.getCommonName(),
                species.getScientificName(),
                species.getFamily(),
                species.getOrder(),
                species.getDescription(),
                species.getLifespan(),
                species.getDiet(),
                species.getHabitat(),
                species.getSizeDescription(),
                species.getConservationStatus(),
                species.getNativeRange(),
                images
        );
    }

    private SpeciesImageResponseDto toImageResponse(SpeciesImage image) {
        return new SpeciesImageResponseDto(
                image.getId(),
                image.getLifeStage(),
                image.getGender(),
                image.getImageUrl(),
                fileStorageService.thumbnailUrl(image.getImageUrl()).orElse(null),
                image.getCaption(),
                image.getLicenseCode(),
                image.getAttribution(),
                image.getSourceUrl()
        );
    }
}
