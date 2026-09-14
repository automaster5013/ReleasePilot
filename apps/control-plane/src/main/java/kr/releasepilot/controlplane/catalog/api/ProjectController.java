package kr.releasepilot.controlplane.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.releasepilot.controlplane.catalog.Project;
import kr.releasepilot.controlplane.catalog.ProjectService;
import kr.releasepilot.controlplane.identity.UserAccountPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERATOR')")
    public ProjectResponse create(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal UserAccountPrincipal principal
    ) {
        return ProjectResponse.from(projectService.create(
                request.key(), request.name(), request.description(), principal.id()
        ));
    }

    @GetMapping
    public ProjectPage list(@RequestParam(defaultValue = "20") int limit,
                            @AuthenticationPrincipal UserAccountPrincipal principal) {
        int safeLimit = Math.clamp(limit, 1, 100);
        boolean operator = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"));
        List<ProjectResponse> projects = projectService.list(safeLimit, principal.id(), operator).stream()
                .map(ProjectResponse::from)
                .toList();
        return new ProjectPage(projects, null);
    }

    public record CreateProjectRequest(
            @NotBlank
            @Size(max = 63)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
            String key,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description
    ) {
    }

    public record ProjectResponse(
            UUID id,
            String key,
            String name,
            String description,
            String status,
            Instant createdAt
    ) {
        static ProjectResponse from(Project project) {
            return new ProjectResponse(
                    project.getId(),
                    project.getKey(),
                    project.getName(),
                    project.getDescription(),
                    project.getStatus().name(),
                    project.getCreatedAt()
            );
        }
    }

    public record ProjectPage(List<ProjectResponse> items, String nextCursor) {
    }
}
