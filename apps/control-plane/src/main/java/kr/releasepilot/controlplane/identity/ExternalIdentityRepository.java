package kr.releasepilot.controlplane.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {
    java.util.List<ExternalIdentity> findByUserId(UUID userId);

    Optional<ExternalIdentity> findByIssuerAndSubject(String issuer, String subject);
}
