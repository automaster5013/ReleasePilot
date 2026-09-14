package kr.releasepilot.controlplane.environment;
import java.util.UUID;
public interface PolicyValidationGateway {
 Snapshot inspect(UUID policyVersionId);
 record Snapshot(boolean exists,boolean active,boolean semanticallyValid){}
}
