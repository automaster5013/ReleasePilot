package kr.releasepilot.controlplane.connection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OutboundTargetPolicy {
    private final Set<String> allowedHosts;

    public OutboundTargetPolicy(@Value("${releasepilot.connection-validation.allowed-hosts:}") String configuredHosts) {
        this.allowedHosts = Arrays.stream(configuredHosts.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(OutboundTargetPolicy::normalizeHost).collect(Collectors.toUnmodifiableSet());
    }

    public void requireAllowed(String value, Set<String> allowedSchemes) {
        URI uri;
        try { uri = URI.create(value); }
        catch (IllegalArgumentException exception) { throw new TargetNotAllowedException(); }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (!allowedSchemes.contains(scheme) || host == null || uri.getUserInfo() != null
                || !allowedHosts.contains(normalizeHost(host))) throw new TargetNotAllowedException();
    }

    private static String normalizeHost(String host) {
        return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
    }

    public static final class TargetNotAllowedException extends IllegalArgumentException {
        public TargetNotAllowedException() { super("Outbound target is not allowlisted"); }
    }
}
