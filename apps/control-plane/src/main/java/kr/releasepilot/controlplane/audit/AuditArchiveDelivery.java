package kr.releasepilot.controlplane.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="audit_archive_deliveries")
class AuditArchiveDelivery {
    enum Status { PENDING, DELIVERED }
    @Id private UUID id;
    @Column(name="audit_event_id",nullable=false,unique=true) private UUID auditEventId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(nullable=false) private int attempts;
    @Column(name="available_at",nullable=false) private Instant availableAt;
    @Column(name="delivered_at") private Instant deliveredAt;
    @Column(name="last_error",length=500) private String lastError;
    protected AuditArchiveDelivery(){}
    static AuditArchiveDelivery pending(UUID eventId,Instant now){var value=new AuditArchiveDelivery();value.id=UUID.randomUUID();value.auditEventId=eventId;value.status=Status.PENDING;value.availableAt=now;return value;}
    UUID auditEventId(){return auditEventId;}
    void delivered(Instant now){status=Status.DELIVERED;deliveredAt=now;lastError=null;}
    void failed(String error,Instant now){attempts++;lastError=error.substring(0,Math.min(error.length(),500));availableAt=now.plusSeconds(Math.min(300,1L<<Math.min(attempts,9)));}
}
