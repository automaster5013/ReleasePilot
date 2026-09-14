package kr.releasepilot.controlplane.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;

@Component
@ConditionalOnExpression("${releasepilot.audit.archive.enabled:false} and '${releasepilot.audit.archive.provider:http}' == 's3'")
class S3AuditArchiveSink implements AuditArchiveSink,AutoCloseable {
    private final S3Client s3;private final ObjectMapper json;private final String bucket;private final String prefix;
    @Autowired
    S3AuditArchiveSink(ObjectMapper json,@Value("${releasepilot.audit.archive.s3.bucket}")String bucket,@Value("${releasepilot.audit.archive.s3.prefix:audit-events}")String prefix,@Value("${AWS_REGION:ap-northeast-2}")String region){this(json,bucket,prefix,S3Client.builder().region(Region.of(region)).build());}
    S3AuditArchiveSink(ObjectMapper json,String bucket,String prefix,S3Client s3){this.json=json;this.bucket=bucket;this.prefix=prefix.replaceAll("^/+|/+$","");this.s3=s3;}
    public void archive(AuditEvent event){try{var body=new LinkedHashMap<String,Object>();body.put("sequence",event.getChainSequence());body.put("previousHash",event.getPreviousHash());body.put("eventHash",event.getEventHash());body.put("id",event.getId());body.put("aggregateType",event.getAggregateType());body.put("aggregateId",event.getAggregateId());body.put("eventType",event.getEventType());body.put("actorType",event.getActorType());body.put("actorId",event.getActorId());body.put("occurredAt",event.getOccurredAt());body.put("correlationId",event.getCorrelationId());body.put("payload",json.readTree(event.getPayloadJson()));byte[] content=json.writeValueAsBytes(body);String key=(prefix.isBlank()?"":prefix+"/")+event.getChainSequence()+"-"+event.getEventHash()+".json";s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/json").ifNoneMatch("*").metadata(java.util.Map.of("event-hash",event.getEventHash())).build(),RequestBody.fromBytes(content));}catch(Exception failure){throw new IllegalStateException("S3 audit archive failed",failure);}}
    public void close(){s3.close();}
}
