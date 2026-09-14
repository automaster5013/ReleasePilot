package kr.releasepilot.controlplane.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "services")
public class CatalogService {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "service_key", nullable = false, length = 63) private String key;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "repository_url", nullable = false, length = 500) private String repositoryUrl;
    @Column(nullable = false, length = 100) private String owner;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ServiceStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected CatalogService() {}

    public static CatalogService create(UUID projectId, String key, String name,
                                        String repositoryUrl, String owner, Instant createdAt) {
        CatalogService service = new CatalogService();
        service.id = UUID.randomUUID();
        service.projectId = projectId;
        service.key = key;
        service.name = name;
        service.repositoryUrl = repositoryUrl;
        service.owner = owner;
        service.status = ServiceStatus.ACTIVE;
        service.createdAt = createdAt;
        return service;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public String getOwner() { return owner; }
    public ServiceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
