package kr.releasepilot.controlplane.policy;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface PolicyRepository extends JpaRepository<Policy,UUID>{boolean existsByName(String name);}
