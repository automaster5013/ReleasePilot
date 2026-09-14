package kr.releasepilot.controlplane.policy;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="policy_versions") public class PolicyVersion {
 @Id private UUID id; @Column(name="policy_id",nullable=false) private UUID policyId; @Column(nullable=false) private int version;
 @Column(nullable=false,columnDefinition="json") private String definition; @Column(nullable=false,length=64) private String checksum;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PolicyVersionStatus status;
 @Column(name="created_by",nullable=false) private UUID createdBy; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; protected PolicyVersion(){}
 public static PolicyVersion draft(UUID policyId,int version,String definition,String checksum,UUID actor,Instant now){var p=new PolicyVersion();p.id=UUID.randomUUID();p.policyId=policyId;p.version=version;p.definition=definition;p.checksum=checksum;p.status=PolicyVersionStatus.DRAFT;p.createdBy=actor;p.createdAt=now;return p;}
 public void activate(){if(status!=PolicyVersionStatus.DRAFT)throw new IllegalStateException("Only draft policy versions can be activated");status=PolicyVersionStatus.ACTIVE;}
 public UUID getId(){return id;} public UUID getPolicyId(){return policyId;} public int getVersion(){return version;} public String getDefinition(){return definition;} public String getChecksum(){return checksum;} public PolicyVersionStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
}
