package kr.releasepilot.controlplane.identity;

import kr.releasepilot.controlplane.audit.AuditEvent;
import kr.releasepilot.controlplane.audit.AuditTrail;
import kr.releasepilot.controlplane.shared.error.NotFoundException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class SessionManagementService {
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> repositories;
    private final AuditTrail audits;
    private final Clock clock;

    public SessionManagementService(ObjectProvider<FindByIndexNameSessionRepository<?>> repositories,
                                    AuditTrail audits, Clock clock) {
        this.repositories = repositories;
        this.audits = audits;
        this.clock = clock;
    }

    public List<SessionView> list(String username, String currentSessionId) {
        return repository().findByPrincipalName(username).values().stream()
                .filter(session -> !session.isExpired())
                .sorted(Comparator.comparing(Session::getLastAccessedTime).reversed())
                .map(session -> view(session, currentSessionId))
                .toList();
    }

    @Transactional
    public boolean revoke(String username, UUID actorId, String reference, String currentSessionId) {
        var repository = repository();
        var session = repository.findByPrincipalName(username).values().stream()
                .filter(candidate -> reference(candidate.getId()).equals(reference))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("SESSION_NOT_FOUND", "Session not found"));
        repository.deleteById(session.getId());
        audits.record(AuditEvent.sessionsRevoked(actorId, actorId, 1, "SELECTED", clock.instant()));
        return session.getId().equals(currentSessionId);
    }

    @Transactional
    public int revokeOthers(String username, UUID actorId, String currentSessionId) {
        var repository = repository();
        var sessions = repository.findByPrincipalName(username).values().stream()
                .filter(session -> !session.getId().equals(currentSessionId))
                .toList();
        sessions.forEach(session -> repository.deleteById(session.getId()));
        if (!sessions.isEmpty())
            audits.record(AuditEvent.sessionsRevoked(actorId, actorId, sessions.size(), "OTHERS", clock.instant()));
        return sessions.size();
    }

    private FindByIndexNameSessionRepository<?> repository() {
        var repository = repositories.getIfAvailable();
        if (repository == null) throw new SessionStoreUnavailableException();
        return repository;
    }

    private static SessionView view(Session session, String currentSessionId) {
        Instant expiresAt = session.getLastAccessedTime().plus(session.getMaxInactiveInterval());
        return new SessionView(reference(session.getId()), session.getId().equals(currentSessionId),
                session.getCreationTime(), session.getLastAccessedTime(), expiresAt);
    }

    static String reference(String sessionId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 12);
        } catch (Exception failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    public record SessionView(String reference, boolean current, Instant createdAt,
                              Instant lastAccessedAt, Instant expiresAt) {}

    public static final class SessionStoreUnavailableException extends RuntimeException {
        SessionStoreUnavailableException() { super("Durable session store is unavailable"); }
    }
}
