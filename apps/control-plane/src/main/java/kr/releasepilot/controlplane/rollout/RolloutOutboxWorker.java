package kr.releasepilot.controlplane.rollout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "releasepilot.rollout.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RolloutOutboxWorker {
    private final OutboxCommandProcessor processor;
    private final OutboxRecoveryService recovery;
    private final RolloutCommandHandler handler;
    private final RolloutStateReconciler stateReconciler;
    private final Duration leaseTimeout;
    private final int batchSize;

    public RolloutOutboxWorker(OutboxCommandProcessor processor, OutboxRecoveryService recovery,
                               RolloutCommandHandler handler,RolloutStateReconciler stateReconciler,
                               @Value("${releasepilot.rollout.worker.lease-timeout:PT2M}") Duration leaseTimeout,
                               @Value("${releasepilot.rollout.worker.batch-size:20}") int batchSize) {
        if (leaseTimeout.isNegative() || leaseTimeout.isZero()) throw new IllegalArgumentException("lease timeout must be positive");
        if (batchSize < 1 || batchSize > 1000) throw new IllegalArgumentException("batch size must be between 1 and 1000");
        this.processor = processor;
        this.recovery = recovery;
        this.handler = handler;
        this.stateReconciler=stateReconciler;
        this.leaseTimeout = leaseTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${releasepilot.rollout.worker.poll-interval:PT2S}")
    public void tick() {
        recovery.recoverExpired(leaseTimeout, batchSize);
        for (int processed = 0; processed < batchSize && processor.processOne(handler); processed++) {
            // The bounded loop drains ready commands without monopolizing the scheduler thread.
        }
        stateReconciler.reconcileActive();
    }
}
