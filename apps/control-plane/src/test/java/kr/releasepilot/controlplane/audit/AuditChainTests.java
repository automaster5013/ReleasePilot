package kr.releasepilot.controlplane.audit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class AuditChainTests {
    @Autowired AuditTrail trail;
    @Autowired AuditChainVerifier verifier;
    @Autowired AuditEventRepository events;
    @Autowired AuditArchiveDeliveryRepository deliveries;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test void sealsAContiguousChainAndQueuesExternalArchive(){
        var first=trail.record(AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-09-14T00:00:00Z")));
        var second=trail.record(AuditEvent.serviceCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-09-14T00:00:01Z")));
        assertThat(first.getChainSequence()).isEqualTo(1);
        assertThat(first.getPreviousHash()).isEqualTo(AuditTrail.GENESIS_HASH);
        assertThat(second.getChainSequence()).isEqualTo(2);
        assertThat(second.getPreviousHash()).isEqualTo(first.getEventHash());
        assertThat(verifier.verify().valid()).isTrue();
        assertThat(deliveries.count()).isEqualTo(2);
    }

    @Test void detectsPersistedPayloadTampering(){
        var event=trail.record(AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-09-14T00:00:00Z")));
        events.flush();
        jdbc.update("update audit_events set payload_json=? where id=?","{\"tampered\":true}",event.getId());
        entityManager.clear();
        var result=verifier.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.failedEventId()).isEqualTo(event.getId());
    }
}
