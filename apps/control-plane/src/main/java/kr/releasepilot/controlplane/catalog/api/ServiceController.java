package kr.releasepilot.controlplane.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.releasepilot.controlplane.catalog.CatalogService;
import kr.releasepilot.controlplane.catalog.CatalogServiceService;
import kr.releasepilot.controlplane.identity.UserAccountPrincipal;
import kr.releasepilot.controlplane.identity.ProjectAccess;
import kr.releasepilot.controlplane.shared.error.NotFoundException;
import org.hibernate.validator.constraints.URL;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
@RequestMapping("/api/v1/projects/{projectId}/services")
public class ServiceController {
    private final CatalogServiceService service;
    private final ProjectAccess projectAccess;

    public ServiceController(CatalogServiceService service, ProjectAccess projectAccess) {
        this.service = service;
        this.projectAccess = projectAccess;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERATOR')")
    public ServiceResponse create(@PathVariable UUID projectId,
                                  @Valid @RequestBody CreateServiceRequest request,
                                  @AuthenticationPrincipal UserAccountPrincipal principal) {
        return ServiceResponse.from(service.create(projectId, request.key(), request.name(),
                request.repositoryUrl(), request.owner(), principal.id()));
    }

    @GetMapping
    public ServicePage list(@PathVariable UUID projectId,
                            @RequestParam(defaultValue = "20") int limit,
                            Authentication authentication) {
        if (!projectAccess.canView(projectId, authentication)) {
            throw new NotFoundException("PROJECT_NOT_FOUND", "Project not found");
        }
        List<ServiceResponse> items = service.list(projectId, Math.clamp(limit, 1, 100)).stream()
                .map(ServiceResponse::from)
                .toList();
        return new ServicePage(items, null);
    }

    public record CreateServiceRequest(
            @NotBlank @Size(max = 63)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String key,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500) @URL(protocol = "https") String repositoryUrl,
            @NotBlank @Size(max = 100) String owner
    ) {}

    public record ServiceResponse(UUID id, UUID projectId, String key, String name,
                                  String repositoryUrl, String owner, String status,
                                  Instant createdAt) {
        static ServiceResponse from(CatalogService service) {
            return new ServiceResponse(service.getId(), service.getProjectId(), service.getKey(),
                    service.getName(), service.getRepositoryUrl(), service.getOwner(),
                    service.getStatus().name(), service.getCreatedAt());
        }
    }

    public record ServicePage(List<ServiceResponse> items, String nextCursor) {}
}
