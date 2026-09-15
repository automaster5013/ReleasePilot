package kr.releasepilot.controlplane.connection;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Entity @Table(name = "cluster_connections")
public class ClusterConnection {
    @Id private UUID id;
    @Column(nullable=false,unique=true,length=100) private String name;
    @Column(name="api_server",nullable=false,length=500) private String apiServer;
    @Column(name="allowed_namespaces",nullable=false,length=2000) private String allowedNamespaces;
    @Column(name="secret_ref",nullable=false,length=255) private String secretRef;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ConnectionStatus status;
    @Column(name="last_validated_at") private Instant lastValidatedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    protected ClusterConnection() {}
    public static ClusterConnection create(String name,String apiServer,List<String> namespaces,String secretRef,Instant now){
        var v=new ClusterConnection(); v.id=UUID.randomUUID(); v.name=name; v.apiServer=apiServer;
        v.allowedNamespaces=String.join(",",namespaces); v.secretRef=secretRef;
        v.status=ConnectionStatus.UNVERIFIED; v.createdAt=now; return v;
    }
    public UUID getId(){return id;} public String getName(){return name;} public String getApiServer(){return apiServer;}
    public List<String> getAllowedNamespaces(){return List.of(allowedNamespaces.split(","));}
    public String getSecretRef(){return secretRef;} public ConnectionStatus getStatus(){return status;}
    public Instant getLastValidatedAt(){return lastValidatedAt;} public Instant getCreatedAt(){return createdAt;}
    public void validated(ConnectionStatus status,Instant now){
        if(status!=ConnectionStatus.ACTIVE&&status!=ConnectionStatus.INVALID)throw new IllegalArgumentException("Validation status must be ACTIVE or INVALID");
        this.status=status;this.lastValidatedAt=now;
    }
    public void update(String name,String apiServer,List<String> namespaces,String secretRef){
        this.name=name;this.apiServer=apiServer;this.allowedNamespaces=String.join(",",namespaces);this.secretRef=secretRef;
        this.status=ConnectionStatus.UNVERIFIED;this.lastValidatedAt=null;
    }
}
