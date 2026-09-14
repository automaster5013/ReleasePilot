package kr.releasepilot.controlplane.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.*;

interface AuditArchiveDeliveryRepository extends JpaRepository<AuditArchiveDelivery,UUID>{
    List<AuditArchiveDelivery> findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(AuditArchiveDelivery.Status status,Instant now,Pageable page);
}
