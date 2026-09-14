package kr.releasepilot.controlplane.policy;
import kr.releasepilot.controlplane.environment.*; import org.springframework.stereotype.Component; import tools.jackson.databind.ObjectMapper; import java.util.UUID;
@Component
public class JpaPolicyValidationGateway implements PolicyValidationGateway {
 private final PolicyVersionRepository versions; private final PolicyRepository policies; private final PolicyDefinitionValidator validator; private final ObjectMapper json;
 public JpaPolicyValidationGateway(PolicyVersionRepository versions,PolicyRepository policies,PolicyDefinitionValidator validator,ObjectMapper json){this.versions=versions;this.policies=policies;this.validator=validator;this.json=json;}
 public Snapshot inspect(UUID id){var version=versions.findById(id).orElse(null);if(version==null)return new Snapshot(false,false,false,null);var policy=policies.findById(version.getPolicyId()).orElse(null);boolean active=policy!=null&&policy.getStatus()==PolicyStatus.ACTIVE&&version.getStatus()==PolicyVersionStatus.ACTIVE;RolloutStrategy strategy=null;try{strategy=RolloutStrategy.valueOf(json.readTree(version.getDefinition()).path("strategy").asText());}catch(Exception ignored){}return new Snapshot(true,active,validator.validate(version.getDefinition()).valid(),strategy);}
}
