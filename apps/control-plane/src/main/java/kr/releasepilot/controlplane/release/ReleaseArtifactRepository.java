package kr.releasepilot.controlplane.release;import org.springframework.data.jpa.repository.JpaRepository;import java.util.UUID;
public interface ReleaseArtifactRepository extends JpaRepository<ReleaseArtifact,UUID>{java.util.Optional<ReleaseArtifact> findByReleaseId(UUID releaseId);}
