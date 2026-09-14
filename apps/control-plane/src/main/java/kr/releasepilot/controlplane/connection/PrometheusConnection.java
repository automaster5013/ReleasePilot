package kr.releasepilot.controlplane.connection;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "prometheus_connections")
public class PrometheusConnection {
    @Id private UUID id;
    @Column(nullable=false,unique=true,length=100) private String name;
    @Column(name="base_url",nullable=false,length=500) private String baseUrl;
    @Column(name="secret_ref",length=255) private String secretRef;
    @Column(name="query_timeout_seconds",nullable=false) private int queryTimeoutSeconds;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ConnectionStatus status;
    @Column(name="last_validated_at") private Instant lastValidatedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected PrometheusConnection() {}
    public static PrometheusConnection create(String name,String baseUrl,String secretRef,int timeout,Instant now){
        var v=new PrometheusConnection(); v.id=UUID.randomUUID(); v.name=name; v.baseUrl=baseUrl;
        v.secretRef=secretRef; v.queryTimeoutSeconds=timeout; v.status=ConnectionStatus.UNVERIFIED; v.createdAt=now; return v;
    }
    public UUID getId(){return id;} public String getName(){return name;} public String getBaseUrl(){return baseUrl;}
    public String getSecretRef(){return secretRef;} public int getQueryTimeoutSeconds(){return queryTimeoutSeconds;}
    public ConnectionStatus getStatus(){return status;} public Instant getLastValidatedAt(){return lastValidatedAt;}
    public Instant getCreatedAt(){return createdAt;}
}
