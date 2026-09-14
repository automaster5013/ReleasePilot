package kr.releasepilot.controlplane.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    boolean existsByKey(String key);
    Optional<Project> findByKey(String key);
    List<Project> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
