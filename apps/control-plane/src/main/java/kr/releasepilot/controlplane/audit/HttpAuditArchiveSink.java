package kr.releasepilot.controlplane.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.LinkedHashMap;

@Component
@ConditionalOnExpression("${releasepilot.audit.archive.enabled:false} and '${releasepilot.audit.archive.provider:http}' == 'http'")
class HttpAuditArchiveSink implements AuditArchiveSink {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final URI endpoint;
    private final String token;
    HttpAuditArchiveSink(ObjectMapper json,@Value("${releasepilot.audit.archive.endpoint}")URI endpoint,@Value("${releasepilot.audit.archive.token:}")String token){this.json=json;this.endpoint=endpoint;this.token=token;}
    public void archive(AuditEvent event){
        try{
            var body=new LinkedHashMap<String,Object>();body.put("sequence",event.getChainSequence());body.put("previousHash",event.getPreviousHash());body.put("eventHash",event.getEventHash());body.put("id",event.getId());body.put("aggregateType",event.getAggregateType());body.put("aggregateId",event.getAggregateId());body.put("eventType",event.getEventType());body.put("actorType",event.getActorType());body.put("actorId",event.getActorId());body.put("occurredAt",event.getOccurredAt());body.put("correlationId",event.getCorrelationId());body.put("payload",json.readTree(event.getPayloadJson()));
            var request=HttpRequest.newBuilder(endpoint.resolve("./"+event.getChainSequence()+"-"+event.getEventHash()+".json")).timeout(Duration.ofSeconds(10)).header("Content-Type","application/json").header("Idempotency-Key",event.getEventHash()).PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if(!token.isBlank())request.header("Authorization","Bearer "+token);
            var response=http.send(request.build(),HttpResponse.BodyHandlers.discarding());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Archive returned HTTP "+response.statusCode());
        }catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("Audit archive interrupted",failure);}catch(Exception failure){if(failure instanceof IllegalStateException state)throw state;throw new IllegalStateException("Audit archive failed",failure);}
    }
}
