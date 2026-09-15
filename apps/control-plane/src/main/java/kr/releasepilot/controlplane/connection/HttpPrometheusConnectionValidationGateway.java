package kr.releasepilot.controlplane.connection;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class HttpPrometheusConnectionValidationGateway implements PrometheusConnectionValidationGateway {
    private final SecretResolver secrets;private final HttpClient http;private final ObjectMapper json;
    public HttpPrometheusConnectionValidationGateway(SecretResolver secrets,HttpClient http,ObjectMapper json){this.secrets=secrets;this.http=http;this.json=json;}
    @Override public Result validate(PrometheusConnection connection){
        String token=null;if(connection.getSecretRef()!=null){var secret=secrets.resolve(connection.getSecretRef());if(secret.isEmpty())return invalid("SECRET_UNAVAILABLE");token=secret.get().bearerToken();}
        try{String root=strip(connection.getBaseUrl());var ready=send(root+"/-/ready",token,connection.getQueryTimeoutSeconds());if(ready.statusCode()!=200)return invalid("PROMETHEUS_NOT_READY");var query=send(root+"/api/v1/query?query="+URLEncoder.encode("vector(1)",StandardCharsets.UTF_8),token,connection.getQueryTimeoutSeconds());if(query.statusCode()!=200)return invalid("PROMETHEUS_QUERY_FAILED");try{var body=json.readTree(query.body());if(!"success".equals(body.path("status").asText())||body.path("data").path("result").isEmpty())return invalid("PROMETHEUS_QUERY_FAILED");}catch(Exception invalidResponse){return invalid("PROMETHEUS_QUERY_FAILED");}return new Result(ConnectionStatus.ACTIVE,"");}
        catch(InterruptedException exception){Thread.currentThread().interrupt();return invalid("PROMETHEUS_UNREACHABLE");}catch(Exception exception){return invalid("PROMETHEUS_UNREACHABLE");}
    }
    private HttpResponse<String> send(String uri,String token,int timeout)throws Exception{var builder=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(timeout)).GET();if(token!=null)builder.header("Authorization","Bearer "+token);return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
    private Result invalid(String code){return new Result(ConnectionStatus.INVALID,code);}
    private String strip(String value){return value.endsWith("/")?value.substring(0,value.length()-1):value;}
}
