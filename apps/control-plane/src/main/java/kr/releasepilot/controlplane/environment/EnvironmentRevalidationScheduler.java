package kr.releasepilot.controlplane.environment;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "releasepilot.environment-revalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EnvironmentRevalidationScheduler {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentRevalidationScheduler.class);
    private final EnvironmentService environments;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Duration maxAge;
    private final Duration failureRetry;
    private final Duration leaseTimeout;
    private final int batchSize;

    public EnvironmentRevalidationScheduler(EnvironmentService environments, MeterRegistry meters, Clock clock,
            @Value("${releasepilot.environment-revalidation.max-age:PT6H}") Duration maxAge,
            @Value("${releasepilot.environment-revalidation.failure-retry:PT15M}") Duration failureRetry,
            @Value("${releasepilot.environment-revalidation.lease-timeout:PT10M}") Duration leaseTimeout,
            @Value("${releasepilot.environment-revalidation.batch-size:20}") int batchSize) {
        this.environments = environments;
        this.meters = meters;
        this.clock = clock;
        this.maxAge = maxAge;
        this.failureRetry = failureRetry;
        this.leaseTimeout = leaseTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${releasepilot.environment-revalidation.poll-interval:PT5M}",
            initialDelayString = "${releasepilot.environment-revalidation.initial-delay:PT1M}")
    public void tick() {
        var now = clock.instant();
        var activeCutoff = now.minus(maxAge);
        var failureCutoff = now.minus(failureRetry);
        var staleBefore = now.minus(leaseTimeout);
        for (var id : environments.dueIds(activeCutoff, failureCutoff, staleBefore, batchSize)) {
            if (!environments.claimDue(id, now, activeCutoff, failureCutoff, staleBefore)) continue;
            try (var ignored = MDC.putCloseable("correlationId", UUID.randomUUID().toString())) {
                try {
                    var report = environments.revalidateClaimed(id);
                    meters.counter("releasepilot.environment.revalidations", "outcome", report.environment().getStatus().name()).increment();
                    log.info("Periodic environment validation completed environmentId={} status={}", id, report.environment().getStatus());
                } catch (Exception failure) {
                    environments.failScheduledValidation(id);
                    meters.counter("releasepilot.environment.revalidations", "outcome", "ERROR").increment();
                    log.warn("Periodic environment validation failed environmentId={} failureType={}", id, failure.getClass().getSimpleName());
                }
            }
        }
    }
}
