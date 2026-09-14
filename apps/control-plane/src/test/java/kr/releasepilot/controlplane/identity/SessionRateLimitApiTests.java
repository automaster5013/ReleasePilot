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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "releasepilot.demo.enabled=true",
        "releasepilot.security.rate-limit.demo.ip.max-attempts=2",
        "releasepilot.security.rate-limit.demo.ip.window=PT1H"
})
class SessionRateLimitApiTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void returnsProblemAndRetryAfterWhenDemoLimitIsExceeded() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/session/demo").with(csrf()).with(request -> {
                        request.setRemoteAddr("198.51.100.91"); return request;
                    })).andExpect(status().isOk());
        }

        mvc.perform(post("/api/v1/session/demo").with(csrf()).with(request -> {
                    request.setRemoteAddr("198.51.100.91"); return request;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("DEMO_SESSION_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }
}
