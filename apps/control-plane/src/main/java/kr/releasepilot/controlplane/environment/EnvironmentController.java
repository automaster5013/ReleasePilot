package kr.releasepilot.controlplane.environment;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import kr.releasepilot.controlplane.catalog.CatalogServiceRepository; import kr.releasepilot.controlplane.identity.ProjectAccess; import kr.releasepilot.controlplane.shared.error.NotFoundException;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import kr.releasepilot.controlplane.identity.UserAccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.time.Duration; import java.time.Instant; import java.util.*;
@RestController
public class EnvironmentController {
 private final EnvironmentService service; private final CatalogServiceRepository services; private final ProjectAccess projectAccess; private final Duration validationMaxAge;
 public EnvironmentController(EnvironmentService service,CatalogServiceRepository services,ProjectAccess projectAccess,@Value("${releasepilot.environment-revalidation.max-age:PT6H}")Duration validationMaxAge){this.service=service;this.services=services;this.projectAccess=projectAccess;this.validationMaxAge=validationMaxAge;}
 @PostMapping("/api/v1/services/{serviceId}/environments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('OPERATOR')")
 EnvironmentResponse create(@PathVariable UUID serviceId,@Valid @RequestBody CreateEnvironmentRequest r) {
  String selector=r.workloadLabelSelector().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+","+b).orElseThrow();
  return EnvironmentResponse.from(service.create(serviceId,r.name(),r.clusterId(),r.namespace(),r.rolloutName(),r.containerName(),r.strategy()==null?RolloutStrategy.CANARY:r.strategy(),r.stableServiceName(),r.canaryServiceName(),r.prometheusConnectionId(),selector,r.defaultPolicyVersionId()));}
 @GetMapping("/api/v1/services/{serviceId}/environments")
 EnvironmentPage list(@PathVariable UUID serviceId,@RequestParam(defaultValue="20") int limit,Authentication authentication){
  var catalogService=services.findById(serviceId).orElseThrow(()->new NotFoundException("SERVICE_NOT_FOUND","Service not found"));
  if(!projectAccess.canView(catalogService.getProjectId(),authentication))throw new NotFoundException("SERVICE_NOT_FOUND","Service not found");
  return new EnvironmentPage(service.list(serviceId,limit).stream().map(EnvironmentResponse::from).toList(),null);}
 @PostMapping("/api/v1/environments/{id}/validate") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasRole('OPERATOR')")
 ValidationResponse validate(@PathVariable UUID id,@AuthenticationPrincipal UserAccountPrincipal actor){return ValidationResponse.from(service.validate(id,actor.id()),validationMaxAge);}
 @GetMapping("/api/v1/environments/{id}/validation-results/latest")
 ValidationResponse latest(@PathVariable UUID id,Authentication authentication){var report=service.latest(id);var catalogService=services.findById(report.environment().getServiceId()).orElseThrow(()->new NotFoundException("ENVIRONMENT_NOT_FOUND","Environment not found"));if(!projectAccess.canView(catalogService.getProjectId(),authentication))throw new NotFoundException("ENVIRONMENT_NOT_FOUND","Environment not found");return ValidationResponse.from(report,validationMaxAge);}
 public record CreateEnvironmentRequest(@NotNull @Pattern(regexp="staging|production") String name,@NotNull UUID clusterId,
  @NotBlank @Pattern(regexp="^[a-z0-9]([-a-z0-9]*[a-z0-9])?$") String namespace,
  @NotBlank @Size(max=253) String rolloutName,@NotBlank @Size(max=253) @Pattern(regexp="^[a-z0-9]([-a-z0-9]*[a-z0-9])?$") String containerName,RolloutStrategy strategy,@NotBlank @Size(max=253) String stableServiceName,
  @NotBlank @Size(max=253) String canaryServiceName,@NotNull UUID prometheusConnectionId,
  @NotEmpty Map<@Pattern(regexp="^(service_namespace|service_name)$") String,@NotBlank @Size(max=100) String> workloadLabelSelector,
  @NotNull UUID defaultPolicyVersionId){}
 public record EnvironmentResponse(UUID id,UUID serviceId,String name,UUID clusterId,String namespace,String rolloutName,String containerName,String strategy,String status,Instant createdAt){static EnvironmentResponse from(Environment e){return new EnvironmentResponse(e.getId(),e.getServiceId(),e.getName(),e.getClusterId(),e.getNamespace(),e.getRolloutName(),e.getContainerName(),e.getRolloutStrategy().name(),e.getStatus().name(),e.getCreatedAt());}}
 public record EnvironmentPage(List<EnvironmentResponse> items,String nextCursor){}
 public record ValidationResponse(UUID environmentId,String status,Instant checkedAt,Instant validUntil,List<EnvironmentInspector.Check> checks){static ValidationResponse from(EnvironmentService.ValidationReport report,Duration maxAge){return new ValidationResponse(report.environment().getId(),report.environment().getStatus().name(),report.checkedAt(),report.checkedAt().plus(maxAge),report.checks());}}
}
