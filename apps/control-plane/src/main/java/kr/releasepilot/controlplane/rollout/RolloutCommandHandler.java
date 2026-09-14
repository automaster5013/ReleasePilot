package kr.releasepilot.controlplane.rollout;

import java.util.UUID;

public interface RolloutCommandHandler {
    void handle(Command command);

    record Command(UUID id, UUID executionId, UUID correlationId, String type, String payloadJson, int attempt) {}
}
