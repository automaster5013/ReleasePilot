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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class ServiceApiTests {
    @Autowired private WebApplicationContext context;
    @Autowired private ProjectRepository projects;
    @Autowired private AuditEventRepository auditEvents;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
    }

    @Test
    void operatorCreatesAndListsService() throws Exception {
        Project project = projects.save(Project.create("commerce", "Commerce", null, Instant.now()));
        mockMvc.perform(post("/api/v1/projects/{projectId}/services", project.getId())
                        .with(authentication(operator())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"checkout","name":"Checkout","repositoryUrl":"https://github.com/acme/checkout","owner":"platform"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("checkout"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/services", project.getId())
                        .with(authentication(operator())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].owner").value("platform"));
        org.assertj.core.api.Assertions.assertThat(auditEvents.count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRepositoryAndDuplicateKey() throws Exception {
        Project project = projects.save(Project.create("core", "Core", null, Instant.now()));
        String valid = """
                {"key":"api","name":"API","repositoryUrl":"https://github.com/acme/api","owner":"core"}
                """;
        mockMvc.perform(post("/api/v1/projects/{projectId}/services", project.getId())
                        .with(authentication(operator())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/{projectId}/services", project.getId())
                        .with(authentication(operator())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERVICE_KEY_ALREADY_EXISTS"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/services", project.getId())
                        .with(authentication(operator())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key":"bad","name":"Bad","repositoryUrl":"http://example.com/repo","owner":"core"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerOutsideProjectReceivesNotFound() throws Exception {
        Project project = projects.save(Project.create("private-project", "Private", null, Instant.now()));
        mockMvc.perform(get("/api/v1/projects/{projectId}/services", project.getId())
                        .with(authentication(viewer())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    private UsernamePasswordAuthenticationToken operator() {
        var principal = new UserAccountPrincipal(UUID.randomUUID(), "operator", "unused", "Operator", true,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, principal.getPassword(), principal.getAuthorities());
    }

    private UsernamePasswordAuthenticationToken viewer() {
        var principal = new UserAccountPrincipal(UUID.randomUUID(), "viewer", "unused", "Viewer", true,
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, principal.getPassword(), principal.getAuthorities());
    }
}
