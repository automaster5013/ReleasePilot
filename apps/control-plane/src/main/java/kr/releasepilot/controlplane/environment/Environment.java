package kr.releasepilot.controlplane.environment;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="environments")
public class Environment {
 @Id private UUID id; @Column(name="service_id",nullable=false) private UUID serviceId;
 @Column(nullable=false,length=20) private String name; @Column(name="cluster_id",nullable=false) private UUID clusterId;
 @Column(nullable=false,length=63) private String namespace; @Column(name="rollout_name",nullable=false,length=253) private String rolloutName;
 @Column(name="container_name",nullable=false,length=253) private String containerName;
 @Column(name="stable_service_name",nullable=false,length=253) private String stableServiceName;
 @Column(name="canary_service_name",nullable=false,length=253) private String canaryServiceName;
 @Column(name="prometheus_connection_id",nullable=false) private UUID prometheusConnectionId;
 @Column(name="workload_label_selector",nullable=false,length=2000) private String workloadLabelSelector;
 @Column(name="default_policy_version_id",nullable=false) private UUID defaultPolicyVersionId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EnvironmentStatus status;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="validated_at") private Instant validatedAt;
 protected Environment(){}
 public static Environment create(UUID serviceId,String name,UUID clusterId,String namespace,String rolloutName,String containerName,String stable,String canary,UUID prometheusId,String selector,UUID policyId,Instant now){
  var v=new Environment();v.id=UUID.randomUUID();v.serviceId=serviceId;v.name=name;v.clusterId=clusterId;v.namespace=namespace;v.rolloutName=rolloutName;v.containerName=containerName;v.stableServiceName=stable;v.canaryServiceName=canary;v.prometheusConnectionId=prometheusId;v.workloadLabelSelector=selector;v.defaultPolicyVersionId=policyId;v.status=EnvironmentStatus.DRAFT;v.createdAt=now;return v;}
 public void validating(){status=EnvironmentStatus.VALIDATING;} public void complete(boolean failed,boolean warnings,Instant at){status=failed?EnvironmentStatus.INVALID:(warnings?EnvironmentStatus.ACTIVE_WITH_WARNINGS:EnvironmentStatus.ACTIVE);validatedAt=at;}
 public UUID getId(){return id;} public UUID getServiceId(){return serviceId;} public String getName(){return name;} public UUID getClusterId(){return clusterId;} public String getNamespace(){return namespace;} public String getRolloutName(){return rolloutName;} public String getContainerName(){return containerName;} public String getStableServiceName(){return stableServiceName;} public String getCanaryServiceName(){return canaryServiceName;} public UUID getPrometheusConnectionId(){return prometheusConnectionId;} public String getWorkloadLabelSelector(){return workloadLabelSelector;} public UUID getDefaultPolicyVersionId(){return defaultPolicyVersionId;} public EnvironmentStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public Instant getValidatedAt(){return validatedAt;}
}
