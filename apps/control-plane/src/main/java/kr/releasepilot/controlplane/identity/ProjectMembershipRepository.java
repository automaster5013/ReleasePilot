package kr.releasepilot.controlplane.identity;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership,UUID>{Optional<ProjectMembership> findByProjectIdAndUserId(UUID projectId,UUID userId);List<ProjectMembership> findByProjectIdOrderByCreatedAtAsc(UUID projectId);boolean existsByProjectIdAndUserId(UUID projectId,UUID userId);}
