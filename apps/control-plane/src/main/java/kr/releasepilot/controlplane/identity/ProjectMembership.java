package kr.releasepilot.controlplane.identity;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="project_memberships") public class ProjectMembership {
 @Id private UUID id; @Column(name="project_id",nullable=false) private UUID projectId; @Column(name="user_id",nullable=false) private UUID userId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; protected ProjectMembership(){}
 public static ProjectMembership create(UUID projectId,UUID userId,Role role,Instant now){if(role==Role.OPERATOR)throw new IllegalArgumentException("OPERATOR is a platform role");var m=new ProjectMembership();m.id=UUID.randomUUID();m.projectId=projectId;m.userId=userId;m.role=role;m.createdAt=now;return m;}
 public UUID getId(){return id;} public UUID getProjectId(){return projectId;} public UUID getUserId(){return userId;} public Role getRole(){return role;} public Instant getCreatedAt(){return createdAt;}
}
