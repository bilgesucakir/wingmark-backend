package com.wingmark.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wingmark.backend.exception.BadRequestException;
import com.wingmark.backend.exception.ErrorCode;
import com.wingmark.backend.exception.ExternalServiceException;
import com.wingmark.backend.service.CommonsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Looks a file up on Wikimedia Commons and returns its licence, author and page address. Only licences that allow free
 * reuse with credit are accepted; non-commercial, no-derivatives and fair-use files are refused.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommonsServiceImpl implements CommonsService {

    private static final Set<String> HOSTS = Set.of("commons.wikimedia.org", "commons.m.wikimedia.org");
    /** CC BY or CC BY-SA, with an optional version and jurisdiction; "-NC" or "-ND" after BY does not match. */
    private static final Pattern CC_BY = Pattern.compile("^CC BY(-SA)?( [0-9]+\\.[0-9]+)?( [A-Z]{2,3})?$");
    private static final Pattern PUBLIC_DOMAIN = Pattern.compile("^(PUBLIC DOMAIN|PD([- ].*)?|CC0([ -].*)?|CC-ZERO)$");
    private static final int MAX_ATTRIBUTION = 300;

    private final WebClient commonsWebClient;

    @Override
    public CommonsFile describe(String fileUrl) {
        String title = fileTitle(fileUrl);
        JsonNode info;
        try {
            JsonNode response = commonsWebClient.get()
                    .uri(uri -> uri.path("/w/api.php")
                            .queryParam("action", "query").queryParam("titles", title)
                            .queryParam("prop", "imageinfo").queryParam("iiprop", "extmetadata")
                            .queryParam("format", "json").queryParam("formatversion", 2).build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofSeconds(8))
                    .onErrorMap(WebClientException.class, ex -> new ExternalServiceException("Failed to reach Wikimedia Commons", ex))
                    .block();
            info = response == null ? null : response.path("query").path("pages").path(0);
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Unexpected error reading Commons file {}", title, ex);
            throw new ExternalServiceException("Failed to reach Wikimedia Commons", ex);
        }
        if (info == null || info.path("missing").asBoolean(false) || info.path("imageinfo").path(0).isMissingNode()) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "That file was not found on Wikimedia Commons: " + title);
        }

        JsonNode meta = info.path("imageinfo").path(0).path("extmetadata");
        String license = meta.path("LicenseShortName").path("value").asText("").trim();
        if (!isFree(license)) {
            throw new BadRequestException(ErrorCode.BAD_REQUEST, "This file's licence ("
                    + (license.isBlank() ? "unknown" : license) + ") cannot be used: only public domain, CC0, CC BY and CC BY-SA are accepted");
        }
        String author = plainText(meta.path("Artist").path("value").asText(""));
        return new CommonsFile(licenseCode(license), attribution(author, license),
                "https://commons.wikimedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8).replace("%3A", ":").replace("+", "%20"));
    }

    /** Extracts {@code File:Name.jpg} from a Commons page address; anything else is refused. */
    static String fileTitle(String fileUrl) {
        URI uri;
        try {
            uri = URI.create(fileUrl == null ? "" : fileUrl.trim());
        } catch (IllegalArgumentException e) {
            throw notACommonsFile();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || !HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
            throw notACommonsFile();
        }
        String title = null;
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (path.startsWith("/wiki/")) {
            title = URLDecoder.decode(path.substring("/wiki/".length()), StandardCharsets.UTF_8);
        } else if (uri.getRawQuery() != null) {
            for (String pair : uri.getRawQuery().split("&")) {
                if (pair.startsWith("title=")) {
                    title = URLDecoder.decode(pair.substring("title=".length()), StandardCharsets.UTF_8);
                }
            }
        }
        if (title == null || !title.regionMatches(true, 0, "File:", 0, 5) || title.length() <= 5) {
            throw notACommonsFile();
        }
        return "File:" + title.substring(5).replace('_', ' ').trim();
    }

    private static BadRequestException notACommonsFile() {
        return new BadRequestException(ErrorCode.BAD_REQUEST,
                "Enter the Commons file page address, like https://commons.wikimedia.org/wiki/File:Example.jpg");
    }

    static boolean isFree(String license) {
        String normalized = license.trim().toUpperCase(Locale.ROOT);
        return CC_BY.matcher(normalized).matches() || PUBLIC_DOMAIN.matcher(normalized).matches();
    }

    static String licenseCode(String license) {
        return license.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
    }

    private static boolean isPublicDomain(String license) {
        return PUBLIC_DOMAIN.matcher(license.trim().toUpperCase(Locale.ROOT)).matches();
    }

    /** Builds the credit line shown under the image, e.g. "Jane Doe / Wikimedia Commons, CC BY-SA 4.0". */
    static String attribution(String author, String license) {
        String source = author.isBlank() ? "Wikimedia Commons" : author + " / Wikimedia Commons";
        String credit = isPublicDomain(license) ? source + " (public domain)" : source + ", " + license.trim();
        return credit.length() > MAX_ATTRIBUTION ? credit.substring(0, MAX_ATTRIBUTION - 1) + "…" : credit;
    }

    /** Commons returns the author as HTML (often a link); this keeps just the text. */
    static String plainText(String html) {
        return html.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'")
                .replaceAll("\\s+", " ").trim();
    }
}
