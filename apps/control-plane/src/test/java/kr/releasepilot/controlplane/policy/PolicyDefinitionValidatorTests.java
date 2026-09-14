package kr.releasepilot.controlplane.policy;
import org.junit.jupiter.api.Test; import tools.jackson.databind.ObjectMapper; import static org.assertj.core.api.Assertions.assertThat;
class PolicyDefinitionValidatorTests {
 private final PolicyDefinitionValidator validator=new PolicyDefinitionValidator(new ObjectMapper());
 @Test void acceptsMvpCanaryPolicy(){assertThat(validator.validate(valid()).valid()).isTrue();}
 @Test void reportsNonIncreasingWeightsAndMissingMetric(){String invalid=valid().replace("{\"weight\":30,\"minimumObservationSeconds\":300}","{\"weight\":10,\"minimumObservationSeconds\":300}").replace(",{\"key\":\"REQUEST_COUNT\",\"required\":true,\"threshold\":1000}","");var result=validator.validate(invalid);assertThat(result.valid()).isFalse();assertThat(result.violations()).anyMatch(v->v.contains("weights")).anyMatch(v->v.contains("REQUEST_COUNT"));}
 @Test void rejectsRequestCountBaseline(){String invalid=valid().replace("\"threshold\":1000}","\"threshold\":1000,\"relativeToBaseline\":{\"maximumAbsoluteIncrease\":1}}");assertThat(validator.validate(invalid).violations()).contains("REQUEST_COUNT cannot define relativeToBaseline");}
 private String valid(){return """
 {"schemaVersion":"1.0","name":"production","strategy":"CANARY","steps":[{"weight":10,"minimumObservationSeconds":300},{"weight":30,"minimumObservationSeconds":300},{"weight":100,"minimumObservationSeconds":300}],"metrics":[{"key":"HTTP_5XX_RATE","required":true,"threshold":0.01},{"key":"HTTP_P95_LATENCY_MS","required":true,"threshold":500},{"key":"REQUEST_COUNT","required":true,"threshold":1000}],"inconclusivePolicy":{"additionalObservationSeconds":300,"maximumAttempts":2,"onExhausted":"PAUSE"}}
 """;}
}
