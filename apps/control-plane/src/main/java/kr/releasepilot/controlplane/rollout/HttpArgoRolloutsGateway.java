package kr.releasepilot.controlplane.rollout;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
public class HttpArgoRolloutsGateway implements ArgoRolloutsGateway {
    private final HttpClient http;
    private final ObjectMapper json;

    public HttpArgoRolloutsGateway(HttpClient http, ObjectMapper json) { this.http = http; this.json = json; }

    @Override public ObservedRollout start(StartRequest request) {
        try {
            var uri = rolloutUri(request);
            var current = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + request.bearerToken()).header("Accept", "application/json").GET().build());
            requireSuccess(current, "read");
            JsonNode rollout = json.readTree(current.body());
            validatePolicySteps(rollout, request.policyWeights());
            String uid = requiredText(rollout.path("metadata"), "uid");
            String resourceVersion = requiredText(rollout.path("metadata"), "resourceVersion");
            var containers = rollout.path("spec").path("template").path("spec").path("containers");
            int index = -1;
            for (int i = 0; i < containers.size(); i++) if (request.containerName().equals(containers.get(i).path("name").asText())) { index = i; break; }
            if (index < 0) throw new IllegalStateException("Container not found in Rollout: " + request.containerName());
            if (request.image().equals(containers.get(index).path("image").asText())) return new ObservedRollout(uid, resourceVersion);
            String patch = json.writeValueAsString(new Object[]{
                    new PatchOperation("test", "/metadata/uid", uid),
                    new PatchOperation("test", "/metadata/resourceVersion", resourceVersion),
                    new PatchOperation("replace", "/spec/template/spec/containers/" + index + "/image", request.image())});
            var updated = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + request.bearerToken())
                    .header("Accept", "application/json").header("Content-Type", "application/json-patch+json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(patch)).build());
            requireSuccess(updated, "patch");
            JsonNode result = json.readTree(updated.body());
            if (!uid.equals(requiredText(result.path("metadata"), "uid"))) throw new IllegalStateException("Rollout UID changed during patch");
            return new ObservedRollout(uid, requiredText(result.path("metadata"), "resourceVersion"));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Argo Rollout request interrupted", interrupted);
        } catch (Exception failure) {
            if (failure instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Argo Rollout request failed", failure);
        }
    }

    @Override public ObservedRollout control(ControlRequest request) {
        try {
            URI uri = URI.create(strip(request.apiServer()) + "/apis/argoproj.io/v1alpha1/namespaces/" + request.namespace() + "/rollouts/" + request.rolloutName());
            var current = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization", "Bearer " + request.bearerToken()).header("Accept", "application/json").GET().build());
            requireSuccess(current, "read"); JsonNode rollout=json.readTree(current.body());String uid=requiredText(rollout.path("metadata"),"uid");String rv=requiredText(rollout.path("metadata"),"resourceVersion");
            if(!request.expectedUid().equals(uid))throw new IllegalStateException("Rollout UID does not match execution");
            boolean statusAction=request.action()==Action.ABORT||request.action()==Action.PROMOTE;String path=request.action()==Action.ABORT?"/status/abort":request.action()==Action.PROMOTE?"/status/pauseConditions":"/spec/paused";Object value=request.action()==Action.PROMOTE?java.util.List.of():request.action()!=Action.RESUME;
            String patch=json.writeValueAsString(new Object[]{new PatchOperation("test","/metadata/uid",uid),new PatchOperation("test","/metadata/resourceVersion",rv),new ObjectPatchOperation("add",path,value)});
            URI patchUri=statusAction?URI.create(uri+"/status"):uri;var updated=send(HttpRequest.newBuilder(patchUri).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+request.bearerToken()).header("Accept","application/json").header("Content-Type","application/json-patch+json").method("PATCH",HttpRequest.BodyPublishers.ofString(patch)).build());
            requireSuccess(updated,"control");JsonNode result=json.readTree(updated.body());if(!uid.equals(requiredText(result.path("metadata"),"uid")))throw new IllegalStateException("Rollout UID changed during control operation");return new ObservedRollout(uid,requiredText(result.path("metadata"),"resourceVersion"));
        } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Argo Rollout request interrupted",interrupted);}catch(Exception failure){if(failure instanceof IllegalStateException state)throw state;throw new IllegalStateException("Argo Rollout control request failed",failure);}
    }

    @Override public Observation observe(ObserveRequest request){try{URI uri=URI.create(strip(request.apiServer())+"/apis/argoproj.io/v1alpha1/namespaces/"+request.namespace()+"/rollouts/"+request.rolloutName());var response=send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+request.bearerToken()).header("Accept","application/json").GET().build());requireSuccess(response,"observe");JsonNode rollout=json.readTree(response.body());String image="";for(JsonNode container:rollout.path("spec").path("template").path("spec").path("containers"))if(request.containerName().equals(container.path("name").asText()))image=container.path("image").asText("");if(image.isBlank())throw new IllegalStateException("Container not found in Rollout: "+request.containerName());JsonNode status=rollout.path("status");JsonNode metadata=rollout.path("metadata");int logicalStep=logicalCanaryStep(rollout.path("spec").path("strategy").path("canary").path("steps"),status.path("currentStepIndex").asInt(0));return new Observation(requiredText(metadata,"uid"),requiredText(metadata,"resourceVersion"),status.path("phase").asText("Unknown"),logicalStep,image,status.path("abort").asBoolean(false),metadata.path("generation").asLong(0),status.path("observedGeneration").asLong(0));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Argo Rollout observation interrupted",e);}catch(Exception e){if(e instanceof IllegalStateException state)throw state;throw new IllegalStateException("Argo Rollout observation failed",e);}}

    private int logicalCanaryStep(JsonNode steps,int rawStepIndex){int weightSteps=0;int limit=Math.min(rawStepIndex,steps.size()-1);for(int index=0;index<=limit;index++)if(steps.get(index).has("setWeight"))weightSteps++;if(rawStepIndex>=steps.size())return weightSteps;return Math.max(0,weightSteps-1);}

    private void validatePolicySteps(JsonNode rollout, java.util.List<Integer> weights) {
        if (weights.isEmpty()) return;
        var strategy = rollout.path("spec").path("strategy");
        if (strategy.has("blueGreen")) {
            if (!weights.equals(java.util.List.of(100)) || strategy.path("blueGreen").path("autoPromotionEnabled").asBoolean(true))
                throw new IllegalStateException("ROLLOUT_POLICY_STEPS_MISMATCH");
            return;
        }
        var definitions = strategy.path("canary").path("steps");
        if (!definitions.isArray() || definitions.size() != weights.size() * 2)
            throw new IllegalStateException("ROLLOUT_POLICY_STEPS_MISMATCH");
        for (int index = 0; index < weights.size(); index++) {
            var weight = definitions.get(index * 2);
            var pause = definitions.get(index * 2 + 1);
            if (weight.size() != 1 || weight.path("setWeight").asInt(-1) != weights.get(index)
                    || pause.size() != 1 || !pause.path("pause").isObject() || !pause.path("pause").isEmpty())
                throw new IllegalStateException("ROLLOUT_POLICY_STEPS_MISMATCH");
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception { return http.send(request, HttpResponse.BodyHandlers.ofString()); }
    private URI rolloutUri(StartRequest r) { return URI.create(strip(r.apiServer()) + "/apis/argoproj.io/v1alpha1/namespaces/" + r.namespace() + "/rollouts/" + r.rolloutName()); }
    private String strip(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private void requireSuccess(HttpResponse<String> response, String action) { if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Cannot " + action + " Rollout: HTTP " + response.statusCode()); }
    private String requiredText(JsonNode node, String field) { String value = node.path(field).asText(""); if (value.isBlank()) throw new IllegalStateException("Rollout metadata." + field + " is missing"); return value; }
    private record PatchOperation(String op, String path, String value) {}
    private record ObjectPatchOperation(String op, String path, Object value) {}
}
