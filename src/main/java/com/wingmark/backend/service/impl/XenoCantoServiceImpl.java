package com.wingmark.backend.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wingmark.backend.dto.species.SpeciesRecordingResponse;
import com.wingmark.backend.exception.ExternalServiceException;
import com.wingmark.backend.service.XenoCantoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class XenoCantoServiceImpl implements XenoCantoService {

    private final WebClient xenoCantoWebClient;

    @Override
    public List<SpeciesRecordingResponse> findRecordings(String scientificName) {
        try {
            XenoCantoApiResponse response = xenoCantoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/recordings")
                            .queryParam("query", scientificName)
                            .build())
                    .retrieve()
                    .bodyToMono(XenoCantoApiResponse.class)
                    .timeout(Duration.ofSeconds(8))
                    .onErrorMap(WebClientException.class,
                            ex -> new ExternalServiceException("Failed to reach the bird sound service", ex))
                    .block();

            if (response == null || response.recordings() == null) {
                return List.of();
            }

            return response.recordings().stream()
                    .map(this::toResponse)
                    .toList();
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error fetching recordings for {}", scientificName, ex);
            throw new ExternalServiceException("Failed to reach the bird sound service", ex);
        }
    }

    private SpeciesRecordingResponse toResponse(XenoCantoRecording recording) {
        return new SpeciesRecordingResponse(
                recording.id(),
                normalizeUrl(recording.file()),
                recording.type(),
                recording.q(),
                recording.rec(),
                normalizeUrl(recording.lic())
        );
    }

    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.startsWith("//") ? "https:" + url : url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record XenoCantoApiResponse(List<XenoCantoRecording> recordings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record XenoCantoRecording(String id, String file, String type, String q, String rec, String lic) {
    }
}
