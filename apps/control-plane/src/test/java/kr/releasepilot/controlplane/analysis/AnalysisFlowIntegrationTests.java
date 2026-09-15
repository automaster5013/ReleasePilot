package kr.releasepilot.controlplane.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.*;
import kr.releasepilot.controlplane.audit.AuditEventRepository;
import kr.releasepilot.controlplane.catalog.*;
import kr.releasepilot.controlplane.connection.*;
import kr.releasepilot.controlplane.environment.*;
import kr.releasepilot.controlplane.identity.*;
import kr.releasepilot.controlplane.policy.*;
import kr.releasepilot.controlplane.release.*;
import kr.releasepilot.controlplane.rollout.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={
        "spring.datasource.url=${ANALYSIS_TEST_JDBC_URL:jdbc:h2:mem:analysis-flow;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${ANALYSIS_TEST_USERNAME:sa}",
        "spring.datasource.password=${ANALYSIS_TEST_PASSWORD:}",
        "spring.flyway.enabled=${ANALYSIS_TEST_FLYWAY:false}",
        "spring.jpa.hibernate.ddl-auto=${ANALYSIS_TEST_DDL:create-drop}"})
class AnalysisFlowIntegrationTests {
    @Autowired AnalysisJobProcessor analysis;
    @Autowired OutboxCommandProcessor dispatcher;
    @Autowired StartRolloutCommandHandler handler;
    @Autowired AnalysisJobRepository jobs;
    @Autowired OutboxCommandRepository outbox;
    @Autowired RolloutStepRepository steps;
    @Autowired RolloutExecutionRepository executions;
    @Autowired ReleaseRepository releases;
    @Autowired ReleaseArtifactRepository artifacts;
    @Autowired PolicySnapshotRepository snapshots;
    @Autowired ProjectRepository projects;
    @Autowired CatalogServiceRepository services;
    @Autowired ClusterConnectionRepository clusters;
    @Autowired PrometheusConnectionRepository prometheus;
    @Autowired EnvironmentRepository environments;
    @Autowired UserAccountRepository users;
    @Autowired PolicyRepository policies;
    @Autowired PolicyVersionRepository versions;
    @Autowired AuditEventRepository audits;
    @Autowired TransactionTemplate transactions;
    @MockitoBean AnalysisWorkerGateway worker;
    @MockitoBean ArgoRolloutsGateway argo;
    @MockitoBean SecretResolver secrets;

    @ParameterizedTest
    @EnumSource(AnalysisVerdict.class)
    void persistedVerdictDrivesOutboxControlAndAudit(AnalysisVerdict verdict) {
        var seed=seed(false);
        assertThat(snapshots.findByReleaseId(seed.releaseId()).orElseThrow().getDefinition()).startsWith("{");
        when(worker.evaluate(any())).thenReturn(new AnalysisWorkerGateway.Result(verdict,"FIXTURE_VERDICT","[]"));
        var action=verdict==AnalysisVerdict.PASS?ArgoRolloutsGateway.Action.PROMOTE:
                verdict==AnalysisVerdict.FAIL?ArgoRolloutsGateway.Action.ABORT:ArgoRolloutsGateway.Action.PAUSE;

        assertThat(analysis.processOne()).isTrue();
        assertThat(analysis.processOne()).isFalse();
        var persisted=jobs.findById(seed.jobId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
        assertThat(persisted.getVerdict()).isEqualTo(verdict);
        assertThat(outbox.findAll()).singleElement().satisfies(command -> {
            assertThat(command.getStatus()).isEqualTo(OutboxCommandStatus.PENDING);
            assertThat(command.getCommandType()).isEqualTo(action.name()+"_ROLLOUT");
        });

        assertThat(dispatcher.processOne(handler)).isTrue();
        assertThat(dispatcher.processOne(handler)).isFalse();
        verify(argo).control(argThat(request -> request.action()==action));
        assertThat(outbox.findAll()).singleElement().satisfies(command -> {
            assertThat(command.getStatus()).isEqualTo(OutboxCommandStatus.PROCESSED);
            assertThat(command.getAttempts()).isEqualTo(1);
        });
        assertThat(audits.findAll().stream().filter(event -> event.getAggregateId().equals(seed.releaseId())).toList())
                .singleElement().satisfies(event -> assertThat(event.getPayloadJson()).contains("FIXTURE_VERDICT"));
        var execution=executions.findById(seed.executionId()).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(action==ArgoRolloutsGateway.Action.PROMOTE?
                RolloutExecutionStatus.RUNNING:action==ArgoRolloutsGateway.Action.ABORT?
                RolloutExecutionStatus.ABORTED:RolloutExecutionStatus.PAUSED);
        assertThat(steps.findById(seed.stepId()).orElseThrow().getStatus()).isEqualTo(
                verdict==AnalysisVerdict.PASS?RolloutStepStatus.PASSED:
                verdict==AnalysisVerdict.FAIL?RolloutStepStatus.FAILED:RolloutStepStatus.EVALUATING);
    }

    @Test
    void connectionDisabledAfterQueueingPreventsPersistedPromotionExecution() {
        var seed=seed(false);
        when(worker.evaluate(any())).thenReturn(new AnalysisWorkerGateway.Result(AnalysisVerdict.PASS,"FIXTURE_PASS","[]"));
        analysis.processOne();
        transactions.executeWithoutResult(tx -> prometheus.findById(seed.prometheusId()).orElseThrow().disable());

        assertThat(dispatcher.processOne(handler)).isTrue();

        verifyNoInteractions(argo);
        assertThat(outbox.findAll()).singleElement().satisfies(command -> assertThat(command.getStatus()).isEqualTo(OutboxCommandStatus.FAILED));
        assertThat(steps.findById(seed.stepId()).orElseThrow().getStatus()).isEqualTo(RolloutStepStatus.EVALUATING);
        assertThat(executions.findById(seed.executionId()).orElseThrow().getCurrentStepIndex()).isZero();
        assertThat(audits.findAll().stream().filter(event -> event.getAggregateId().equals(seed.releaseId()))).isEmpty();
    }

    @Test
    void unavailableConfiguredCredentialPersistsInconclusiveAndExecutesPause() {
        var seed=seed(true);
        analysis.processOne();
        assertThat(jobs.findById(seed.jobId()).orElseThrow().getReasonCode()).isEqualTo("SECRET_UNAVAILABLE");
        assertThat(dispatcher.processOne(handler)).isTrue();
        verifyNoInteractions(worker);
        verify(argo).control(argThat(request -> request.action()==ArgoRolloutsGateway.Action.PAUSE));
        assertThat(executions.findById(seed.executionId()).orElseThrow().getStatus()).isEqualTo(RolloutExecutionStatus.PAUSED);
    }

    @Test
    void workerExceptionPersistsSafeFailureAndExecutesPause() {
        var seed=seed(false);
        when(worker.evaluate(any())).thenThrow(new IllegalStateException("test-only-sensitive-token"));
        analysis.processOne();
        var job=jobs.findById(seed.jobId()).orElseThrow();
        assertThat(job.getVerdict()).isEqualTo(AnalysisVerdict.INCONCLUSIVE);
        assertThat(job.getReasonCode()).isEqualTo("PROMETHEUS_UNAVAILABLE");
        assertThat(job.getEvidenceJson()).isEqualTo("[]");
        dispatcher.processOne(handler);
        verify(argo).control(argThat(request -> request.action()==ArgoRolloutsGateway.Action.PAUSE));
        assertThat(audits.findAll().stream().filter(event -> event.getAggregateId().equals(seed.releaseId())).toList())
                .singleElement().satisfies(event -> assertThat(event.getPayloadJson()).doesNotContain("test-only-sensitive-token"));
    }

    private Seed seed(boolean credentialUnavailable) {
        when(secrets.resolve("env:TEST_CLUSTER_TOKEN")).thenReturn(Optional.of(new SecretResolver.SecretMaterial("test-only-cluster-token")));
        when(argo.control(any())).thenReturn(new ArgoRolloutsGateway.ObservedRollout("test-only-uid","2"));
        return transactions.execute(tx -> {
            jobs.deleteAll();outbox.deleteAll();steps.deleteAll();executions.deleteAll();snapshots.deleteAll();artifacts.deleteAll();releases.deleteAll();
            var now=Instant.now();
            var suffix=UUID.randomUUID().toString();
            var project=projects.save(Project.create("analysis-"+suffix,"Analysis fixture",null,now));
            var service=services.save(CatalogService.create(project.getId(),"app","App","https://example.invalid/app","test",now));
            var user=users.save(UserAccount.create("analysis-"+suffix,"test-only-hash","Fixture",Set.of(Role.DEVELOPER),now));
            var cluster=ClusterConnection.create("cluster-"+suffix,"https://k.example",List.of("demo"),"env:TEST_CLUSTER_TOKEN",now);
            cluster.validated(ConnectionStatus.ACTIVE,now);clusters.save(cluster);
            var prom=PrometheusConnection.create("prom-"+suffix,"https://prom.example",credentialUnavailable?"env:TEST_MISSING_TOKEN":null,10,now);
            prom.validated(ConnectionStatus.ACTIVE,now);prometheus.save(prom);
            var policy=policies.save(Policy.create("policy-"+suffix,now));
            String definition="{\"metrics\":[],\"inconclusivePolicy\":{\"additionalObservationSeconds\":120}}";
            var version=PolicyVersion.draft(policy.getId(),1,definition,"a".repeat(64),user.getId(),now);version.activate();versions.save(version);
            var env=Environment.create(service.getId(),"staging",cluster.getId(),"demo","app","app","stable","canary",prom.getId(),"app=x",version.getId(),now);
            env.complete(false,false,now);environments.save(env);
            var release=Release.pending(service.getId(),env.getId(),user.getId(),"1","fixture","a".repeat(40),"https://ci.example","key-"+suffix,now);
            release.approve(now);release.running();releases.save(release);
            artifacts.save(ReleaseArtifact.create(release.getId(),"registry/app","sha256:"+"b".repeat(64)));
            snapshots.save(PolicySnapshot.create(release.getId(),version.getId(),definition,"a".repeat(64),now));
            var execution=RolloutExecution.pending(release.getId(),cluster.getId(),"demo","app","sha256:"+"b".repeat(64),now);
            execution.started("test-only-uid","1",now);executions.save(execution);
            var step=RolloutStep.pending(execution.getId(),0,100,60);step.start(now);step.evaluating(now);steps.save(step);
            var job=jobs.save(AnalysisJob.pending(step.getId(),UUID.randomUUID(),1,now.minusSeconds(61),now.minusSeconds(1)));
            return new Seed(job.getId(),release.getId(),execution.getId(),step.getId(),prom.getId());
        });
    }

    private record Seed(UUID jobId,UUID releaseId,UUID executionId,UUID stepId,UUID prometheusId) {}
}
