package kr.releasepilot.controlplane.environment;
import org.junit.jupiter.api.Test; import java.time.Instant; import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
class DefaultEnvironmentInspectorTests {
 @Test void allChecksPassForValidCanaryTarget(){
  var checks=inspector(validKubernetes(),validPrometheus(),validPolicy()).inspect(environment());
  assertThat(checks).hasSize(11).allMatch(c->c.outcome()==ValidationOutcome.PASS);
 }
 @Test void blueGreenAndMissingRbacAreReportedSeparately(){
  KubernetesValidationGateway gateway=ignored->new KubernetesValidationGateway.Snapshot(true,"argoproj.io/v1alpha1","Rollout",false,"checkout-stable","checkout-canary",Set.of("checkout-stable","checkout-canary"),Set.of("get","watch"),"uid-1");
  var checks=inspector(gateway,validPrometheus(),validPolicy()).inspect(environment());
  assertThat(outcome(checks,"CANARY_STRATEGY")).isEqualTo(ValidationOutcome.FAIL);
  assertThat(outcome(checks,"ROLLOUT_RBAC")).isEqualTo(ValidationOutcome.FAIL);
  assertThat(checks.stream().filter(c->c.code().equals("ROLLOUT_RBAC")).findFirst().orElseThrow().detailsJson()).contains("patch");
 }
 @Test void missingReleaseTrackFailsEvenWhenPrometheusIsReachable(){
  PrometheusValidationGateway gateway=ignored->new PrometheusValidationGateway.Snapshot(true,true,true,false);
  var checks=inspector(validKubernetes(),gateway,validPolicy()).inspect(environment());
  assertThat(outcome(checks,"PROMETHEUS_REACHABLE")).isEqualTo(ValidationOutcome.PASS);
  assertThat(outcome(checks,"RELEASE_TRACK_LABEL")).isEqualTo(ValidationOutcome.FAIL);
 }
 private DefaultEnvironmentInspector inspector(KubernetesValidationGateway k,PrometheusValidationGateway p,PolicyValidationGateway policy){return new DefaultEnvironmentInspector(k,p,policy);}
 private KubernetesValidationGateway validKubernetes(){return ignored->new KubernetesValidationGateway.Snapshot(true,"argoproj.io/v1alpha1","Rollout",true,"checkout-stable","checkout-canary",Set.of("checkout-stable","checkout-canary"),Set.of("get","watch","patch"),"uid-1");}
 private PrometheusValidationGateway validPrometheus(){return ignored->new PrometheusValidationGateway.Snapshot(true,true,true,true);}
 private PolicyValidationGateway validPolicy(){return ignored->new PolicyValidationGateway.Snapshot(true,true,true);}
 private ValidationOutcome outcome(List<EnvironmentInspector.Check> checks,String code){return checks.stream().filter(c->c.code().equals(code)).findFirst().orElseThrow().outcome();}
 private Environment environment(){return Environment.create(UUID.randomUUID(),"production",UUID.randomUUID(),"releasepilot-demo","checkout","checkout","checkout-stable","checkout-canary",UUID.randomUUID(),"service_name=checkout",UUID.randomUUID(),Instant.now());}
}
