package kr.releasepilot.controlplane.rollout;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import kr.releasepilot.controlplane.shared.config.CorrelationIdFilter;

@Entity
@Table(name = "rollout_executions")
public class RolloutExecution {
    @Id private UUID id;
    @Column(name = "release_id", nullable = false, unique = true) private UUID releaseId;
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "cluster_id", nullable = false) private UUID clusterId;
    @Column(nullable = false, length = 63) private String namespace;
    @Column(name = "rollout_name", nullable = false, length = 253) private String rolloutName;
    @Column(name = "target_revision", nullable = false, length = 71) private String targetRevision;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RolloutExecutionStatus status;
    @Column(name = "current_step_index", nullable = false) private int currentStepIndex;
    @Column(name = "rollout_uid", length = 128) private String rolloutUid;
    @Column(name = "last_observed_resource_version", length = 128) private String lastObservedResourceVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Version private long version;

    protected RolloutExecution() {}

    public static RolloutExecution pending(UUID releaseId, UUID clusterId, String namespace,
                                           String rolloutName, String targetRevision, Instant now) {
        var execution = new RolloutExecution();
        execution.id = UUID.randomUUID();
        execution.releaseId = releaseId;
        execution.correlationId = CorrelationIdFilter.current();
        execution.clusterId = clusterId;
        execution.namespace = namespace;
        execution.rolloutName = rolloutName;
        execution.targetRevision = targetRevision;
        execution.status = RolloutExecutionStatus.PENDING;
        execution.currentStepIndex = 0;
        execution.createdAt = now;
        return execution;
    }

    public UUID getId() { return id; }
    public UUID getReleaseId() { return releaseId; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getClusterId() { return clusterId; }
    public String getNamespace() { return namespace; }
    public String getRolloutName() { return rolloutName; }
    public String getTargetRevision() { return targetRevision; }
    public RolloutExecutionStatus getStatus() { return status; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public String getRolloutUid() { return rolloutUid; }
    public String getLastObservedResourceVersion() { return lastObservedResourceVersion; }

    public void started(String uid, String resourceVersion, Instant now) {
        if (status == RolloutExecutionStatus.RUNNING && uid.equals(rolloutUid)) {
            lastObservedResourceVersion = resourceVersion;
            return;
        }
        if (status != RolloutExecutionStatus.PENDING) throw new IllegalStateException("Execution is not pending");
        rolloutUid = uid;
        lastObservedResourceVersion = resourceVersion;
        status = RolloutExecutionStatus.RUNNING;
        startedAt = now;
    }
    public void controlled(ArgoRolloutsGateway.Action action,String resourceVersion,Instant now){
        if(action==ArgoRolloutsGateway.Action.PROMOTE){if(status!=RolloutExecutionStatus.RUNNING)throw new IllegalStateException("Execution is not running");currentStepIndex++;}
        else if(action==ArgoRolloutsGateway.Action.PAUSE){if(status!=RolloutExecutionStatus.RUNNING)throw new IllegalStateException("Execution is not running");status=RolloutExecutionStatus.PAUSED;}
        else if(action==ArgoRolloutsGateway.Action.RESUME){if(status!=RolloutExecutionStatus.PAUSED)throw new IllegalStateException("Execution is not paused");status=RolloutExecutionStatus.RUNNING;}
        else {if(status!=RolloutExecutionStatus.RUNNING&&status!=RolloutExecutionStatus.PAUSED)throw new IllegalStateException("Execution cannot be aborted");status=RolloutExecutionStatus.ABORTED;finishedAt=now;}
        lastObservedResourceVersion=resourceVersion;
    }
    public void observed(String resourceVersion,int stepIndex){lastObservedResourceVersion=resourceVersion;if(stepIndex>currentStepIndex)currentStepIndex=stepIndex;}
    public void succeeded(Instant now){if(status!=RolloutExecutionStatus.RUNNING)throw new IllegalStateException("Execution is not running");status=RolloutExecutionStatus.SUCCEEDED;finishedAt=now;}
    public void failed(Instant now){if(status==RolloutExecutionStatus.SUCCEEDED||status==RolloutExecutionStatus.ABORTED)return;status=RolloutExecutionStatus.FAILED;finishedAt=now;}
}
