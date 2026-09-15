package kr.releasepilot.controlplane.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    java.util.List<AuditEvent> findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc(String aggregateType, UUID aggregateId);
    java.util.List<AuditEvent> findAllByOrderByOccurredAtDesc(org.springframework.data.domain.Pageable pageable);
    java.util.List<AuditEvent> findByEventHashIsNotNullOrderByChainSequenceAsc();
    @org.springframework.data.jpa.repository.Query("select e from AuditEvent e where e.chainSequence is not null or e.previousHash is not null or e.eventHash is not null order by e.chainSequence asc")
    java.util.List<AuditEvent> findChainCandidates();
}
