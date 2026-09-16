package kr.releasepilot.controlplane.rollout;

public interface ArgoRolloutsGateway {
    ObservedRollout start(StartRequest request);
    ObservedRollout control(ControlRequest request);
    Observation observe(ObserveRequest request);

    record StartRequest(String apiServer, String bearerToken, String namespace, String rolloutName,
                        String containerName, String image, java.util.List<Integer> policyWeights) {
        public StartRequest(String apiServer, String bearerToken, String namespace, String rolloutName, String containerName, String image) {
            this(apiServer, bearerToken, namespace, rolloutName, containerName, image, java.util.List.of());
        }
    }
    record ObservedRollout(String uid, String resourceVersion) {}
    record ControlRequest(String apiServer, String bearerToken, String namespace, String rolloutName,
                          String expectedUid, Action action) {}
    enum Action { PROMOTE, PAUSE, RESUME, ABORT }
    record ObserveRequest(String apiServer,String bearerToken,String namespace,String rolloutName,String containerName){}
    record Observation(String uid,String resourceVersion,String phase,int currentStepIndex,String image,
                       boolean aborted,long generation,long observedGeneration) {
        boolean controllerObservedDesiredGeneration() {
            return generation > 0 && observedGeneration >= generation;
        }
    }
}
