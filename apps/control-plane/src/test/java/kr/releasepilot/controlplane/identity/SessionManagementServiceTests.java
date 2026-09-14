package kr.releasepilot.controlplane.identity;

import kr.releasepilot.controlplane.audit.AuditTrail;
import kr.releasepilot.controlplane.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionManagementServiceTests {
    private final FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
    private final ObjectProvider<FindByIndexNameSessionRepository<?>> provider = mock(ObjectProvider.class);
    private final AuditTrail audits = mock(AuditTrail.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);
    private final SessionManagementService service;

    SessionManagementServiceTests() {
        doReturn(repository).when(provider).getIfAvailable();
        service = new SessionManagementService(provider, audits, clock);
    }

    @Test
    void listsOnlySafeReferencesAndMarksCurrentSession() {
        var current = session("raw-current-token", Instant.parse("2026-09-14T23:00:00Z"));
        var other = session("raw-other-token", Instant.parse("2026-09-14T22:00:00Z"));
        var stored = map(current, other);
        when(repository.findByPrincipalName("member")).thenReturn(stored);
        var result = service.list("member", current.getId());
        assertThat(result).hasSize(2);
        assertThat(result).anyMatch(SessionManagementService.SessionView::current);
        assertThat(result).allMatch(view -> view.reference().length() == 24);
        assertThat(result.toString()).doesNotContain("raw-current-token", "raw-other-token");
    }

    @Test
    void revokesAllOtherSessionsAndRecordsOneAuditEvent() {
        var current = session("current", Instant.parse("2026-09-14T23:00:00Z"));
        var first = session("first", Instant.parse("2026-09-14T22:00:00Z"));
        var second = session("second", Instant.parse("2026-09-14T21:00:00Z"));
        var stored = map(current, first, second);
        when(repository.findByPrincipalName("member")).thenReturn(stored);
        int revoked = service.revokeOthers("member", UUID.randomUUID(), current.getId());
        assertThat(revoked).isEqualTo(2);
        verify(repository).deleteById("first");
        verify(repository).deleteById("second");
        verify(repository, never()).deleteById("current");
        verify(audits).record(any());
    }

    @Test
    void cannotRevokeAnotherUsersUnknownSessionReference() {
        when(repository.findByPrincipalName("member")).thenReturn(Map.of());
        assertThatThrownBy(() -> service.revoke("member", UUID.randomUUID(), "not-owned"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        failure -> assertThat(failure.getCode()).isEqualTo("SESSION_NOT_FOUND"));
        verify(repository, never()).deleteById(any());
    }

    private static Session session(String id, Instant lastAccessedAt) {
        var session = mock(Session.class);
        when(session.getId()).thenReturn(id);
        when(session.getCreationTime()).thenReturn(lastAccessedAt.minus(Duration.ofMinutes(10)));
        when(session.getLastAccessedTime()).thenReturn(lastAccessedAt);
        when(session.getMaxInactiveInterval()).thenReturn(Duration.ofMinutes(30));
        when(session.isExpired()).thenReturn(false);
        return session;
    }

    private static Map<String, Session> map(Session... sessions) {
        var result = new LinkedHashMap<String, Session>();
        for (var session : sessions) result.put(session.getId(), session);
        return result;
    }
}
