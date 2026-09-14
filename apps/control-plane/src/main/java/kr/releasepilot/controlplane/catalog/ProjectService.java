package kr.releasepilot.controlplane.catalog;

import kr.releasepilot.controlplane.audit.AuditEvent;
import kr.releasepilot.controlplane.audit.AuditTrail;
import kr.releasepilot.controlplane.shared.error.ConflictException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import kr.releasepilot.controlplane.identity.ProjectMembershipRepository;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final AuditTrail auditEventRepository;
    private final Clock clock;
    private final ProjectMembershipRepository memberships;

    public ProjectService(
            ProjectRepository projectRepository,
            AuditTrail auditEventRepository,
            Clock clock,
            ProjectMembershipRepository memberships
    ) {
        this.projectRepository = projectRepository;
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
        this.memberships = memberships;
    }

    @Transactional
    public Project create(String key, String name, String description, UUID actorId) {
        if (projectRepository.existsByKey(key)) {
            throw new ConflictException("PROJECT_KEY_ALREADY_EXISTS", "Project key already exists");
        }
        var now = clock.instant();
        Project project = projectRepository.save(Project.create(key, name, description, now));
        auditEventRepository.record(AuditEvent.projectCreated(project.getId(), actorId, now));
        return project;
    }

    @Transactional(readOnly = true)
    public List<Project> list(int limit, UUID actorId, boolean operator) {
        var page = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return projectRepository.findAllByOrderByCreatedAtDesc(page).stream()
                .filter(project -> operator || memberships.existsByProjectIdAndUserId(project.getId(), actorId))
                .toList();
    }
}
