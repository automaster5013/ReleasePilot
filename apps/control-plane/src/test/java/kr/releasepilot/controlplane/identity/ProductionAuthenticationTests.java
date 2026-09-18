package kr.releasepilot.controlplane.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
@TestPropertySource(properties = {
        "releasepilot.security.local-login-enabled=false",
        "releasepilot.demo.enabled=false"
})
class ProductionAuthenticationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void localPasswordLoginIsNotExposed() throws Exception {
        mvc.perform(post("/api/v1/session/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"username\":\"operator\",\"password\":\"not-used\"}"))
                .andExpect(status().isNotFound());
    }
}
