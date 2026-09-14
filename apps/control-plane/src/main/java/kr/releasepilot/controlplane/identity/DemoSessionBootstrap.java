package kr.releasepilot.controlplane.identity;

import kr.releasepilot.controlplane.catalog.Project;
import kr.releasepilot.controlplane.catalog.ProjectRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Configuration
public class DemoSessionBootstrap {
    @Bean ApplicationRunner bootstrapDemo(DemoCreator creator,
            @Value("${releasepilot.demo.enabled:false}") boolean enabled,
            @Value("${releasepilot.demo.username:releasepilot-demo}") String username) {
        return args -> creator.createIfRequired(enabled, username);
    }

    @Bean DemoCreator demoCreator(UserAccountRepository users, ProjectRepository projects,
            ProjectMembershipRepository memberships, PasswordEncoder encoder, Clock clock) {
        return new DemoCreator(users, projects, memberships, encoder, clock);
    }

    static class DemoCreator {
        private final UserAccountRepository users; private final ProjectRepository projects;
        private final ProjectMembershipRepository memberships; private final PasswordEncoder encoder; private final Clock clock;
        DemoCreator(UserAccountRepository users, ProjectRepository projects, ProjectMembershipRepository memberships,
                    PasswordEncoder encoder, Clock clock) { this.users=users;this.projects=projects;this.memberships=memberships;this.encoder=encoder;this.clock=clock; }
        @Transactional void createIfRequired(boolean enabled, String username) {
            if (!enabled) return;
            var user=users.findByUsername(username).orElseGet(()->users.save(UserAccount.create(username,
                    encoder.encode(UUID.randomUUID().toString()+UUID.randomUUID()),"ReleasePilot Demo",Set.of(Role.VIEWER),clock.instant())));
            var project=projects.findByKey("public-demo").orElseGet(()->projects.save(Project.create("public-demo","Public Canary Demo","Resettable, synthetic read-only demo data",clock.instant())));
            if(!memberships.existsByProjectIdAndUserId(project.getId(),user.getId()))memberships.save(ProjectMembership.create(project.getId(),user.getId(),Role.VIEWER,clock.instant()));
        }
    }
}
