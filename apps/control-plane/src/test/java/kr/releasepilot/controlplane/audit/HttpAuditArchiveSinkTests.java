package kr.releasepilot.controlplane.audit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class HttpAuditArchiveSinkTests {
    @Test void putsImmutableEnvelopeWithEventHashAsIdempotencyKey() throws Exception {
        var path=new AtomicReference<String>();var key=new AtomicReference<String>();var authorization=new AtomicReference<String>();var body=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/archive/",exchange->{path.set(exchange.getRequestURI().getPath());key.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));exchange.sendResponseHeaders(201,-1);exchange.close();});server.start();
        try{
            var event=AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),Instant.parse("2026-09-14T00:00:00Z"));
            event.seal(7,AuditTrail.GENESIS_HASH,"a".repeat(64));
            new HttpAuditArchiveSink(new ObjectMapper(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/archive/"),"archive-token").archive(event);
            assertThat(path).hasValue("/archive/7-"+"a".repeat(64)+".json");
            assertThat(key).hasValue("a".repeat(64));assertThat(authorization).hasValue("Bearer archive-token");
            assertThat(body.get()).contains("\"sequence\":7").contains("\"eventHash\":\""+"a".repeat(64)+"\"");
        }finally{server.stop(0);}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={429,500,503})
    void actualHttpFailureRetriesSameEnvelopeAndRecovers(int failureStatus) throws Exception {
        var requests=java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        var bodies=java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/archive/",exchange->{
            requests.add(exchange.getRequestMethod()+" "+exchange.getRequestURI().getPath()+" "+exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response="Bearer synthetic-response-secret private-endpoint".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(calls.incrementAndGet()==1?failureStatus:201,response.length);
            exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        try {
            var now=Instant.parse("2026-09-15T12:00:00Z");
            var event=AuditEvent.projectCreated(UUID.randomUUID(),UUID.randomUUID(),now);
            event.seal(7,AuditTrail.GENESIS_HASH,"a".repeat(64));
            var delivery=AuditArchiveDelivery.pending(event.getId(),now);
            var deliveries=org.mockito.Mockito.mock(AuditArchiveDeliveryRepository.class);
            var events=org.mockito.Mockito.mock(AuditEventRepository.class);
            org.mockito.Mockito.when(deliveries.findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                    org.mockito.ArgumentMatchers.eq(AuditArchiveDelivery.Status.PENDING),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
                    .thenReturn(java.util.List.of(delivery));
            org.mockito.Mockito.when(events.findById(event.getId())).thenReturn(java.util.Optional.of(event));
            var sink=new HttpAuditArchiveSink(new ObjectMapper(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/archive/"),"synthetic-test-token");
            new AuditArchiveWorker(deliveries,events,sink,java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)).tick();
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(delivery,"status")).isEqualTo(AuditArchiveDelivery.Status.PENDING);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(delivery,"lastError")).isEqualTo("AUDIT_ARCHIVE_UNAVAILABLE");
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(delivery,"availableAt")).isEqualTo(now.plusSeconds(2));
            new AuditArchiveWorker(deliveries,events,sink,java.time.Clock.fixed(now.plusSeconds(2),java.time.ZoneOffset.UTC)).tick();
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(delivery,"status")).isEqualTo(AuditArchiveDelivery.Status.DELIVERED);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(delivery,"lastError")).isNull();
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(delivery,"deliveredAt")).isEqualTo(now.plusSeconds(2));
            assertThat(calls.get()).isEqualTo(2);
            assertThat(requests).containsExactly("PUT /archive/7-"+"a".repeat(64)+".json "+"a".repeat(64),requests.get(0));
            assertThat(bodies).hasSize(2);
            assertThat(bodies.get(1)).isEqualTo(bodies.get(0)).doesNotContain("synthetic-test-token","synthetic-response-secret");
        } finally {
            server.stop(0);
        }
    }
}
