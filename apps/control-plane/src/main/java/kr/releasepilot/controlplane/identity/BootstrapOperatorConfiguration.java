package kr.releasepilot.controlplane.identity;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(BootstrapOperatorProperties.class)
public class BootstrapOperatorConfiguration {

    @Bean
    ApplicationRunner bootstrapOperator(
            BootstrapOperatorProperties properties,
            BootstrapOperatorCreator creator
    ) {
        return arguments -> creator.createIfRequired(properties);
    }

    @Bean
    BootstrapOperatorCreator bootstrapOperatorCreator(
            UserAccountRepository repository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        return new BootstrapOperatorCreator(repository, passwordEncoder, clock);
    }

    static class BootstrapOperatorCreator {
        private final UserAccountRepository repository;
        private final PasswordEncoder passwordEncoder;
        private final Clock clock;

        BootstrapOperatorCreator(
                UserAccountRepository repository,
                PasswordEncoder passwordEncoder,
                Clock clock
        ) {
            this.repository = repository;
            this.passwordEncoder = passwordEncoder;
            this.clock = clock;
        }

        @Transactional
        public void createIfRequired(BootstrapOperatorProperties properties) {
            if (!properties.enabled() || repository.findByUsername(properties.username()).isPresent()) {
                return;
            }
            if (properties.password() == null || properties.password().length() < 12) {
                throw new IllegalStateException("Bootstrap operator password must be at least 12 characters");
            }
            repository.save(UserAccount.create(
                    properties.username(),
                    passwordEncoder.encode(properties.password()),
                    properties.displayName(),
                    Set.of(Role.OPERATOR),
                    clock.instant()
            ));
        }
    }
}
