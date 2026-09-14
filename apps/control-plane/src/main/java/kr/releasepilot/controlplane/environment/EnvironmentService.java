package kr.releasepilot.controlplane.environment;
import kr.releasepilot.controlplane.audit.*; import kr.releasepilot.controlplane.catalog.*; import kr.releasepilot.controlplane.connection.*; import kr.releasepilot.controlplane.shared.error.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.*; import java.util.*;
@Service
public class EnvironmentService {
 private final CatalogServiceRepository services; private final ClusterConnectionRepository clusters; private final PrometheusConnectionRepository prometheus;
 private final EnvironmentRepository environments; private final EnvironmentValidationResultRepository results; private final EnvironmentInspector inspector; private final AuditTrail audits; private final Clock clock;
 public EnvironmentService(CatalogServiceRepository services,ClusterConnectionRepository clusters,PrometheusConnectionRepository prometheus,EnvironmentRepository environments,EnvironmentValidationResultRepository results,EnvironmentInspector inspector,AuditTrail audits,Clock clock){this.services=services;this.clusters=clusters;this.prometheus=prometheus;this.environments=environments;this.results=results;this.inspector=inspector;this.audits=audits;this.clock=clock;}
 @Transactional public Environment create(UUID serviceId,String name,UUID clusterId,String namespace,String rollout,String containerName,RolloutStrategy strategy,String stable,String canary,UUID prometheusId,String selector,UUID policyId){
  if(!services.existsById(serviceId))throw new NotFoundException("SERVICE_NOT_FOUND","Service not found");
  var cluster=clusters.findById(clusterId).orElseThrow(()->new NotFoundException("CLUSTER_CONNECTION_NOT_FOUND","Cluster connection not found"));
  if(!cluster.getAllowedNamespaces().contains(namespace))throw new ConflictException("NAMESPACE_NOT_ALLOWED","Namespace is not allowed by cluster connection");
  if(!prometheus.existsById(prometheusId))throw new NotFoundException("PROMETHEUS_CONNECTION_NOT_FOUND","Prometheus connection not found");
  if(environments.existsByServiceIdAndName(serviceId,name))throw new ConflictException("ENVIRONMENT_NAME_ALREADY_EXISTS","Environment name already exists in service");
  return environments.save(Environment.create(serviceId,name,clusterId,namespace,rollout,containerName,strategy,stable,canary,prometheusId,selector,policyId,clock.instant()));}
 @Transactional(readOnly=true) public List<Environment> list(UUID serviceId,int limit){
  if(!services.existsById(serviceId))throw new NotFoundException("SERVICE_NOT_FOUND","Service not found");
  return environments.findAllByServiceIdOrderByCreatedAtDesc(serviceId,PageRequest.of(0,Math.clamp(limit,1,100)));}
 @Transactional public ValidationReport validate(UUID id){
  var env=environments.findById(id).orElseThrow(()->new NotFoundException("ENVIRONMENT_NOT_FOUND","Environment not found")); var now=clock.instant(); env.validating(now); return inspect(env,now);}
 @Transactional public boolean claimDue(UUID id,Instant now,Instant activeCutoff,Instant failureCutoff,Instant staleBefore){return environments.claimDue(id,now,activeCutoff,failureCutoff,staleBefore,EnvironmentStatus.VALIDATING,EnvironmentStatus.INVALID,EnvironmentStatus.DISABLED)==1;}
 @Transactional(readOnly=true) public List<UUID> dueIds(Instant activeCutoff,Instant failureCutoff,Instant staleBefore,int batch){return environments.findDueIds(activeCutoff,failureCutoff,staleBefore,EnvironmentStatus.VALIDATING,EnvironmentStatus.INVALID,EnvironmentStatus.DISABLED,PageRequest.of(0,batch));}
 @Transactional public ValidationReport revalidateClaimed(UUID id){
  var env=environments.findById(id).orElseThrow(()->new NotFoundException("ENVIRONMENT_NOT_FOUND","Environment not found"));
  if(env.getStatus()!=EnvironmentStatus.VALIDATING)throw new ConflictException("ENVIRONMENT_VALIDATION_NOT_CLAIMED","Environment validation lease is not held");
  var report=inspect(env,clock.instant()); audits.record(AuditEvent.environmentRevalidated(id,env.getStatus().name(),"SCHEDULED",report.checkedAt())); return report;}
 @Transactional public void failScheduledValidation(UUID id){
  var env=environments.findById(id).orElseThrow(()->new NotFoundException("ENVIRONMENT_NOT_FOUND","Environment not found")); var now=clock.instant();
  if(env.getStatus()!=EnvironmentStatus.VALIDATING)return; results.deleteByEnvironmentId(id);
  results.save(EnvironmentValidationResult.of(id,"PERIODIC_VALIDATION_ERROR",ValidationOutcome.FAIL,"Periodic validation could not complete","{}",now)); env.complete(true,false,now);
  audits.record(AuditEvent.environmentRevalidated(id,env.getStatus().name(),"SCHEDULED",now));}
 private ValidationReport inspect(Environment env,Instant now){
  results.deleteByEnvironmentId(env.getId()); var checks=inspector.inspect(env); checks.forEach(c->results.save(EnvironmentValidationResult.of(env.getId(),c.code(),c.outcome(),c.message(),c.detailsJson(),now)));
  boolean fail=checks.stream().anyMatch(c->c.outcome()==ValidationOutcome.FAIL), warning=checks.stream().anyMatch(c->c.outcome()==ValidationOutcome.WARNING); env.complete(fail,warning,now); return new ValidationReport(env,List.copyOf(checks),now);}
 @Transactional(readOnly=true) public ValidationReport latest(UUID id){
  var env=environments.findById(id).orElseThrow(()->new NotFoundException("ENVIRONMENT_NOT_FOUND","Environment not found"));
  if(env.getValidatedAt()==null)throw new NotFoundException("ENVIRONMENT_VALIDATION_NOT_FOUND","Environment has not been validated");
  var checks=results.findByEnvironmentIdOrderByCheckedAtAsc(id).stream().map(v->new EnvironmentInspector.Check(v.getCheckCode(),v.getOutcome(),v.getMessage(),v.getDetailsJson())).toList();
  return new ValidationReport(env,checks,env.getValidatedAt());}
 public record ValidationReport(Environment environment,List<EnvironmentInspector.Check> checks,java.time.Instant checkedAt){}
}
