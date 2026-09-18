package kr.releasepilot.controlplane.notification;

import com.sun.net.httpserver.HttpServer;
import kr.releasepilot.controlplane.connection.SecretResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubChecksTokenProviderTests {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void exchangesSignedAppJwtOnceAndCachesInstallationToken() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("RSA"); keyPair.initialize(2048);
        var pair = keyPair.generateKeyPair();
        Path key = Files.createTempFile("github-app-", ".pem");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        var requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/app/installations/456/access_tokens", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Bearer ").doesNotContain("installation-token");
            byte[] body = "{\"token\":\"installation-token\",\"expires_at\":\"2026-09-19T02:00:00Z\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var provider = new GithubChecksTokenProvider(HttpClient.newHttpClient(), new ObjectMapper(), mock(SecretResolver.class), Clock.fixed(Instant.parse("2026-09-19T01:00:00Z"), ZoneOffset.UTC), "http://127.0.0.1:" + server.getAddress().getPort(), "env:TOKEN", 123, 456, key.toString());
        assertThat(provider.token()).isEqualTo("installation-token");
        assertThat(provider.token()).isEqualTo("installation-token");
        assertThat(requests).hasValue(1);
        Files.deleteIfExists(key);
    }

    @Test void preservesStaticSecretFallbackOutsideGithubAppDeployments() {
        var secrets = mock(SecretResolver.class);
        when(secrets.resolve("env:TOKEN")).thenReturn(Optional.of(new SecretResolver.SecretMaterial("static-token")));
        var provider = new GithubChecksTokenProvider(HttpClient.newHttpClient(), new ObjectMapper(), secrets, Clock.systemUTC(), "https://api.github.com", "env:TOKEN", 0, 0, "");
        assertThat(provider.token()).isEqualTo("static-token");
    }
}
