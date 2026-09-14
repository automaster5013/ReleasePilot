package kr.releasepilot.controlplane.policy.api;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import kr.releasepilot.controlplane.identity.UserAccountPrincipal; import kr.releasepilot.controlplane.policy.*;
import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*;
import java.time.Instant; import java.util.UUID;
import tools.jackson.databind.JsonNode;
@RestController @RequestMapping("/api/v1/policies") @PreAuthorize("hasRole('OPERATOR')")
public class PolicyController {
 private final PolicyService service;public PolicyController(PolicyService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) PolicyResponse create(@Valid @RequestBody CreatePolicyRequest r){return PolicyResponse.from(service.create(r.name()));}
 @PostMapping("/{policyId}/versions") @ResponseStatus(HttpStatus.CREATED) VersionResponse version(@PathVariable UUID policyId,@Valid @RequestBody CreateVersionRequest r,@AuthenticationPrincipal UserAccountPrincipal principal){return VersionResponse.from(service.createVersion(policyId,r.definition().toString(),principal.id()));}
 @PostMapping("/versions/{versionId}/activate") VersionResponse activate(@PathVariable UUID versionId){return VersionResponse.from(service.activate(versionId));}
 public record CreatePolicyRequest(@NotBlank @Size(max=100) String name){} public record CreateVersionRequest(@NotNull JsonNode definition){}
 public record PolicyResponse(UUID id,String name,String status,Instant createdAt){static PolicyResponse from(Policy p){return new PolicyResponse(p.getId(),p.getName(),p.getStatus().name(),p.getCreatedAt());}}
 public record VersionResponse(UUID id,UUID policyId,int version,String checksum,String status,Instant createdAt){static VersionResponse from(PolicyVersion p){return new VersionResponse(p.getId(),p.getPolicyId(),p.getVersion(),p.getChecksum(),p.getStatus().name(),p.getCreatedAt());}}
}
