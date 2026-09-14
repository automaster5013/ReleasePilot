package kr.releasepilot.controlplane.notification;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class GithubCheckDeliveryTests {
    @Test void preservesANewerDesiredStateWhenAnOlderDeliveryCompletes(){
        var now=Instant.parse("2026-09-14T12:00:00Z");
        var delivery=GithubCheckDelivery.queued(UUID.randomUUID(),"owner","repo","a".repeat(40),"Queued","Waiting",now);
        var claimed=delivery.claim(now);
        delivery.request(GithubCheckDelivery.CheckStatus.IN_PROGRESS,null,"Running","Rolling out",now.plusSeconds(1));
        delivery.complete(claimed.generation(),123L,now.plusSeconds(2));

        var next=delivery.claim(now.plusSeconds(3));
        assertThat(next.checkRunId()).isEqualTo(123L);
        assertThat(next.status()).isEqualTo(GithubCheckDelivery.CheckStatus.IN_PROGRESS);
        assertThat(next.generation()).isGreaterThan(claimed.generation());
    }
}
