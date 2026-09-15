package kr.releasepilot.controlplane.environment;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="environment_validation_results")
public class EnvironmentValidationResult {
 @Id private UUID id; @Column(name="environment_id",nullable=false) private UUID environmentId;
 @Column(name="check_code",nullable=false,length=60) private String checkCode;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private ValidationOutcome outcome;
 @Column(nullable=false,length=500) private String message; @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) @Column(name="details_json",nullable=false,columnDefinition="json") private String detailsJson;
 @Column(name="checked_at",nullable=false) private Instant checkedAt; protected EnvironmentValidationResult(){}
 public static EnvironmentValidationResult of(UUID env,String code,ValidationOutcome outcome,String message,String details,Instant at){var v=new EnvironmentValidationResult();v.id=UUID.randomUUID();v.environmentId=env;v.checkCode=code;v.outcome=outcome;v.message=message;v.detailsJson=details;v.checkedAt=at;return v;}
 public String getCheckCode(){return checkCode;} public ValidationOutcome getOutcome(){return outcome;} public String getMessage(){return message;} public String getDetailsJson(){return detailsJson;} public Instant getCheckedAt(){return checkedAt;}
}
