package kr.releasepilot.controlplane.audit;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class S3AuditArchiveSinkTests {
    @Test void writesAnAppendOnlyDeterministicObject(){
        var s3=mock(S3Client.class);when(s3.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenReturn(PutObjectResponse.builder().eTag("etag").build());
        var event=AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-09-14T00:00:00Z"));event.seal(9,AuditTrail.GENESIS_HASH,"b".repeat(64));
        new S3AuditArchiveSink(new ObjectMapper(),"audit-bucket","audit-events",s3).archive(event);
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);verify(s3).putObject(request.capture(),any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("audit-bucket");assertThat(request.getValue().key()).isEqualTo("audit-events/9-"+"b".repeat(64)+".json");assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");assertThat(request.getValue().metadata()).containsEntry("event-hash","b".repeat(64));
    }
}
