package kr.releasepilot.controlplane.rollout;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface RolloutExecutionRepository extends JpaRepository<RolloutExecution, UUID> {
    Optional<RolloutExecution> findByReleaseId(UUID releaseId);
    List<RolloutExecution> findByStatusIn(Collection<RolloutExecutionStatus> statuses);
}
