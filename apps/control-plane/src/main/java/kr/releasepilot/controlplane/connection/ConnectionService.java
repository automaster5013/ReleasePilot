package kr.releasepilot.controlplane.connection;

import kr.releasepilot.controlplane.audit.AuditEvent;
import kr.releasepilot.controlplane.audit.AuditEventRepository;
import kr.releasepilot.controlplane.shared.error.ConflictException;
import kr.releasepilot.controlplane.shared.error.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectionService {
    private final ClusterConnectionRepository clusters;
    private final PrometheusConnectionRepository prometheus;
    private final AuditEventRepository auditEvents;
    private final ClusterValidationGateway clusterValidation;
    private final Clock clock;
    public ConnectionService(ClusterConnectionRepository clusters, PrometheusConnectionRepository prometheus,
                             AuditEventRepository auditEvents,ClusterValidationGateway clusterValidation,Clock clock) {
        this.clusters=clusters; this.prometheus=prometheus; this.auditEvents=auditEvents; this.clusterValidation=clusterValidation;this.clock=clock;
    }
    @Transactional
    public ClusterConnection createCluster(String name,String apiServer,List<String> namespaces,String secretRef,UUID actorId){
        if(clusters.existsByName(name)) throw new ConflictException("CLUSTER_NAME_ALREADY_EXISTS","Cluster connection name already exists");
        var now=clock.instant(); var value=clusters.save(ClusterConnection.create(name,apiServer,namespaces,secretRef,now));
        auditEvents.save(AuditEvent.connectionCreated("CLUSTER_CONNECTION",value.getId(),actorId,now)); return value;
    }
    @Transactional
    public PrometheusConnection createPrometheus(String name,String baseUrl,String secretRef,int timeout,UUID actorId){
        if(prometheus.existsByName(name)) throw new ConflictException("PROMETHEUS_NAME_ALREADY_EXISTS","Prometheus connection name already exists");
        var now=clock.instant(); var value=prometheus.save(PrometheusConnection.create(name,baseUrl,secretRef,timeout,now));
        auditEvents.save(AuditEvent.connectionCreated("PROMETHEUS_CONNECTION",value.getId(),actorId,now)); return value;
    }
    @Transactional(readOnly=true) public List<ClusterConnection> listClusters(){return clusters.findAll(Sort.by("name"));}
    @Transactional(readOnly=true) public List<PrometheusConnection> listPrometheus(){return prometheus.findAll(Sort.by("name"));}
    @Transactional public ClusterValidationGateway.Result validateCluster(UUID clusterId,UUID actorId){
        var cluster=clusters.findById(clusterId).orElseThrow(()->new NotFoundException("CLUSTER_CONNECTION_NOT_FOUND","Cluster connection not found"));
        return validate(cluster,actorId);
    }
    @Transactional public List<ClusterValidation> validateAllClusters(UUID actorId){
        return clusters.findAll(Sort.by("name")).stream().map(cluster->new ClusterValidation(cluster,validate(cluster,actorId))).toList();
    }
    private ClusterValidationGateway.Result validate(ClusterConnection cluster,UUID actorId){
        var result=clusterValidation.validate(cluster);var now=clock.instant();cluster.validated(result.status(),now);
        auditEvents.save(AuditEvent.clusterConnectionValidated(cluster.getId(),actorId,result.status().name(),result.failureCode(),now));return result;
    }
    public record ClusterValidation(ClusterConnection cluster,ClusterValidationGateway.Result result) {}
}
