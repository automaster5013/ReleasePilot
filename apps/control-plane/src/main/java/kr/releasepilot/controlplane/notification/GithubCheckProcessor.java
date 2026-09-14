package kr.releasepilot.controlplane.notification;

import kr.releasepilot.controlplane.connection.SecretResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GithubCheckProcessor {
    private final GithubCheckDeliveryRepository deliveries;private final GithubChecksGateway gateway;private final SecretResolver secrets;private final TransactionTemplate transactions;private final Clock clock;private final String secretRef;
    public GithubCheckProcessor(GithubCheckDeliveryRepository deliveries,GithubChecksGateway gateway,SecretResolver secrets,TransactionTemplate transactions,Clock clock,@Value("${releasepilot.github-checks.secret-ref:env:GITHUB_CHECKS_TOKEN}")String secretRef){this.deliveries=deliveries;this.gateway=gateway;this.secrets=secrets;this.transactions=transactions;this.clock=clock;this.secretRef=secretRef;}
    public boolean processOne(){Optional<GithubCheckDelivery.Snapshot> claimed=transactions.execute(status->deliveries.findFirstByDeliveryStatusInAndAvailableAtLessThanEqualOrderByUpdatedAtAsc(List.of(GithubCheckDelivery.DeliveryStatus.PENDING,GithubCheckDelivery.DeliveryStatus.FAILED),clock.instant()).map(value->value.claim(clock.instant())));if(claimed.isEmpty())return false;var delivery=claimed.get();try{String token=secrets.resolve(secretRef).orElseThrow(()->new IllegalStateException("GitHub Checks secret is unavailable")).bearerToken();Long id=delivery.checkRunId()==null?gateway.create(token,delivery):delivery.checkRunId();if(delivery.checkRunId()!=null)gateway.update(token,delivery);complete(delivery.id(),delivery.generation(),id);}catch(RuntimeException failure){retry(delivery,failure);}return true;}
    public void recoverExpired(Duration leaseTimeout){var cutoff=clock.instant().minus(leaseTimeout);transactions.executeWithoutResult(status->deliveries.findTop100ByDeliveryStatusAndClaimedAtLessThanEqualOrderByClaimedAtAsc(GithubCheckDelivery.DeliveryStatus.PROCESSING,cutoff).forEach(value->value.recover(clock.instant())));}
    private void complete(UUID id,int generation,Long externalId){transactions.executeWithoutResult(status->deliveries.findById(id).orElseThrow().complete(generation,externalId,clock.instant()));}
    private void retry(GithubCheckDelivery.Snapshot d,RuntimeException failure){long delay=Math.min(300,1L<<Math.min(d.attempt()-1,8));transactions.executeWithoutResult(status->deliveries.findById(d.id()).orElseThrow().retry(d.generation(),String.valueOf(failure.getMessage()),clock.instant().plusSeconds(delay),clock.instant()));}
}
