package kr.releasepilot.controlplane.environment;
import java.util.Set;
public interface KubernetesValidationGateway {
 Snapshot inspect(Environment environment);
 record Snapshot(boolean reachable,String apiVersion,String kind,RolloutStrategy strategy,
  String stableService,String canaryService,Set<String> existingServices,Set<String> rolloutVerbs,String rolloutUid){}
}
