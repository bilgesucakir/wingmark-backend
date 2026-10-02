package com.wingmark.backend.service.impl;

import com.wingmark.backend.dto.species.PhotoCandidateDto;
import com.wingmark.backend.enums.ImageGender;
import com.wingmark.backend.enums.LifeStageImage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class INaturalistServiceImplTest {

    private static INaturalistServiceImpl serviceReturning(String json) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(json)
                        .build()))
                .build();
        return new INaturalistServiceImpl(webClient);
    }

    @Test
    void onlyOpenlyLicensedPhotosAreOfferedAndTheirLicenseIsPassedThrough() {
        String json = """
                {"results": [{"id": 1, "uri": "https://www.inaturalist.org/observations/1", "photos": [
                  {"id": 10, "url": "https://img/square.jpg", "attribution": "(c) Jane, some rights reserved (CC BY)", "license_code": "cc-by", "hidden": false},
                  {"id": 11, "url": "https://img/square2.jpg", "attribution": "(c) Joe, all rights reserved", "license_code": null, "hidden": false},
                  {"id": 12, "url": "https://img/square3.jpg", "attribution": "(c) Ann", "license_code": "", "hidden": false}
                ]}]}
                """;

        List<PhotoCandidateDto> candidates = serviceReturning(json)
                .findPhotoCandidates("Forpus coelestis", LifeStageImage.ADULT, ImageGender.NOT_APPLICABLE);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).licenseCode()).isEqualTo("cc-by");
        assertThat(candidates.get(0).attribution()).contains("Jane");
        assertThat(candidates.get(0).observationUrl()).isEqualTo("https://www.inaturalist.org/observations/1");
    }
}
