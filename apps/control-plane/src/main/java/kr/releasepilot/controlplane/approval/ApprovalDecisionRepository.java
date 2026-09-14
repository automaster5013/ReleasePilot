package kr.releasepilot.controlplane.approval;import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface ApprovalDecisionRepository extends JpaRepository<ApprovalDecision,UUID>{Optional<ApprovalDecision> findByReleaseId(UUID releaseId);Optional<ApprovalDecision> findByDecidedByAndIdempotencyKey(UUID actor,String key);}
