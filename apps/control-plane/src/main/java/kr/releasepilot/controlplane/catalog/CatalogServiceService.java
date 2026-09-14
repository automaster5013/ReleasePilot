package kr.releasepilot.controlplane.catalog;

import kr.releasepilot.controlplane.audit.AuditEvent;
import kr.releasepilot.controlplane.audit.AuditTrail;
import kr.releasepilot.controlplane.shared.error.ConflictException;
import kr.releasepilot.controlplane.shared.error.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CatalogServiceService {
    private final ProjectRepository projects;
    private final CatalogServiceRepository services;
    private final AuditTrail auditEvents;
    private final Clock clock;

    public CatalogServiceService(ProjectRepository projects, CatalogServiceRepository services,
                                 AuditTrail auditEvents, Clock clock) {
        this.projects = projects;
        this.services = services;
        this.auditEvents = auditEvents;
        this.clock = clock;
    }

    @Transactional
    public CatalogService create(UUID projectId, String key, String name, String repositoryUrl,
                                 String owner, UUID actorId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new NotFoundException("PROJECT_NOT_FOUND", "Project not found"));
        if (project.getStatus() != ProjectStatus.ACTIVE) {
            throw new ConflictException("PROJECT_NOT_ACTIVE", "Project is not active");
        }
        if (services.existsByProjectIdAndKey(projectId, key)) {
            throw new ConflictException("SERVICE_KEY_ALREADY_EXISTS", "Service key already exists in project");
        }
        Instant now = clock.instant();
        CatalogService service = services.save(CatalogService.create(
                projectId, key, name, repositoryUrl, owner, now));
        auditEvents.record(AuditEvent.serviceCreated(service.getId(), actorId, now));
        return service;
    }

    @Transactional(readOnly = true)
    public List<CatalogService> list(UUID projectId, int limit) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("PROJECT_NOT_FOUND", "Project not found");
        }
        return services.findAllByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, limit));
    }
}
