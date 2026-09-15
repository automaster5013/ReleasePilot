package kr.releasepilot.controlplane.rollout;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_commands")
public class OutboxCommand {
    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 60) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "command_type", nullable = false, length = 60) private String commandType;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(name = "payload_json", nullable = false, columnDefinition = "json") private String payloadJson;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OutboxCommandStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "last_error", length = 2000) private String lastError;

    protected OutboxCommand() {}

    public static OutboxCommand startRollout(RolloutExecution execution, Instant now) {
        var command = new OutboxCommand();
        command.id = UUID.randomUUID();
        command.aggregateType = "ROLLOUT_EXECUTION";
        command.aggregateId = execution.getId();
        command.correlationId = execution.getCorrelationId();
        command.commandType = "START_ROLLOUT";
        command.payloadJson = "{\"executionId\":\"" + execution.getId() + "\",\"releaseId\":\"" + execution.getReleaseId() + "\"}";
        command.idempotencyKey = "start-rollout:" + execution.getReleaseId();
        command.status = OutboxCommandStatus.PENDING;
        command.attempts = 0;
        command.availableAt = now;
        command.createdAt = now;
        return command;
    }

    public static OutboxCommand operation(RolloutExecution execution, String type, String idempotencyKey,
                                          String payloadJson, Instant now) {
        var command = new OutboxCommand(); command.id=UUID.randomUUID();command.aggregateType="ROLLOUT_EXECUTION";
        command.aggregateId=execution.getId();command.correlationId=execution.getCorrelationId();command.commandType=type;command.payloadJson=payloadJson;
        command.idempotencyKey=idempotencyKey;command.status=OutboxCommandStatus.PENDING;command.attempts=0;
        command.availableAt=now;command.createdAt=now;return command;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public UUID getCorrelationId() { return correlationId; }
    public String getCommandType() { return commandType; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OutboxCommandStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getAvailableAt() { return availableAt; }

    public Instant getClaimedAt() { return claimedAt; }

    public void claim(Instant now) {
        if (status != OutboxCommandStatus.PENDING && status != OutboxCommandStatus.FAILED) {
            throw new IllegalStateException("Only dispatchable commands can be claimed");
        }
        status = OutboxCommandStatus.PROCESSING;
        attempts++;
        claimedAt = now;
        lastError = null;
    }

    public void processed(Instant now) {
        if (status != OutboxCommandStatus.PROCESSING) throw new IllegalStateException("Command is not processing");
        status = OutboxCommandStatus.PROCESSED;
        processedAt = now;
        claimedAt = null;
    }

    public void retry(String error, Instant availableAt) {
        if (status != OutboxCommandStatus.PROCESSING) throw new IllegalStateException("Command is not processing");
        status = OutboxCommandStatus.FAILED;
        lastError = error == null ? "Unknown dispatch failure" : error.substring(0, Math.min(error.length(), 2000));
        this.availableAt = availableAt;
        claimedAt = null;
    }

    public void recoverStaleClaim(Instant now) {
        if (status != OutboxCommandStatus.PROCESSING) throw new IllegalStateException("Command is not processing");
        status = OutboxCommandStatus.FAILED;
        lastError = "Processing lease expired before completion";
        availableAt = now;
        claimedAt = null;
    }
}
