package kr.releasepilot.controlplane.environment;
public interface PrometheusValidationGateway {
 Snapshot inspect(Environment environment);
 record Snapshot(boolean ready,boolean queryReachable,boolean recentSeries,boolean releaseTrackLabel){}
}
