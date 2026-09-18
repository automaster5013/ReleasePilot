package kr.releasepilot.controlplane.analysis;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpAnalysisWorkerGatewayTests {
    private HttpServer server;
    @AfterEach void stop(){if(server!=null)server.stop(0);}

    @Test void sendsSnapshotContextAndRouteImportanceThenReturnsEvidence()throws Exception{
        var received=new AtomicReference<String>();var receivedToken=new AtomicReference<String>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/analyses",exchange->{
            received.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-ReleasePilot-Worker-Token"));
            byte[] body="{\"verdict\":\"PASS\",\"reason_code\":\"ALL_RULES_PASSED\",\"evidence\":[{\"metric_key\":\"REQUEST_COUNT\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.start();
        var gateway=new HttpAnalysisWorkerGateway(HttpClient.newHttpClient(),new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort(),"worker-test-token");
        var request=new AnalysisWorkerGateway.Request("","http://prometheus:9090","secret","prom-1","checksum",
                Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2026-01-01T00:05:00Z"),"shop","checkout","production",
                "[{\"key\":\"REQUEST_COUNT\",\"required\":true,\"comparison\":\"GREATER_THAN_OR_EQUAL\",\"threshold\":100,\"route\":\"/checkout/{id}\",\"importance\":\"CRITICAL\"}]");
        var result=gateway.evaluate(request);
        assertThat(result.verdict()).isEqualTo(AnalysisVerdict.PASS);
        assertThat(result.evidenceJson()).contains("REQUEST_COUNT");
        assertThat(received.get()).contains("prometheus_url","policy_snapshot_checksum","\"bearer_token\":\"secret\"",
                "\"route\":\"/checkout/{id}\"","\"importance\":\"CRITICAL\"").doesNotContain("release_track");
        assertThat(receivedToken.get()).isEqualTo("worker-test-token");
    }
}
