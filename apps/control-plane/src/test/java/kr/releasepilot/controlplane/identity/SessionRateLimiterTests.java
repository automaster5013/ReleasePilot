package kr.releasepilot.controlplane.identity;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionRateLimiterTests {
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-14T00:00:30Z"), ZoneOffset.UTC);

    @Test
    void limitsDemoSessionsByClientIpAndReturnsRetryDelay() {
        var store = mock(AuthenticationRateLimitStore.class);
        when(store.consume(anyString(), anyLong(), anyLong(), anyInt(), anyLong()))
                .thenReturn(allowed(1), allowed(2), new AuthenticationRateLimit.Decision(false, 30, 2));
        var limiter = limiter(store, 20, 5, 2);
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
        var store = mock(AuthenticationRateLimitStore.class);
        var accountAttempts = new AtomicInteger();
        when(store.consume(anyString(), anyLong(), anyLong(), anyInt(), anyLong())).thenAnswer(call -> {
            if (!call.<String>getArgument(0).startsWith("login-account:")) return allowed(1);
            int attempt = accountAttempts.incrementAndGet();
            return attempt > 2 ? new AuthenticationRateLimit.Decision(false, 30, 2) : allowed(attempt);
        });
        var limiter = limiter(store, 20, 2, 30);
        limiter.checkLogin(request("198.51.100.1"), "Operator");
        limiter.checkLogin(request("198.51.100.2"), "operator");

        assertThatThrownBy(() -> limiter.checkLogin(request("198.51.100.3"), "OPERATOR"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        failure -> assertThat(failure.code()).isEqualTo("LOGIN_RATE_LIMITED"));
    }

    @Test
    void ignoresForwardedHeaderFromAnUntrustedDirectClient() {
        var store = mock(AuthenticationRateLimitStore.class);
        var ipAttempts = new AtomicInteger();
        when(store.consume(anyString(), anyLong(), anyLong(), anyInt(), anyLong())).thenAnswer(call -> {
            if (!call.<String>getArgument(0).startsWith("login-ip:")) return allowed(1);
            return ipAttempts.incrementAndGet() == 1
                    ? allowed(1) : new AuthenticationRateLimit.Decision(false, 30, 1);
        });
        var limiter = limiter(store, 1, 20, 30);
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

    private static AuthenticationRateLimit.Decision allowed(int attempts) {
        return new AuthenticationRateLimit.Decision(true, 0, attempts);
    }

    private static SessionRateLimiter limiter(AuthenticationRateLimitStore store, int ip, int account, int demo) {
        return new SessionRateLimiter(NOW, store, true,
                ip, Duration.ofMinutes(5), account, Duration.ofMinutes(5), demo, Duration.ofMinutes(1));
    }

    private static MockHttpServletRequest request(String address) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.10");
        request.addHeader("X-Forwarded-For", "203.0.113.5, " + address);
        return request;
    }
}
