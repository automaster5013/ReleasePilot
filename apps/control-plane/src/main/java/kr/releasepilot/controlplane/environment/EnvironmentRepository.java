package kr.releasepilot.controlplane.environment;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface EnvironmentRepository extends JpaRepository<Environment,UUID>{boolean existsByServiceIdAndName(UUID serviceId,String name);}
