package kr.releasepilot.controlplane.connection;

import kr.releasepilot.controlplane.audit.AuditEvent;
import kr.releasepilot.controlplane.audit.AuditTrail;
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
    private final AuditTrail auditEvents;
    private final ClusterValidationGateway clusterValidation;
    private final PrometheusConnectionValidationGateway prometheusValidation;
    private final Clock clock;
    public ConnectionService(ClusterConnectionRepository clusters, PrometheusConnectionRepository prometheus,
                             AuditTrail auditEvents,ClusterValidationGateway clusterValidation,PrometheusConnectionValidationGateway prometheusValidation,Clock clock) {
        this.clusters=clusters; this.prometheus=prometheus; this.auditEvents=auditEvents; this.clusterValidation=clusterValidation;this.prometheusValidation=prometheusValidation;this.clock=clock;
    }
    @Transactional
    public ClusterConnection createCluster(String name,String apiServer,List<String> namespaces,String secretRef,UUID actorId){
        if(clusters.existsByName(name)) throw new ConflictException("CLUSTER_NAME_ALREADY_EXISTS","Cluster connection name already exists");
        var now=clock.instant(); var value=clusters.save(ClusterConnection.create(name,apiServer,namespaces,secretRef,now));
        auditEvents.record(AuditEvent.connectionCreated("CLUSTER_CONNECTION",value.getId(),actorId,now)); return value;
    }
    @Transactional
    public PrometheusConnection createPrometheus(String name,String baseUrl,String secretRef,int timeout,UUID actorId){
        if(prometheus.existsByName(name)) throw new ConflictException("PROMETHEUS_NAME_ALREADY_EXISTS","Prometheus connection name already exists");
        var now=clock.instant(); var value=prometheus.save(PrometheusConnection.create(name,baseUrl,secretRef,timeout,now));
        auditEvents.record(AuditEvent.connectionCreated("PROMETHEUS_CONNECTION",value.getId(),actorId,now)); return value;
    }
    @Transactional
    public ClusterConnection updateCluster(UUID id,String name,String apiServer,List<String> namespaces,String secretRef,UUID actorId){
        var value=clusters.findById(id).orElseThrow(()->new NotFoundException("CLUSTER_CONNECTION_NOT_FOUND","Cluster connection not found"));
        if(!value.getName().equals(name)&&clusters.existsByName(name)) throw new ConflictException("CLUSTER_NAME_ALREADY_EXISTS","Cluster connection name already exists");
        value.update(name,apiServer,namespaces,secretRef);var now=clock.instant();
        auditEvents.record(AuditEvent.connectionUpdated("CLUSTER_CONNECTION",id,actorId,now));return value;
    }
    @Transactional
    public PrometheusConnection updatePrometheus(UUID id,String name,String baseUrl,String secretRef,int timeout,UUID actorId){
        var value=prometheus.findById(id).orElseThrow(()->new NotFoundException("PROMETHEUS_CONNECTION_NOT_FOUND","Prometheus connection not found"));
        if(!value.getName().equals(name)&&prometheus.existsByName(name)) throw new ConflictException("PROMETHEUS_NAME_ALREADY_EXISTS","Prometheus connection name already exists");
        value.update(name,baseUrl,secretRef,timeout);var now=clock.instant();
        auditEvents.record(AuditEvent.connectionUpdated("PROMETHEUS_CONNECTION",id,actorId,now));return value;
    }
    @Transactional public ClusterConnection setClusterEnabled(UUID id,boolean enabled,UUID actorId){
        var value=clusters.findById(id).orElseThrow(()->new NotFoundException("CLUSTER_CONNECTION_NOT_FOUND","Cluster connection not found"));
        boolean changed=enabled?value.enable():value.disable();
        if(changed)auditEvents.record(AuditEvent.connectionStatusChanged("CLUSTER_CONNECTION",id,actorId,value.getStatus().name(),clock.instant()));return value;
    }
    @Transactional public PrometheusConnection setPrometheusEnabled(UUID id,boolean enabled,UUID actorId){
        var value=prometheus.findById(id).orElseThrow(()->new NotFoundException("PROMETHEUS_CONNECTION_NOT_FOUND","Prometheus connection not found"));
        boolean changed=enabled?value.enable():value.disable();
        if(changed)auditEvents.record(AuditEvent.connectionStatusChanged("PROMETHEUS_CONNECTION",id,actorId,value.getStatus().name(),clock.instant()));return value;
    }
    @Transactional(readOnly=true) public List<ClusterConnection> listClusters(){return clusters.findAll(Sort.by("name"));}
    @Transactional(readOnly=true) public List<PrometheusConnection> listPrometheus(){return prometheus.findAll(Sort.by("name"));}
    @Transactional public ClusterValidationGateway.Result validateCluster(UUID clusterId,UUID actorId){
        var cluster=clusters.findById(clusterId).orElseThrow(()->new NotFoundException("CLUSTER_CONNECTION_NOT_FOUND","Cluster connection not found"));
        if(cluster.getStatus()==ConnectionStatus.DISABLED)throw new ConflictException("CLUSTER_CONNECTION_DISABLED","Disabled cluster connection must be enabled before validation");
        return validate(cluster,actorId);
    }
    @Transactional public List<ClusterValidation> validateAllClusters(UUID actorId){
        return clusters.findAll(Sort.by("name")).stream().filter(cluster->cluster.getStatus()!=ConnectionStatus.DISABLED).map(cluster->new ClusterValidation(cluster,validate(cluster,actorId))).toList();
    }
    private ClusterValidationGateway.Result validate(ClusterConnection cluster,UUID actorId){
        var result=clusterValidation.validate(cluster);var now=clock.instant();cluster.validated(result.status(),now);
        auditEvents.record(AuditEvent.clusterConnectionValidated(cluster.getId(),actorId,result.status().name(),result.failureCode(),now));return result;
    }
    @Transactional public PrometheusConnectionValidationGateway.Result validatePrometheus(UUID connectionId,UUID actorId){
        var connection=prometheus.findById(connectionId).orElseThrow(()->new NotFoundException("PROMETHEUS_CONNECTION_NOT_FOUND","Prometheus connection not found"));
        if(connection.getStatus()==ConnectionStatus.DISABLED)throw new ConflictException("PROMETHEUS_CONNECTION_DISABLED","Disabled Prometheus connection must be enabled before validation");
        var result=prometheusValidation.validate(connection);var now=clock.instant();connection.validated(result.status(),now);
        auditEvents.record(AuditEvent.prometheusConnectionValidated(connection.getId(),actorId,result.status().name(),result.failureCode(),now));return result;
    }
    public record ClusterValidation(ClusterConnection cluster,ClusterValidationGateway.Result result) {}
}
