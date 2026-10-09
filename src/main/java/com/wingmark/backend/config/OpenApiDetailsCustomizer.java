package com.wingmark.backend.config;

import com.wingmark.backend.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Completes the generated OpenAPI document: marks public endpoints as not needing a token, documents the real
 * success and error status codes with the shared error body, and fills in parameter and field descriptions.
 */
@Component
public class OpenApiDetailsCustomizer implements OpenApiCustomizer {

    /** Endpoints reachable without a token, as {@code METHOD path}; mirrors {@code SecurityConfig}. */
    static final Set<String> PUBLIC_OPERATIONS = Set.of(
            "POST /api/auth/register", "POST /api/auth/login", "POST /api/auth/refresh", "POST /api/auth/logout",
            "POST /api/auth/forgot-password", "POST /api/auth/reset-password",
            "POST /api/auth/resend-verification-email", "GET /api/auth/verify-email",
            "GET /api/species", "GET /api/species/{id}", "GET /api/species/{id}/sound",
            "GET /api/badges/catalog", "GET /api/avatars", "GET /api/legal", "GET /uploads/{filename}");

    /** Success status for operations that do not return 200 and are not a DELETE (which returns 204). */
    private static final Map<String, Integer> SUCCESS_STATUS = Map.ofEntries(
            Map.entry("POST /api/auth/register", 201),
            Map.entry("POST /api/auth/logout", 204),
            Map.entry("POST /api/auth/logout-all", 204),
            Map.entry("POST /api/auth/forgot-password", 202),
            Map.entry("POST /api/auth/reset-password", 204),
            Map.entry("POST /api/auth/resend-verification-email", 202),
            Map.entry("POST /api/badges", 201),
            Map.entry("POST /api/species", 201),
            Map.entry("POST /api/species/{id}/images", 201),
            Map.entry("POST /api/bird-logs", 201),
            Map.entry("POST /api/uploads/photo", 201));

    private static final Map<Integer, String> STATUS_TEXT = Map.ofEntries(
            Map.entry(200, "OK"), Map.entry(201, "Created"), Map.entry(202, "Accepted"), Map.entry(204, "No content"),
            Map.entry(400, "Invalid request: validation failed, malformed body or bad parameter (codes VALIDATION_FAILED, "
                    + "MALFORMED_REQUEST, INVALID_PARAMETER, BAD_REQUEST, AGE_NOT_CONFIRMED and operation-specific ones)"),
            Map.entry(401, "Missing, invalid or expired token (UNAUTHENTICATED, INVALID_CREDENTIALS, INVALID_OR_EXPIRED_TOKEN)"),
            Map.entry(403, "Not allowed (FORBIDDEN, EMAIL_NOT_VERIFIED, WRONG_PASSWORD, CANNOT_MODIFY_SELF, PHOTO_QUOTA_EXCEEDED)"),
            Map.entry(404, "Not found, or not the caller's own resource (NOT_FOUND)"),
            Map.entry(409, "Conflict with existing data (EMAIL_TAKEN, USERNAME_TAKEN, LAST_ADMIN, CONFLICT)"),
            Map.entry(413, "Body or file too large (REQUEST_TOO_LARGE, FILE_TOO_LARGE)"),
            Map.entry(415, "Unsupported media type (UNSUPPORTED_MEDIA_TYPE)"),
            Map.entry(422, "A referenced resource does not exist (INVALID_REFERENCE)"),
            Map.entry(429, "Rate limit exceeded (RATE_LIMITED); wait the number of seconds in the Retry-After header"),
            Map.entry(502, "An external service did not answer (EXTERNAL_SERVICE_ERROR)"));

    /** Extra error statuses per operation, on top of the rules in {@link #errorStatuses}. */
    private static final Map<String, List<Integer>> EXTRA_ERRORS = Map.ofEntries(
            Map.entry("POST /api/auth/register", List.of(409, 429)),
            Map.entry("POST /api/auth/login", List.of(401, 403, 429)),
            Map.entry("POST /api/auth/refresh", List.of(401, 429)),
            Map.entry("POST /api/auth/forgot-password", List.of(429)),
            Map.entry("POST /api/auth/reset-password", List.of(429)),
            Map.entry("POST /api/auth/resend-verification-email", List.of(429)),
            Map.entry("POST /api/users/{userId}/password", List.of(403, 429)),
            Map.entry("DELETE /api/users/{userId}", List.of(403, 409, 429)),
            Map.entry("PUT /api/users/{userId}", List.of(422)),
            Map.entry("PUT /api/admin/users/{id}", List.of(403, 422)),
            Map.entry("DELETE /api/admin/users/{id}", List.of(403, 409)),
            Map.entry("POST /api/bird-logs", List.of(422)),
            Map.entry("PUT /api/bird-logs/{id}", List.of(422)),
            Map.entry("POST /api/species", List.of(409)),
            Map.entry("PUT /api/species/{id}", List.of(409)),
            Map.entry("POST /api/uploads/photo", List.of(403, 413, 415)),
            Map.entry("GET /api/species/{id}/sound", List.of(502)));

    private static final Map<String, String> PARAMETERS = Map.ofEntries(
            Map.entry("userId", "Id of the user. Must be the caller's own id; any other id gives 404, except for admins."),
            Map.entry("imageId", "Id of the species reference image."),
            Map.entry("token", "Verification token from the link in the verification email."),
            Map.entry("filename", "Stored upload filename, as in the /uploads/ URL returned on upload."),
            Map.entry("hasSpecies", "true: only logs with a species; false: only logs without; omitted: both."),
            Map.entry("gender", "Filter by gender; omitted: all."),
            Map.entry("lifeStage", "Filter by life stage; omitted: all."),
            Map.entry("sortDirection", "Sort by observedAt: ASC or DESC (default DESC, newest first)."),
            Map.entry("minLat", "Southern edge of the visible map area, in degrees (-90 to 90)."),
            Map.entry("maxLat", "Northern edge of the visible map area, in degrees (-90 to 90)."),
            Map.entry("minLng", "Western edge of the visible map area, in degrees. May be greater than maxLng when the area crosses the antimeridian."),
            Map.entry("maxLng", "Eastern edge of the visible map area, in degrees."),
            Map.entry("limit", "Maximum number of logs to return; X-Result-Truncated says if more matched."),
            Map.entry("search", "Text matched literally, ignoring case and accents, against the common name (English or Turkish) or the scientific name."),
            Map.entry("pageable", "Paging and sorting: page (from 0), size, and sort=<field>,<asc|desc> on commonName.en, commonName.tr or scientificName."));

    @Override
    public void customise(io.swagger.v3.oas.models.OpenAPI openApi) {
        if (openApi.getComponents() != null) {
            ModelConverters.getInstance().readAll(ErrorResponse.class).forEach((name, schema) ->
                    openApi.getComponents().addSchemas(name, schema));
            describeFields(openApi);
        }
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            String key = method + " " + path;
            boolean secured = !PUBLIC_OPERATIONS.contains(key);
            if (!secured) {
                operation.setSecurity(new ArrayList<>());
            }
            applySuccessStatus(key, method, operation);
            applyErrorStatuses(key, path, method, operation, secured);
            describeParameters(path, operation);
        }));
    }

    private static void applySuccessStatus(String key, PathItem.HttpMethod method, Operation operation) {
        int status = method == PathItem.HttpMethod.DELETE ? 204 : SUCCESS_STATUS.getOrDefault(key, 200);
        ApiResponses responses = operation.getResponses();
        if (status == 200 || responses == null) {
            return;
        }
        ApiResponse success = responses.remove("200");
        ApiResponse documented = success == null ? new ApiResponse() : success;
        documented.setDescription(STATUS_TEXT.get(status));
        if (status == 204 || status == 202) {
            documented.setContent(null);
        }
        responses.addApiResponse(String.valueOf(status), documented);
    }

    /** Returns the error statuses an operation can answer with. */
    static Set<Integer> errorStatuses(String key, String path, PathItem.HttpMethod method, Operation operation, boolean secured) {
        Set<Integer> statuses = new TreeSet<>();
        boolean hasBody = operation.getRequestBody() != null;
        boolean hasParameters = operation.getParameters() != null && !operation.getParameters().isEmpty();
        if (hasBody || hasParameters) {
            statuses.add(400);
        }
        if (hasBody) {
            statuses.add(413);
        }
        if (secured) {
            statuses.add(401);
        }
        boolean adminOnly = path.startsWith("/api/admin")
                || (secured && (path.startsWith("/api/badges") || path.startsWith("/api/species"))
                && method != PathItem.HttpMethod.GET);
        if (adminOnly) {
            statuses.add(403);
        }
        if (path.contains("{")) {
            statuses.add(404);
        }
        statuses.addAll(EXTRA_ERRORS.getOrDefault(key, List.of()));
        return statuses;
    }

    private static void applyErrorStatuses(String key, String path, PathItem.HttpMethod method, Operation operation, boolean secured) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        Schema<?> errorBody = new Schema<>().$ref("#/components/schemas/ErrorResponse");
        for (int status : errorStatuses(key, path, method, operation, secured)) {
            String code = String.valueOf(status);
            if (operation.getResponses().containsKey(code)) {
                continue;
            }
            operation.getResponses().addApiResponse(code, new ApiResponse()
                    .description(STATUS_TEXT.get(status))
                    .content(new Content().addMediaType("application/json", new MediaType().schema(errorBody))));
        }
    }

    private static void describeParameters(String path, Operation operation) {
        if (operation.getParameters() == null) {
            return;
        }
        for (Parameter parameter : operation.getParameters()) {
            if (parameter.getDescription() != null && !parameter.getDescription().isBlank()) {
                continue;
            }
            String description = PARAMETERS.get(parameter.getName());
            if ("id".equals(parameter.getName())) {
                description = idDescription(path);
            }
            if (description != null) {
                parameter.setDescription(description);
            }
        }
    }

    private static String idDescription(String path) {
        if (path.startsWith("/api/badges")) {
            return "Id of the badge.";
        }
        if (path.startsWith("/api/species")) {
            return "Id of the species.";
        }
        if (path.startsWith("/api/bird-logs")) {
            return "Id of the bird log; it must be the caller's own.";
        }
        if (path.startsWith("/api/admin/users")) {
            return "Id of the user.";
        }
        return "Id of the resource.";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void describeFields(io.swagger.v3.oas.models.OpenAPI openApi) {
        Map<String, Schema> schemas = new LinkedHashMap<>(openApi.getComponents().getSchemas());
        schemas.forEach((schemaName, schema) -> {
            Map<String, Schema> properties = schema.getProperties();
            if (properties == null) {
                return;
            }
            properties.forEach((field, property) -> {
                ApiFieldDocs.Doc doc = ApiFieldDocs.of(schemaName, field);
                if (doc == null) {
                    return;
                }
                if (property.get$ref() != null) {
                    // OpenAPI 3.0 ignores a description next to a $ref, so wrap the reference.
                    Schema wrapped = new Schema<>().addAllOfItem(new Schema<>().$ref(property.get$ref()));
                    wrapped.setDescription(doc.description());
                    properties.put(field, wrapped);
                    return;
                }
                if (property.getDescription() == null || property.getDescription().isBlank()) {
                    property.setDescription(doc.description());
                }
                if (property.getExample() == null && doc.example() != null && property.get$ref() == null
                        && property.getEnum() == null && isScalarText(property)) {
                    property.setExample(doc.example());
                }
            });
        });
    }

    private static boolean isScalarText(Schema<?> property) {
        return "string".equals(property.getType());
    }
}
