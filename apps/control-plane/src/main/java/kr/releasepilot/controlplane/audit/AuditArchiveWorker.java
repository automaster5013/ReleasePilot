package kr.releasepilot.controlplane.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Component
@ConditionalOnBean(AuditArchiveSink.class)
class AuditArchiveWorker {
    private final AuditArchiveDeliveryRepository deliveries;private final AuditEventRepository events;private final AuditArchiveSink sink;private final Clock clock;
    AuditArchiveWorker(AuditArchiveDeliveryRepository deliveries,AuditEventRepository events,AuditArchiveSink sink,Clock clock){this.deliveries=deliveries;this.events=events;this.sink=sink;this.clock=clock;}
    @Scheduled(fixedDelayString="${releasepilot.audit.archive.poll-interval:PT10S}") @Transactional
    public void tick(){for(var delivery:deliveries.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(AuditArchiveDelivery.Status.PENDING,clock.instant(),PageRequest.of(0,20))){try{sink.archive(events.findById(delivery.auditEventId()).orElseThrow());delivery.delivered(clock.instant());}catch(Exception failure){delivery.failed("AUDIT_ARCHIVE_UNAVAILABLE",clock.instant());}}}
}
