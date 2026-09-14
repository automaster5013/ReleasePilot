package kr.releasepilot.controlplane.environment;
import org.springframework.stereotype.Component; import java.util.*;
@Component
public class DefaultEnvironmentInspector implements EnvironmentInspector {
 private final KubernetesValidationGateway kubernetes; private final PrometheusValidationGateway prometheus; private final PolicyValidationGateway policies;
 public DefaultEnvironmentInspector(KubernetesValidationGateway kubernetes,PrometheusValidationGateway prometheus,PolicyValidationGateway policies){this.kubernetes=kubernetes;this.prometheus=prometheus;this.policies=policies;}
 public List<Check> inspect(Environment env){
  var k=kubernetes.inspect(env); var p=prometheus.inspect(env); var policy=policies.inspect(env.getDefaultPolicyVersionId());
  boolean rollout="argoproj.io/v1alpha1".equals(k.apiVersion())&&"Rollout".equals(k.kind());
  boolean traffic=env.getStableServiceName().equals(k.stableService())&&env.getCanaryServiceName().equals(k.canaryService());
  boolean services=k.existingServices().containsAll(Set.of(env.getStableServiceName(),env.getCanaryServiceName()));
  boolean rbac=k.rolloutVerbs().containsAll(Set.of("get","watch","patch"));
  boolean promReachable=p.ready()&&p.queryReachable(); boolean policyOk=policy.exists()&&policy.active()&&policy.semanticallyValid();
  return List.of(
   check("KUBERNETES_API_REACHABLE",k.reachable(),"Kubernetes API is reachable","Kubernetes API is unreachable","{}"),
   check("NAMESPACE_ALLOWED",true,"Namespace is allowed by the registered connection","Namespace is not allowed","{}"),
   check("ROLLOUT_RESOURCE",rollout,"Argo Rollout exists","Resource must be argoproj.io/v1alpha1 Rollout",json("observedKind",k.kind())),
   check("CANARY_STRATEGY",rollout&&k.canaryStrategy(),"Rollout uses Canary strategy","Rollout strategy is not Canary","{}"),
   check("TRAFFIC_SERVICES_MATCH",traffic,"Traffic service references match","Stable or Canary service reference does not match","{}"),
   check("KUBERNETES_SERVICES_EXIST",services,"Both Kubernetes Services exist","Stable or Canary Kubernetes Service is missing","{}"),
   check("ROLLOUT_RBAC",rbac,"Required Rollout permissions are granted","Required get/watch/patch permissions are missing","{\"requiredVerbs\":[\"get\",\"watch\",\"patch\"]}"),
   check("PROMETHEUS_REACHABLE",promReachable,"Prometheus ready and query endpoints are reachable","Prometheus endpoint is unreachable","{}"),
   check("RECENT_SERIES_AVAILABLE",p.recentSeries(),"Recent metric series exist","No recent metric series found","{}"),
   check("RELEASE_TRACK_LABEL",p.releaseTrackLabel(),"release_track label exists","release_track label is missing","{}"),
   check("DEFAULT_POLICY_ACTIVE",policyOk,"Default policy is active and valid","Default policy is missing, inactive, or invalid","{}"));
 }
 private Check check(String code,boolean pass,String ok,String fail,String details){return new Check(code,pass?ValidationOutcome.PASS:ValidationOutcome.FAIL,pass?ok:fail,details);}
 private String json(String key,String value){return "{\""+key+"\":\""+(value==null?"":value.replace("\"","\\\""))+"\"}";}
}
