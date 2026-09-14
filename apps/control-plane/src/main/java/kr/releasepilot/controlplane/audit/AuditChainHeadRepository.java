package kr.releasepilot.controlplane.audit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

interface AuditChainHeadRepository extends JpaRepository<AuditChainHead,Integer>{
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from AuditChainHead h where h.id=:id")
    AuditChainHead lockById(@Param("id") int id);
}
