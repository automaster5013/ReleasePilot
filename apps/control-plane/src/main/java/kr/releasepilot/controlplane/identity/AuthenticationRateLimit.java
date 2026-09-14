package kr.releasepilot.controlplane.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "authentication_rate_limits")
class AuthenticationRateLimit {
    @Id
    @Column(name = "rate_key", length = 80, nullable = false, updatable = false)
    private String key;
    @Column(name = "window_start", nullable = false)
    private long windowStart;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected AuthenticationRateLimit() {}

    static AuthenticationRateLimit create(String key, long windowStart, long windowSeconds) {
        var value = new AuthenticationRateLimit();
        value.key = key;
        value.windowStart = windowStart;
        value.expiresAt = windowStart + windowSeconds;
        return value;
    }

    Decision consume(long expectedWindowStart, long windowSeconds, int maximum, long now) {
        if (windowStart != expectedWindowStart) {
            windowStart = expectedWindowStart;
            attempts = 0;
            expiresAt = expectedWindowStart + windowSeconds;
        }
        if (attempts >= maximum) return new Decision(false, Math.max(1, expiresAt - now), attempts);
        attempts++;
        return new Decision(true, 0, attempts);
    }

    record Decision(boolean allowed, long retryAfterSeconds, int attempts) {}
}
