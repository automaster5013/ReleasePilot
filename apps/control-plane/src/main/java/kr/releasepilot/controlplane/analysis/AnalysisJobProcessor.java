package kr.releasepilot.controlplane.analysis;
import kr.releasepilot.controlplane.catalog.*;import kr.releasepilot.controlplane.connection.*;import kr.releasepilot.controlplane.environment.*;import kr.releasepilot.controlplane.release.*;import kr.releasepilot.controlplane.rollout.*;import org.springframework.stereotype.Service;import org.springframework.transaction.support.TransactionTemplate;import tools.jackson.databind.*;import java.time.*;import java.util.*;
@Service public class AnalysisJobProcessor{
 private final AnalysisJobRepository jobs;private final RolloutStepRepository steps;private final RolloutExecutionRepository executions;private final ReleaseRepository releases;private final EnvironmentRepository environments;private final CatalogServiceRepository services;private final PrometheusConnectionRepository prometheus;private final PolicySnapshotRepository snapshots;private final SecretResolver secrets;private final AnalysisWorkerGateway worker;private final OutboxCommandRepository commands;private final ObjectMapper json;private final TransactionTemplate transactions;private final Clock clock;
 public AnalysisJobProcessor(AnalysisJobRepository jobs,RolloutStepRepository steps,RolloutExecutionRepository executions,ReleaseRepository releases,EnvironmentRepository environments,CatalogServiceRepository services,PrometheusConnectionRepository prometheus,PolicySnapshotRepository snapshots,SecretResolver secrets,AnalysisWorkerGateway worker,OutboxCommandRepository commands,ObjectMapper json,TransactionTemplate transactions,Clock clock){this.jobs=jobs;this.steps=steps;this.executions=executions;this.releases=releases;this.environments=environments;this.services=services;this.prometheus=prometheus;this.snapshots=snapshots;this.secrets=secrets;this.worker=worker;this.commands=commands;this.json=json;this.transactions=transactions;this.clock=clock;}
 public boolean processOne(){var claimed=claim();if(claimed.isEmpty())return false;var context=context(claimed.get());try{finish(claimed.get(),context,context.preflightFailure()!=null?new AnalysisWorkerGateway.Result(AnalysisVerdict.INCONCLUSIVE,context.preflightFailure(),"[]"):worker.evaluate(context.request()));}catch(RuntimeException failure){handleFailure(claimed.get(),context);}return true;}
 private Optional<UUID> claim(){return transactions.execute(status->jobs.findFirstByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(List.of(AnalysisJobStatus.PENDING,AnalysisJobStatus.RETRY_WAIT),clock.instant()).map(job->{job.claim(clock.instant());return job.getId();}));}
 private Context context(UUID id){
  var job=jobs.findById(id).orElseThrow();
  var step=steps.findById(job.getStepId()).orElseThrow();
  var execution=executions.findById(step.getExecutionId()).orElseThrow();
  var release=releases.findById(execution.getReleaseId()).orElseThrow();
  try{
   var env=environments.findById(release.getEnvironmentId()).orElseThrow();
   var service=services.findById(release.getServiceId()).orElseThrow();
   var connection=prometheus.findById(env.getPrometheusConnectionId()).orElseThrow();
   var snapshot=snapshots.findByReleaseId(release.getId()).orElseThrow();
   JsonNode policy=json.readTree(snapshot.getDefinition());
   if(policy==null||!policy.isObject()||!policy.path("metrics").isArray())
    return new Context(step,execution,release,null,300,"ANALYSIS_CONTEXT_UNAVAILABLE");
   String token=null;boolean secretUnavailable=false;
   if(connection.getSecretRef()!=null){
    try{token=secrets.resolve(connection.getSecretRef()).map(SecretResolver.SecretMaterial::bearerToken).filter(value->!value.isBlank()).orElse(null);}
    catch(RuntimeException ignored){/* Never downgrade a configured credential to anonymous access. */}
    secretUnavailable=token==null;
   }
   var request=new AnalysisWorkerGateway.Request("",connection.getBaseUrl(),token,connection.getId().toString(),snapshot.getChecksum(),job.getWindowStart(),job.getWindowEnd(),env.getNamespace(),service.getKey(),env.getName(),json.writeValueAsString(policy.path("metrics")));
   int delay=policy.path("inconclusivePolicy").path("additionalObservationSeconds").asInt(300);
   return new Context(step,execution,release,request,Math.max(1,delay),secretUnavailable?"SECRET_UNAVAILABLE":null);
  }catch(Exception ignored){
   // Keep request/JSON/credential details out of the persisted retry record.
   return new Context(step,execution,release,null,300,"ANALYSIS_CONTEXT_UNAVAILABLE");
  }
 }
 private void finish(UUID jobId,Context context,AnalysisWorkerGateway.Result result){transactions.executeWithoutResult(tx->{var job=jobs.findById(jobId).orElseThrow();if(result.verdict()==AnalysisVerdict.INCONCLUSIVE&&job.getAttempts()<job.getMaxAttempts()){job.retry(result.reasonCode(),clock.instant().plusSeconds(context.retrySeconds()));return;}job.complete(result.verdict(),result.reasonCode(),result.evidenceJson(),clock.instant());ArgoRolloutsGateway.Action action=result.verdict()==AnalysisVerdict.PASS?ArgoRolloutsGateway.Action.PROMOTE:result.verdict()==AnalysisVerdict.FAIL?ArgoRolloutsGateway.Action.ABORT:ArgoRolloutsGateway.Action.PAUSE;if(result.verdict()==AnalysisVerdict.FAIL)steps.findById(job.getStepId()).orElseThrow().fail(clock.instant());enqueue(context.execution(),context.release(),job,action,result.reasonCode());});}
 private void handleFailure(UUID jobId,Context context){transactions.executeWithoutResult(tx->{var job=jobs.findById(jobId).orElseThrow();if(job.getAttempts()<job.getMaxAttempts()){job.retry("PROMETHEUS_UNAVAILABLE",clock.instant().plusSeconds(context.retrySeconds()));return;}String evidence="[]";job.complete(AnalysisVerdict.INCONCLUSIVE,"PROMETHEUS_UNAVAILABLE",evidence,clock.instant());enqueue(context.execution(),context.release(),job,ArgoRolloutsGateway.Action.PAUSE,"PROMETHEUS_UNAVAILABLE");});}
 private void enqueue(RolloutExecution execution,Release release,AnalysisJob job,ArgoRolloutsGateway.Action action,String reason){try{String type=action.name()+"_ROLLOUT";String key="analysis:"+job.getId()+":"+action;String payload=json.writeValueAsString(new RolloutOperationService.Payload(release.getId(),null,"Automatic analysis: "+reason));if(commands.findByIdempotencyKey(key).isEmpty())commands.save(OutboxCommand.operation(execution,type,key,payload,clock.instant()));}catch(Exception e){throw new IllegalStateException("Cannot enqueue automatic rollout action",e);}}
 private record Context(RolloutStep step,RolloutExecution execution,Release release,AnalysisWorkerGateway.Request request,int retrySeconds,String preflightFailure){}
}
