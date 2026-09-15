package kr.releasepilot.controlplane.audit;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={
        "spring.datasource.url=${ANALYSIS_TEST_JDBC_URL:jdbc:h2:mem:audit-storage;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${ANALYSIS_TEST_USERNAME:sa}",
        "spring.datasource.password=${ANALYSIS_TEST_PASSWORD:}",
        "spring.flyway.enabled=${ANALYSIS_TEST_FLYWAY:false}",
        "spring.jpa.hibernate.ddl-auto=${ANALYSIS_TEST_DDL:create-drop}"})
@Transactional
class AuditStorageIntegrationTests {
    @Autowired AuditTrail trail;
    @Autowired AuditChainVerifier verifier;
    @Autowired AuditEventRepository events;
    @Autowired AuditArchiveDeliveryRepository deliveries;
    @Autowired EntityManager entityManager;

    @Test
    void unicodeNestedJsonAndMicrosecondTimeSurviveDatabaseRoundTrip() {
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        long archiveCount=deliveries.count();
        var first=record("{\"z\":[null,true,2,1.5,\"한글\"],\"a\":{\"second\":false,\"first\":\"줄\\n바꿈\"}}");
        var second=record("{\"reason\":\"다음 이벤트\"}");
        events.flush();entityManager.clear();
        var stored=events.findById(first.getId()).orElseThrow();
        assertThat(stored.getPayloadJson()).contains("한글").startsWith("{");
        assertThat(stored.getOccurredAt().getNano()).isEqualTo(123456000);
        var result=verifier.verify();
        assertThat(result.valid()).isTrue();
        assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
        assertThat(result.headHash()).isEqualTo(second.getEventHash());
        assertThat(deliveries.count()).isEqualTo(archiveCount+2);
    }

    @Test
    void equivalentObjectOrderingAndWhitespaceDoNotLookLikeTampering() {
        var event=record("{\"z\":2,\"a\":{\"y\":1.5,\"x\":true}}");
        rewrite(event,"payloadJson","{ \"a\": {\"x\":true, \"y\":1.5}, \"z\":2 }");
        assertThat(verifier.verify().valid()).isTrue();
    }

    @Test
    void changedPayloadIsDetectedAfterDatabaseRoundTrip() {
        var event=record("{\"reason\":\"original\"}");
        rewrite(event,"payloadJson","{\"reason\":\"tampered\"}");
        var result=verifier.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.failedEventId()).isEqualTo(event.getId());
    }

    @Test
    void changedPreviousHashIsDetectedAfterDatabaseRoundTrip() {
        var event=record("{}");
        rewrite(event,"previousHash","f".repeat(64));
        var result=verifier.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.failedEventId()).isEqualTo(event.getId());
    }

    @Test
    void sequenceGapIsDetectedAfterDatabaseRoundTrip() {
        var event=record("{}");
        rewrite(event,"chainSequence",event.getChainSequence()+1);
        var result=verifier.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.failedEventId()).isEqualTo(event.getId());
    }

    private AuditEvent record(String payload) {
        return trail.record(AuditEvent.rolloutOperation(UUID.randomUUID(),UUID.randomUUID(),
                "SUCCEEDED","PAUSE",payload,Instant.parse("2026-09-15T00:00:00.123456789Z")));
    }

    private void rewrite(AuditEvent event,String field,Object value) {
        events.flush();entityManager.clear();
        var stored=events.findById(event.getId()).orElseThrow();
        ReflectionTestUtils.setField(stored,field,value);
        events.flush();entityManager.clear();
    }
}
