package kr.releasepilot.controlplane.rollout;
import kr.releasepilot.controlplane.connection.*;
import kr.releasepilot.controlplane.environment.*;
import kr.releasepilot.controlplane.release.*;
import kr.releasepilot.controlplane.notification.GithubCheckService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RolloutCompletionGateTests {
 @Test void healthyCannotCompleteUntilEveryPolicyStepPassed() {
  var now=Instant.now();var executions=mock(RolloutExecutionRepository.class);var releases=mock(ReleaseRepository.class);
  var artifacts=mock(ReleaseArtifactRepository.class);var environments=mock(EnvironmentRepository.class);
  var clusters=mock(ClusterConnectionRepository.class);var secrets=mock(SecretResolver.class);
  var argo=mock(ArgoRolloutsGateway.class);var checks=mock(GithubCheckService.class);var steps=mock(RolloutStepRepository.class);
  var release=Release.pending(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"v3","change","a".repeat(40),"https://ci.example","completion-key",now);
  release.approve(now);release.running();
  var artifact=ReleaseArtifact.create(release.getId(),"registry/app","sha256:"+"b".repeat(64));
  var cluster=ClusterConnection.create("cluster","https://k.example",List.of("demo"),"file:token",now);
  var env=Environment.create(release.getServiceId(),"staging",cluster.getId(),"demo","app","app","stable","canary",UUID.randomUUID(),"app=x",UUID.randomUUID(),now);
  var execution=RolloutExecution.pending(release.getId(),cluster.getId(),"demo","app",artifact.getImageDigest(),now);execution.started("uid","1",now);
  var first=RolloutStep.pending(execution.getId(),0,20,60);first.start(now);first.pass(now);
  var last=RolloutStep.pending(execution.getId(),1,100,60);last.start(now);
  when(executions.findByStatusIn(any())).thenReturn(List.of(execution));
  when(releases.findById(release.getId())).thenReturn(Optional.of(release));when(artifacts.findByReleaseId(release.getId())).thenReturn(Optional.of(artifact));
  when(environments.findById(release.getEnvironmentId())).thenReturn(Optional.of(env));when(clusters.findById(cluster.getId())).thenReturn(Optional.of(cluster));
  when(secrets.resolve("file:token")).thenReturn(Optional.of(new SecretResolver.SecretMaterial("token")));
  when(argo.observe(any())).thenReturn(new ArgoRolloutsGateway.Observation("uid","2","Healthy",2,"registry/app@"+artifact.getImageDigest(),false,2,2));
  when(steps.findByExecutionIdOrderByStepIndexAsc(execution.getId())).thenReturn(List.of(first,last));
  var reconciler=new RolloutStateReconciler(executions,releases,artifacts,environments,clusters,secrets,argo,Clock.fixed(now,ZoneOffset.UTC),checks,steps);
  reconciler.reconcileActive();assertThat(release.getStatus()).isEqualTo(ReleaseStatus.RUNNING);
  assertThat(execution.getCurrentStepIndex()).isZero();
  last.pass(now);reconciler.reconcileActive();assertThat(release.getStatus()).isEqualTo(ReleaseStatus.SUCCEEDED);
 }
}
