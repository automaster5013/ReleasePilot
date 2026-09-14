package kr.releasepilot.controlplane.policy;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface PolicyVersionRepository extends JpaRepository<PolicyVersion,UUID>{Optional<PolicyVersion> findTopByPolicyIdOrderByVersionDesc(UUID policyId);boolean existsByPolicyIdAndStatus(UUID policyId,PolicyVersionStatus status);}
