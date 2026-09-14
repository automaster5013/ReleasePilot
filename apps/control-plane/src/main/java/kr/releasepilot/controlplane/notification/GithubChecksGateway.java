package kr.releasepilot.controlplane.notification;

public interface GithubChecksGateway {
    long create(String token, GithubCheckDelivery.Snapshot delivery);
    void update(String token, GithubCheckDelivery.Snapshot delivery);
}
