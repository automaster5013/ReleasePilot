package kr.releasepilot.controlplane.environment;
import kr.releasepilot.controlplane.connection.*; import org.springframework.stereotype.Component; import tools.jackson.databind.*;
import java.net.*; import java.net.http.*; import java.nio.charset.StandardCharsets; import java.time.Duration;
@Component
public class HttpPrometheusValidationGateway implements PrometheusValidationGateway {
 private final PrometheusConnectionRepository connections; private final SecretResolver secrets; private final HttpClient http; private final ObjectMapper json;
 public HttpPrometheusValidationGateway(PrometheusConnectionRepository connections,SecretResolver secrets,HttpClient http,ObjectMapper json){this.connections=connections;this.secrets=secrets;this.http=http;this.json=json;}
 public Snapshot inspect(Environment env){
  var connection=connections.findById(env.getPrometheusConnectionId()).orElse(null);if(connection==null)return unavailable();
  String token=connection.getSecretRef()==null?null:secrets.resolve(connection.getSecretRef()).map(SecretResolver.SecretMaterial::bearerToken).orElse(null);
  if(connection.getSecretRef()!=null&&token==null)return unavailable();
  try{String root=strip(connection.getBaseUrl());boolean ready=send(root+"/-/ready",token).statusCode()==200;
   var recent=query(root,"count({"+env.getWorkloadLabelSelector().replace("=","=\"").replace(",","\",")+"\"})",token,connection.getQueryTimeoutSeconds());
   var tracks=query(root,"count by (release_track) ({"+env.getWorkloadLabelSelector().replace("=","=\"").replace(",","\",")+"\"})",token,connection.getQueryTimeoutSeconds());
   return new Snapshot(ready,recent.statusCode()==200,hasResults(recent.body()),hasResults(tracks.body()));
  }catch(Exception ignored){return unavailable();}
 }
 private HttpResponse<String> query(String root,String query,String token,int timeout)throws Exception{return send(root+"/api/v1/query?query="+URLEncoder.encode(query,StandardCharsets.UTF_8),token,timeout);}
 private HttpResponse<String> send(String uri,String token)throws Exception{return send(uri,token,10);}
 private HttpResponse<String> send(String uri,String token,int timeout)throws Exception{var b=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(timeout)).GET();if(token!=null)b.header("Authorization","Bearer "+token);return http.send(b.build(),HttpResponse.BodyHandlers.ofString());}
 private boolean hasResults(String body)throws Exception{return json.readTree(body).path("data").path("result").size()>0;}
 private String strip(String value){return value.endsWith("/")?value.substring(0,value.length()-1):value;}
 private Snapshot unavailable(){return new Snapshot(false,false,false,false);}
}
