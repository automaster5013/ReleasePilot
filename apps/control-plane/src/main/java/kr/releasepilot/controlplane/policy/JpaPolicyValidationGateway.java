package kr.releasepilot.controlplane.policy;
import kr.releasepilot.controlplane.environment.PolicyValidationGateway; import org.springframework.stereotype.Component; import java.util.UUID;
@Component
public class JpaPolicyValidationGateway implements PolicyValidationGateway {
 private final PolicyVersionRepository versions; private final PolicyRepository policies; private final PolicyDefinitionValidator validator;
 public JpaPolicyValidationGateway(PolicyVersionRepository versions,PolicyRepository policies,PolicyDefinitionValidator validator){this.versions=versions;this.policies=policies;this.validator=validator;}
 public Snapshot inspect(UUID id){var version=versions.findById(id).orElse(null);if(version==null)return new Snapshot(false,false,false);var policy=policies.findById(version.getPolicyId()).orElse(null);boolean active=policy!=null&&policy.getStatus()==PolicyStatus.ACTIVE&&version.getStatus()==PolicyVersionStatus.ACTIVE;return new Snapshot(true,active,validator.validate(version.getDefinition()).valid());}
}
