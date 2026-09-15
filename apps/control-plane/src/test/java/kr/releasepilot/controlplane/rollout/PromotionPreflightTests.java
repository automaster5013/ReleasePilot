package kr.releasepilot.controlplane.rollout;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import kr.releasepilot.controlplane.audit.AuditTrail;
import kr.releasepilot.controlplane.connection.*;
import kr.releasepilot.controlplane.environment.*;
import kr.releasepilot.controlplane.notification.GithubCheckService;
import kr.releasepilot.controlplane.release.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class PromotionPreflightTests {
    private static final Instant NOW=Instant.parse("2026-09-15T00:00:00Z");

    @ParameterizedTest
    @ValueSource(strings={"environment-inactive","environment-expired","environment-null",
            "cluster-inactive","cluster-expired","cluster-null",
            "prometheus-inactive","prometheus-expired","prometheus-null"})
    void queuedPromotionChecksCurrentReadinessBeforeMutation(String scenario) {
        var fixture=new Fixture();
        String code;
        switch(scenario) {
            case "environment-inactive" -> {fixture.environment.complete(true,false,NOW);code="ENVIRONMENT_NOT_ACTIVE";}
            case "environment-expired" -> {fixture.environment.complete(false,false,NOW.minus(Duration.ofHours(6)));code="ENVIRONMENT_VALIDATION_STALE";}
            case "environment-null" -> {fixture.environment.complete(false,false,null);code="ENVIRONMENT_VALIDATION_STALE";}
            case "cluster-inactive" -> {fixture.cluster.disable();code="CLUSTER_CONNECTION_NOT_ACTIVE";}
            case "cluster-expired" -> {fixture.cluster.validated(ConnectionStatus.ACTIVE,NOW.minus(Duration.ofHours(6)));code="CLUSTER_CONNECTION_VALIDATION_STALE";}
            case "cluster-null" -> {fixture.cluster.validated(ConnectionStatus.ACTIVE,null);code="CLUSTER_CONNECTION_VALIDATION_STALE";}
            case "prometheus-inactive" -> {fixture.prometheus.disable();code="PROMETHEUS_CONNECTION_NOT_ACTIVE";}
            case "prometheus-expired" -> {fixture.prometheus.validated(ConnectionStatus.ACTIVE,NOW.minus(Duration.ofHours(6)));code="PROMETHEUS_CONNECTION_VALIDATION_STALE";}
            default -> {fixture.prometheus.validated(ConnectionStatus.ACTIVE,null);code="PROMETHEUS_CONNECTION_VALIDATION_STALE";}
        }
        assertThatThrownBy(()->fixture.handler.handle(fixture.command("PROMOTE_ROLLOUT"))).hasMessage(code);
        verifyNoInteractions(fixture.secrets,fixture.argo,fixture.audits);
        assertThat(fixture.execution.getCurrentStepIndex()).isZero();
        assertThat(fixture.step.getStatus()).isEqualTo(RolloutStepStatus.RUNNING);
    }

    @Test
    void healthyPromotionStillControlsAndRecordsSuccess() {
        var fixture=new Fixture();
        fixture.handler.handle(fixture.command("PROMOTE_ROLLOUT"));
        var request=ArgumentCaptor.forClass(ArgoRolloutsGateway.ControlRequest.class);
        verify(fixture.argo).control(request.capture());
        assertThat(request.getValue().action()).isEqualTo(ArgoRolloutsGateway.Action.PROMOTE);
        assertThat(fixture.execution.getCurrentStepIndex()).isEqualTo(1);
        assertThat(fixture.step.getStatus()).isEqualTo(RolloutStepStatus.PASSED);
        verify(fixture.audits).record(any());
    }

    @Test
    void revalidatedConnectionAllowsRetryOfSameQueuedCommand() {
        var fixture=new Fixture();
        var command=fixture.command("PROMOTE_ROLLOUT");
        fixture.prometheus.disable();
        assertThatThrownBy(()->fixture.handler.handle(command)).hasMessage("PROMETHEUS_CONNECTION_NOT_ACTIVE");
        verifyNoInteractions(fixture.argo);
        fixture.prometheus.enable();
        fixture.prometheus.validated(ConnectionStatus.ACTIVE,NOW);
        fixture.handler.handle(command);
        verify(fixture.argo).control(any());
        assertThat(fixture.step.getStatus()).isEqualTo(RolloutStepStatus.PASSED);
    }

    @ParameterizedTest
    @ValueSource(strings={"PAUSE_ROLLOUT","ABORT_ROLLOUT"})
    void safetyActionsRemainAvailableWhenPrometheusIsDisabled(String type) {
        var fixture=new Fixture();
        fixture.prometheus.disable();
        fixture.environment.complete(false,false,NOW.minus(Duration.ofHours(7)));
        fixture.handler.handle(fixture.command(type));
        var request=ArgumentCaptor.forClass(ArgoRolloutsGateway.ControlRequest.class);
        verify(fixture.argo).control(request.capture());
        assertThat(request.getValue().action()).isEqualTo(type.equals("PAUSE_ROLLOUT")?
                ArgoRolloutsGateway.Action.PAUSE:ArgoRolloutsGateway.Action.ABORT);
    }

    private static class Fixture {
        final SecretResolver secrets=mock(SecretResolver.class);
        final ArgoRolloutsGateway argo=mock(ArgoRolloutsGateway.class);
        final AuditTrail audits=mock(AuditTrail.class);
        final Release release;
        final Environment environment;
        final ClusterConnection cluster;
        final PrometheusConnection prometheus;
        final RolloutExecution execution;
        final RolloutStep step;
        final StartRolloutCommandHandler handler;
        final ObjectMapper json=new ObjectMapper();

        Fixture() {
            var executions=mock(RolloutExecutionRepository.class);
            var releases=mock(ReleaseRepository.class);
            var artifacts=mock(ReleaseArtifactRepository.class);
            var environments=mock(EnvironmentRepository.class);
            var clusters=mock(ClusterConnectionRepository.class);
            var connections=mock(PrometheusConnectionRepository.class);
            var steps=mock(RolloutStepRepository.class);
            release=Release.pending(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"1","change",
                    "a".repeat(40),"https://ci","request-key",NOW);
            var artifact=ReleaseArtifact.create(release.getId(),"registry/app","sha256:"+"b".repeat(64));
            cluster=ClusterConnection.create("cluster","https://k.example",List.of("demo"),"env:TEST_ONLY_TOKEN",NOW);
            cluster.validated(ConnectionStatus.ACTIVE,NOW);
            prometheus=PrometheusConnection.create("prometheus","https://prom.example",null,10,NOW);
            prometheus.validated(ConnectionStatus.ACTIVE,NOW);
            environment=Environment.create(release.getServiceId(),"production",cluster.getId(),"demo","app","app",
                    "stable","canary",prometheus.getId(),"app=x",UUID.randomUUID(),NOW);
            environment.complete(false,false,NOW);
            execution=RolloutExecution.pending(release.getId(),cluster.getId(),"demo","app",artifact.getImageDigest(),NOW);
            execution.started("test-only-uid","1",NOW);
            release.approve(NOW);
            release.running();
            step=RolloutStep.pending(execution.getId(),0,100,60);
            step.start(NOW);
            when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));
            when(releases.findById(release.getId())).thenReturn(Optional.of(release));
            when(artifacts.findByReleaseId(release.getId())).thenReturn(Optional.of(artifact));
            when(environments.findById(release.getEnvironmentId())).thenReturn(Optional.of(environment));
            when(clusters.findById(cluster.getId())).thenReturn(Optional.of(cluster));
            when(connections.findById(prometheus.getId())).thenReturn(Optional.of(prometheus));
            when(steps.findByExecutionIdOrderByStepIndexAsc(execution.getId())).thenReturn(List.of(step));
            when(secrets.resolve("env:TEST_ONLY_TOKEN")).thenReturn(Optional.of(new SecretResolver.SecretMaterial("test-only-token")));
            when(argo.control(any())).thenReturn(new ArgoRolloutsGateway.ObservedRollout("test-only-uid","2"));
            handler=new StartRolloutCommandHandler(executions,releases,artifacts,environments,clusters,connections,
                    secrets,argo,steps,audits,json,Clock.fixed(NOW,ZoneOffset.UTC),mock(GithubCheckService.class),
                    Duration.ofHours(6),Duration.ofHours(6));
        }

        RolloutCommandHandler.Command command(String type) {
            String payload=json.writeValueAsString(new RolloutOperationService.Payload(release.getId(),null,"Readiness regression test"));
            return new RolloutCommandHandler.Command(UUID.randomUUID(),execution.getId(),UUID.randomUUID(),type,payload,1);
        }
    }
}
