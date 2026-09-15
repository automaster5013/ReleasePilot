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
    @Autowired AuditChainHeadRepository heads;

    @org.junit.jupiter.api.BeforeEach
    void ensureHead() {
        if (!heads.existsById(1)) heads.saveAndFlush(AuditChainHead.genesis());
    }

    @Test
    void archiveFailureAndRecoverySurviveDatabaseRoundTrip() {
        var event=record("{\"reason\":\"archive storage test\"}");
        var now=Instant.parse("2026-09-15T12:00:00Z");
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Bearer synthetic-secret private-url"))
                .when(sink).archive(org.mockito.ArgumentMatchers.any());
        new AuditArchiveWorker(deliveries,events,sink,
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick();
        deliveries.flush();entityManager.clear();
        var failed=deliveryFor(event);
        assertThat(ReflectionTestUtils.getField(failed,"lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
        assertThat(ReflectionTestUtils.getField(failed,"status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
        assertThat(ReflectionTestUtils.getField(failed,"attempts")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(failed,"availableAt")).isEqualTo(now.plusSeconds(2));
        assertThat(ReflectionTestUtils.getField(failed,"deliveredAt")).isNull();
        assertThat(verifier.verify().valid()).isTrue();

        org.mockito.Mockito.reset(sink);
        new AuditArchiveWorker(deliveries,events,sink,
                java.time.Clock.fixed(now.plusSeconds(2),java.time.ZoneOffset.UTC)).tick();
        deliveries.flush();entityManager.clear();
        var recovered=deliveryFor(event);
        assertThat(ReflectionTestUtils.getField(recovered,"status")).isEqualTo(AuditArchiveDelivery.Status.DELIVERED);
        assertThat(ReflectionTestUtils.getField(recovered,"lastError")).isNull();
        assertThat(ReflectionTestUtils.getField(recovered,"deliveredAt")).isEqualTo(now.plusSeconds(2));
        assertThat(ReflectionTestUtils.getField(recovered,"attempts")).isEqualTo(1);
        org.mockito.Mockito.verify(sink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(event.getId())));
        assertThat(verifier.verify().valid()).isTrue();
    }

    @Test
    void archiveRetryInFutureIsNotSelectedFromDatabase() {
        var event=record("{}");
        var now=Instant.parse("2026-09-15T12:00:00Z");
        var pending=deliveryFor(event);
        pending.failed("AUDIT_ARCHIVE_UNAVAILABLE",now);
        deliveries.flush();entityManager.clear();
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        new AuditArchiveWorker(deliveries,events,sink,
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick();
        deliveries.flush();entityManager.clear();
        var stored=deliveryFor(event);
        assertThat(ReflectionTestUtils.getField(stored,"status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
        assertThat(ReflectionTestUtils.getField(stored,"attempts")).isEqualTo(1);
        org.mockito.Mockito.verify(sink,org.mockito.Mockito.never()).archive(
                org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(event.getId())));
    }

    private AuditArchiveDelivery deliveryFor(AuditEvent event) {
        return deliveries.findAll().stream().filter(value -> value.auditEventId().equals(event.getId()))
                .findFirst().orElseThrow();
    }

    @Test
    void deletedTailDoesNotPassHeadVerification() {
        var event=record("{}");
        events.flush();
        deliveries.deleteAll(deliveries.findAll().stream()
                .filter(delivery -> delivery.auditEventId().equals(event.getId())).toList());
        deliveries.flush();
        events.delete(event);events.flush();entityManager.clear();
        var result=verifier.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.failedEventId()).isNull();
    }

    @Test
    void missingHeadIsNotReportedAsValid() {
        heads.deleteById(1);heads.flush();entityManager.clear();
        assertThat(verifier.verify().valid()).isFalse();
    }

    @Test
    void changedHeadHashIsDetected() {
        record("{}");events.flush();
        ReflectionTestUtils.setField(heads.findById(1).orElseThrow(),"lastHash","f".repeat(64));
        heads.flush();entityManager.clear();
        assertThat(verifier.verify().valid()).isFalse();
    }

    @Test
    void changedHeadSequenceIsDetected() {
        record("{}");events.flush();
        var head=heads.findById(1).orElseThrow();
        ReflectionTestUtils.setField(head,"lastSequence",head.lastSequence()+1);
        heads.flush();entityManager.clear();
        assertThat(verifier.verify().valid()).isFalse();
    }

    @Test
    void clearedEventHashIsNotSilentlyExcluded() {
        var event=record("{}");
        rewrite(event,"eventHash",null);
        var result=verifier.verify();
        assertThat(result.valid()).isFalse();
        assertThat(result.failedEventId()).isEqualTo(event.getId());
    }

    @Test
    void fullyUnsealedLegacyEventRemainsOutsideChain() {
        var before=verifier.verify();
        events.saveAndFlush(AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.now()));
        entityManager.clear();
        var result=verifier.verify();
        assertThat(result.valid()).isTrue();
        assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents());
    }

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
