package kr.releasepilot.controlplane.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "github_check_deliveries")
public class GithubCheckDelivery {
    public enum CheckStatus { QUEUED, IN_PROGRESS, COMPLETED }
    public enum DeliveryStatus { PENDING, PROCESSING, FAILED, SYNCED }

    @Id private UUID id;
    @Column(name="release_id",nullable=false,unique=true) private UUID releaseId;
    @Column(name="repository_owner",nullable=false,length=100) private String repositoryOwner;
    @Column(name="repository_name",nullable=false,length=100) private String repositoryName;
    @Column(name="head_sha",nullable=false,length=64) private String headSha;
    @Column(name="external_check_run_id") private Long externalCheckRunId;
    @Enumerated(EnumType.STRING) @Column(name="desired_status",nullable=false,length=20) private CheckStatus desiredStatus;
    @Column(name="desired_conclusion",length=30) private String desiredConclusion;
    @Column(nullable=false,length=255) private String title;
    @Column(nullable=false,length=2000) private String summary;
    @Enumerated(EnumType.STRING) @Column(name="delivery_status",nullable=false,length=20) private DeliveryStatus deliveryStatus;
    @Column(nullable=false) private int generation;
    @Column(nullable=false) private int attempts;
    @Column(name="available_at",nullable=false) private Instant availableAt;
    @Column(name="claimed_at") private Instant claimedAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @Column(name="last_error",length=2000) private String lastError;

    protected GithubCheckDelivery() {}

    public static GithubCheckDelivery queued(UUID releaseId,String owner,String repository,String sha,String title,String summary,Instant now){
        var value=new GithubCheckDelivery();value.id=UUID.randomUUID();value.releaseId=releaseId;value.repositoryOwner=owner;
        value.repositoryName=repository;value.headSha=sha;value.desiredStatus=CheckStatus.QUEUED;value.title=title;
        value.summary=summary;value.deliveryStatus=DeliveryStatus.PENDING;value.generation=1;value.availableAt=now;value.updatedAt=now;return value;
    }
    public void request(CheckStatus status,String conclusion,String title,String summary,Instant now){desiredStatus=status;desiredConclusion=conclusion;this.title=title;this.summary=summary;generation++;deliveryStatus=DeliveryStatus.PENDING;availableAt=now;claimedAt=null;updatedAt=now;lastError=null;}
    public Snapshot claim(Instant now){if(deliveryStatus!=DeliveryStatus.PENDING&&deliveryStatus!=DeliveryStatus.FAILED)throw new IllegalStateException("Delivery is not dispatchable");deliveryStatus=DeliveryStatus.PROCESSING;attempts++;claimedAt=now;return snapshot();}
    public void complete(int deliveredGeneration,Long checkRunId,Instant now){if(externalCheckRunId==null)externalCheckRunId=checkRunId;if(generation==deliveredGeneration)deliveryStatus=DeliveryStatus.SYNCED;else deliveryStatus=DeliveryStatus.PENDING;claimedAt=null;updatedAt=now;}
    public void retry(int deliveredGeneration,String error,Instant retryAt,Instant now){if(generation==deliveredGeneration){deliveryStatus=DeliveryStatus.FAILED;availableAt=retryAt;lastError=error.substring(0,Math.min(error.length(),2000));}else deliveryStatus=DeliveryStatus.PENDING;claimedAt=null;updatedAt=now;}
    public void recover(Instant now){if(deliveryStatus!=DeliveryStatus.PROCESSING)throw new IllegalStateException("Delivery is not processing");deliveryStatus=DeliveryStatus.FAILED;availableAt=now;claimedAt=null;updatedAt=now;lastError="Delivery lease expired before completion";}
    public Snapshot snapshot(){return new Snapshot(id,releaseId,repositoryOwner,repositoryName,headSha,externalCheckRunId,desiredStatus,desiredConclusion,title,summary,generation,attempts);}
    public record Snapshot(UUID id,UUID releaseId,String owner,String repository,String sha,Long checkRunId,CheckStatus status,String conclusion,String title,String summary,int generation,int attempt){}
}
