package kr.releasepilot.controlplane.connection;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class HttpClusterValidationGateway implements ClusterValidationGateway {
    private static final List<String> REQUIRED_VERBS=List.of("get","watch","patch");
    private final SecretResolver secrets;
    private final HttpClient http;
    private final ObjectMapper json;
    private final OutboundTargetPolicy targets;

    public HttpClusterValidationGateway(SecretResolver secrets,HttpClient http,ObjectMapper json,OutboundTargetPolicy targets){
        this.secrets=secrets;this.http=http;this.json=json;this.targets=targets;
    }

    @Override public Result validate(ClusterConnection connection){
        var secret=secrets.resolve(connection.getSecretRef());
        if(secret.isEmpty())return invalid("SECRET_UNAVAILABLE",List.of());
        try{targets.requireAllowed(connection.getApiServer(),java.util.Set.of("https"));}
        catch(OutboundTargetPolicy.TargetNotAllowedException exception){return invalid("TARGET_NOT_ALLOWED",List.of());}
        try{
            String root=strip(connection.getApiServer());
            var versionResponse=send(HttpRequest.newBuilder(URI.create(root+"/version")).timeout(Duration.ofSeconds(10))
                    .header("Authorization","Bearer "+secret.get().bearerToken()).header("Accept","application/json").GET().build());
            if(versionResponse.statusCode()==401||versionResponse.statusCode()==403)return invalid("AUTHENTICATION_FAILED",List.of());
            if(versionResponse.statusCode()!=200)return invalid("CLUSTER_UNREACHABLE",List.of());
            String version=json.readTree(versionResponse.body()).path("gitVersion").asText("");
            if(version.isBlank())return invalid("KUBERNETES_VERSION_UNAVAILABLE",List.of());
            var namespaces=new ArrayList<NamespaceAccess>();
            for(String namespace:connection.getAllowedNamespaces()){
                var missing=new ArrayList<String>();
                for(String verb:REQUIRED_VERBS)if(!allowed(root,namespace,verb,secret.get().bearerToken()))missing.add(verb);
                namespaces.add(new NamespaceAccess(namespace,missing.isEmpty(),missing));
            }
            boolean active=namespaces.stream().allMatch(NamespaceAccess::allowed);
            return new Result(active?ConnectionStatus.ACTIVE:ConnectionStatus.INVALID,version,
                    active?"":"NAMESPACE_ACCESS_DENIED",namespaces);
        }catch(InterruptedException exception){
            Thread.currentThread().interrupt();return invalid("CLUSTER_UNREACHABLE",List.of());
        }catch(Exception exception){
            return invalid("CLUSTER_UNREACHABLE",List.of());
        }
    }

    private boolean allowed(String root,String namespace,String verb,String token)throws Exception{
        String body=json.writeValueAsString(java.util.Map.of(
                "apiVersion","authorization.k8s.io/v1","kind","SelfSubjectAccessReview","spec",java.util.Map.of(
                        "resourceAttributes",java.util.Map.of("group","argoproj.io","resource","rollouts","verb",verb,"namespace",namespace))));
        var request=HttpRequest.newBuilder(URI.create(root+"/apis/authorization.k8s.io/v1/selfsubjectaccessreviews"))
                .timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response=send(request);
        return (response.statusCode()==200||response.statusCode()==201)&&json.readTree(response.body()).path("status").path("allowed").asBoolean(false);
    }
    private HttpResponse<String> send(HttpRequest request)throws Exception{return http.send(request,HttpResponse.BodyHandlers.ofString());}
    private Result invalid(String code,List<NamespaceAccess> namespaces){return new Result(ConnectionStatus.INVALID,"",code,namespaces);}
    private String strip(String value){return value.endsWith("/")?value.substring(0,value.length()-1):value;}
}
