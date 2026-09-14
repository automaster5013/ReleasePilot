package kr.releasepilot.controlplane.environment;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnvironmentRevalidationSchedulerTests {
    private final EnvironmentService environments = mock(EnvironmentService.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T00:00:00Z"), ZoneOffset.UTC);
    private final EnvironmentRevalidationScheduler scheduler = new EnvironmentRevalidationScheduler(
            environments, meters, clock, Duration.ofHours(6), Duration.ofMinutes(15), Duration.ofMinutes(10), 20);

    @Test
    void onlyTheWorkerThatClaimsAnEnvironmentRevalidatesIt() {
        var claimed = UUID.randomUUID();
        var lost = UUID.randomUUID();
        var environment = mock(Environment.class);
        when(environment.getStatus()).thenReturn(EnvironmentStatus.ACTIVE);
        when(environments.dueIds(any(), any(), any(), eq(20))).thenReturn(List.of(claimed, lost));
        when(environments.claimDue(eq(claimed), any(), any(), any(), any())).thenReturn(true);
        when(environments.claimDue(eq(lost), any(), any(), any(), any())).thenReturn(false);
        when(environments.revalidateClaimed(claimed)).thenReturn(
                new EnvironmentService.ValidationReport(environment, List.of(), clock.instant()));

        scheduler.tick();

        verify(environments).revalidateClaimed(claimed);
        verify(environments, never()).revalidateClaimed(lost);
        verify(environments, never()).failScheduledValidation(any());
    }

    @Test
    void marksAClaimedEnvironmentInvalidWhenInspectionCrashes() {
        var id = UUID.randomUUID();
        when(environments.dueIds(any(), any(), any(), eq(20))).thenReturn(List.of(id));
        when(environments.claimDue(eq(id), any(), any(), any(), any())).thenReturn(true);
        when(environments.revalidateClaimed(id)).thenThrow(new IllegalStateException("secret-bearing upstream error"));

        scheduler.tick();

        verify(environments).failScheduledValidation(id);
    }
}
