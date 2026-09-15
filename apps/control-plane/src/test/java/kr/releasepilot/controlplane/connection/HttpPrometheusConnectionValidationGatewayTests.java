package kr.releasepilot.controlplane.connection;

import com.sun.net.httpserver.HttpServer;import org.junit.jupiter.api.*;import tools.jackson.databind.ObjectMapper;
import java.net.*;import java.net.http.HttpClient;import java.nio.charset.StandardCharsets;import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;import static org.mockito.Mockito.*;

class HttpPrometheusConnectionValidationGatewayTests {
 private HttpServer server;@AfterEach void stop(){if(server!=null)server.stop(0);}
 @Test void readyAndQueryEndpointsActivateConnection()throws Exception{server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/-/ready",exchange->{exchange.sendResponseHeaders(200,-1);exchange.close();});server.createContext("/api/v1/query",exchange->{byte[] body="{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{},\"value\":[1,\"1\"]}]}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();var connection=PrometheusConnection.create("prom","http://127.0.0.1:"+server.getAddress().getPort(),null,5,Instant.now());var result=new HttpPrometheusConnectionValidationGateway(mock(SecretResolver.class),HttpClient.newHttpClient(),new ObjectMapper()).validate(connection);assertThat(result.status()).isEqualTo(ConnectionStatus.ACTIVE);assertThat(result.failureCode()).isEmpty();}
}
