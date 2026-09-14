package kr.releasepilot.controlplane.catalog;

import kr.releasepilot.controlplane.audit.AuditEventRepository;
import kr.releasepilot.controlplane.identity.UserAccountPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ProjectApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void operatorCreatesProjectAndAuditEvent() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(authentication(operator()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "checkout",
                                  "name": "Checkout",
                                  "description": "Demo checkout service"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("checkout"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        org.assertj.core.api.Assertions.assertThat(auditEventRepository.count()).isEqualTo(1);
    }

    @Test
    void viewerCannotCreateProject() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .with(authentication(viewer()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"forbidden","name":"Forbidden"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateProjectKeyReturnsConflict() throws Exception {
        String body = """
                {"key":"duplicate","name":"Duplicate"}
                """;
        mockMvc.perform(post("/api/v1/projects")
                        .with(authentication(operator()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects")
                        .with(authentication(operator()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_KEY_ALREADY_EXISTS"));
    }

    private UsernamePasswordAuthenticationToken operator() {
        return principalAuthentication("ROLE_OPERATOR");
    }

    private UsernamePasswordAuthenticationToken viewer() {
        return principalAuthentication("ROLE_VIEWER");
    }

    private UsernamePasswordAuthenticationToken principalAuthentication(String role) {
        var principal = new UserAccountPrincipal(
                UUID.randomUUID(),
                "test-user",
                "not-used",
                "Test User",
                true,
                List.of(new SimpleGrantedAuthority(role))
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );
    }
}
