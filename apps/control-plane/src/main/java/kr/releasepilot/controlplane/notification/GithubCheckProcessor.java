package kr.releasepilot.controlplane.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GithubCheckProcessor {
    private final GithubCheckDeliveryRepository deliveries;private final GithubChecksGateway gateway;private final GithubChecksTokenProvider tokens;private final TransactionTemplate transactions;private final Clock clock;
    public GithubCheckProcessor(GithubCheckDeliveryRepository deliveries,GithubChecksGateway gateway,GithubChecksTokenProvider tokens,TransactionTemplate transactions,Clock clock){this.deliveries=deliveries;this.gateway=gateway;this.tokens=tokens;this.transactions=transactions;this.clock=clock;}
    public boolean processOne(){Optional<GithubCheckDelivery.Snapshot> claimed=transactions.execute(status->deliveries.findFirstByDeliveryStatusInAndAvailableAtLessThanEqualOrderByUpdatedAtAsc(List.of(GithubCheckDelivery.DeliveryStatus.PENDING,GithubCheckDelivery.DeliveryStatus.FAILED),clock.instant()).map(value->value.claim(clock.instant())));if(claimed.isEmpty())return false;var delivery=claimed.get();try{String token=tokens.token();Long id=delivery.checkRunId()==null?gateway.create(token,delivery):delivery.checkRunId();if(delivery.checkRunId()!=null)gateway.update(token,delivery);complete(delivery.id(),delivery.generation(),id);}catch(RuntimeException failure){retry(delivery,failure);}return true;}
    public void recoverExpired(Duration leaseTimeout){var cutoff=clock.instant().minus(leaseTimeout);transactions.executeWithoutResult(status->deliveries.findTop100ByDeliveryStatusAndClaimedAtLessThanEqualOrderByClaimedAtAsc(GithubCheckDelivery.DeliveryStatus.PROCESSING,cutoff).forEach(value->value.recover(clock.instant())));}
    private void complete(UUID id,int generation,Long externalId){transactions.executeWithoutResult(status->deliveries.findById(id).orElseThrow().complete(generation,externalId,clock.instant()));}
    private void retry(GithubCheckDelivery.Snapshot d,RuntimeException failure){long delay=Math.min(300,1L<<Math.min(d.attempt()-1,8));transactions.executeWithoutResult(status->deliveries.findById(d.id()).orElseThrow().retry(d.generation(),String.valueOf(failure.getMessage()),clock.instant().plusSeconds(delay),clock.instant()));}
}
