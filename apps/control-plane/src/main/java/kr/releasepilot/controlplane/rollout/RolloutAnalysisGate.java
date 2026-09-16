package kr.releasepilot.controlplane.rollout;

import kr.releasepilot.controlplane.analysis.*;
import org.springframework.stereotype.Service;

@Service
public class RolloutAnalysisGate {
    private final RolloutStepRepository steps;
    private final AnalysisJobRepository jobs;
    public RolloutAnalysisGate(RolloutStepRepository steps, AnalysisJobRepository jobs) { this.steps = steps; this.jobs = jobs; }
    public void requirePass(RolloutExecution execution) {
        var definitions = steps.findByExecutionIdOrderByStepIndexAsc(execution.getId());
        int index = execution.getCurrentStepIndex();
        if (index >= definitions.size()) throw new IllegalStateException("ROLLOUT_ANALYSIS_PASS_REQUIRED");
        var step = definitions.get(index);
        if (step.getStatus() != RolloutStepStatus.RUNNING && step.getStatus() != RolloutStepStatus.EVALUATING)
            throw new IllegalStateException("ROLLOUT_ANALYSIS_PASS_REQUIRED");
        boolean passed = jobs.findByStepIdIn(java.util.List.of(step.getId())).stream()
                .anyMatch(job -> job.getStatus() == AnalysisJobStatus.COMPLETED && job.getVerdict() == AnalysisVerdict.PASS);
        if (!passed) throw new IllegalStateException("ROLLOUT_ANALYSIS_PASS_REQUIRED");
    }
}
