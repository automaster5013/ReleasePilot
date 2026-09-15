package kr.releasepilot.controlplane.connection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import kr.releasepilot.controlplane.identity.UserAccountPrincipal;
import org.hibernate.validator.constraints.URL;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/connections")
public class ConnectionController {
    private final ConnectionService service;
    public ConnectionController(ConnectionService service){this.service=service;}

    @PostMapping("/clusters") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('OPERATOR')")
    ClusterResponse createCluster(@Valid @RequestBody CreateClusterRequest request,@AuthenticationPrincipal UserAccountPrincipal principal){
        return ClusterResponse.from(service.createCluster(request.name(),request.apiServer(),request.allowedNamespaces(),request.secretRef(),principal.id()));
    }
    @GetMapping("/clusters") List<ClusterResponse> clusters(){return service.listClusters().stream().map(ClusterResponse::from).toList();}
    @PostMapping("/clusters/{clusterId}/validate") @PreAuthorize("hasRole('OPERATOR')")
    ClusterValidationResponse validateCluster(@PathVariable UUID clusterId,@AuthenticationPrincipal UserAccountPrincipal principal){
        return ClusterValidationResponse.from(clusterId,service.validateCluster(clusterId,principal.id()));
    }
    @PostMapping("/clusters/validate") @PreAuthorize("hasRole('OPERATOR')")
    List<ClusterValidationResponse> validateClusters(@AuthenticationPrincipal UserAccountPrincipal principal){
        return service.validateAllClusters(principal.id()).stream().map(v->ClusterValidationResponse.from(v.cluster().getId(),v.result())).toList();
    }
    @PostMapping("/prometheus") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('OPERATOR')")
    PrometheusResponse createPrometheus(@Valid @RequestBody CreatePrometheusRequest request,@AuthenticationPrincipal UserAccountPrincipal principal){
        return PrometheusResponse.from(service.createPrometheus(request.name(),request.baseUrl(),request.secretRef(),request.queryTimeoutSeconds(),principal.id()));
    }
    @GetMapping("/prometheus") List<PrometheusResponse> prometheus(){return service.listPrometheus().stream().map(PrometheusResponse::from).toList();}
    @PostMapping("/prometheus/{connectionId}/validate") @PreAuthorize("hasRole('OPERATOR')")
    PrometheusValidationResponse validatePrometheus(@PathVariable UUID connectionId,@AuthenticationPrincipal UserAccountPrincipal principal){return PrometheusValidationResponse.from(connectionId,service.validatePrometheus(connectionId,principal.id()));}

    public record CreateClusterRequest(@NotBlank @Size(max=100) String name,
        @NotBlank @URL(protocol="https") @Size(max=500) String apiServer,
        @NotEmpty @Size(max=100) List<@Pattern(regexp="^[a-z0-9]([-a-z0-9]*[a-z0-9])?$") String> allowedNamespaces,
        @NotBlank @Pattern(regexp="^[A-Za-z0-9._:/-]+$") @Size(max=255) String secretRef){}
    public record CreatePrometheusRequest(@NotBlank @Size(max=100) String name,
        @NotBlank @URL @Size(max=500) String baseUrl,
        @Pattern(regexp="^[A-Za-z0-9._:/-]+$") @Size(max=255) String secretRef,
        @Min(1) @Max(120) int queryTimeoutSeconds){}
    public record ClusterResponse(UUID id,String name,String apiServer,List<String> allowedNamespaces,
        String secretRef,String status,Instant lastValidatedAt,Instant createdAt){
        static ClusterResponse from(ClusterConnection v){return new ClusterResponse(v.getId(),v.getName(),v.getApiServer(),v.getAllowedNamespaces(),v.getSecretRef(),v.getStatus().name(),v.getLastValidatedAt(),v.getCreatedAt());}}
    public record ClusterValidationResponse(UUID clusterId,String status,String serverVersion,String failureCode,
                                            List<ClusterValidationGateway.NamespaceAccess> namespaces){
        static ClusterValidationResponse from(UUID clusterId,ClusterValidationGateway.Result result){
            return new ClusterValidationResponse(clusterId,result.status().name(),result.serverVersion(),result.failureCode(),result.namespaces());
        }}
    public record PrometheusResponse(UUID id,String name,String baseUrl,String secretRef,int queryTimeoutSeconds,
        String status,Instant lastValidatedAt,Instant createdAt){
        static PrometheusResponse from(PrometheusConnection v){return new PrometheusResponse(v.getId(),v.getName(),v.getBaseUrl(),v.getSecretRef(),v.getQueryTimeoutSeconds(),v.getStatus().name(),v.getLastValidatedAt(),v.getCreatedAt());}}
    public record PrometheusValidationResponse(UUID connectionId,String status,String failureCode){static PrometheusValidationResponse from(UUID id,PrometheusConnectionValidationGateway.Result result){return new PrometheusValidationResponse(id,result.status().name(),result.failureCode());}}
}
