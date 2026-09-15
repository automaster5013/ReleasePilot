package kr.releasepilot.controlplane.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={
        "spring.datasource.url=${ANALYSIS_TEST_JDBC_URL:jdbc:h2:mem:audit-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${ANALYSIS_TEST_USERNAME:sa}",
        "spring.datasource.password=${ANALYSIS_TEST_PASSWORD:}",
        "spring.flyway.enabled=${ANALYSIS_TEST_FLYWAY:false}",
        "spring.jpa.hibernate.ddl-auto=${ANALYSIS_TEST_DDL:create-drop}"})
class AuditConcurrencyIntegrationTests {
    @Autowired AuditTrail trail;
    @Autowired AuditChainVerifier verifier;
    @Autowired AuditChainHeadRepository heads;
    @Autowired AuditArchiveDeliveryRepository deliveries;
    @Autowired AuditEventRepository events;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void initializeHead() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            if (!heads.existsById(1)) heads.saveAndFlush(AuditChainHead.genesis());
        });
    }

    @Test
    void concurrentWritersCommitUniqueContiguousSequencesAndArchiveDeliveries() throws Exception {
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        long archiveCount=deliveries.count();
        var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(8)) {
            var futures=new ArrayList<Future<AuditEvent>>();
            for(int i=0;i<8;i++) futures.add(executor.submit(() -> {
                await(start);
                return trail.record(event());
            }));
            start.countDown();
            var sequences=new ArrayList<Long>();
            for(var future:futures) sequences.add(future.get(15,TimeUnit.SECONDS).getChainSequence());
            assertThat(sequences).doesNotHaveDuplicates();
            assertThat(sequences.stream().sorted().toList()).containsExactly(
                    before.verifiedEvents()+1,before.verifiedEvents()+2,before.verifiedEvents()+3,
                    before.verifiedEvents()+4,before.verifiedEvents()+5,before.verifiedEvents()+6,
                    before.verifiedEvents()+7,before.verifiedEvents()+8);
        }
        var after=verifier.verify();
        assertThat(after.valid()).isTrue();
        assertThat(after.verifiedEvents()).isEqualTo(before.verifiedEvents()+8);
        assertThat(deliveries.count()).isEqualTo(archiveCount+8);
    }

    @Test
    void archiveBatchFailureCommitsAndNewWorkerRecoversInSeparateTransaction() {
        var transaction=new TransactionTemplate(transactionManager);
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        var now=Instant.parse("2030-01-01T00:00:00Z");
        var recorded=transaction.execute(status -> {
            var first=trail.record(event());
            var second=trail.record(event());
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(first),"availableAt",now.minusSeconds(2));
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(second),"availableAt",now.minusSeconds(1));
            return java.util.List.of(first,second);
        });
        var first=recorded.get(0);
        var second=recorded.get(1);
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Bearer synthetic-commit-secret"))
                .when(sink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick());

        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.PENDING,1,"AUDIT_ARCHIVE_UNAVAILABLE",null);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(first),"availableAt"))
                    .isEqualTo(now.plusSeconds(2));
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            var result=verifier.verify();
            assertThat(result.valid()).isTrue();
            assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
            assertThat(result.headHash()).isEqualTo(second.getEventHash());
        });

        var recoveredSink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        var recoveredWorker=new AuditArchiveWorker(deliveries,events,recoveredSink,
                java.time.Clock.fixed(now.plusSeconds(2),java.time.ZoneOffset.UTC));
        transaction.executeWithoutResult(status -> recoveredWorker.tick());
        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.DELIVERED,1,null,now.plusSeconds(2));
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            var result=verifier.verify();
            assertThat(result.valid()).isTrue();
            assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
            assertThat(result.headHash()).isEqualTo(second.getEventHash());
            assertThat(events.findById(first.getId()).orElseThrow().getEventHash()).isEqualTo(first.getEventHash());
        });
        org.mockito.Mockito.verify(recoveredSink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        org.mockito.Mockito.verifyNoMoreInteractions(recoveredSink);
    }

    @Test
    void archiveBatchRollbackLeavesBothEventsDueForRedelivery() {
        var transaction=new TransactionTemplate(transactionManager);
        var before=verifier.verify();
        assertThat(before.valid()).isTrue();
        var now=Instant.parse("2031-01-01T00:00:00Z");
        var recorded=transaction.execute(status -> {
            var first=trail.record(event());
            var second=trail.record(event());
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(first),"availableAt",now.minusSeconds(2));
            org.springframework.test.util.ReflectionTestUtils.setField(deliveryFor(second),"availableAt",now.minusSeconds(1));
            return java.util.List.of(first,second);
        });
        var first=recorded.get(0);
        var second=recorded.get(1);
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic rollback failure"))
                .when(sink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        transaction.executeWithoutResult(status -> {
            new AuditArchiveWorker(deliveries,events,sink,
                    java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick();
            deliveries.flush();
            assertDelivery(first,AuditArchiveDelivery.Status.PENDING,1,"AUDIT_ARCHIVE_UNAVAILABLE",null);
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.PENDING,0,null,null);
            assertDelivery(second,AuditArchiveDelivery.Status.PENDING,0,null,null);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(first),"availableAt"))
                    .isEqualTo(now.minusSeconds(2));
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(deliveryFor(second),"availableAt"))
                    .isEqualTo(now.minusSeconds(1));
            assertThat(verifier.verify().valid()).isTrue();
        });
        org.mockito.Mockito.doNothing().when(sink).archive(
                org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick());
        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,now);
            var result=verifier.verify();
            assertThat(result.valid()).isTrue();
            assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+2);
            assertThat(result.headHash()).isEqualTo(second.getEventHash());
            assertThat(events.findById(first.getId()).orElseThrow().getEventHash()).isEqualTo(first.getEventHash());
        });
        org.mockito.Mockito.verify(sink,org.mockito.Mockito.times(2)).archive(
                org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        // A successful sink call is not undone by the database rollback.
        org.mockito.Mockito.verify(sink,org.mockito.Mockito.times(2)).archive(
                org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(second.getId())));
    }

    private AuditArchiveDelivery deliveryFor(AuditEvent event) {
        return deliveries.findAll().stream().filter(value -> value.auditEventId().equals(event.getId()))
                .findFirst().orElseThrow();
    }

    private void assertDelivery(AuditEvent event,AuditArchiveDelivery.Status expectedStatus,int attempts,
            String error,Instant deliveredAt) {
        var stored=deliveryFor(event);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"status")).isEqualTo(expectedStatus);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"attempts")).isEqualTo(attempts);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"lastError")).isEqualTo(error);
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(stored,"deliveredAt")).isEqualTo(deliveredAt);
    }

    @Test
    void verifierWaitsForWriterCommitAndThenSeesConsistentHead() throws Exception {
        var before=verifier.verify();
        var writerHoldingLock=new CountDownLatch(1);
        var releaseWriter=new CountDownLatch(1);
        var verifierStarted=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var writer=executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                heads.lockById(1);
                var saved=trail.record(event());
                writerHoldingLock.countDown();
                await(releaseWriter);
                return saved;
            }));
            try {
                assertThat(writerHoldingLock.await(10,TimeUnit.SECONDS)).isTrue();
                var verification=executor.submit(() -> {
                    verifierStarted.countDown();
                    return verifier.verify();
                });
                assertThat(verifierStarted.await(10,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> verification.get(200,TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseWriter.countDown();
                var saved=writer.get(10,TimeUnit.SECONDS);
                var result=verification.get(10,TimeUnit.SECONDS);
                assertThat(result.valid()).isTrue();
                assertThat(result.verifiedEvents()).isEqualTo(before.verifiedEvents()+1);
                assertThat(result.headHash()).isEqualTo(saved.getEventHash());
            } finally {
                releaseWriter.countDown();
            }
        }
    }

    private static AuditEvent event() {
        return AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.now());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Test synchronization timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test interrupted",exception);
        }
    }
}
