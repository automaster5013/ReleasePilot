package kr.releasepilot.controlplane.policy;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="policies") public class Policy {
 @Id private UUID id; @Column(nullable=false,unique=true,length=100) private String name;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PolicyStatus status;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; protected Policy(){}
 public static Policy create(String name,Instant now){var p=new Policy();p.id=UUID.randomUUID();p.name=name;p.status=PolicyStatus.ACTIVE;p.createdAt=now;return p;}
 public UUID getId(){return id;} public String getName(){return name;} public PolicyStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
}
