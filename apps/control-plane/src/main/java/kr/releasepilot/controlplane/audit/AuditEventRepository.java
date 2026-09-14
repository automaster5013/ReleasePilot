package kr.releasepilot.controlplane.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    java.util.List<AuditEvent> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, UUID aggregateId);
    java.util.List<AuditEvent> findAllByOrderByOccurredAtDesc(org.springframework.data.domain.Pageable pageable);
}
