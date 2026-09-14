package kr.releasepilot.controlplane.rollout;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

@Service
public class OutboxCommandProcessor {
    private final OutboxCommandRepository commands;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OutboxCommandProcessor(OutboxCommandRepository commands, TransactionTemplate transactions, Clock clock) {
        this.commands = commands;
        this.transactions = transactions;
        this.clock = clock;
    }

    public boolean processOne(RolloutCommandHandler handler) {
        var claimed = claim();
        if (claimed.isEmpty()) return false;
        var command = claimed.get();
        try (var ignored = MDC.putCloseable("correlationId", command.correlationId() == null ? "none" : command.correlationId().toString())) {
            handler.handle(command);
            complete(command.id());
        } catch (RuntimeException failure) {
            retry(command.id(), command.attempt(), failure);
        }
        return true;
    }

    private Optional<RolloutCommandHandler.Command> claim() {
        return transactions.execute(status -> commands
                .findFirstByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(OutboxCommandStatus.PENDING, OutboxCommandStatus.FAILED), clock.instant())
                .map(command -> {
                    command.claim(clock.instant());
                    return new RolloutCommandHandler.Command(command.getId(), command.getAggregateId(), command.getCorrelationId(),
                            command.getCommandType(), command.getPayloadJson(), command.getAttempts());
                }));
    }

    private void complete(UUID commandId) {
        transactions.executeWithoutResult(status -> commands.findById(commandId).orElseThrow().processed(clock.instant()));
    }

    private void retry(UUID commandId, int attempt, RuntimeException failure) {
        long delaySeconds = Math.min(300, 1L << Math.min(attempt - 1, 8));
        transactions.executeWithoutResult(status -> commands.findById(commandId).orElseThrow()
                .retry(failure.getMessage(), clock.instant().plus(Duration.ofSeconds(delaySeconds))));
    }
}
