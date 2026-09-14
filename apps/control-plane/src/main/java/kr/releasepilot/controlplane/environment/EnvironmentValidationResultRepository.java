package kr.releasepilot.controlplane.environment;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.UUID;
public interface EnvironmentValidationResultRepository extends JpaRepository<EnvironmentValidationResult,UUID>{List<EnvironmentValidationResult> findByEnvironmentIdOrderByCheckedAtAsc(UUID environmentId); void deleteByEnvironmentId(UUID environmentId);}
