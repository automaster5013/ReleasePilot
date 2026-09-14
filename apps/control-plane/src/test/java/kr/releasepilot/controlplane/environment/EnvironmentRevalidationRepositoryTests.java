package kr.releasepilot.controlplane.environment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EnvironmentRevalidationRepositoryTests {
    @Autowired EnvironmentRepository environments;

    @Test
    void aDueEnvironmentCanBeClaimedOnlyOnce() {
        var now = Instant.parse("2026-09-15T00:00:00Z");
        var environment = Environment.create(UUID.randomUUID(), "production", UUID.randomUUID(), "demo",
                "checkout", "checkout", "checkout-stable", "checkout-canary", UUID.randomUUID(),
                "service_name=checkout", UUID.randomUUID(), now.minusSeconds(30_000));
        environment.complete(false, false, now.minusSeconds(25_000));
        environments.saveAndFlush(environment);
        var activeCutoff = now.minusSeconds(21_600);
        var failureCutoff = now.minusSeconds(900);
        var staleBefore = now.minusSeconds(600);

        assertThat(environments.findDueIds(activeCutoff, failureCutoff, staleBefore,
                EnvironmentStatus.VALIDATING, EnvironmentStatus.INVALID, EnvironmentStatus.DISABLED,
                PageRequest.of(0, 20))).contains(environment.getId());
        assertThat(environments.claimDue(environment.getId(), now, activeCutoff, failureCutoff, staleBefore,
                EnvironmentStatus.VALIDATING, EnvironmentStatus.INVALID, EnvironmentStatus.DISABLED)).isEqualTo(1);
        assertThat(environments.claimDue(environment.getId(), now, activeCutoff, failureCutoff, staleBefore,
                EnvironmentStatus.VALIDATING, EnvironmentStatus.INVALID, EnvironmentStatus.DISABLED)).isZero();
    }
}
