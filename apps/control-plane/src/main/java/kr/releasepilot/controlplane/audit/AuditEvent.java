package kr.releasepilot.controlplane.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import kr.releasepilot.controlplane.shared.config.CorrelationIdFilter;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;
    @Column(name = "actor_id")
    private UUID actorId;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;
    @Column(name = "chain_sequence", unique = true)
    private Long chainSequence;
    @Column(name = "previous_hash", length = 64)
    private String previousHash;
    @Column(name = "event_hash", length = 64, unique = true)
    private String eventHash;

    protected AuditEvent() {
    }

    public static AuditEvent projectCreated(UUID projectId, UUID actorId, Instant occurredAt) {
        return created("PROJECT", projectId, "PROJECT_CREATED", actorId, occurredAt);
    }

    public static AuditEvent serviceCreated(UUID serviceId, UUID actorId, Instant occurredAt) {
        return created("SERVICE", serviceId, "SERVICE_CREATED", actorId, occurredAt);
    }

    public static AuditEvent connectionCreated(String type, UUID connectionId, UUID actorId, Instant occurredAt) {
        return created(type, connectionId, type + "_CREATED", actorId, occurredAt);
    }

    public static AuditEvent connectionUpdated(String type, UUID connectionId, UUID actorId, Instant occurredAt) {
        return created(type, connectionId, type + "_UPDATED", actorId, occurredAt);
    }

    public static AuditEvent connectionStatusChanged(String type, UUID connectionId, UUID actorId, String status, Instant occurredAt) {
        var event=created(type,connectionId,type+"_"+("DISABLED".equals(status)?"DISABLED":"ENABLED"),actorId,occurredAt);
        event.payloadJson="{\"status\":\""+status+"\"}";return event;
    }

    public static AuditEvent clusterConnectionValidated(UUID connectionId,UUID actorId,String status,String failureCode,Instant occurredAt){
        var event=created("CLUSTER_CONNECTION",connectionId,"CLUSTER_CONNECTION_VALIDATED",actorId,occurredAt);
        event.payloadJson="{\"status\":\""+status+"\",\"failureCode\":\""+failureCode+"\"}";
        return event;
    }

    public static AuditEvent prometheusConnectionValidated(UUID connectionId,UUID actorId,String status,String failureCode,Instant occurredAt){
        var event=created("PROMETHEUS_CONNECTION",connectionId,"PROMETHEUS_CONNECTION_VALIDATED",actorId,occurredAt);
        event.payloadJson="{\"status\":\""+status+"\",\"failureCode\":\""+failureCode+"\"}";
        return event;
    }

    public static AuditEvent releaseRequested(UUID releaseId, UUID actorId, Instant occurredAt) {
        return created("RELEASE", releaseId, "RELEASE_REQUESTED", actorId, occurredAt);
    }

    public static AuditEvent releaseDecision(UUID releaseId, UUID actorId, String decision, Instant occurredAt) {
        return created("RELEASE", releaseId, "RELEASE_" + decision, actorId, occurredAt);
    }

    public static AuditEvent sessionsRevoked(UUID userId, UUID actorId, int count, String scope, Instant occurredAt) {
        var event = created("USER", userId, "SESSIONS_REVOKED", actorId, occurredAt);
        event.payloadJson = "{\"count\":" + count + ",\"scope\":\"" + scope + "\"}";
        return event;
    }

    public static AuditEvent environmentRevalidated(UUID environmentId, String status, String trigger, Instant occurredAt) {
        var event = created("ENVIRONMENT", environmentId, "ENVIRONMENT_REVALIDATED", null, occurredAt);
        event.actorType = "SYSTEM";
        event.payloadJson = "{\"status\":\"" + status + "\",\"trigger\":\"" + trigger + "\"}";
        return event;
    }

    public static AuditEvent environmentRevalidated(UUID environmentId, UUID actorId, String status, Instant occurredAt) {
        var event = created("ENVIRONMENT", environmentId, "ENVIRONMENT_REVALIDATED", actorId, occurredAt);
        event.payloadJson = "{\"status\":\"" + status + "\",\"trigger\":\"MANUAL\"}";
        return event;
    }

    public static AuditEvent rolloutOperation(UUID releaseId, UUID actorId, String phase, String operation,
                                              String payloadJson, Instant occurredAt) {
        var event=created("RELEASE",releaseId,"ROLLOUT_"+operation+"_"+phase,actorId,occurredAt);
        event.payloadJson=payloadJson;return event;
    }

    private static AuditEvent created(String aggregateType, UUID aggregateId, String eventType,
                                      UUID actorId, Instant occurredAt) {
        AuditEvent event = new AuditEvent();
        event.id = UUID.randomUUID();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.actorType = "USER";
        event.actorId = actorId;
        event.occurredAt = occurredAt;
        event.correlationId = CorrelationIdFilter.current();
        event.payloadJson = "{}";
        return event;
    }
    public UUID getId(){return id;} public String getAggregateType(){return aggregateType;} public UUID getAggregateId(){return aggregateId;}
    public String getEventType(){return eventType;} public String getActorType(){return actorType;} public UUID getActorId(){return actorId;}
    public Instant getOccurredAt(){return occurredAt;} public UUID getCorrelationId(){return correlationId;} public String getPayloadJson(){return payloadJson;}
    public Long getChainSequence(){return chainSequence;} public String getPreviousHash(){return previousHash;} public String getEventHash(){return eventHash;}
    void seal(long sequence,String previousHash,String eventHash){if(this.eventHash!=null)throw new IllegalStateException("Audit event is already sealed");this.chainSequence=sequence;this.previousHash=previousHash;this.eventHash=eventHash;}
}
