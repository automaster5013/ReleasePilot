package kr.releasepilot.controlplane.notification;

import kr.releasepilot.controlplane.connection.SecretResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
public class GithubChecksTokenProvider {
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private final HttpClient http;
    private final ObjectMapper json;
    private final SecretResolver secrets;
    private final Clock clock;
    private final String apiBaseUrl;
    private final String secretRef;
    private final long appId;
    private final long installationId;
    private final String privateKeyPath;
    private CachedToken cached;

    public GithubChecksTokenProvider(
            HttpClient http,
            ObjectMapper json,
            SecretResolver secrets,
            Clock clock,
            @Value("${releasepilot.github-checks.api-base-url:https://api.github.com}") String apiBaseUrl,
            @Value("${releasepilot.github-checks.secret-ref:env:GITHUB_CHECKS_TOKEN}") String secretRef,
            @Value("${releasepilot.github-checks.app-id:0}") long appId,
            @Value("${releasepilot.github-checks.installation-id:0}") long installationId,
            @Value("${releasepilot.github-checks.private-key-path:}") String privateKeyPath) {
        this.http = http;
        this.json = json;
        this.secrets = secrets;
        this.clock = clock;
        this.apiBaseUrl = apiBaseUrl.replaceFirst("/$", "");
        this.secretRef = secretRef;
        this.appId = appId;
        this.installationId = installationId;
        this.privateKeyPath = privateKeyPath;
    }

    public synchronized String token() {
        if (appId <= 0 || installationId <= 0 || privateKeyPath.isBlank()) {
            return secrets.resolve(secretRef)
                    .orElseThrow(() -> new IllegalStateException("GitHub Checks secret is unavailable"))
                    .bearerToken();
        }
        Instant now = clock.instant();
        if (cached != null && cached.expiresAt().isAfter(now.plus(5, ChronoUnit.MINUTES))) return cached.value();
        cached = requestInstallationToken(now);
        return cached.value();
    }

    private CachedToken requestInstallationToken(Instant now) {
        try {
            String jwt = signedJwt(now, readPrivateKey());
            var request = HttpRequest.newBuilder(URI.create(apiBaseUrl + "/app/installations/" + installationId + "/access_tokens"))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + jwt)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("GitHub installation token request returned HTTP " + response.statusCode());
            var body = json.readTree(response.body());
            String value = body.path("token").asText();
            Instant expiresAt = Instant.parse(body.path("expires_at").asText());
            if (value.isBlank() || !expiresAt.isAfter(now))
                throw new IllegalStateException("GitHub installation token response is invalid");
            return new CachedToken(value, expiresAt);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GitHub installation token request was interrupted", failure);
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("GitHub installation token request failed", failure);
        }
    }

    private PrivateKey readPrivateKey() throws Exception {
        String pem = Files.readString(Path.of(privateKeyPath), StandardCharsets.US_ASCII).strip();
        byte[] der;
        if (pem.startsWith("-----BEGIN PRIVATE KEY-----")) {
            der = decodePem(pem, "PRIVATE KEY");
        } else if (pem.startsWith("-----BEGIN RSA PRIVATE KEY-----")) {
            der = wrapPkcs1(decodePem(pem, "RSA PRIVATE KEY"));
        } else {
            throw new IllegalStateException("GitHub App private key format is unsupported");
        }
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private String signedJwt(Instant now, PrivateKey privateKey) throws Exception {
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"iat\":" + now.minusSeconds(60).getEpochSecond() + ",\"exp\":" + now.plusSeconds(540).getEpochSecond() + ",\"iss\":\"" + appId + "\"}");
        String unsigned = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
        return unsigned + "." + BASE64_URL.encodeToString(signature.sign());
    }

    private static String encode(String value) {
        return BASE64_URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] decodePem(String pem, String type) {
        return Base64.getMimeDecoder().decode(pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", ""));
    }

    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] rsaAlgorithm = {0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] privateKey = der(0x04, pkcs1);
        return der(0x30, concat(version, rsaAlgorithm, privateKey));
    }

    private static byte[] der(int tag, byte[] value) {
        var output = new ByteArrayOutputStream();
        output.write(tag);
        if (value.length < 128) output.write(value.length);
        else {
            int count = 0;
            for (int length = value.length; length > 0; length >>= 8) count++;
            output.write(0x80 | count);
            for (int shift = (count - 1) * 8; shift >= 0; shift -= 8) output.write(value.length >> shift);
        }
        output.writeBytes(value);
        return output.toByteArray();
    }

    private static byte[] concat(byte[]... values) {
        var output = new ByteArrayOutputStream();
        for (byte[] value : values) output.writeBytes(value);
        return output.toByteArray();
    }

    private record CachedToken(String value, Instant expiresAt) {}
}
