package kr.releasepilot.controlplane.release;import org.springframework.data.jpa.repository.JpaRepository;import java.util.UUID;
public interface PolicySnapshotRepository extends JpaRepository<PolicySnapshot,UUID>{java.util.Optional<PolicySnapshot> findByReleaseId(UUID releaseId);}
