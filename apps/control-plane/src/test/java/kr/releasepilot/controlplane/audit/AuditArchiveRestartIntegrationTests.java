package kr.releasepilot.controlplane.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={
        "spring.datasource.url=${ANALYSIS_TEST_JDBC_URL:jdbc:h2:mem:audit-restart;MODE=MySQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${ANALYSIS_TEST_USERNAME:sa}",
        "spring.datasource.password=${ANALYSIS_TEST_PASSWORD:}",
        "spring.flyway.enabled=${ANALYSIS_TEST_FLYWAY:false}",
        "spring.jpa.hibernate.ddl-auto=${ANALYSIS_TEST_DDL:create-drop}"})
class AuditArchiveRestartIntegrationTests {
    private static final Instant NOW=Instant.parse("2035-01-01T00:00:00Z");
    @Autowired AuditTrail trail;
    @Autowired AuditChainVerifier verifier;
    @Autowired AuditChainHeadRepository heads;
    @Autowired AuditEventRepository events;
    @Autowired AuditArchiveDeliveryRepository deliveries;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void committedBatchStateSurvivesBetweenSeedAndRecovery() {
        var phase=System.getenv("AUDIT_RESTART_PHASE");
        var marker=System.getenv("AUDIT_RESTART_RUN_ID");
        if (phase==null) {
            var run=UUID.randomUUID();
            seed(run);
            recover(run);
        } else {
            assertThat(phase).isIn("seed","recover");
            assertThat(marker).isNotBlank();
            var run=UUID.fromString(marker);
            if (phase.equals("seed")) seed(run); else recover(run);
            System.out.println("Audit archive restart phase="+phase+" run="+run+" JVM pid="+ProcessHandle.current().pid());
        }
    }

    private void seed(UUID run) {
        var transaction=new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            if (!heads.existsById(1)) heads.saveAndFlush(AuditChainHead.genesis());
            assertThat(events.findAll().stream().filter(value -> value.getAggregateId().equals(run)).toList()).isEmpty();
            var first=trail.record(AuditEvent.projectCreated(run,UUID.randomUUID(),NOW.minusSeconds(10)));
            var second=trail.record(AuditEvent.serviceCreated(run,UUID.randomUUID(),NOW.minusSeconds(9)));
            ReflectionTestUtils.setField(deliveryFor(first),"availableAt",NOW.minusSeconds(2));
            ReflectionTestUtils.setField(deliveryFor(second),"availableAt",NOW.minusSeconds(1));
        });
        var first=eventFor(run,"PROJECT");
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("Bearer synthetic-restart-secret"))
                .when(sink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                Clock.fixed(NOW,ZoneOffset.UTC)).tick());
        transaction.executeWithoutResult(status -> assertSeedState(run));
    }

    private void recover(UUID run) {
        var transaction=new TransactionTemplate(transactionManager);
        // In the helper's recovery invocation, all entities are loaded by a new JVM.
        transaction.executeWithoutResult(status -> assertSeedState(run));
        var first=eventFor(run,"PROJECT");
        var second=eventFor(run,"SERVICE");
        var sink=org.mockito.Mockito.mock(AuditArchiveSink.class);
        transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                Clock.fixed(NOW.plusSeconds(1),ZoneOffset.UTC)).tick());
        org.mockito.Mockito.verifyNoInteractions(sink);
        transaction.executeWithoutResult(status -> assertSeedState(run));
        transaction.executeWithoutResult(status -> new AuditArchiveWorker(deliveries,events,sink,
                Clock.fixed(NOW.plusSeconds(2),ZoneOffset.UTC)).tick());
        transaction.executeWithoutResult(status -> {
            assertDelivery(first,AuditArchiveDelivery.Status.DELIVERED,1,null,NOW.plusSeconds(2));
            assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,NOW);
            var result=verifier.verify();
            assertThat(result.valid()).isTrue();
            assertThat(result.headHash()).isEqualTo(second.getEventHash());
            assertThat(result.verifiedEvents()).isEqualTo(second.getChainSequence());
        });
        org.mockito.Mockito.verify(sink).archive(org.mockito.ArgumentMatchers.argThat(value -> value.getId().equals(first.getId())));
        org.mockito.Mockito.verifyNoMoreInteractions(sink);
    }

    private void assertSeedState(UUID run) {
        var first=eventFor(run,"PROJECT");
        var second=eventFor(run,"SERVICE");
        assertDelivery(first,AuditArchiveDelivery.Status.PENDING,1,"AUDIT_ARCHIVE_UNAVAILABLE",null);
        assertThat(ReflectionTestUtils.getField(deliveryFor(first),"availableAt")).isEqualTo(NOW.plusSeconds(2));
        assertDelivery(second,AuditArchiveDelivery.Status.DELIVERED,0,null,NOW);
        assertThat(second.getChainSequence()).isEqualTo(first.getChainSequence()+1);
        assertThat(second.getPreviousHash()).isEqualTo(first.getEventHash());
        var result=verifier.verify();
        assertThat(result.valid()).isTrue();
        assertThat(result.headHash()).isEqualTo(second.getEventHash());
        assertThat(result.verifiedEvents()).isEqualTo(second.getChainSequence());
    }

    private AuditEvent eventFor(UUID run,String type) {
        var matching=events.findAll().stream().filter(value -> value.getAggregateId().equals(run)
                &&value.getAggregateType().equals(type)).toList();
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }

    private AuditArchiveDelivery deliveryFor(AuditEvent event) {
        return deliveries.findAll().stream().filter(value -> value.auditEventId().equals(event.getId()))
                .findFirst().orElseThrow();
    }

    private void assertDelivery(AuditEvent event,AuditArchiveDelivery.Status expected,int attempts,String error,Instant completed) {
        var stored=deliveryFor(event);
        assertThat(ReflectionTestUtils.getField(stored,"status")).isEqualTo(expected);
        assertThat(ReflectionTestUtils.getField(stored,"attempts")).isEqualTo(attempts);
        assertThat(ReflectionTestUtils.getField(stored,"lastError")).isEqualTo(error);
        assertThat(ReflectionTestUtils.getField(stored,"deliveredAt")).isEqualTo(completed);
    }
}
