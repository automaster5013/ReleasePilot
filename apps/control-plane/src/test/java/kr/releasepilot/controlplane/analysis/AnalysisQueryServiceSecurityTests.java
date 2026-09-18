package kr.releasepilot.controlplane.analysis;

import kr.releasepilot.controlplane.catalog.CatalogService;
import kr.releasepilot.controlplane.catalog.CatalogServiceRepository;
import kr.releasepilot.controlplane.identity.ProjectAccess;
import kr.releasepilot.controlplane.release.Release;
import kr.releasepilot.controlplane.release.ReleaseRepository;
import kr.releasepilot.controlplane.rollout.RolloutExecutionRepository;
import kr.releasepilot.controlplane.rollout.RolloutStepRepository;
import kr.releasepilot.controlplane.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AnalysisQueryServiceSecurityTests {
    @Test
    void hidesAnalysisFromUsersOutsideTheReleaseProject() {
        var releases=mock(ReleaseRepository.class);var services=mock(CatalogServiceRepository.class);
        var access=mock(ProjectAccess.class);var executions=mock(RolloutExecutionRepository.class);
        var steps=mock(RolloutStepRepository.class);var jobs=mock(AnalysisJobRepository.class);
        var authentication=mock(Authentication.class);var release=mock(Release.class);var service=mock(CatalogService.class);
        var releaseId=UUID.randomUUID();var serviceId=UUID.randomUUID();var projectId=UUID.randomUUID();
        when(releases.findById(releaseId)).thenReturn(Optional.of(release));when(release.getServiceId()).thenReturn(serviceId);
        when(services.findById(serviceId)).thenReturn(Optional.of(service));when(service.getProjectId()).thenReturn(projectId);
        when(access.canView(projectId,authentication)).thenReturn(false);
        var query=new AnalysisQueryService(releases,services,access,executions,steps,jobs,new ObjectMapper());

        assertThatThrownBy(()->query.byRelease(releaseId,authentication)).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(executions,steps,jobs);
    }
}
