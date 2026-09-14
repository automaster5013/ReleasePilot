package kr.releasepilot.controlplane.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GithubCheckDeliveryRepository extends JpaRepository<GithubCheckDelivery, UUID> {
    Optional<GithubCheckDelivery> findByReleaseId(UUID releaseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<GithubCheckDelivery> findFirstByDeliveryStatusInAndAvailableAtLessThanEqualOrderByUpdatedAtAsc(
            List<GithubCheckDelivery.DeliveryStatus> statuses, Instant now);
    List<GithubCheckDelivery> findTop100ByDeliveryStatusAndClaimedAtLessThanEqualOrderByClaimedAtAsc(
            GithubCheckDelivery.DeliveryStatus status, Instant claimedBefore);
}
