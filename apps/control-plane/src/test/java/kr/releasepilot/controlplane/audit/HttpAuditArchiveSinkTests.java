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
        var server=HttpServer.create(new InetSocketAddress(0),0);
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
}
