package kr.releasepilot.controlplane.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kr.releasepilot.controlplane.catalog.*;
import kr.releasepilot.controlplane.connection.*;
import kr.releasepilot.controlplane.environment.*;
import kr.releasepilot.controlplane.release.*;
import kr.releasepilot.controlplane.rollout.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class AnalysisJobProcessorTests {
    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"{", "null", "{}", "[]", "{\"metrics\":\"test-only-sensitive-token\"}"})
    void invalidPolicyRetriesInsteadOfLeavingJobProcessing(String definition) {
        var fixture = new Fixture(3);
        when(fixture.snapshot.getDefinition()).thenReturn(definition);
        assertThat(fixture.processor.processOne()).isTrue();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        assertThat(fixture.job.getAttempts()).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isEqualTo("ANALYSIS_CONTEXT_UNAVAILABLE");
        assertThat(ReflectionTestUtils.getField(fixture.job, "availableAt")).isEqualTo(NOW.plusSeconds(300));
        verifyNoInteractions(fixture.worker, fixture.commands, fixture.secrets);
    }

    @Test
    void missingSnapshotRetriesWithoutCallingWorker() {
        var fixture = new Fixture(3);
        when(fixture.snapshots.findByReleaseId(any())).thenReturn(Optional.empty());
        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isEqualTo("ANALYSIS_CONTEXT_UNAVAILABLE");
        verifyNoInteractions(fixture.worker, fixture.commands, fixture.secrets);
    }

    @Test
    void invalidPolicyAtMaxAttemptsPausesWithStableReason() {
        var fixture = new Fixture(1);
        when(fixture.snapshot.getDefinition()).thenReturn("test-only-sensitive-token");
        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(fixture.job.getVerdict()).isEqualTo(AnalysisVerdict.INCONCLUSIVE);
        assertThat(fixture.job.getReasonCode()).isEqualTo("ANALYSIS_CONTEXT_UNAVAILABLE");
        assertThat(fixture.job.getEvidenceJson()).isEqualTo("[]");
        var saved = ArgumentCaptor.forClass(OutboxCommand.class);
        verify(fixture.commands).save(saved.capture());
        assertThat(saved.getValue().getCommandType()).isEqualTo("PAUSE_ROLLOUT");
        assertThat(saved.getValue().getPayloadJson()).contains("ANALYSIS_CONTEXT_UNAVAILABLE")
                .doesNotContain("test-only-sensitive-token");
        verifyNoInteractions(fixture.worker, fixture.secrets);
    }

    @Test
    void repairedPolicyIsReadAgainAndCanRecoverOnNextAttempt() {
        var fixture = new Fixture(3);
        when(fixture.snapshot.getDefinition()).thenReturn("{", "{\"metrics\":[]}");
        when(fixture.worker.evaluate(any())).thenReturn(
                new AnalysisWorkerGateway.Result(AnalysisVerdict.PASS, "ALL_RULES_PASSED", "[]"));
        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        verifyNoInteractions(fixture.worker, fixture.commands);
        when(fixture.clock.instant()).thenReturn(NOW.plusSeconds(300));
        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(fixture.job.getVerdict()).isEqualTo(AnalysisVerdict.PASS);
        assertThat(fixture.job.getAttempts()).isEqualTo(2);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isNull();
        verify(fixture.worker).evaluate(any());
    }

    @Test
    void missingConfiguredSecretRetriesWithoutCallingWorker() {
        var fixture = new Fixture(3);
        when(fixture.connection.getSecretRef()).thenReturn("env:TEST_ONLY_TOKEN");
        when(fixture.secrets.resolve("env:TEST_ONLY_TOKEN")).thenReturn(Optional.empty());
        assertThat(fixture.processor.processOne()).isTrue();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(ReflectionTestUtils.getField(fixture.job, "availableAt")).isEqualTo(NOW.plusSeconds(120));
        verifyNoInteractions(fixture.worker, fixture.commands);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n"})
    void blankConfiguredSecretDoesNotCallWorker(String token) {
        var fixture = new Fixture(3);
        when(fixture.connection.getSecretRef()).thenReturn("env:TEST_ONLY_TOKEN");
        when(fixture.secrets.resolve(anyString())).thenReturn(Optional.of(new SecretResolver.SecretMaterial(token)));
        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isEqualTo("SECRET_UNAVAILABLE");
        verifyNoInteractions(fixture.worker, fixture.commands);
    }

    @Test
    void resolverExceptionIsHandledWithoutPersistingMessageOrCallingWorker() {
        var fixture = new Fixture(3);
        when(fixture.connection.getSecretRef()).thenReturn("env:TEST_ONLY_TOKEN");
        when(fixture.secrets.resolve(anyString())).thenThrow(new IllegalStateException("test-only-sensitive-token"));
        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isEqualTo("SECRET_UNAVAILABLE");
        verifyNoInteractions(fixture.worker, fixture.commands);
    }

    @Test
    void exhaustedMissingSecretPausesInsteadOfPromoting() {
        var fixture = new Fixture(1);
        when(fixture.connection.getSecretRef()).thenReturn("env:TEST_ONLY_TOKEN");
        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(fixture.job.getVerdict()).isEqualTo(AnalysisVerdict.INCONCLUSIVE);
        assertThat(fixture.job.getReasonCode()).isEqualTo("SECRET_UNAVAILABLE");
        var saved = ArgumentCaptor.forClass(OutboxCommand.class);
        verify(fixture.commands).save(saved.capture());
        assertThat(saved.getValue().getCommandType()).isEqualTo("PAUSE_ROLLOUT");
        assertThat(saved.getValue().getPayloadJson()).contains("SECRET_UNAVAILABLE");
        verifyNoInteractions(fixture.worker);
    }

    @Test
    void connectionWithoutSecretStillUsesAnonymousWorkerRequest() {
        var fixture = new Fixture(3);
        when(fixture.worker.evaluate(any())).thenReturn(new AnalysisWorkerGateway.Result(AnalysisVerdict.INCONCLUSIVE,"NO_DATA","[]"));
        fixture.processor.processOne();
        var request = ArgumentCaptor.forClass(AnalysisWorkerGateway.Request.class);
        verify(fixture.worker).evaluate(request.capture());
        assertThat(request.getValue().bearerToken()).isNull();
        verifyNoInteractions(fixture.secrets);
    }

    @Test
    void availableConfiguredSecretStillReachesWorker() {
        var fixture = new Fixture(3);
        when(fixture.connection.getSecretRef()).thenReturn("env:TEST_ONLY_TOKEN");
        when(fixture.secrets.resolve(anyString())).thenReturn(Optional.of(new SecretResolver.SecretMaterial("test-only-token")));
        when(fixture.worker.evaluate(any())).thenReturn(new AnalysisWorkerGateway.Result(AnalysisVerdict.INCONCLUSIVE,"NO_DATA","[]"));
        fixture.processor.processOne();
        var request = ArgumentCaptor.forClass(AnalysisWorkerGateway.Request.class);
        verify(fixture.worker).evaluate(request.capture());
        assertThat(request.getValue().bearerToken()).isEqualTo("test-only-token");
    }

    @Test
    void restoredCredentialIsResolvedAgainOnNextScheduledAttempt() {
        var fixture = new Fixture(3);
        when(fixture.connection.getSecretRef()).thenReturn("env:TEST_ONLY_TOKEN");
        when(fixture.secrets.resolve(anyString())).thenReturn(Optional.empty(),
                Optional.of(new SecretResolver.SecretMaterial("test-only-restored-token")));
        when(fixture.worker.evaluate(any())).thenReturn(
                new AnalysisWorkerGateway.Result(AnalysisVerdict.PASS, "ALL_RULES_PASSED", "[]"));

        fixture.processor.processOne();
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        verifyNoInteractions(fixture.worker, fixture.commands);
        when(fixture.clock.instant()).thenReturn(NOW.plusSeconds(120));
        fixture.processor.processOne();

        assertThat(fixture.job.getAttempts()).isEqualTo(2);
        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(fixture.job.getVerdict()).isEqualTo(AnalysisVerdict.PASS);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isNull();
        verify(fixture.secrets, times(2)).resolve("env:TEST_ONLY_TOKEN");
        var request = ArgumentCaptor.forClass(AnalysisWorkerGateway.Request.class);
        verify(fixture.worker).evaluate(request.capture());
        assertThat(request.getValue().bearerToken()).isEqualTo("test-only-restored-token");
        var saved = ArgumentCaptor.forClass(OutboxCommand.class);
        verify(fixture.commands).save(saved.capture());
        assertThat(saved.getValue().getCommandType()).isEqualTo("PROMOTE_ROLLOUT");
    }

    @Test
    void retryStoresStableCodeInsteadOfSensitiveExceptionMessage() {
        var fixture = new Fixture(3);
        when(fixture.worker.evaluate(any())).thenThrow(new IllegalStateException(
                "Bearer test-only-sensitive-token https://private.example/query?password=test-only-password"));

        assertThat(fixture.processor.processOne()).isTrue();

        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        assertThat(fixture.job.getAttempts()).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isEqualTo("PROMETHEUS_UNAVAILABLE");
        assertThat(ReflectionTestUtils.getField(fixture.job, "availableAt")).isEqualTo(NOW.plusSeconds(120));
        verifyNoInteractions(fixture.commands);
    }

    @Test
    void nullExceptionMessageAlsoStoresStableCode() {
        var fixture = new Fixture(3);
        when(fixture.worker.evaluate(any())).thenThrow(new IllegalStateException());

        fixture.processor.processOne();

        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.RETRY_WAIT);
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isEqualTo("PROMETHEUS_UNAVAILABLE");
        verifyNoInteractions(fixture.commands);
    }

    @Test
    void exhaustedRetriesPauseWithoutPersistingExceptionDetails() {
        var fixture = new Fixture(1);
        when(fixture.worker.evaluate(any())).thenThrow(new IllegalStateException("test-only-sensitive-token"));

        fixture.processor.processOne();

        assertThat(fixture.job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(fixture.job.getVerdict()).isEqualTo(AnalysisVerdict.INCONCLUSIVE);
        assertThat(fixture.job.getReasonCode()).isEqualTo("PROMETHEUS_UNAVAILABLE");
        assertThat(fixture.job.getEvidenceJson()).isEqualTo("[]");
        assertThat(ReflectionTestUtils.getField(fixture.job, "lastError")).isNull();
        var saved = ArgumentCaptor.forClass(OutboxCommand.class);
        verify(fixture.commands).save(saved.capture());
        assertThat(saved.getValue().getCommandType()).isEqualTo("PAUSE_ROLLOUT");
        assertThat(saved.getValue().getPayloadJson()).contains("PROMETHEUS_UNAVAILABLE")
                .doesNotContain("test-only-sensitive-token");
    }

    private static class Fixture {
        final AnalysisJob job;
        final AnalysisWorkerGateway worker = mock(AnalysisWorkerGateway.class);
        final OutboxCommandRepository commands = mock(OutboxCommandRepository.class);
        final AnalysisJobProcessor processor;
        final SecretResolver secrets = mock(SecretResolver.class);
        final PrometheusConnection connection = mock(PrometheusConnection.class);
        final Clock clock = mock(Clock.class);
        final PolicySnapshot snapshot = mock(PolicySnapshot.class);
        final PolicySnapshotRepository snapshots = mock(PolicySnapshotRepository.class);

        Fixture(int maxAttempts) {
            var jobs = mock(AnalysisJobRepository.class);
            var steps = mock(RolloutStepRepository.class);
            var executions = mock(RolloutExecutionRepository.class);
            var releases = mock(ReleaseRepository.class);
            var environments = mock(EnvironmentRepository.class);
            var services = mock(CatalogServiceRepository.class);
            var connections = mock(PrometheusConnectionRepository.class);
            var step = mock(RolloutStep.class);
            var execution = mock(RolloutExecution.class);
            var release = mock(Release.class);
            var environment = mock(Environment.class);
            var service = mock(CatalogService.class);
            var stepId = UUID.randomUUID();
            var executionId = UUID.randomUUID();
            var releaseId = UUID.randomUUID();
            var environmentId = UUID.randomUUID();
            var serviceId = UUID.randomUUID();
            var connectionId = UUID.randomUUID();
            job = AnalysisJob.pending(stepId, UUID.randomUUID(), maxAttempts, NOW.minusSeconds(60), NOW);
            when(jobs.findFirstByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(anyList(), any(Instant.class)))
                    .thenReturn(Optional.of(job));
            when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
            when(steps.findById(stepId)).thenReturn(Optional.of(step));
            when(step.getExecutionId()).thenReturn(executionId);
            when(executions.findById(executionId)).thenReturn(Optional.of(execution));
            when(execution.getId()).thenReturn(executionId);
            when(execution.getReleaseId()).thenReturn(releaseId);
            when(releases.findById(releaseId)).thenReturn(Optional.of(release));
            when(release.getId()).thenReturn(releaseId);
            when(release.getEnvironmentId()).thenReturn(environmentId);
            when(release.getServiceId()).thenReturn(serviceId);
            when(environments.findById(environmentId)).thenReturn(Optional.of(environment));
            when(environment.getPrometheusConnectionId()).thenReturn(connectionId);
            when(services.findById(serviceId)).thenReturn(Optional.of(service));
            when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
            when(connection.getId()).thenReturn(connectionId);
            when(connection.getBaseUrl()).thenReturn("http://prometheus.invalid");
            when(snapshots.findByReleaseId(releaseId)).thenReturn(Optional.of(snapshot));
            when(snapshot.getDefinition()).thenReturn(
                    "{\"metrics\":[],\"inconclusivePolicy\":{\"additionalObservationSeconds\":120}}");
            var manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            when(clock.instant()).thenReturn(NOW);
            processor = new AnalysisJobProcessor(jobs, steps, executions, releases, environments,
                    services, connections, snapshots, secrets, worker, commands, new ObjectMapper(),
                    new TransactionTemplate(manager), clock);
        }
    }
}
