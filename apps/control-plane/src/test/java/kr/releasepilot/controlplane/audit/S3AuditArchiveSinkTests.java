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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={403,500,503})
    void sdkFailureRetriesSameImmutableObjectAndRecovers(int status) throws Exception {
        var s3=mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class),any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(status).message("Bearer synthetic-sdk-secret private-bucket").build())
                .thenReturn(PutObjectResponse.builder().eTag("synthetic-etag").build());
        var fixture=new Fixture(s3);
        fixture.tick(0);
        assertThat(fixture.field("status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
        assertThat(fixture.field("lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
        assertThat(fixture.field("availableAt")).isEqualTo(fixture.now.plusSeconds(2));
        fixture.tick(2);
        assertThat(fixture.field("status")).isEqualTo(AuditArchiveDelivery.Status.DELIVERED);
        assertThat(fixture.field("lastError")).isNull();
        assertThat(fixture.field("deliveredAt")).isEqualTo(fixture.now.plusSeconds(2));
        var requests=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        var bodies=org.mockito.ArgumentCaptor.forClass(RequestBody.class);
        verify(s3,times(2)).putObject(requests.capture(),bodies.capture());
        assertThat(requests.getAllValues().get(1)).isEqualTo(requests.getAllValues().get(0));
        for(var request:requests.getAllValues()) {
            assertThat(request.ifNoneMatch()).isEqualTo("*");
            assertThat(request.key()).isEqualTo("audit-events/9-"+"b".repeat(64)+".json");
            assertThat(request.metadata()).containsEntry("event-hash","b".repeat(64));
        }
        try(var first=bodies.getAllValues().get(0).contentStreamProvider().newStream();
            var second=bodies.getAllValues().get(1).contentStreamProvider().newStream()) {
            var original=first.readAllBytes();
            assertThat(second.readAllBytes()).isEqualTo(original);
            assertThat(new String(original,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("synthetic-sdk-secret");
        }
        verifyNoMoreInteractions(s3);
    }

    @Test
    void preconditionConflictDoesNotOverwriteOrFalselyClaimDelivery() {
        var s3=mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class),any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(412).message("synthetic-conflict").build());
        var fixture=new Fixture(s3);
        fixture.tick(0);fixture.tick(2);
        assertThat(fixture.field("status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
        assertThat(fixture.field("attempts")).isEqualTo(2);
        assertThat(fixture.field("deliveredAt")).isNull();
        assertThat(fixture.field("lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3,times(2)).putObject(request.capture(),any(RequestBody.class));
        assertThat(request.getAllValues()).allSatisfy(value -> assertThat(value.ifNoneMatch()).isEqualTo("*"));
        verifyNoMoreInteractions(s3);
    }

    private static class Fixture {
        final Instant now=Instant.parse("2026-09-15T12:00:00Z");
        final AuditArchiveDeliveryRepository deliveries=mock(AuditArchiveDeliveryRepository.class);
        final AuditEventRepository events=mock(AuditEventRepository.class);
        final AuditArchiveDelivery delivery;
        final S3AuditArchiveSink sink;
        Fixture(S3Client s3) {
            var event=AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),now);
            event.seal(9,AuditTrail.GENESIS_HASH,"b".repeat(64));
            delivery=AuditArchiveDelivery.pending(event.getId(),now);
            when(deliveries.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                    eq(AuditArchiveDelivery.Status.PENDING),any(),any())).thenReturn(java.util.List.of(delivery));
            when(events.findById(event.getId())).thenReturn(java.util.Optional.of(event));
            sink=new S3AuditArchiveSink(new ObjectMapper(),"synthetic-audit-bucket","audit-events",s3);
        }
        void tick(long seconds) {
            new AuditArchiveWorker(deliveries,events,sink,
                    java.time.Clock.fixed(now.plusSeconds(seconds),java.time.ZoneOffset.UTC)).tick();
        }
        Object field(String name) {return org.springframework.test.util.ReflectionTestUtils.getField(delivery,name);}
    }

    @Test void writesAnAppendOnlyDeterministicObject(){
        var s3=mock(S3Client.class);when(s3.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenReturn(PutObjectResponse.builder().eTag("etag").build());
        var event=AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-09-14T00:00:00Z"));event.seal(9,AuditTrail.GENESIS_HASH,"b".repeat(64));
        new S3AuditArchiveSink(new ObjectMapper(),"audit-bucket","audit-events",s3).archive(event);
        var request=org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);verify(s3).putObject(request.capture(),any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("audit-bucket");assertThat(request.getValue().key()).isEqualTo("audit-events/9-"+"b".repeat(64)+".json");assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");assertThat(request.getValue().metadata()).containsEntry("event-hash","b".repeat(64));
    }
}
