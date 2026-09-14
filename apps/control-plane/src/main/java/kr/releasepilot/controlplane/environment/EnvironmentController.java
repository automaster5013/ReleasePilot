package kr.releasepilot.controlplane.environment;
import jakarta.validation.Valid; import jakarta.validation.constraints.*;
import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
import java.time.Instant; import java.util.*;
@RestController
public class EnvironmentController {
 private final EnvironmentService service;
 public EnvironmentController(EnvironmentService service){this.service=service;}
 @PostMapping("/api/v1/services/{serviceId}/environments") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('OPERATOR')")
 EnvironmentResponse create(@PathVariable UUID serviceId,@Valid @RequestBody CreateEnvironmentRequest r) {
  String selector=r.workloadLabelSelector().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->e.getKey()+"="+e.getValue()).reduce((a,b)->a+","+b).orElseThrow();
  return EnvironmentResponse.from(service.create(serviceId,r.name(),r.clusterId(),r.namespace(),r.rolloutName(),r.containerName(),r.stableServiceName(),r.canaryServiceName(),r.prometheusConnectionId(),selector,r.defaultPolicyVersionId()));}
 @PostMapping("/api/v1/environments/{id}/validate") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasRole('OPERATOR')")
 ValidationResponse validate(@PathVariable UUID id){var report=service.validate(id);return new ValidationResponse(report.environment().getId(),report.environment().getStatus().name(),report.checkedAt(),report.checks());}
 @GetMapping("/api/v1/environments/{id}/validation-results/latest")
 ValidationResponse latest(@PathVariable UUID id){var report=service.latest(id);return new ValidationResponse(report.environment().getId(),report.environment().getStatus().name(),report.checkedAt(),report.checks());}
 public record CreateEnvironmentRequest(@NotNull @Pattern(regexp="staging|production") String name,@NotNull UUID clusterId,
  @NotBlank @Pattern(regexp="^[a-z0-9]([-a-z0-9]*[a-z0-9])?$") String namespace,
  @NotBlank @Size(max=253) String rolloutName,@NotBlank @Size(max=253) @Pattern(regexp="^[a-z0-9]([-a-z0-9]*[a-z0-9])?$") String containerName,@NotBlank @Size(max=253) String stableServiceName,
  @NotBlank @Size(max=253) String canaryServiceName,@NotNull UUID prometheusConnectionId,
  @NotEmpty Map<@Pattern(regexp="^(service_namespace|service_name)$") String,@NotBlank @Size(max=100) String> workloadLabelSelector,
  @NotNull UUID defaultPolicyVersionId){}
 public record EnvironmentResponse(UUID id,UUID serviceId,String name,UUID clusterId,String namespace,String rolloutName,String containerName,String status,Instant createdAt){static EnvironmentResponse from(Environment e){return new EnvironmentResponse(e.getId(),e.getServiceId(),e.getName(),e.getClusterId(),e.getNamespace(),e.getRolloutName(),e.getContainerName(),e.getStatus().name(),e.getCreatedAt());}}
 public record ValidationResponse(UUID environmentId,String status,Instant checkedAt,List<EnvironmentInspector.Check> checks){}
}
