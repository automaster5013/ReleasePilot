package kr.releasepilot.controlplane.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix="releasepilot.github-checks",name="enabled",havingValue="true")
public class GithubCheckWorker {
    private final GithubCheckProcessor processor;private final int batchSize;private final Duration leaseTimeout;
    public GithubCheckWorker(GithubCheckProcessor processor,@Value("${releasepilot.github-checks.batch-size:20}")int batchSize,@Value("${releasepilot.github-checks.lease-timeout:PT2M}")Duration leaseTimeout){if(batchSize<1||batchSize>100)throw new IllegalArgumentException("batch size must be between 1 and 100");if(leaseTimeout.isZero()||leaseTimeout.isNegative())throw new IllegalArgumentException("lease timeout must be positive");this.processor=processor;this.batchSize=batchSize;this.leaseTimeout=leaseTimeout;}
    @Scheduled(fixedDelayString="${releasepilot.github-checks.poll-interval:PT5S}") public void tick(){processor.recoverExpired(leaseTimeout);for(int count=0;count<batchSize&&processor.processOne();count++){}}
}
