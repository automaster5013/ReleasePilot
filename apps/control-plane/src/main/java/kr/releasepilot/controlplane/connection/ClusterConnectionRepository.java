package kr.releasepilot.controlplane.connection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ClusterConnectionRepository extends JpaRepository<ClusterConnection,UUID>{boolean existsByName(String name);}
