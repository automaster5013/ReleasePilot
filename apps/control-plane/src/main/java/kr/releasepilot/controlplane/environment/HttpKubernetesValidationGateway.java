package kr.releasepilot.controlplane.environment;
import kr.releasepilot.controlplane.connection.*; import org.springframework.stereotype.Component; import tools.jackson.databind.*;
import java.net.URI; import java.net.http.*; import java.time.Duration; import java.util.*;
@Component
public class HttpKubernetesValidationGateway implements KubernetesValidationGateway {
 private final ClusterConnectionRepository connections; private final SecretResolver secrets; private final HttpClient http; private final ObjectMapper json;
 public HttpKubernetesValidationGateway(ClusterConnectionRepository connections,SecretResolver secrets,HttpClient http,ObjectMapper json){this.connections=connections;this.secrets=secrets;this.http=http;this.json=json;}
 public Snapshot inspect(Environment env){
  var connection=connections.findById(env.getClusterId()).orElse(null); if(connection==null)return unavailable();
  var secret=secrets.resolve(connection.getSecretRef()).orElse(null); if(secret==null)return unavailable();
  try{
   String root=strip(connection.getApiServer()); String ns=env.getNamespace(); String name=env.getRolloutName();
   if(get(root+"/version",secret.bearerToken()).statusCode()!=200)return unavailable();
   var rollout=get(root+"/apis/argoproj.io/v1alpha1/namespaces/"+ns+"/rollouts/"+name,secret.bearerToken());
   if(rollout.statusCode()!=200)return reachableWithoutRollout(); JsonNode node=json.readTree(rollout.body());
   String api=text(node,"apiVersion"),kind=text(node,"kind"),uid=text(node,"metadata","uid"); JsonNode canary=node.path("spec").path("strategy").path("canary");
   String stable=text(canary,"stableService"),candidate=text(canary,"canaryService"); Set<String> existing=new HashSet<>();
   for(String service:Set.of(env.getStableServiceName(),env.getCanaryServiceName()))if(get(root+"/api/v1/namespaces/"+ns+"/services/"+service,secret.bearerToken()).statusCode()==200)existing.add(service);
   Set<String> verbs=new HashSet<>(); for(String verb:List.of("get","watch","patch"))if(access(root,ns,name,verb,secret.bearerToken()))verbs.add(verb);
   return new Snapshot(true,api,kind,!canary.isMissingNode(),stable,candidate,existing,verbs,uid);
  }catch(Exception ignored){return unavailable();}
 }
 private HttpResponse<String> get(String uri,String token)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token).header("Accept","application/json").GET().build(),HttpResponse.BodyHandlers.ofString());}
 private boolean access(String root,String ns,String name,String verb,String token)throws Exception{
  String body="{\"apiVersion\":\"authorization.k8s.io/v1\",\"kind\":\"SelfSubjectAccessReview\",\"spec\":{\"resourceAttributes\":{\"group\":\"argoproj.io\",\"resource\":\"rollouts\",\"verb\":\""+verb+"\",\"namespace\":\""+ns+"\",\"name\":\""+name+"\"}}}";
  var request=HttpRequest.newBuilder(URI.create(root+"/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  var response=http.send(request,HttpResponse.BodyHandlers.ofString()); return (response.statusCode()==200||response.statusCode()==201)&&json.readTree(response.body()).path("status").path("allowed").asBoolean(false);
 }
 private String text(JsonNode node,String...path){for(String p:path)node=node.path(p);return node.asText("");}
 private String strip(String value){return value.endsWith("/")?value.substring(0,value.length()-1):value;}
 private Snapshot unavailable(){return new Snapshot(false,"","",false,"","",Set.of(),Set.of(),"");}
 private Snapshot reachableWithoutRollout(){return new Snapshot(true,"","",false,"","",Set.of(),Set.of(),"");}
}
