package kr.releasepilot.controlplane.rollout;

import kr.releasepilot.controlplane.environment.Environment;
import kr.releasepilot.controlplane.release.Release;
import kr.releasepilot.controlplane.release.ReleaseArtifactRepository;
import kr.releasepilot.controlplane.release.PolicySnapshotRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;

@Service
public class RolloutDispatchService {
    private final RolloutExecutionRepository executions;
    private final OutboxCommandRepository commands;
    private final ReleaseArtifactRepository artifacts;
    private final PolicySnapshotRepository snapshots;
    private final RolloutStepRepository steps;
    private final ObjectMapper json;

    public RolloutDispatchService(RolloutExecutionRepository executions, OutboxCommandRepository commands,
                                  ReleaseArtifactRepository artifacts,PolicySnapshotRepository snapshots,
                                  RolloutStepRepository steps,ObjectMapper json) {
        this.executions = executions;
        this.commands = commands;
        this.artifacts = artifacts;
        this.snapshots=snapshots;this.steps=steps;this.json=json;
    }

    public RolloutExecution schedule(Release release, Environment environment, Instant now) {
        var existing = executions.findByReleaseId(release.getId());
        if (existing.isPresent()) return existing.get();
        var artifact = artifacts.findByReleaseId(release.getId()).orElseThrow();
        var execution = executions.save(RolloutExecution.pending(release.getId(), environment.getClusterId(),
                environment.getNamespace(), environment.getRolloutName(), artifact.getImageDigest(), now));
        try{var policy=json.readTree(snapshots.findByReleaseId(release.getId()).orElseThrow().getDefinition());if(!environment.getRolloutStrategy().name().equals(policy.path("strategy").asText()))throw new IllegalStateException("Policy strategy does not match environment");var definitions=policy.path("steps");if(!definitions.isArray()||definitions.isEmpty())throw new IllegalStateException("Policy snapshot has no rollout steps");int previous=0;for(int index=0;index<definitions.size();index++){var definition=definitions.get(index);int weight=definition.path("weight").asInt();int observation=definition.path("minimumObservationSeconds").asInt(-1);if(weight<=previous)throw new IllegalStateException("Rollout weights must increase");steps.save(RolloutStep.pending(execution.getId(),index,weight,observation));previous=weight;}if(previous!=100)throw new IllegalStateException("Last rollout weight must be 100");}catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Cannot create Rollout steps from policy snapshot",e);}
        commands.save(OutboxCommand.startRollout(execution, now));
        return execution;
    }
}
