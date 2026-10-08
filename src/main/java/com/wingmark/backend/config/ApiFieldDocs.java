package com.wingmark.backend.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Descriptions and examples for the API's request and response fields, shown in Swagger. Entries are
 * {@code field|description|example}; a {@code Schema.field} key overrides the general entry for one schema.
 */
final class ApiFieldDocs {

    /** A field description with an optional example. */
    record Doc(String description, String example) {
    }

    private static final String SAMPLE_ID = "3f6c0a34-8d1e-4b7a-9c2f-5a1d6e7b8c90";

    private static final String TABLE = """
            email|Account email address.|amelia@example.com
            password|Account password: 10-72 characters with at least one letter and one number.|
            currentPassword|The account's current password.|
            newPassword|New password: 10-72 characters with at least one letter and one number, different from the current one.|
            username|Username, 3-30 characters.|amelia_birds
            firstName|Given name (optional).|Amelia
            lastName|Family name (optional).|Birdwell
            acceptedTermsVersion|Terms of Service version the user accepted. Required once a terms version is published (see GET /api/legal).|2026-10-03
            acceptedPrivacyVersion|Privacy Policy version the user accepted. Required once a privacy version is published (see GET /api/legal).|2026-10-03
            accessToken|Short-lived JWT (15 minutes) for the Authorization: Bearer header.|
            refreshToken|Long-lived rotating token used to get a new token pair; each one works once.|
            expiresInMs|Lifetime of the access token in milliseconds.|900000
            pendingConsents|Legal documents whose current version the user has not accepted yet; empty when none.|
            confirmedAge13|Must be true: confirms the user is at least the minimum age (13). Missing or false gives 400 AGE_NOT_CONFIRMED.|true
            minimumAge|Minimum age to sign up; the age confirmation is recorded as a consent of type AGE whose version is this number.|13
            role|Account role: USER or ADMIN.|USER
            emailVerified|Whether the email address has been verified.|true
            favoriteSpeciesId|Id of the user's favorite species, or null.|%1$s
            favoriteSpeciesName|Name of the favorite species in the request locale, or null.|House Sparrow
            profilePicture|Profile picture: a preset avatar key (GET /api/avatars), an /uploads/... URL from POST /api/uploads/photo, or null.|avatar-1
            createdAt|When the record was created (UTC).|2026-10-03T08:15:30Z
            observedAt|When the bird was seen (UTC). Defaults to now; must not be in the future.|2026-10-03T08:15:30Z
            id|Unique id (UUID).|%1$s
            userId|Id of the user (UUID).|%1$s
            key|Preset avatar key; the images ship inside the iOS app.|avatar-1
            message|Human-readable message.|
            name|Name by locale code.|
            description|Description by locale code.|
            icon|Emoji or icon identifier shown for the badge.|🐦
            criteriaType|What the badge counts: TOTAL_LOGS, UNIQUE_SPECIES, BABY_LOGS, UNKNOWN_SPECIES_LOGS, PET_LOGS, SPECIES_IN_RADIUS, SIGHTINGS_IN_RADIUS, SPECIES_LOGS, SAME_GENUS_SPECIES or FAVORITE_SPECIES_LOGS.|TOTAL_LOGS
            criteriaValue|Target the progress must reach to earn the badge (at least 1).|10
            criteriaMetadata|Optional parameters for some types: radiusMeters (*_IN_RADIUS, default 5000), speciesId (SPECIES_LOGS), genus (SAME_GENUS_SPECIES, must be a non-blank string when given).|
            tier|Badge tier: BRONZE, SILVER or GOLD.|BRONZE
            displayOrder|Position in the badge lists, lowest first; badges without one come last.|1
            badgeId|Id of the badge (UUID).|%1$s
            badgeName|Badge name in the request locale.|First Sighting
            badgeIcon|Badge icon.|🐦
            earned|Whether the user has earned the badge.|false
            earnedAt|When the badge was earned (UTC), or null.|2026-10-03T08:15:30Z
            progress|The user's current progress toward the target.|3
            targetValue|Progress needed to earn the badge.|10
            speciesId|Id of the species (UUID), or null if the bird was not identified.|%1$s
            speciesCommonName|Species common name in the request locale, or null.|House Sparrow
            speciesStatus|How sure the user is about the species: GUESS or CONFIDENT.|CONFIDENT
            pet|Whether the bird is a pet. Pet logs count in the badges like any other.|false
            customName|Optional name the user gave the bird.|Kiwi
            lifeStage|Life stage: BABY, ADULT or UNKNOWN.|ADULT
            gender|Gender: MALE, FEMALE or UNKNOWN.|UNKNOWN
            photoUrl|Photo URL, usually from POST /api/uploads/photo.|/uploads/9d2f6c1e.jpg
            photoThumbnailUrl|Small (400 px) JPEG thumbnail of the photo, for lists; null if the photo is not one of our uploads.|/uploads/9d2f6c1e_thumb.jpg
            note|Free-text note.|Feeding at the balcony.
            latitude|Latitude in degrees, -90 to 90.|40.9397
            longitude|Longitude in degrees, -180 to 180.|29.1196
            locationName|Free-text place name.|Maltepe Park
            visibility|PRIVATE or PUBLIC; all logs are private for now.|PRIVATE
            logs|Bird logs in the area.|
            truncated|True if more logs matched than the limit returned.|false
            type|Type of the item.|
            version|Document version.|2026-10-03
            acceptedAt|When the document version was accepted (UTC).|2026-10-03T08:15:30Z
            termsVersion|Current Terms of Service version, or null if none is published.|
            termsUrl|Public URL of the Terms of Service, or null.|
            privacyVersion|Current Privacy Policy version, or null if none is published.|2026-10-03
            privacyUrl|Public URL of the Privacy Policy, or null.|https://example.com/privacy.html
            exportedAt|When the export was created (UTC).|2026-10-03T08:15:30Z
            profile|The user's profile.|
            settings|The user's app settings.|
            birdLogs|All of the user's bird logs.|
            badges|The user's badges with progress.|
            consents|The user's recorded consents, oldest first.|
            totalUsers|Number of registered users.|42
            favoriteSpecies|Users per favorite species.|
            badgeCompletions|Users who earned each badge.|
            topRegions|Regions with the most logs.|
            localeUsage|Users per locale.|
            calculatedAt|When the metrics were computed (UTC).|2026-10-03T08:15:30Z
            earnedCount|Number of users who earned the badge.|5
            locale|Locale code such as en or tr.|en
            region|Coordinate grid cell "{lat}, {lng}" of about 11 km, not the free-text location name.|40.9, 29.1
            logCount|Number of bird logs.|58
            userCount|Number of users.|12
            speciesName|Species name in the request locale.|House Sparrow
            commonName|Common name by locale code; "en" is required.|
            scientificName|Scientific (Latin) name, unique across species.|Passer domesticus
            family|Taxonomic family.|Passeridae
            order|Taxonomic order.|Passeriformes
            lifespan|Typical lifespan by locale code.|
            diet|Diet by locale code.|
            habitat|Habitat by locale code.|
            sizeDescription|Size description by locale code.|
            conservationStatus|Conservation status by locale code.|
            nativeRange|Native range by locale code.|
            images|Reference images of the species.|
            imageUrl|Image URL.|https://example.com/sparrow.jpg
            caption|Optional image caption.|Adult male
            licenseCode|Image license such as cc-by, or null for the operator's own uploads.|cc-by
            attribution|Credit line the app must show for the image, or null.|(c) Jane Doe
            commonsFileUrl|Page address of a Wikimedia Commons file, like https://commons.wikimedia.org/wiki/File:Example.jpg. When set, licenseCode, attribution and sourceUrl are read from Commons and the values sent for them are ignored; only public domain, CC0, CC BY and CC BY-SA files are accepted.|https://commons.wikimedia.org/wiki/File:Example.jpg
            sourceUrl|Where the image came from, or null.|https://www.inaturalist.org/observations/1
            observationId|iNaturalist observation id.|1234567
            observationUrl|Link to the iNaturalist observation.|https://www.inaturalist.org/observations/1234567
            recordingUrl|Link to the recording.|https://xeno-canto.org/123456
            quality|Recording quality grade from Xeno-canto, A to E.|A
            recordist|Person who made the recording.|Jane Doe
            licenseUrl|License of the recording.|https://creativecommons.org/licenses/by-nc-sa/4.0/
            unitPreference|Distance unit: METRIC or IMPERIAL.|METRIC
            timestamp|When the error happened (UTC).|2026-10-03T08:15:30Z
            status|HTTP status code.|400
            error|HTTP status text.|Bad Request
            code|Stable machine-readable error code; clients should branch on this, not on the message.|VALIDATION_FAILED
            path|Request path.|/api/auth/register
            validationErrors|Field name to message, present for validation failures.|
            RegisterResponseDto.userId|Id of the new account.|%1$s
            RegisterResponseDto.email|Email address of the new account.|amelia@example.com
            RegisterResponseDto.username|Username of the new account.|amelia_birds
            RegisterResponseDto.emailVerified|Always false until the email link is opened.|false
            RegisterResponseDto.message|Next-step message for the user.|Account created. Check your inbox for a verification link, then log in.
            ResetPasswordRequestDto.code|The 6-digit code from the reset email.|482913
            DeleteAccountRequestDto.password|The account's current password, to confirm the deletion.|
            AdminUpdateUserRequestDto.role|New role: USER or ADMIN. Admins cannot change their own.|USER
            AdminUpdateUserRequestDto.emailVerified|Whether the email counts as verified. Admins cannot change their own.|true
            BadgeResponseDto.id|Badge id (UUID).|%1$s
            BadgeResponseDto.name|Badge name by locale code; "en" is required.|
            CreateBadgeRequestDto.name|Badge name by locale code; "en" is required.|
            UpdateBadgeRequestDto.name|Badge name by locale code; "en" is required.|
            BirdLogResponseDto.id|Bird log id (UUID).|%1$s
            BirdLogResponseDto.userId|Id of the user who owns the log.|%1$s
            SpeciesResponseDto.id|Species id (UUID).|%1$s
            thumbnailUrl|Small (400 px) JPEG thumbnail of the image, for lists; null if the image is not one of our uploads.|/uploads/9d2f6c1e_thumb.jpg
            SpeciesImageResponseDto.id|Image id (UUID).|%1$s
            SpeciesRecordingResponseDto.id|Xeno-canto recording id.|123456
            SpeciesRecordingResponseDto.type|Recording type such as call or song.|song
            UserProfileResponseDto.id|User id (UUID).|%1$s
            UserProfileResponseDto.createdAt|When the account was created (UTC).|2026-10-03T08:15:30Z
            BirdLogResponseDto.createdAt|When the log was created (UTC).|2026-10-03T08:15:30Z
            CreateSpeciesImageRequestDto.lifeStage|Life stage shown: BABY or ADULT.|ADULT
            CreateSpeciesImageRequestDto.gender|Sex shown: MALE, FEMALE or NOT_APPLICABLE.|NOT_APPLICABLE
            SpeciesImageResponseDto.lifeStage|Life stage shown: BABY or ADULT.|ADULT
            SpeciesImageResponseDto.gender|Sex shown: MALE, FEMALE or NOT_APPLICABLE.|NOT_APPLICABLE
            ConsentResponseDto.type|Consent type: TERMS, PRIVACY or AGE.|PRIVACY
            AcceptConsentRequestDto.type|Consent type: TERMS, PRIVACY or AGE.|PRIVACY
            AcceptConsentRequestDto.version|Exact current version: the document version from GET /api/legal, or the minimum age (e.g. 13) for AGE.|2026-10-03
            ConsentResponseDto.version|Version of the document that was accepted.|2026-10-03
            PendingConsentsResponseDto.pendingConsents|Documents still to accept after this call; empty when none.|
            LocaleUsageCountDto.locale|Language code such as en or tr, or "unset" if the user never chose one.|en
            SettingsResponseDto.locale|Preferred language code such as en or tr, or null.|en
            UpdateSettingsRequestDto.locale|Preferred language code such as en or tr, or null.|en
            Pageable.page|Zero-based page index (default 0).|0
            Pageable.size|Page size (default 20).|20
            Pageable.sort|Sort as field,direction, e.g. commonName.en,asc. May be repeated.|commonName.en,asc
            PagedModel.content|Items on this page.|
            PagedModel.page|Paging metadata.|
            PageMetadata.size|Page size.|20
            PageMetadata.number|Zero-based number of this page.|0
            PageMetadata.totalElements|Total number of matching items.|135
            PageMetadata.totalPages|Total number of pages.|7
            generatedAt|When the statistics were computed (UTC).|2026-10-03T08:15:30Z
            minGroupSize|Fewest different users a species, region or bucket needs before it is listed.|5
            activeUsersLast7Days|Users who logged in or refreshed their session in the last 7 days.|12
            activeUsersLast30Days|Users who logged in or refreshed their session in the last 30 days.|30
            newUsersLast30Days|Accounts created in the last 30 days.|8
            totalLogs|Total number of bird logs.|420
            logsLast7Days|Bird logs created in the last 7 days.|35
            logsLast30Days|Bird logs created in the last 30 days.|140
            logsPerDay|Logs created per day for the last 30 days, oldest first, zero days included.|
            logsPerWeek|Logs created per week for the last 12 weeks, oldest first.|
            sightingsPerUser|How many users have 0, 1, 2-5, 6-20 or 21+ logs. A count is null when fewer than minGroupSize users fall in the range.|
            topSpecies|Most-logged species, only those logged by at least minGroupSize different users (up to 10).|
            regions|Coarse one-degree grid cells with logs from at least minGroupSize different users.|
            DayCount.date|The day (UTC).|2026-10-03
            DayCount.logs|Logs created that day.|5
            WeekCount.weekStart|Monday that starts the week (UTC).|2026-09-28
            WeekCount.logs|Logs created that week.|35
            SightingsBucket.range|Range of logs per user: 0, 1, 2-5, 6-20 or 21+.|2-5
            SightingsBucket.users|Number of users in the range, or null when too few to show.|9
            SpeciesCount.speciesName|Species common name in English.|House Sparrow
            SpeciesCount.logs|Number of logs of the species.|58
            SpeciesCount.users|Number of different users who logged it.|9
            RegionCount.cell|Grid cell as "{latitude}, {longitude}" rounded down to whole degrees (about 110 km).|40, 29
            RegionCount.logs|Number of logs in the cell.|58
            RegionCount.users|Number of different users with logs in the cell.|9
            PhotoCandidateDto.photoUrl|URL of the candidate photo.|https://static.inaturalist.org/photos/1/medium.jpg
            """;

    private static final Map<String, Doc> DOCS = parse();

    private ApiFieldDocs() {
    }

    private static Map<String, Doc> parse() {
        Map<String, Doc> docs = new HashMap<>();
        for (String line : TABLE.formatted(SAMPLE_ID).split("\\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 2 || parts[0].isBlank()) {
                continue;
            }
            String example = parts.length > 2 && !parts[2].isBlank() ? parts[2] : null;
            docs.put(parts[0].trim(), new Doc(parts[1].trim(), example));
        }
        return docs;
    }

    /** Returns the documentation for a field of a schema: the schema-specific entry, else the general one, else null. */
    static Doc of(String schema, String field) {
        Doc specific = DOCS.get(schema + "." + field);
        return specific != null ? specific : DOCS.get(field);
    }
}
