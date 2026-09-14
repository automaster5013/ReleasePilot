package kr.releasepilot.controlplane.rollout;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Duration;

@Service
public class OutboxRecoveryService {
    private final OutboxCommandRepository commands;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OutboxRecoveryService(OutboxCommandRepository commands, TransactionTemplate transactions, Clock clock) {
        this.commands = commands;
        this.transactions = transactions;
        this.clock = clock;
    }

    public int recoverExpired(Duration leaseTimeout, int limit) {
        if (leaseTimeout.isNegative() || leaseTimeout.isZero()) throw new IllegalArgumentException("leaseTimeout must be positive");
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        int recovered = 0;
        while (recovered < limit && recoverOne(leaseTimeout)) recovered++;
        return recovered;
    }

    private boolean recoverOne(Duration leaseTimeout) {
        Boolean recovered = transactions.execute(status -> {
            var now = clock.instant();
            var stale = commands.findFirstByStatusAndClaimedAtLessThanEqualOrderByClaimedAtAsc(
                    OutboxCommandStatus.PROCESSING, now.minus(leaseTimeout));
            stale.ifPresent(command -> command.recoverStaleClaim(now));
            return stale.isPresent();
        });
        return Boolean.TRUE.equals(recovered);
    }
}
