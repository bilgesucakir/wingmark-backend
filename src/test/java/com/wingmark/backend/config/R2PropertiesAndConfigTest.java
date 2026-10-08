package com.wingmark.backend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class R2PropertiesAndConfigTest {

    private static final R2Properties COMPLETE = new R2Properties("https://a.eu.r2.example.invalid", "photos", "auto", "key-id-value", "secret-value");
    private static final R2Properties EMPTY = new R2Properties("", null, "auto", " ", "");
    private static final R2MigrationProperties MIGRATION_OFF = new R2MigrationProperties(false, true, 50, "0 20 * * * *");
    private static final R2MigrationProperties MIGRATION_ON = new R2MigrationProperties(true, true, 50, "0 20 * * * *");

    private static R2StorageConfig config(String storage, R2Properties r2, R2MigrationProperties migration) {
        return new R2StorageConfig(new UploadProperties(200, storage), r2, migration);
    }

    @Test
    void theKeysNeverAppearInTheTextFormOfTheSettings() {
        assertThat(COMPLETE.toString()).doesNotContain("key-id-value").doesNotContain("secret-value").contains("photos");
    }

    @Test
    void missingSettingsAreNamedByTheirEnvironmentVariable() {
        assertThat(EMPTY.missing()).containsExactly("R2_ENDPOINT", "R2_BUCKET", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY");
        assertThat(COMPLETE.missing()).isEmpty();
    }

    @Test
    void theDatabaseModeNeedsNoR2SettingsAndIsTheDefaultSetup() {
        assertThatCode(() -> config("gridfs", EMPTY, MIGRATION_OFF).validate()).doesNotThrowAnyException();
    }

    @Test
    void r2ModeWithAllSettingsStarts() {
        assertThatCode(() -> config("r2", COMPLETE, MIGRATION_OFF).validate()).doesNotThrowAnyException();
        assertThatCode(() -> config("r2", COMPLETE, MIGRATION_ON).validate()).doesNotThrowAnyException();
    }

    @Test
    void r2ModeWithMissingSettingsRefusesToStartNamingThemWithoutPrintingValues() {
        assertThatThrownBy(() -> config("r2", new R2Properties("https://a.example.invalid", "photos", "auto", "", ""), MIGRATION_OFF).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("R2_ACCESS_KEY_ID").hasMessageContaining("R2_SECRET_ACCESS_KEY")
                .hasMessageNotContaining("a.example.invalid");
    }

    @Test
    void anUnknownStorageModeRefusesToStart() {
        assertThatThrownBy(() -> config("s3", COMPLETE, MIGRATION_OFF).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UPLOAD_STORAGE");
    }

    @Test
    void theMigrationCannotBeEnabledWithoutR2Storage() {
        assertThatThrownBy(() -> config("gridfs", COMPLETE, MIGRATION_ON).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("R2_MIGRATION_ENABLED");
    }

    @Test
    void theMigrationDefaultsAreOffDryRunAndCapped() {
        assertThat(MIGRATION_OFF.enabled()).isFalse();
        assertThat(MIGRATION_OFF.dryRun()).isTrue();
        assertThat(MIGRATION_OFF.maxPerRun()).isEqualTo(50);
    }
}
