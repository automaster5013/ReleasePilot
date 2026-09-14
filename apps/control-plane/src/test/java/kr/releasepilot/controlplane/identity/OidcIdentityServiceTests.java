package kr.releasepilot.controlplane.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcIdentityServiceTests {

    private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsUnknownIdentityWhenAutoProvisioningIsDisabled() {
        when(identities.findByIssuerAndSubject("https://idp.example", "subject-1")).thenReturn(Optional.empty());
        var service = new OidcIdentityService(identities, users, clock, false, "example.com");

        assertThatThrownBy(() -> service.resolve("https://idp.example", "subject-1", "dev@example.com", true, "Dev"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        failure -> assertThat(failure.getError().getErrorCode()).isEqualTo("identity_not_linked"));
    }

    @Test
    void provisionsOnlyAnInternalViewerForAnAllowedDomain() {
        when(identities.findByIssuerAndSubject("https://idp.example", "subject-2")).thenReturn(Optional.empty());
        when(users.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(identities.save(any(ExternalIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new OidcIdentityService(identities, users, clock, true, "example.com, example.com");

        var principal = service.resolve("https://idp.example", "subject-2", "dev@EXAMPLE.com", true, "Developer");

        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_VIEWER");
        assertThat(principal.getUsername()).startsWith("oidc-");
        verify(identities).save(any(ExternalIdentity.class));
    }

    @Test
    void rejectsAutoProvisioningForAnUnverifiedEmail() {
        when(identities.findByIssuerAndSubject("https://idp.example", "subject-unverified"))
                .thenReturn(Optional.empty());
        var service = new OidcIdentityService(identities, users, clock, true, "example.com");

        assertThatThrownBy(() -> service.resolve(
                "https://idp.example", "subject-unverified", "dev@example.com", false, "Dev"))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        failure -> assertThat(failure.getError().getErrorCode()).isEqualTo("email_not_allowed"));
    }

    @Test
    void immutableIssuerAndSubjectMappingWinsOverChangedClaims() {
        var account = UserAccount.create("linked", "hash", "Linked User", Set.of(Role.APPROVER), clock.instant());
        var identity = ExternalIdentity.create(account, "https://idp.example", "subject-3", "old@example.com", clock.instant());
        when(identities.findByIssuerAndSubject("https://idp.example", "subject-3")).thenReturn(Optional.of(identity));
        var service = new OidcIdentityService(identities, users, clock, false, "");

        var principal = service.resolve("https://idp.example", "subject-3", "new@other.example", false, "Changed Name");

        assertThat(principal.getUsername()).isEqualTo("linked");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_APPROVER");
    }
}
