package kr.releasepilot.controlplane.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SecretMaterialTests {
    @Test
    void jsonSerializationOmitsBearerToken() {
        var material = new SecretResolver.SecretMaterial("test-only-sensitive-token");
        var json = new ObjectMapper();
        assertThat(json.writeValueAsString(material)).isEqualTo("{}");
        assertThat(material.bearerToken()).isEqualTo("test-only-sensitive-token");
    }

    @Test
    void nestedJsonSerializationOmitsBearerToken() {
        var material = new SecretResolver.SecretMaterial("test-only-sensitive-token");
        var json = new ObjectMapper();
        assertThat(json.writeValueAsString(java.util.Map.of("secret", material)))
                .isEqualTo("{\"secret\":{}}");
        assertThat(json.writeValueAsString(List.of(material))).isEqualTo("[{}]");
    }

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
