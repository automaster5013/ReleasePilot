package kr.releasepilot.controlplane.connection;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest @Transactional
class ConnectionApiTests {
    @Autowired WebApplicationContext context; private MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PrometheusConnectionRepository prometheus;
    @BeforeEach void setUp(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();}
    @Test void operatorRegistersSecretReferencesWithoutCredentials() throws Exception {
        mvc.perform(post("/api/v1/connections/clusters").with(authentication(auth("ROLE_OPERATOR"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"demo-cluster","apiServer":"https://kubernetes.example","allowedNamespaces":["releasepilot-demo"],"secretRef":"vault:kubernetes/demo"}
                """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("UNVERIFIED"))
            .andExpect(jsonPath("$.allowedNamespaces[0]").value("releasepilot-demo"));
        var createdPrometheus=mvc.perform(post("/api/v1/connections/prometheus").with(authentication(auth("ROLE_OPERATOR"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"demo-prometheus","baseUrl":"http://prometheus:9090","secretRef":"env:DEFINITELY_MISSING_PROMETHEUS_TOKEN","queryTimeoutSeconds":15}
                """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.queryTimeoutSeconds").value(15)).andReturn();
        String prometheusId=json.readTree(createdPrometheus.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/connections/prometheus/"+prometheusId+"/validate").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID")).andExpect(jsonPath("$.failureCode").value("SECRET_UNAVAILABLE"));
        org.assertj.core.api.Assertions.assertThat(prometheus.findById(UUID.fromString(prometheusId)).orElseThrow().getLastValidatedAt()).isNotNull();
        mvc.perform(post("/api/v1/connections/prometheus/"+prometheusId+"/validate").with(authentication(auth("ROLE_VIEWER"))).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void viewerCannotRegisterConnection() throws Exception {
        mvc.perform(post("/api/v1/connections/clusters").with(authentication(auth("ROLE_VIEWER"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"blocked","apiServer":"https://kubernetes.example","allowedNamespaces":["default"],"secretRef":"vault:kubernetes/blocked"}
                """))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/connections/prometheus").with(authentication(auth("ROLE_VIEWER"))))
            .andExpect(status().isForbidden());
    }
    @Test void operatorCanValidateOneOrAllClustersAndMissingSecretFailsClosed()throws Exception{
        var created=mvc.perform(post("/api/v1/connections/clusters").with(authentication(auth("ROLE_OPERATOR"))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"validation-cluster","apiServer":"https://kubernetes.example","allowedNamespaces":["east","west"],"secretRef":"env:DEFINITELY_MISSING_TEST_TOKEN"}
                """)).andExpect(status().isCreated()).andReturn();
        String id=json.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/connections/clusters/"+id+"/validate").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID"))
                .andExpect(jsonPath("$.failureCode").value("SECRET_UNAVAILABLE"));
        mvc.perform(post("/api/v1/connections/clusters/validate").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].clusterId").value(id));
        mvc.perform(post("/api/v1/connections/clusters/"+id+"/validate").with(authentication(auth("ROLE_VIEWER"))).with(csrf()))
                .andExpect(status().isForbidden());
    }
    private UsernamePasswordAuthenticationToken auth(String role){
        var p=new UserAccountPrincipal(UUID.randomUUID(),"test","unused","Test",true,List.of(new SimpleGrantedAuthority(role)));
        return UsernamePasswordAuthenticationToken.authenticated(p,p.getPassword(),p.getAuthorities());
    }
}
