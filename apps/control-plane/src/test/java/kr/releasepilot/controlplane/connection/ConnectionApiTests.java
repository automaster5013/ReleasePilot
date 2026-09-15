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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        mvc.perform(get("/api/v1/connections/clusters").with(authentication(auth("ROLE_VIEWER"))))
            .andExpect(status().isForbidden());
    }
    @Test void operatorUpdatesConnectionsAndMustRevalidateWhileViewerIsForbidden() throws Exception {
        var cluster=mvc.perform(post("/api/v1/connections/clusters").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"old-cluster","apiServer":"https://old.example","allowedNamespaces":["old"],"secretRef":"vault:old"}
            """)).andExpect(status().isCreated()).andReturn();
        String clusterId=json.readTree(cluster.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/connections/clusters/"+clusterId+"/validate").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALID"));
        mvc.perform(put("/api/v1/connections/clusters/"+clusterId).with(authentication(auth("ROLE_OPERATOR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"new-cluster","apiServer":"https://new.example","allowedNamespaces":["east","west"],"secretRef":"vault:new"}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNVERIFIED")).andExpect(jsonPath("$.lastValidatedAt").doesNotExist()).andExpect(jsonPath("$.allowedNamespaces[1]").value("west"));
        mvc.perform(get("/api/v1/audit-events").param("aggregateType","CLUSTER_CONNECTION").param("aggregateId",clusterId).with(authentication(auth("ROLE_OPERATOR"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[2].eventType").value("CLUSTER_CONNECTION_UPDATED"));

        var metrics=mvc.perform(post("/api/v1/connections/prometheus").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"old-metrics","baseUrl":"http://old:9090","secretRef":null,"queryTimeoutSeconds":15}
            """)).andExpect(status().isCreated()).andReturn();
        String metricsId=json.readTree(metrics.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(put("/api/v1/connections/prometheus/"+metricsId).with(authentication(auth("ROLE_OPERATOR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"new-metrics","baseUrl":"https://metrics.example","secretRef":"env:METRICS_TOKEN","queryTimeoutSeconds":30}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNVERIFIED")).andExpect(jsonPath("$.queryTimeoutSeconds").value(30));
        mvc.perform(put("/api/v1/connections/prometheus/"+metricsId).with(authentication(auth("ROLE_VIEWER"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"blocked","baseUrl":"https://blocked.example","secretRef":null,"queryTimeoutSeconds":10}
            """)).andExpect(status().isForbidden());
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
    @Test void operatorDisablesAndReenablesConnectionsFailClosedAndViewerIsForbidden() throws Exception {
        var cluster=mvc.perform(post("/api/v1/connections/clusters").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"lifecycle-cluster","apiServer":"https://kubernetes.example","allowedNamespaces":["default"],"secretRef":"env:MISSING_TOKEN"}
            """)).andExpect(status().isCreated()).andReturn();
        String clusterId=json.readTree(cluster.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/connections/clusters/"+clusterId+"/disable").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED")).andExpect(jsonPath("$.lastValidatedAt").doesNotExist());
        mvc.perform(post("/api/v1/connections/clusters/"+clusterId+"/disable").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(post("/api/v1/connections/clusters/"+clusterId+"/validate").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLUSTER_CONNECTION_DISABLED"));
        mvc.perform(post("/api/v1/connections/clusters/"+clusterId+"/enable").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNVERIFIED"));
        mvc.perform(get("/api/v1/audit-events").param("aggregateType","CLUSTER_CONNECTION").param("aggregateId",clusterId).with(authentication(auth("ROLE_OPERATOR"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[1].eventType").value("CLUSTER_CONNECTION_DISABLED"))
            .andExpect(jsonPath("$.items[2].eventType").value("CLUSTER_CONNECTION_ENABLED")).andExpect(jsonPath("$.items.length()").value(3));

        var metrics=mvc.perform(post("/api/v1/connections/prometheus").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
            {"name":"lifecycle-metrics","baseUrl":"http://prometheus:9090","secretRef":null,"queryTimeoutSeconds":15}
            """)).andExpect(status().isCreated()).andReturn();
        String metricsId=json.readTree(metrics.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/connections/prometheus/"+metricsId+"/disable").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(post("/api/v1/connections/prometheus/"+metricsId+"/validate").with(authentication(auth("ROLE_OPERATOR"))).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROMETHEUS_CONNECTION_DISABLED"));
        mvc.perform(post("/api/v1/connections/prometheus/"+metricsId+"/enable").with(authentication(auth("ROLE_VIEWER"))).with(csrf()))
            .andExpect(status().isForbidden());
    }
    private UsernamePasswordAuthenticationToken auth(String role){
        var p=new UserAccountPrincipal(UUID.randomUUID(),"test","unused","Test",true,List.of(new SimpleGrantedAuthority(role)));
        return UsernamePasswordAuthenticationToken.authenticated(p,p.getPassword(),p.getAuthorities());
    }
}
