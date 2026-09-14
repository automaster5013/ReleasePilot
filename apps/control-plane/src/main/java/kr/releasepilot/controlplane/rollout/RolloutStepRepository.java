package kr.releasepilot.controlplane.rollout;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.*;
public interface RolloutStepRepository extends JpaRepository<RolloutStep,UUID>{List<RolloutStep> findByExecutionIdOrderByStepIndexAsc(UUID executionId);List<RolloutStep> findByStatus(RolloutStepStatus status);}
