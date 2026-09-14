package kr.releasepilot.controlplane.environment;
import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import java.time.Instant; import java.util.*;
public interface EnvironmentRepository extends JpaRepository<Environment,UUID>{
 boolean existsByServiceIdAndName(UUID serviceId,String name);
 List<Environment> findAllByServiceIdOrderByCreatedAtDesc(UUID serviceId,Pageable pageable);
 @Query("""
  select e.id from Environment e where e.status <> :disabled and (
   (e.status = :validating and coalesce(e.validationStartedAt,e.createdAt) <= :staleBefore) or
   (e.status <> :validating and e.validatedAt is not null and (
    (e.status = :invalid and e.validatedAt <= :failureCutoff) or
    (e.status <> :invalid and e.validatedAt <= :activeCutoff)))
  ) order by coalesce(e.validatedAt,e.createdAt),e.id
  """)
 List<UUID> findDueIds(@Param("activeCutoff") Instant activeCutoff,@Param("failureCutoff") Instant failureCutoff,
  @Param("staleBefore") Instant staleBefore,@Param("validating") EnvironmentStatus validating,
  @Param("invalid") EnvironmentStatus invalid,@Param("disabled") EnvironmentStatus disabled,Pageable pageable);
 @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("""
  update Environment e set e.status = :validating,e.validationStartedAt = :now where e.id = :id and e.status <> :disabled and (
   (e.status = :validating and coalesce(e.validationStartedAt,e.createdAt) <= :staleBefore) or
   (e.status <> :validating and e.validatedAt is not null and (
    (e.status = :invalid and e.validatedAt <= :failureCutoff) or
    (e.status <> :invalid and e.validatedAt <= :activeCutoff)))
  )
  """)
 int claimDue(@Param("id") UUID id,@Param("now") Instant now,@Param("activeCutoff") Instant activeCutoff,
  @Param("failureCutoff") Instant failureCutoff,@Param("staleBefore") Instant staleBefore,
  @Param("validating") EnvironmentStatus validating,@Param("invalid") EnvironmentStatus invalid,
  @Param("disabled") EnvironmentStatus disabled);
}
