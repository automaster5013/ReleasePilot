package kr.releasepilot.controlplane.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecretMaterialTests {
    @Test
    void stringRepresentationDoesNotExposeBearerToken() {
        var material = new SecretResolver.SecretMaterial("test-only-sensitive-token");
        assertThat(material.toString()).isEqualTo("SecretMaterial[bearerToken=[REDACTED]]");
        assertThat(material.toString()).doesNotContain("test-only-sensitive-token");
        assertThat(material.bearerToken()).isEqualTo("test-only-sensitive-token");
    }

    @Test
    void wrappersAlsoKeepTheSecretRedacted() {
        var material = new SecretResolver.SecretMaterial("test-only-sensitive-token");
        assertThat(Optional.of(material).toString()).doesNotContain("test-only-sensitive-token").contains("[REDACTED]");
        assertThat(List.of(material).toString()).doesNotContain("test-only-sensitive-token").contains("[REDACTED]");
    }

    @Test
    void nullAndEmptyValuesHaveTheSameRedactedRepresentation() {
        var nullMaterial = new SecretResolver.SecretMaterial(null);
        var emptyMaterial = new SecretResolver.SecretMaterial("");
        assertThat(nullMaterial.toString()).isEqualTo(emptyMaterial.toString()).contains("[REDACTED]");
    }
}
