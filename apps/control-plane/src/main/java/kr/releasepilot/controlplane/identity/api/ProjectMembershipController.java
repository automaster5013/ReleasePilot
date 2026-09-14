package kr.releasepilot.controlplane.identity.api;
import jakarta.validation.Valid; import jakarta.validation.constraints.NotNull; import kr.releasepilot.controlplane.identity.*; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import java.time.Instant; import java.util.*;
@RestController @RequestMapping("/api/v1/projects/{projectId}/memberships")
public class ProjectMembershipController {
 private final ProjectMembershipService service;public ProjectMembershipController(ProjectMembershipService service){this.service=service;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('OPERATOR')") MembershipResponse add(@PathVariable UUID projectId,@Valid @RequestBody AddMembershipRequest r){return MembershipResponse.from(service.add(projectId,r.userId(),r.role()));}
 @GetMapping @PreAuthorize("hasRole('OPERATOR')") List<MembershipResponse> list(@PathVariable UUID projectId){return service.list(projectId).stream().map(MembershipResponse::from).toList();}
 public record AddMembershipRequest(@NotNull UUID userId,@NotNull Role role){}
 public record MembershipResponse(UUID id,UUID projectId,UUID userId,String role,Instant createdAt){static MembershipResponse from(ProjectMembership m){return new MembershipResponse(m.getId(),m.getProjectId(),m.getUserId(),m.getRole().name(),m.getCreatedAt());}}
}
