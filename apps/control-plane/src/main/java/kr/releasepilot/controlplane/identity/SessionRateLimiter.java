package kr.releasepilot.controlplane.identity;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class SessionRateLimiter {
    private final AuthenticationRateLimitStore store;
    private final Clock clock;
    private final boolean enabled;
    private final Limit loginIp;
    private final Limit loginAccount;
    private final Limit demoIp;

    public SessionRateLimiter(
            Clock clock,
            AuthenticationRateLimitStore store,
            @Value("${releasepilot.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${releasepilot.security.rate-limit.login.ip.max-attempts:20}") int loginIpMax,
            @Value("${releasepilot.security.rate-limit.login.ip.window:PT5M}") Duration loginIpWindow,
            @Value("${releasepilot.security.rate-limit.login.account.max-attempts:5}") int loginAccountMax,
            @Value("${releasepilot.security.rate-limit.login.account.window:PT5M}") Duration loginAccountWindow,
            @Value("${releasepilot.security.rate-limit.demo.ip.max-attempts:30}") int demoIpMax,
            @Value("${releasepilot.security.rate-limit.demo.ip.window:PT1M}") Duration demoIpWindow
    ) {
        this.clock = clock;
        this.store = store;
        this.enabled = enabled;
        this.loginIp = new Limit(loginIpMax, loginIpWindow);
        this.loginAccount = new Limit(loginAccountMax, loginAccountWindow);
        this.demoIp = new Limit(demoIpMax, demoIpWindow);
    }

    public void checkLogin(HttpServletRequest request, String username) {
        if (!enabled) return;
        consume("login-ip", clientAddress(request), loginIp, "LOGIN_RATE_LIMITED");
        consume("login-account", username.toLowerCase(Locale.ROOT).strip(), loginAccount,
                "LOGIN_RATE_LIMITED");
    }

    public void checkDemo(HttpServletRequest request) {
        if (!enabled) return;
        consume("demo-ip", clientAddress(request), demoIp, "DEMO_SESSION_RATE_LIMITED");
    }

    public void resetLoginAccount(String username) {
        if (enabled) store.reset(key("login-account", username.toLowerCase(Locale.ROOT).strip()));
    }

    private void consume(String scope, String subject, Limit limit, String code) {
        long now = clock.instant().getEpochSecond();
        long windowSeconds = Math.max(1, limit.window().toSeconds());
        long windowStart = Math.floorDiv(now, windowSeconds) * windowSeconds;
        var decision = store.consume(key(scope, subject), windowStart, windowSeconds, limit.maxAttempts(), now);
        if (!decision.allowed()) throw new RateLimitExceededException(code, decision.retryAfterSeconds());
    }

    private static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (trustedProxy(request.getRemoteAddr()) && forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String candidate = hops[hops.length - 1].strip();
            if (!candidate.isBlank()) return candidate;
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private static boolean trustedProxy(String address) {
        if (address == null) return false;
        if (address.equals("::1") || address.startsWith("127.") || address.startsWith("10.") || address.startsWith("192.168."))
            return true;
        if (!address.startsWith("172.")) return false;
        String[] parts = address.split("\\.");
        if (parts.length < 2) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String key(String scope, String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((scope + "\n" + subject).getBytes(StandardCharsets.UTF_8));
            return scope + ':' + HexFormat.of().formatHex(digest);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private record Limit(int maxAttempts, Duration window) {
        private Limit {
            if (maxAttempts < 1 || window.isZero() || window.isNegative())
                throw new IllegalArgumentException("Rate limit must use a positive count and window");
        }
    }
}
