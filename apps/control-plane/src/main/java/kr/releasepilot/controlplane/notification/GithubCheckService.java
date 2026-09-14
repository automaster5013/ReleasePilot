package kr.releasepilot.controlplane.notification;

import kr.releasepilot.controlplane.catalog.CatalogService;
import kr.releasepilot.controlplane.release.Release;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class GithubCheckService {
    private final GithubCheckDeliveryRepository deliveries;private final Clock clock;private final boolean enabled;
    public GithubCheckService(GithubCheckDeliveryRepository deliveries,Clock clock,@Value("${releasepilot.github-checks.enabled:false}")boolean enabled){this.deliveries=deliveries;this.clock=clock;this.enabled=enabled;}
    @Transactional public void requested(Release release,CatalogService service){if(!enabled||deliveries.findByReleaseId(release.getId()).isPresent())return;repository(service.getRepositoryUrl()).ifPresent(repo->deliveries.save(GithubCheckDelivery.queued(release.getId(),repo.owner(),repo.name(),release.getCommitSha(),"Approval required","Release "+release.getVersion()+" is waiting for an independent approval.",clock.instant())));}
    @Transactional public void approved(UUID releaseId,String version){update(releaseId,GithubCheckDelivery.CheckStatus.IN_PROGRESS,null,"Progressive delivery running","Release "+version+" was approved and is being rolled out.");}
    @Transactional public void completed(UUID releaseId,String version,boolean success,String reason){String conclusion=success?"success":"rejected".equals(reason)?"action_required":"aborted".equals(reason)?"cancelled":"failure";update(releaseId,GithubCheckDelivery.CheckStatus.COMPLETED,conclusion,success?"Release succeeded":"Release did not complete","Release "+version+" "+(success?"completed successfully.":"ended: "+reason+"."));}
    private void update(UUID releaseId,GithubCheckDelivery.CheckStatus status,String conclusion,String title,String summary){deliveries.findByReleaseId(releaseId).ifPresent(value->value.request(status,conclusion,title,summary,clock.instant()));}
    private Optional<Repository> repository(String value){try{URI uri=URI.create(value);if(!"https".equalsIgnoreCase(uri.getScheme())||!"github.com".equalsIgnoreCase(uri.getHost()))return Optional.empty();String[] parts=uri.getPath().replaceFirst("^/","").split("/");if(parts.length!=2)return Optional.empty();String name=parts[1].replaceFirst("\\.git$","");if(!parts[0].matches("[A-Za-z0-9_.-]+")||!name.matches("[A-Za-z0-9_.-]+"))return Optional.empty();return Optional.of(new Repository(parts[0].toLowerCase(Locale.ROOT),name));}catch(Exception ignored){return Optional.empty();}}
    private record Repository(String owner,String name){}
}
