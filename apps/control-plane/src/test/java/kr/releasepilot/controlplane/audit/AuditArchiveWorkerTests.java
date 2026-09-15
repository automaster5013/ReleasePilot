package kr.releasepilot.controlplane.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class AuditArchiveWorkerTests {
    private static final Instant NOW=Instant.parse("2026-09-15T12:00:00Z");
    private AuditArchiveDeliveryRepository deliveries;
    private AuditEventRepository events;
    private AuditArchiveSink sink;
    private AuditArchiveWorker worker;
    private AuditEvent event;
    private AuditArchiveDelivery delivery;

    @BeforeEach
    void setup() {
        deliveries=mock(AuditArchiveDeliveryRepository.class);
        events=mock(AuditEventRepository.class);
        sink=mock(AuditArchiveSink.class);
        worker=new AuditArchiveWorker(deliveries,events,sink,Clock.fixed(NOW,ZoneOffset.UTC));
        event=AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),NOW);
        delivery=AuditArchiveDelivery.pending(event.getId(),NOW);
        when(deliveries.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                eq(AuditArchiveDelivery.Status.PENDING),eq(NOW),any())).thenReturn(List.of(delivery));
        when(events.findById(event.getId())).thenReturn(Optional.of(event));
    }

    @Test
    void failureNeverCopiesSensitiveExceptionMessage() {
        doThrow(new IllegalStateException("Bearer synthetic-secret https://private.invalid/?token=synthetic-secret"))
                .when(sink).archive(event);
        worker.tick();
        assertThat(field("lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
        assertThat(field("status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
        assertThat(field("attempts")).isEqualTo(1);
        assertThat(field("availableAt")).isEqualTo(NOW.plusSeconds(2));
        assertThat(field("deliveredAt")).isNull();
    }

    @Test
    void failureWithoutMessageUsesSameStableCode() {
        doThrow(new IllegalStateException()).when(sink).archive(event);
        worker.tick();
        assertThat(field("lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
    }

    @Test
    void missingEventRemainsPendingWithoutCallingSink() {
        when(events.findById(event.getId())).thenReturn(Optional.empty());
        worker.tick();
        assertThat(field("lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
        assertThat(field("status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
        verifyNoInteractions(sink);
    }

    @Test
    void recoveredDeliveryClearsErrorAndRecordsCompletion() {
        doThrow(new IllegalStateException("synthetic-secret")).doNothing().when(sink).archive(event);
        worker.tick();
        var later=NOW.plusSeconds(2);
        when(deliveries.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                eq(AuditArchiveDelivery.Status.PENDING),eq(later),any())).thenReturn(List.of(delivery));
        new AuditArchiveWorker(deliveries,events,sink,Clock.fixed(later,ZoneOffset.UTC)).tick();
        assertThat(field("status")).isEqualTo(AuditArchiveDelivery.Status.DELIVERED);
        assertThat(field("lastError")).isNull();
        assertThat(field("deliveredAt")).isEqualTo(later);
        assertThat(field("attempts")).isEqualTo(1);
        verify(sink,times(2)).archive(event);
    }

    @Test
    void selectionIsLimitedToDuePendingBatch() {
        worker.tick();
        verify(deliveries).findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                AuditArchiveDelivery.Status.PENDING,NOW,PageRequest.of(0,20));
        assertThat(field("status")).isEqualTo(AuditArchiveDelivery.Status.DELIVERED);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void failedFirstItemDoesNotPreventFollowingDelivery(boolean missingEvent) {
        var second=AuditEvent.serviceCreated(UUID.randomUUID(),UUID.randomUUID(),NOW);
        var next=AuditArchiveDelivery.pending(second.getId(),NOW);
        when(deliveries.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                eq(AuditArchiveDelivery.Status.PENDING),eq(NOW),any())).thenReturn(List.of(delivery,next));
        when(events.findById(second.getId())).thenReturn(Optional.of(second));
        if(missingEvent) when(events.findById(event.getId())).thenReturn(Optional.empty());
        else doThrow(new IllegalStateException("synthetic-secret")).when(sink).archive(event);
        worker.tick();
        assertThat(field("status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
        assertThat(field("lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
        assertThat(field("attempts")).isEqualTo(1);
        assertThat(ReflectionTestUtils.getField(next,"status")).isEqualTo(AuditArchiveDelivery.Status.DELIVERED);
        assertThat(ReflectionTestUtils.getField(next,"lastError")).isNull();
        assertThat(ReflectionTestUtils.getField(next,"attempts")).isEqualTo(0);
        assertThat(ReflectionTestUtils.getField(next,"deliveredAt")).isEqualTo(NOW);
        verify(sink).archive(second);
        if(missingEvent) verify(sink,never()).archive(event);
    }

    @Test
    void emptyDueBatchDoesNotLookupEventsOrTransmit() {
        when(deliveries.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                eq(AuditArchiveDelivery.Status.PENDING),eq(NOW),any())).thenReturn(List.of());
        worker.tick();
        verifyNoInteractions(events,sink);
        assertThat(field("attempts")).isEqualTo(0);
        assertThat(field("status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
    }

    @ParameterizedTest
    @CsvSource({"1,2","2,4","7,128","8,256","9,300","10,300","30,300"})
    void exponentialDelayStopsAtFiveMinutes(int attempt,long seconds) {
        ReflectionTestUtils.setField(delivery,"attempts",attempt-1);
        delivery.failed("AUDIT_ARCHIVE_UNAVAILABLE",NOW);
        assertThat(field("availableAt")).isEqualTo(NOW.plusSeconds(seconds));
        assertThat(field("attempts")).isEqualTo(attempt);
    }

    private Object field(String name) {return ReflectionTestUtils.getField(delivery,name);}
}
