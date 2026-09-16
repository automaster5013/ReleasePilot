package kr.releasepilot.controlplane.rollout;
import kr.releasepilot.controlplane.analysis.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RolloutAnalysisGateTests {
 @Test void onlyCompletedPassForCurrentStepAuthorizesPromotion() {
  var steps=mock(RolloutStepRepository.class);var jobs=mock(AnalysisJobRepository.class);
  var now=Instant.now();var execution=RolloutExecution.pending(UUID.randomUUID(),UUID.randomUUID(),"demo","app","digest",now);
  var step=RolloutStep.pending(execution.getId(),0,20,60);
  when(steps.findByExecutionIdOrderByStepIndexAsc(execution.getId())).thenReturn(List.of(step));
  var gate=new RolloutAnalysisGate(steps,jobs);
  when(jobs.findByStepIdIn(List.of(step.getId()))).thenReturn(List.of());
  assertThatThrownBy(()->gate.requirePass(execution)).hasMessage("ROLLOUT_ANALYSIS_PASS_REQUIRED");
  for(var verdict:AnalysisVerdict.values()) {
   var job=AnalysisJob.pending(step.getId(),UUID.randomUUID(),1,now,now);job.claim(now);
   when(jobs.findByStepIdIn(List.of(step.getId()))).thenReturn(List.of(job));
   assertThatThrownBy(()->gate.requirePass(execution)).hasMessage("ROLLOUT_ANALYSIS_PASS_REQUIRED");
   job.complete(verdict,"test","[]",now);
   if(verdict==AnalysisVerdict.PASS)assertThatCode(()->gate.requirePass(execution)).doesNotThrowAnyException();
   else assertThatThrownBy(()->gate.requirePass(execution)).hasMessage("ROLLOUT_ANALYSIS_PASS_REQUIRED");
  }
 }
}
