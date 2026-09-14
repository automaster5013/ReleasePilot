package kr.releasepilot.controlplane.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionRateLimiterTests {
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-14T00:00:30Z"), ZoneOffset.UTC);

    @Test
    void limitsDemoSessionsByClientIpAndReturnsRetryDelay() {
        var limiter = limiter(20, 5, 2);
        var request = request("198.51.100.7");
        limiter.checkDemo(request);
        limiter.checkDemo(request);

        assertThatThrownBy(() -> limiter.checkDemo(request))
                .isInstanceOfSatisfying(RateLimitExceededException.class, failure -> {
                    assertThat(failure.code()).isEqualTo("DEMO_SESSION_RATE_LIMITED");
                    assertThat(failure.retryAfterSeconds()).isEqualTo(30);
                });
    }

    @Test
    void limitsLoginAccountAcrossDifferentAddresses() {
        var limiter = limiter(20, 2, 30);
        limiter.checkLogin(request("198.51.100.1"), "Operator");
        limiter.checkLogin(request("198.51.100.2"), "operator");

        assertThatThrownBy(() -> limiter.checkLogin(request("198.51.100.3"), "OPERATOR"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        failure -> assertThat(failure.code()).isEqualTo("LOGIN_RATE_LIMITED"));
    }

    @Test
    void ignoresForwardedHeaderFromAnUntrustedDirectClient() {
        var limiter = limiter(1, 20, 30);
        var first = new MockHttpServletRequest();
        first.setRemoteAddr("198.51.100.10");
        first.addHeader("X-Forwarded-For", "203.0.113.1");
        var second = new MockHttpServletRequest();
        second.setRemoteAddr("198.51.100.10");
        second.addHeader("X-Forwarded-For", "203.0.113.2");
        limiter.checkLogin(first, "first-user");

        assertThatThrownBy(() -> limiter.checkLogin(second, "second-user"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    private static SessionRateLimiter limiter(int ip, int account, int demo) {
        return new SessionRateLimiter(NOW, true,
                ip, Duration.ofMinutes(5), account, Duration.ofMinutes(5), demo, Duration.ofMinutes(1));
    }

    private static MockHttpServletRequest request(String address) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.5, " + address);
        return request;
    }
}
