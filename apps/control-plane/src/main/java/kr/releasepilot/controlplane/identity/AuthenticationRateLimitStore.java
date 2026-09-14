package kr.releasepilot.controlplane.identity;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Component
class AuthenticationRateLimitStore {
    private static final int MAX_WRITE_RETRIES = 20;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    AuthenticationRateLimitStore(JdbcTemplate jdbc,
                                 PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    AuthenticationRateLimit.Decision consume(String key, long windowStart, long windowSeconds, int maximum, long now) {
        for (int attempt = 1; attempt <= MAX_WRITE_RETRIES; attempt++) {
            try {
                return transactions.execute(status -> {
                    long expiresAt = windowStart + windowSeconds;
                    int updated = jdbc.update("""
                            UPDATE authentication_rate_limits
                               SET attempts = CASE WHEN window_start = ? THEN attempts + 1 ELSE 1 END,
                                   window_start = ?, expires_at = ?, version = version + 1
                             WHERE rate_key = ? AND (window_start <> ? OR attempts < ?)
                            """, windowStart, windowStart, expiresAt, key, windowStart, maximum);
                    if (updated == 1) return new AuthenticationRateLimit.Decision(true, 0, 0);

                    var existing = jdbc.query("""
                            SELECT window_start, attempts, expires_at
                              FROM authentication_rate_limits WHERE rate_key = ?
                            """, (result, row) -> new State(result.getLong(1), result.getInt(2), result.getLong(3)), key);
                    if (!existing.isEmpty()) {
                        State value = existing.getFirst();
                        if (value.windowStart() == windowStart && value.attempts() >= maximum)
                            return new AuthenticationRateLimit.Decision(false,
                                    Math.max(1, value.expiresAt() - now), value.attempts());
                        throw new RetryCollision();
                    }

                    jdbc.update("""
                            INSERT INTO authentication_rate_limits
                                (rate_key, window_start, attempts, expires_at, version)
                            VALUES (?, ?, 1, ?, 0)
                            """, key, windowStart, expiresAt);
                    return new AuthenticationRateLimit.Decision(true, 0, 1);
                });
            } catch (DataIntegrityViolationException | RetryCollision collision) {
                if (attempt == MAX_WRITE_RETRIES) throw collision;
                Thread.onSpinWait();
            }
        }
        throw new IllegalStateException("Rate limit store retry exhausted");
    }

    void reset(String key) {
        transactions.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM authentication_rate_limits WHERE rate_key = ?", key));
    }

    @Scheduled(fixedDelayString = "${releasepilot.security.rate-limit.cleanup-interval:PT10M}")
    void cleanup() {
        transactions.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM authentication_rate_limits WHERE expires_at <= ?", clock.instant().getEpochSecond()));
    }

    private record State(long windowStart, int attempts, long expiresAt) {}
    private static final class RetryCollision extends RuntimeException {}
}
