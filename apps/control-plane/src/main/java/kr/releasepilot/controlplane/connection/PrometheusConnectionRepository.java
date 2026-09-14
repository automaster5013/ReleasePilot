package kr.releasepilot.controlplane.connection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PrometheusConnectionRepository extends JpaRepository<PrometheusConnection,UUID>{boolean existsByName(String name);}
