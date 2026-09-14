package kr.releasepilot.controlplane.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuthenticationRateLimitStoreTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void sharesOneLimitAcrossStoreInstances() {
        var first = store();
        var second = store();

        assertThat(first.consume("demo:key", 1_000, 60, 2, 1_010).allowed()).isTrue();
        assertThat(second.consume("demo:key", 1_000, 60, 2, 1_011).allowed()).isTrue();
        var blocked = first.consume("demo:key", 1_000, 60, 2, 1_012);

        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(48);
    }

    @Test
    void atomicallyCapsConcurrentRequests() throws Exception {
        var store = store();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int round = 0; round < 5; round++) {
                String key = "concurrent:" + round;
                var calls = IntStream.range(0, 20)
                        .mapToObj(index -> (Callable<Boolean>) () ->
                                store.consume(key, 2_000, 60, 5, 2_010).allowed())
                        .toList();
                long allowed = executor.invokeAll(calls).stream().filter(future -> {
                    try { return future.get(); } catch (Exception failure) { throw new RuntimeException(failure); }
                }).count();
                assertThat(allowed).as("round %s", round).isEqualTo(5);
            }
        }
    }

    private AuthenticationRateLimitStore store() {
        return new AuthenticationRateLimitStore(jdbc, transactionManager,
                Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC));
    }
}
