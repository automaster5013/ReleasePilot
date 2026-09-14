package kr.releasepilot.controlplane.rollout;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OutboxCommandRepository extends JpaRepository<OutboxCommand, UUID> {
    Optional<OutboxCommand> findByIdempotencyKey(String idempotencyKey);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OutboxCommand> findFirstByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            Collection<OutboxCommandStatus> statuses, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OutboxCommand> findFirstByStatusAndClaimedAtLessThanEqualOrderByClaimedAtAsc(
            OutboxCommandStatus status, Instant claimedBefore);
}
