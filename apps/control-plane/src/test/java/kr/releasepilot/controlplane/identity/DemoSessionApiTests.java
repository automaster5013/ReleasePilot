package kr.releasepilot.controlplane.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties="releasepilot.demo.enabled=true")
class DemoSessionApiTests {
    @Autowired WebApplicationContext context; MockMvc mvc;
    @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();}
    @Test void missingCsrfCannotCreateDemoSession() throws Exception {
        var result = mvc.perform(post("/api/v1/session/demo"))
                .andExpect(status().isForbidden()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(session == null ? get("/api/v1/session") : get("/api/v1/session").session(session))
                .andExpect(status().isForbidden());
    }
    @Test void realCsrfTokenIsBoundToItsSessionAndAcceptsOnlyMatchingHeader() throws Exception {
        var issued = mvc.perform(get("/api/v1/session/csrf"))
                .andExpect(status().isOk()).andReturn();
        var body = new tools.jackson.databind.ObjectMapper().readTree(issued.getResponse().getContentAsString());
        String header = body.get("headerName").asText();
        String token = body.get("token").asText();
        var session = (MockHttpSession) issued.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        mvc.perform(post("/api/v1/session/demo").session(session).header(header, "invalid-csrf-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/session/demo").session(new MockHttpSession()).header(header, token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/session").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/session/demo").session(session).header(header, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.demo").value(true));
        mvc.perform(get("/api/v1/session").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.roles[0]").value("VIEWER"));
    }
    @Test void createsViewerSessionAndRejectsMutation() throws Exception {
        var result=mvc.perform(post("/api/v1/session/demo").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.demo").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("VIEWER")).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);
        mvc.perform(get("/api/v1/session/active").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/session/revoke-others").session(session).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/releases/"+UUID.randomUUID()+"/abort").session(session).with(csrf())
                        .header("Idempotency-Key",UUID.randomUUID()+"demo")
                        .contentType("application/json").content("{\"reason\":\"must be rejected\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void reportsOidcAsDisabledWithoutAClientRegistration() throws Exception {
        mvc.perform(get("/api/v1/session/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oidc").value(false))
                .andExpect(jsonPath("$.loginUrl").doesNotExist());
    }
}
