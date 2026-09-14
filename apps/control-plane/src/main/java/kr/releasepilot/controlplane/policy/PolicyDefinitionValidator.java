package kr.releasepilot.controlplane.policy;
import org.springframework.stereotype.Component; import tools.jackson.databind.*; import java.util.*;
@Component
public class PolicyDefinitionValidator {
 private static final Set<String> REQUIRED=Set.of("HTTP_5XX_RATE","HTTP_P95_LATENCY_MS","REQUEST_COUNT"); private final ObjectMapper json;
 public PolicyDefinitionValidator(ObjectMapper json){this.json=json;}
 public Result validate(String definition){
  List<String> errors=new ArrayList<>();
  try{JsonNode root=json.readTree(definition);
   if(!"1.0".equals(root.path("schemaVersion").asText()))errors.add("schemaVersion must be 1.0");
   String strategy=root.path("strategy").asText();if(!Set.of("CANARY","BLUE_GREEN").contains(strategy))errors.add("strategy must be CANARY or BLUE_GREEN");
   JsonNode steps=root.path("steps");if(!steps.isArray()||steps.isEmpty())errors.add("steps must not be empty");else{int previous=0;for(JsonNode step:steps){int weight=step.path("weight").asInt(-1);if(weight<=previous||weight>100)errors.add("Rollout weights must increase and be at most 100");previous=weight;if(step.path("minimumObservationSeconds").asInt(0)<30)errors.add("minimumObservationSeconds must be at least 30");}if(previous!=100)errors.add("Last rollout weight must be 100");if("BLUE_GREEN".equals(strategy)&&steps.size()!=1)errors.add("BLUE_GREEN must define exactly one 100% preview analysis step");}
   Map<String,Integer> counts=new HashMap<>();JsonNode metrics=root.path("metrics");if(!metrics.isArray())errors.add("metrics must be an array");else for(JsonNode metric:metrics){String key=metric.path("key").asText();counts.merge(key,1,Integer::sum);if(REQUIRED.contains(key)&&!metric.path("required").asBoolean(false))errors.add(key+" must be required");if("HTTP_5XX_RATE".equals(key)){double threshold=metric.path("threshold").asDouble(-1);if(threshold<0||threshold>1)errors.add("HTTP_5XX_RATE threshold must be between 0 and 1");}if("REQUEST_COUNT".equals(key)&&!metric.path("relativeToBaseline").isMissingNode())errors.add("REQUEST_COUNT cannot define relativeToBaseline");}
   for(String key:REQUIRED)if(counts.getOrDefault(key,0)!=1)errors.add(key+" must appear exactly once");
  }catch(Exception exception){errors.add("Definition must be valid JSON");}
  return new Result(errors.isEmpty(),List.copyOf(errors));
 }
 public record Result(boolean valid,List<String> violations){}
}
