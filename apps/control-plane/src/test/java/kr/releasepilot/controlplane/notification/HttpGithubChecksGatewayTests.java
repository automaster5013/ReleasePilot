package kr.releasepilot.controlplane.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class HttpGithubChecksGatewayTests {
    @Test void createsAndUpdatesTheSameCheckRun() throws Exception {
        var method=new AtomicReference<String>();var body=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress(0),0);
        server.createContext("/repos/owner/repo/check-runs",exchange->{method.set(exchange.getRequestMethod());body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] response="{\"id\":321}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(201,response.length);exchange.getResponseBody().write(response);exchange.close();});
        server.createContext("/repos/owner/repo/check-runs/321",exchange->{method.set(exchange.getRequestMethod());body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));byte[] response="{}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();});
        server.start();
        try{
            var gateway=new HttpGithubChecksGateway(HttpClient.newHttpClient(),new ObjectMapper(),"http://127.0.0.1:"+server.getAddress().getPort());
            var releaseId=UUID.randomUUID();
            var queued=new GithubCheckDelivery.Snapshot(UUID.randomUUID(),releaseId,"owner","repo","a".repeat(40),null,GithubCheckDelivery.CheckStatus.QUEUED,null,"Approval required","Waiting",1,1);
            assertThat(gateway.create("secret",queued)).isEqualTo(321L);
            assertThat(method).hasValue("POST");assertThat(body.get()).contains("\"head_sha\":\""+"a".repeat(40)+"\"").contains("\"status\":\"queued\"");
            var done=new GithubCheckDelivery.Snapshot(queued.id(),releaseId,"owner","repo",queued.sha(),321L,GithubCheckDelivery.CheckStatus.COMPLETED,"success","Release succeeded","Done",2,2);
            gateway.update("secret",done);
            assertThat(method).hasValue("PATCH");assertThat(body.get()).contains("\"status\":\"completed\"").contains("\"conclusion\":\"success\"");
        }finally{server.stop(0);}
    }
}
