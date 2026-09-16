package kr.releasepilot.controlplane.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(properties = {
        "releasepilot.oidc.enabled=true",
        "spring.security.oauth2.client.registration.releasepilot.client-id=test-client",
        "spring.security.oauth2.client.registration.releasepilot.client-secret=test-secret",
        "spring.security.oauth2.client.registration.releasepilot.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.releasepilot.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
        "spring.security.oauth2.client.registration.releasepilot.scope=openid,profile,email",
        "spring.security.oauth2.client.registration.releasepilot.provider=releasepilot",
        "spring.security.oauth2.client.provider.releasepilot.authorization-uri=https://idp.example/authorize",
        "spring.security.oauth2.client.provider.releasepilot.token-uri=https://idp.example/token",
        "spring.security.oauth2.client.provider.releasepilot.jwk-set-uri=https://idp.example/keys",
        "spring.security.oauth2.client.provider.releasepilot.user-info-uri=https://idp.example/userinfo",
        "spring.security.oauth2.client.provider.releasepilot.user-name-attribute=sub"
})
class OidcLoginApiTests {
    @Autowired WebApplicationContext context;
    @Autowired UserAccountRepository users;
    @Autowired ExternalIdentityRepository identities;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void advertisesAndStartsTheConfiguredOidcFlow() throws Exception {
        mvc.perform(get("/api/v1/session/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oidc").value(true))
                .andExpect(jsonPath("$.loginUrl").value("/oauth2/authorization/releasepilot"));

        mvc.perform(get("/oauth2/authorization/releasepilot"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://idp.example/authorize?")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("prompt=login")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("state=")));
    }
    @Test
    @org.springframework.transaction.annotation.Transactional
    void sessionShowsLinkedEmailAndRoleWithoutExposingOtherAccounts() throws Exception {
        var now = java.time.Instant.now();
        var account = users.save(UserAccount.create("identity-" + java.util.UUID.randomUUID(),
                "{noop}unused", "Developer", java.util.Set.of(Role.DEVELOPER), now));
        identities.save(ExternalIdentity.create(account, "https://idp.example", "developer-sub",
                "developer@example.test", now));
        var principal = UserAccountPrincipal.from(account);
        var authentication = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(principal, null, principal.getAuthorities());
        mvc.perform(get("/api/v1/session").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("developer@example.test"))
                .andExpect(jsonPath("$.user.username").value(account.getUsername()))
                .andExpect(jsonPath("$.user.roles[0]").value("DEVELOPER"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

}
