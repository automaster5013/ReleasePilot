package kr.releasepilot.controlplane.release;import org.springframework.data.domain.Pageable;import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;import java.util.*;
public interface ReleaseRepository extends JpaRepository<Release,UUID>{boolean existsByEnvironmentIdAndActiveSlotTrue(UUID environmentId);Optional<Release> findByRequestedByAndIdempotencyKey(UUID user,String key);java.util.List<Release> findAllByOrderByCreatedAtDesc(Pageable pageable);
 @Query("select distinct r from Release r, CatalogService s, ProjectMembership m where r.serviceId=s.id and s.projectId=m.projectId and m.userId=:userId order by r.createdAt desc")
 List<Release> findAccessibleByUserId(@Param("userId")UUID userId,Pageable pageable);}
