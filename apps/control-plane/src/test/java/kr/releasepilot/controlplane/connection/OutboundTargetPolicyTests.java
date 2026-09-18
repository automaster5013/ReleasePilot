package kr.releasepilot.controlplane.connection;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundTargetPolicyTests {
    private final OutboundTargetPolicy policy=new OutboundTargetPolicy("kubernetes.example,prometheus.internal");

    @Test void allowsOnlyExactConfiguredHostsAndSchemes(){
        assertThatCode(()->policy.requireAllowed("https://kubernetes.example",Set.of("https"))).doesNotThrowAnyException();
        assertThatThrownBy(()->policy.requireAllowed("https://kubernetes.example.attacker.test",Set.of("https")))
                .isInstanceOf(OutboundTargetPolicy.TargetNotAllowedException.class);
        assertThatThrownBy(()->policy.requireAllowed("https://127.0.0.1",Set.of("https")))
                .isInstanceOf(OutboundTargetPolicy.TargetNotAllowedException.class);
        assertThatThrownBy(()->policy.requireAllowed("https://user@kubernetes.example",Set.of("https")))
                .isInstanceOf(OutboundTargetPolicy.TargetNotAllowedException.class);
        assertThatThrownBy(()->policy.requireAllowed("http://kubernetes.example",Set.of("https")))
                .isInstanceOf(OutboundTargetPolicy.TargetNotAllowedException.class);
    }
}
