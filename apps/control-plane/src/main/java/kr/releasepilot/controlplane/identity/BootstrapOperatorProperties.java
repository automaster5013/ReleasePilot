package kr.releasepilot.controlplane.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "releasepilot.bootstrap-operator")
public record BootstrapOperatorProperties(
        boolean enabled,
        String username,
        String password,
        String displayName
) {
}
