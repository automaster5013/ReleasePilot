package kr.releasepilot.controlplane.rollout;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.mockito.Mockito.*;

class RolloutOutboxWorkerTests {
    @Test void recoversExpiredClaimsAndDrainsOnlyUntilQueueIsEmpty() {
        var processor = mock(OutboxCommandProcessor.class);
        var recovery = mock(OutboxRecoveryService.class);
        var handler = mock(RolloutCommandHandler.class);
        var reconciler=mock(RolloutStateReconciler.class);
        when(processor.processOne(handler)).thenReturn(true, true, false);
        var worker = new RolloutOutboxWorker(processor, recovery, handler,reconciler, Duration.ofMinutes(2), 20);
        worker.tick();
        verify(recovery).recoverExpired(Duration.ofMinutes(2), 20);
        verify(processor, times(3)).processOne(handler);
        verify(reconciler).reconcileActive();
    }

    @Test void batchLimitPreventsOneTickFromRunningForever() {
        var processor = mock(OutboxCommandProcessor.class);
        var recovery = mock(OutboxRecoveryService.class);
        var handler = mock(RolloutCommandHandler.class);
        var reconciler=mock(RolloutStateReconciler.class);
        when(processor.processOne(handler)).thenReturn(true);
        new RolloutOutboxWorker(processor, recovery, handler,reconciler, Duration.ofMinutes(2), 3).tick();
        verify(processor, times(3)).processOne(handler);
    }
}
