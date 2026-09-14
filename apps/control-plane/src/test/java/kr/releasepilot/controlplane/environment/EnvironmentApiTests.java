package kr.releasepilot.controlplane.environment;
import kr.releasepilot.controlplane.audit.AuditEventRepository; import kr.releasepilot.controlplane.catalog.*; import kr.releasepilot.controlplane.connection.*; import kr.releasepilot.controlplane.identity.UserAccountPrincipal;
import org.junit.jupiter.api.*; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken; import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers; import org.springframework.test.web.servlet.*; import org.springframework.test.web.servlet.setup.MockMvcBuilders; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.context.WebApplicationContext;
import java.time.Instant; import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post; import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @Transactional
class EnvironmentApiTests {
 @Autowired WebApplicationContext context; @Autowired ProjectRepository projects; @Autowired CatalogServiceRepository services;
 @Autowired ClusterConnectionRepository clusters; @Autowired PrometheusConnectionRepository prometheus; @Autowired EnvironmentValidationResultRepository results; @Autowired AuditEventRepository audits; private MockMvc mvc;
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity()).build();}
 @Test void createsDraftAndPersistsItemizedFailedValidation() throws Exception {
  var now=Instant.now();var project=projects.save(Project.create("env-test","Environment Test",null,now));
  var app=services.save(CatalogService.create(project.getId(),"checkout","Checkout","https://github.com/acme/checkout","platform",now));
  var cluster=clusters.save(ClusterConnection.create("env-cluster","https://kubernetes.example",List.of("releasepilot-demo"),"vault:k8s/demo",now));
  var prom=prometheus.save(PrometheusConnection.create("env-prometheus","http://prometheus:9090",null,15,now));
  String response=mvc.perform(post("/api/v1/services/{id}/environments",app.getId()).with(authentication(operator())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
   {"name":"production","clusterId":"%s","namespace":"releasepilot-demo","rolloutName":"checkout","containerName":"checkout","stableServiceName":"checkout-stable","canaryServiceName":"checkout-canary","prometheusConnectionId":"%s","workloadLabelSelector":{"service_namespace":"releasepilot-demo","service_name":"checkout"},"defaultPolicyVersionId":"%s"}
   """.formatted(cluster.getId(),prom.getId(),UUID.randomUUID()))).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.strategy").value("CANARY")).andExpect(jsonPath("$.containerName").value("checkout")).andReturn().getResponse().getContentAsString();
  String id=response.substring(response.indexOf("\"id\":\"")+6,response.indexOf("\"",response.indexOf("\"id\":\"")+6));
  mvc.perform(get("/api/v1/services/{id}/environments",app.getId()).with(authentication(operator())))
   .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id)).andExpect(jsonPath("$.items[0].name").value("production"));
  mvc.perform(post("/api/v1/environments/{id}/validate",id).with(authentication(operator())).with(csrf()))
   .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("INVALID")).andExpect(jsonPath("$.validUntil").isString()).andExpect(jsonPath("$.checks.length()").value(11)).andExpect(jsonPath("$.checks[6].code").value("ROLLOUT_RBAC"));
  mvc.perform(get("/api/v1/environments/{id}/validation-results/latest",id).with(authentication(operator())))
   .andExpect(status().isOk()).andExpect(jsonPath("$.validUntil").isString()).andExpect(jsonPath("$.checks.length()").value(11));
  mvc.perform(get("/api/v1/audit-events").param("aggregateType","ENVIRONMENT").param("aggregateId",id).with(authentication(operator())))
   .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].actorType").value("USER")).andExpect(jsonPath("$.items[0].payloadJson").value(org.hamcrest.Matchers.containsString("MANUAL")));
  mvc.perform(get("/api/v1/environments/{id}/validation-results/latest",id).with(authentication(viewer())))
   .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ENVIRONMENT_NOT_FOUND"));
  org.assertj.core.api.Assertions.assertThat(results.findByEnvironmentIdOrderByCheckedAtAsc(UUID.fromString(id))).hasSize(11);
  org.assertj.core.api.Assertions.assertThat(audits.findByAggregateTypeAndAggregateIdOrderByOccurredAtAsc("ENVIRONMENT",UUID.fromString(id))).hasSize(1);
 }
 @Test void hidesEnvironmentCatalogOutsideProjectMembership() throws Exception {
  var now=Instant.now();var project=projects.save(Project.create("private-env","Private Environment",null,now));
  var app=services.save(CatalogService.create(project.getId(),"private-app","Private App","https://github.com/acme/private","platform",now));
  mvc.perform(get("/api/v1/services/{id}/environments",app.getId()).with(authentication(viewer())))
   .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SERVICE_NOT_FOUND"));
 }
 private UsernamePasswordAuthenticationToken operator(){var p=new UserAccountPrincipal(UUID.randomUUID(),"operator","unused","Operator",true,List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));return UsernamePasswordAuthenticationToken.authenticated(p,p.getPassword(),p.getAuthorities());}
 private UsernamePasswordAuthenticationToken viewer(){var p=new UserAccountPrincipal(UUID.randomUUID(),"viewer","unused","Viewer",true,List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));return UsernamePasswordAuthenticationToken.authenticated(p,p.getPassword(),p.getAuthorities());}
}
