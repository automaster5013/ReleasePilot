package kr.releasepilot.controlplane.catalog;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CatalogServiceRepository extends JpaRepository<CatalogService, UUID> {
    boolean existsByProjectIdAndKey(UUID projectId, String key);
    List<CatalogService> findAllByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
