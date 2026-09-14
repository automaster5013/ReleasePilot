package kr.releasepilot.controlplane.notification;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HttpGithubChecksGateway implements GithubChecksGateway {
    private final HttpClient http; private final ObjectMapper json;private final String apiBaseUrl;
    public HttpGithubChecksGateway(HttpClient http,ObjectMapper json,@Value("${releasepilot.github-checks.api-base-url:https://api.github.com}")String apiBaseUrl){this.http=http;this.json=json;this.apiBaseUrl=apiBaseUrl.replaceFirst("/$","");}
    public long create(String token,GithubCheckDelivery.Snapshot d){var body=payload(d);body.put("head_sha",d.sha());var result=send(token,uri(d,"/check-runs"),"POST",body);long id=result.path("id").asLong();if(id<=0)throw new IllegalStateException("GitHub check run id is missing");return id;}
    public void update(String token,GithubCheckDelivery.Snapshot d){send(token,uri(d,"/check-runs/"+d.checkRunId()),"PATCH",payload(d));}
    private Map<String,Object> payload(GithubCheckDelivery.Snapshot d){var body=new LinkedHashMap<String,Object>();body.put("name","ReleasePilot");body.put("status",d.status().name().toLowerCase());body.put("external_id",d.releaseId().toString());if(d.status()==GithubCheckDelivery.CheckStatus.COMPLETED)body.put("conclusion",d.conclusion());body.put("details_url","https://releasepilot.kr/?release="+d.releaseId());body.put("output",Map.of("title",d.title(),"summary",d.summary()));return body;}
    private tools.jackson.databind.JsonNode send(String token,URI uri,String method,Object body){try{var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Accept","application/vnd.github+json").header("X-GitHub-Api-Version","2026-03-10").header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("GitHub Checks API returned HTTP "+response.statusCode()+": "+response.body().substring(0,Math.min(response.body().length(),500)));return json.readTree(response.body());}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("GitHub Checks request interrupted",e);}catch(Exception e){if(e instanceof IllegalStateException state)throw state;throw new IllegalStateException("GitHub Checks request failed",e);}}
    private URI uri(GithubCheckDelivery.Snapshot d,String suffix){return URI.create(apiBaseUrl+"/repos/"+d.owner()+"/"+d.repository()+suffix);}
}
