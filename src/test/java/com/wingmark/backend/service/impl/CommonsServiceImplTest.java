package com.wingmark.backend.service.impl;

import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ExternalServiceException;
import com.wingmark.backend.service.CommonsService.CommonsFile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Commons API is replaced by canned answers, so nothing here reaches the real site. */
class CommonsServiceImplTest {

    private static final String PAGE = "https://commons.wikimedia.org/wiki/File:Pacific_parrotlet.jpg";

    private static CommonsServiceImpl returning(HttpStatus status, String json) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(status)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(json).build()))
                .build();
        return new CommonsServiceImpl(webClient);
    }

    private static String page(String license, String artist) {
        return "{\"query\":{\"pages\":[{\"title\":\"File:Pacific parrotlet.jpg\",\"imageinfo\":[{\"extmetadata\":{"
                + "\"LicenseShortName\":{\"value\":\"" + license + "\"},"
                + "\"Artist\":{\"value\":\"" + artist + "\"}}}]}]}}";
    }

    @Test
    void aShareAlikeFileGetsItsLicenceCodeAPlainTextCreditAndTheNormalisedPageAddress() {
        CommonsFile file = returning(HttpStatus.OK, page("CC BY-SA 4.0", "<a href=\\\"//commons.wikimedia.org/wiki/User:Jane\\\">Jane &amp; Co</a>")).describe(PAGE);

        assertThat(file.licenseCode()).isEqualTo("cc-by-sa-4.0");
        assertThat(file.attribution()).isEqualTo("Jane & Co / Wikimedia Commons, CC BY-SA 4.0");
        assertThat(file.sourceUrl()).isEqualTo("https://commons.wikimedia.org/wiki/File:Pacific_parrotlet.jpg");
    }

    @Test
    void publicDomainAndCc0FilesAreAcceptedAndNeedNoLicenceNameInTheCredit() {
        CommonsFile pd = returning(HttpStatus.OK, page("Public domain", "Unknown")).describe(PAGE);
        CommonsFile cc0 = returning(HttpStatus.OK, page("CC0", "")).describe(PAGE);

        assertThat(pd.licenseCode()).isEqualTo("public-domain");
        assertThat(pd.attribution()).isEqualTo("Unknown / Wikimedia Commons (public domain)");
        assertThat(cc0.licenseCode()).isEqualTo("cc0");
        assertThat(cc0.attribution()).isEqualTo("Wikimedia Commons (public domain)");
    }

    @Test
    void plainCcByIsAccepted() {
        assertThat(returning(HttpStatus.OK, page("CC BY 2.0", "Ann")).describe(PAGE).licenseCode()).isEqualTo("cc-by-2.0");
    }

    @Test
    void nonCommercialNoDerivativesAndUnknownLicencesAreRefusedNamingTheLicence() {
        for (String license : new String[]{"CC BY-NC 4.0", "CC BY-NC-SA 3.0", "CC BY-ND 2.0", "CC BY-NC-ND 4.0", "Fair use", "All rights reserved", ""}) {
            assertThatThrownBy(() -> returning(HttpStatus.OK, page(license, "Ann")).describe(PAGE))
                    .as(license).isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot be used");
        }
    }

    @Test
    void aFileThatDoesNotExistOnCommonsIsABadRequest() {
        assertThatThrownBy(() -> returning(HttpStatus.OK, "{\"query\":{\"pages\":[{\"title\":\"File:Nope.jpg\",\"missing\":true}]}}").describe(PAGE))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("not found");
    }

    @Test
    void aFailingCommonsIsReportedAsAnExternalServiceError() {
        assertThatThrownBy(() -> returning(HttpStatus.SERVICE_UNAVAILABLE, "{}").describe(PAGE))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void onlyCommonsFilePageAddressesAreAccepted() {
        for (String bad : new String[]{null, "", "not a url", "http://commons.wikimedia.org/wiki/File:A.jpg",
                "https://example.com/wiki/File:A.jpg", "https://commons.wikimedia.org/wiki/Category:Birds",
                "https://commons.wikimedia.org/wiki/File:", "https://en.wikipedia.org/wiki/File:A.jpg"}) {
            assertThatThrownBy(() -> CommonsServiceImpl.fileTitle(bad)).as(String.valueOf(bad)).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void theFileTitleIsReadFromTheCommonAddressForms() {
        assertThat(CommonsServiceImpl.fileTitle("https://commons.wikimedia.org/wiki/File:Pacific_parrotlet.jpg")).isEqualTo("File:Pacific parrotlet.jpg");
        assertThat(CommonsServiceImpl.fileTitle("https://commons.wikimedia.org/wiki/File:Caf%C3%A9_bird.jpg")).isEqualTo("File:Caf\u00e9 bird.jpg");
        assertThat(CommonsServiceImpl.fileTitle("https://commons.m.wikimedia.org/wiki/File:A.jpg")).isEqualTo("File:A.jpg");
        assertThat(CommonsServiceImpl.fileTitle("https://commons.wikimedia.org/w/index.php?title=File:B_c.png")).isEqualTo("File:B c.png");
    }

    @Test
    void theCreditLineIsCappedInLength() {
        assertThat(CommonsServiceImpl.attribution("x".repeat(500), "CC BY 4.0").length()).isEqualTo(300);
    }
}
