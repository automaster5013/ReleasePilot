package kr.releasepilot.controlplane.rollout;

import kr.releasepilot.controlplane.catalog.*;
import kr.releasepilot.controlplane.connection.*;
import kr.releasepilot.controlplane.environment.*;
import kr.releasepilot.controlplane.identity.*;
import kr.releasepilot.controlplane.policy.*;
import kr.releasepilot.controlplane.release.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OutboxCommandProcessorTests {
    @Autowired OutboxCommandProcessor processor;
    @Autowired OutboxRecoveryService recovery;
    @Autowired OutboxCommandRepository outbox;
    @Autowired RolloutExecutionRepository executions;
    @Autowired ReleaseRepository releases;
    @Autowired ReleaseArtifactRepository artifacts;
    @Autowired ProjectRepository projects;
    @Autowired CatalogServiceRepository services;
    @Autowired ClusterConnectionRepository clusters;
    @Autowired PrometheusConnectionRepository prometheus;
    @Autowired EnvironmentRepository environments;
    @Autowired UserAccountRepository users;
    @Autowired PolicyRepository policies;
    @Autowired PolicyVersionRepository versions;
    @Autowired TransactionTemplate transactions;

    @Test void successfulCommandIsClaimedOnceAndMarkedProcessed() {
        seedCommand();
        var calls = new AtomicInteger();
        assertThat(processor.processOne(command -> calls.incrementAndGet())).isTrue();
        assertThat(processor.processOne(command -> calls.incrementAndGet())).isFalse();
        assertThat(calls).hasValue(1);
        var command = outbox.findAll().getFirst();
        assertThat(command.getStatus()).isEqualTo(OutboxCommandStatus.PROCESSED);
        assertThat(command.getAttempts()).isEqualTo(1);
    }

    @Test void failedCommandIsRetriedAfterBackoffInsteadOfImmediately() {
        seedCommand();
        assertThat(processor.processOne(command -> { throw new IllegalStateException("cluster unavailable"); })).isTrue();
        assertThat(processor.processOne(command -> {})).isFalse();
        var command = outbox.findAll().getFirst();
        assertThat(command.getStatus()).isEqualTo(OutboxCommandStatus.FAILED);
        assertThat(command.getAttempts()).isEqualTo(1);
        assertThat(command.getAvailableAt()).isAfter(Instant.now());
    }

    @Test void staleProcessingLeaseIsRecoveredAndCanRunAgain() {
        seedCommand();
        transactions.executeWithoutResult(ignored -> {
            var command = outbox.findAll().getFirst();
            command.claim(Instant.now().minusSeconds(120));
        });
        assertThat(recovery.recoverExpired(java.time.Duration.ofSeconds(60), 10)).isEqualTo(1);
        assertThat(processor.processOne(command -> {})).isTrue();
        var command = outbox.findAll().getFirst();
        assertThat(command.getStatus()).isEqualTo(OutboxCommandStatus.PROCESSED);
        assertThat(command.getAttempts()).isEqualTo(2);
    }

    private void seedCommand() {
        transactions.executeWithoutResult(ignored -> {
            outbox.deleteAll(); executions.deleteAll(); artifacts.deleteAll(); releases.deleteAll();
            var now = Instant.now();
            var project = projects.save(Project.create("outbox-" + System.nanoTime(), "Outbox", null, now));
            var service = services.save(CatalogService.create(project.getId(), "app", "App", "https://github.com/acme/app", "platform", now));
            var user = users.save(UserAccount.create("worker-" + System.nanoTime(), "hash", "Worker", Set.of(Role.DEVELOPER), now));
            var cluster = clusters.save(ClusterConnection.create("cluster-" + System.nanoTime(), "https://k.example", List.of("demo"), "env:TOKEN", now));
            var prom = prometheus.save(PrometheusConnection.create("prom-" + System.nanoTime(), "http://prometheus", null, 15, now));
            var policy = policies.save(Policy.create("policy-" + System.nanoTime(), now));
            var version = PolicyVersion.draft(policy.getId(), 1, "{}", "checksum", user.getId(), now); version.activate(); versions.save(version);
            var env = Environment.create(service.getId(), "staging", cluster.getId(), "demo", "app", "app", "stable", "canary", prom.getId(), "app=x", version.getId(), now); env.complete(false, false, now); environments.save(env);
            var release = releases.save(Release.pending(service.getId(), env.getId(), user.getId(), "1", "test", "a".repeat(40), "https://ci", "key-" + System.nanoTime(), now));
            artifacts.save(ReleaseArtifact.create(release.getId(), "ghcr.io/acme/app", "sha256:" + "b".repeat(64)));
            var execution = executions.save(RolloutExecution.pending(release.getId(), cluster.getId(), "demo", "app", "sha256:" + "b".repeat(64), now));
            outbox.save(OutboxCommand.startRollout(execution, now));
        });
    }
}
