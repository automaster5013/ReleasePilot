package kr.releasepilot.controlplane.analysis;
import java.time.Instant;import java.util.UUID;
public interface AnalysisWorkerGateway{
 Result evaluate(Request request);
 record Request(String workerUrl,String prometheusUrl,String bearerToken,String sourceId,String checksum,Instant windowStart,Instant windowEnd,String serviceNamespace,String serviceName,String environment,String metricsJson){}
 record Result(AnalysisVerdict verdict,String reasonCode,String evidenceJson){}
}
