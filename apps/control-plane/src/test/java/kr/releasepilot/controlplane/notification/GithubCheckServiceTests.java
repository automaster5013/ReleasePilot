package kr.releasepilot.controlplane.notification;

import kr.releasepilot.controlplane.catalog.CatalogService;
import kr.releasepilot.controlplane.release.Release;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GithubCheckServiceTests {
    private final Instant now=Instant.parse("2026-09-14T12:00:00Z");

    @Test void schedulesAQueuedCheckForAGithubService(){
        var deliveries=mock(GithubCheckDeliveryRepository.class);when(deliveries.findByReleaseId(any())).thenReturn(Optional.empty());
        var service=new GithubCheckService(deliveries,Clock.fixed(now,ZoneOffset.UTC),true);
        var release=Release.pending(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"v1","change","a".repeat(40),"https://ci.example/run","key-123456789012",now);
        var catalog=CatalogService.create(UUID.randomUUID(),"api","API","https://github.com/Owner/repo.git","team",now);

        service.requested(release,catalog);

        var captor=ArgumentCaptor.forClass(GithubCheckDelivery.class);verify(deliveries).save(captor.capture());
        var snapshot=captor.getValue().snapshot();assertThat(snapshot.owner()).isEqualTo("owner");assertThat(snapshot.repository()).isEqualTo("repo");assertThat(snapshot.status()).isEqualTo(GithubCheckDelivery.CheckStatus.QUEUED);
    }

    @Test void ignoresNonGithubRepositories(){
        var deliveries=mock(GithubCheckDeliveryRepository.class);when(deliveries.findByReleaseId(any())).thenReturn(Optional.empty());
        var service=new GithubCheckService(deliveries,Clock.fixed(now,ZoneOffset.UTC),true);
        var release=Release.pending(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"v1","change","a".repeat(40),"https://ci.example/run","key-123456789012",now);
        var catalog=CatalogService.create(UUID.randomUUID(),"api","API","https://gitlab.com/owner/repo","team",now);
        service.requested(release,catalog);
        verify(deliveries,never()).save(any());
    }
}
