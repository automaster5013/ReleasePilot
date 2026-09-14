package kr.releasepilot.controlplane.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class OidcIdentityService {

    private final ExternalIdentityRepository identities;
    private final UserAccountRepository users;
    private final Clock clock;
    private final boolean autoProvision;
    private final Set<String> allowedEmailDomains;

    public OidcIdentityService(
            ExternalIdentityRepository identities,
            UserAccountRepository users,
            Clock clock,
            @Value("${releasepilot.oidc.auto-provision:false}") boolean autoProvision,
            @Value("${releasepilot.oidc.allowed-email-domains:}") String allowedEmailDomains
    ) {
        this.identities = identities;
        this.users = users;
        this.clock = clock;
        this.autoProvision = autoProvision;
        this.allowedEmailDomains = Arrays.stream(allowedEmailDomains.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public UserAccountPrincipal resolve(String issuer, String subject, String email, boolean emailVerified, String displayName) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw failure("invalid_identity", "OIDC issuer and subject are required");
        }
        var now = clock.instant();
        var existing = identities.findByIssuerAndSubject(issuer, subject);
        if (existing.isPresent()) {
            var identity = existing.get();
            if (!identity.getUser().isEnabled()) throw new DisabledException("User account is disabled");
            identity.recordLogin(email, now);
            return UserAccountPrincipal.from(identity.getUser());
        }
        if (!autoProvision) throw failure("identity_not_linked", "OIDC identity is not linked");
        if (!emailVerified || !emailAllowed(email)) {
            throw failure("email_not_allowed", "A verified OIDC email in an allowed domain is required");
        }

        String username = "oidc-" + sha256(issuer + "\n" + subject).substring(0, 48);
        String safeName = displayName == null || displayName.isBlank() ? email : displayName.trim();
        if (safeName.length() > 100) safeName = safeName.substring(0, 100);
        var user = users.save(UserAccount.create(username, "{noop}" + UUID.randomUUID(), safeName,
                Set.of(Role.VIEWER), now));
        identities.save(ExternalIdentity.create(user, issuer, subject, email, now));
        return UserAccountPrincipal.from(user);
    }

    private boolean emailAllowed(String email) {
        if (email == null || email.isBlank() || allowedEmailDomains.isEmpty()) return false;
        int separator = email.lastIndexOf('@');
        return separator > 0 && allowedEmailDomains.contains(email.substring(separator + 1).toLowerCase(Locale.ROOT));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static OAuth2AuthenticationException failure(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
