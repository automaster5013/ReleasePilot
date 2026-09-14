package kr.releasepilot.controlplane.connection;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpClusterValidationGatewayTests {
    @Test void validatesEveryNamespaceAndRequiredRolloutVerb()throws Exception{
        var server=server(false);server.start();
        try{
            var gateway=new HttpClusterValidationGateway(ref->java.util.Optional.of(new SecretResolver.SecretMaterial("secret")),HttpClient.newHttpClient(),new ObjectMapper());
            var cluster=ClusterConnection.create("multi","http://127.0.0.1:"+server.getAddress().getPort(),List.of("east","west"),"env:TOKEN",Instant.now());
            var result=gateway.validate(cluster);
            assertThat(result.status()).isEqualTo(ConnectionStatus.ACTIVE);
            assertThat(result.serverVersion()).isEqualTo("v1.35.2");
            assertThat(result.namespaces()).extracting(ClusterValidationGateway.NamespaceAccess::namespace).containsExactly("east","west");
            assertThat(result.namespaces()).allMatch(ClusterValidationGateway.NamespaceAccess::allowed);
        }finally{server.stop(0);}
    }

    @Test void deniesActivationWhenOneClusterNamespaceLacksPatch()throws Exception{
        var server=server(true);server.start();
        try{
            var gateway=new HttpClusterValidationGateway(ref->java.util.Optional.of(new SecretResolver.SecretMaterial("secret")),HttpClient.newHttpClient(),new ObjectMapper());
            var cluster=ClusterConnection.create("multi","http://127.0.0.1:"+server.getAddress().getPort(),List.of("east","west"),"env:TOKEN",Instant.now());
            var result=gateway.validate(cluster);
            assertThat(result.status()).isEqualTo(ConnectionStatus.INVALID);
            assertThat(result.failureCode()).isEqualTo("NAMESPACE_ACCESS_DENIED");
            assertThat(result.namespaces()).filteredOn(v->v.namespace().equals("west")).singleElement()
                    .satisfies(v->assertThat(v.missingVerbs()).containsExactly("patch"));
        }finally{server.stop(0);}
    }

    @Test void missingSecretFailsClosedWithoutNetworkAccess(){
        var gateway=new HttpClusterValidationGateway(ref->java.util.Optional.empty(),HttpClient.newHttpClient(),new ObjectMapper());
        var cluster=ClusterConnection.create("offline","https://never-contact.invalid",List.of("default"),"env:MISSING",Instant.now());
        var result=gateway.validate(cluster);
        assertThat(result.status()).isEqualTo(ConnectionStatus.INVALID);
        assertThat(result.failureCode()).isEqualTo("SECRET_UNAVAILABLE");
    }

    private HttpServer server(boolean denyWestPatch)throws Exception{
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/version",exchange->respond(exchange,200,"{\"gitVersion\":\"v1.35.2\"}"));
        server.createContext("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews",exchange->{
            String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            boolean allowed=!(denyWestPatch&&body.contains("\"namespace\":\"west\"")&&body.contains("\"verb\":\"patch\""));
            respond(exchange,201,"{\"status\":{\"allowed\":"+allowed+"}}");
        });
        return server;
    }
    private void respond(com.sun.net.httpserver.HttpExchange exchange,int status,String body)throws java.io.IOException{
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
    }
}
