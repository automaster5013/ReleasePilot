package kr.releasepilot.controlplane.rollout;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class HttpArgoRolloutsGatewayTests {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void patchesOnlyNamedContainerWithUidAndResourceVersionGuards() throws Exception {
        var patchBody = new AtomicReference<String>(); var patchCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apis/argoproj.io/v1alpha1/namespaces/demo/rollouts/checkout", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, rollout("7", "ghcr.io/acme/sidecar@sha256:old", "ghcr.io/acme/checkout@sha256:old"));
            } else {
                patchCalls.incrementAndGet(); patchBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/json-patch+json");
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer token");
                respond(exchange, 200, rollout("8", "ghcr.io/acme/sidecar@sha256:old", "ghcr.io/acme/checkout@sha256:new"));
            }
        }); server.start();
        var gateway = new HttpArgoRolloutsGateway(HttpClient.newHttpClient(), new ObjectMapper());
        var observed = gateway.start(request("ghcr.io/acme/checkout@sha256:new"));
        assertThat(observed.uid()).isEqualTo("rollout-uid"); assertThat(observed.resourceVersion()).isEqualTo("8");
        assertThat(patchCalls).hasValue(1);
        assertThat(patchBody.get()).contains("/metadata/uid", "/metadata/resourceVersion", "/containers/1/image")
                .contains("ghcr.io/acme/checkout@sha256:new");
    }

    @Test void alreadyAppliedImageIsIdempotentAndDoesNotPatch() throws Exception {
        var patchCalls = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apis/argoproj.io/v1alpha1/namespaces/demo/rollouts/checkout", exchange -> {
            if ("PATCH".equals(exchange.getRequestMethod())) patchCalls.incrementAndGet();
            respond(exchange, 200, rollout("9", "sidecar", "ghcr.io/acme/checkout@sha256:new"));
        }); server.start();
        var observed = new HttpArgoRolloutsGateway(HttpClient.newHttpClient(), new ObjectMapper()).start(request("ghcr.io/acme/checkout@sha256:new"));
        assertThat(observed.resourceVersion()).isEqualTo("9"); assertThat(patchCalls).hasValue(0);
    }

    @Test void pauseUsesExecutionUidAndResourceVersionGuard() throws Exception {
        var patchBody=new AtomicReference<String>();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/apis/argoproj.io/v1alpha1/namespaces/demo/rollouts/checkout",exchange->{if("GET".equals(exchange.getRequestMethod()))respond(exchange,200,rollout("11","sidecar","app"));else{patchBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));respond(exchange,200,rollout("12","sidecar","app"));}});server.start();
        var gateway=new HttpArgoRolloutsGateway(HttpClient.newHttpClient(),new ObjectMapper());
        var observed=gateway.control(new ArgoRolloutsGateway.ControlRequest("http://127.0.0.1:"+server.getAddress().getPort(),"token","demo","checkout","rollout-uid",ArgoRolloutsGateway.Action.PAUSE));
        assertThat(observed.resourceVersion()).isEqualTo("12");assertThat(patchBody.get()).contains("/metadata/uid","/metadata/resourceVersion","/spec/paused","true");
    }

    @Test void abortPatchesStatusAbortInsteadOfNonexistentSpecField() throws Exception {
        var patchBody=new AtomicReference<String>();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/apis/argoproj.io/v1alpha1/namespaces/demo/rollouts/checkout",exchange->{if("GET".equals(exchange.getRequestMethod()))respond(exchange,200,rollout("13","sidecar","app"));else if(exchange.getRequestURI().getPath().endsWith("/status")){patchBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));respond(exchange,200,rollout("14","sidecar","app"));}else respond(exchange,404,"{}");});server.start();
        new HttpArgoRolloutsGateway(HttpClient.newHttpClient(),new ObjectMapper()).control(new ArgoRolloutsGateway.ControlRequest("http://127.0.0.1:"+server.getAddress().getPort(),"token","demo","checkout","rollout-uid",ArgoRolloutsGateway.Action.ABORT));
        assertThat(patchBody.get()).contains("/status/abort").doesNotContain("/spec/abort");
    }

    @Test void observesPhaseStepAndTargetContainerImage() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/apis/argoproj.io/v1alpha1/namespaces/demo/rollouts/checkout",exchange->respond(exchange,200,rollout("15","sidecar","target").replace("\"resourceVersion\":\"15\"", "\"resourceVersion\":\"15\",\"generation\":4").replace("\"spec\":", "\"status\":{\"phase\":\"Progressing\",\"currentStepIndex\":3,\"abort\":false,\"observedGeneration\":4},\"spec\":").replace("\"template\":", "\"strategy\":{\"canary\":{\"steps\":[{\"setWeight\":10},{\"pause\":{}},{\"setWeight\":30},{\"pause\":{}}]}},\"template\":")));server.start();
        var result=new HttpArgoRolloutsGateway(HttpClient.newHttpClient(),new ObjectMapper()).observe(new ArgoRolloutsGateway.ObserveRequest("http://127.0.0.1:"+server.getAddress().getPort(),"token","demo","checkout","checkout"));
        assertThat(result.phase()).isEqualTo("Progressing");assertThat(result.currentStepIndex()).isEqualTo(1);assertThat(result.image()).isEqualTo("target");assertThat(result.aborted()).isFalse();assertThat(result.controllerObservedDesiredGeneration()).isTrue();
    }

    @Test void observesBlueGreenPreviewAsSingleAnalysisStep() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/apis/argoproj.io/v1alpha1/namespaces/demo/rollouts/checkout",exchange->respond(exchange,200,rollout("16","sidecar","target").replace("\"resourceVersion\":\"16\"", "\"resourceVersion\":\"16\",\"generation\":2").replace("\"spec\":", "\"status\":{\"phase\":\"Paused\",\"pauseConditions\":[{\"reason\":\"BlueGreenPause\"}],\"abort\":false,\"observedGeneration\":2},\"spec\":").replace("\"template\":", "\"strategy\":{\"blueGreen\":{\"activeService\":\"checkout-active\",\"previewService\":\"checkout-preview\",\"autoPromotionEnabled\":false}},\"template\":")));server.start();
        var result=new HttpArgoRolloutsGateway(HttpClient.newHttpClient(),new ObjectMapper()).observe(new ArgoRolloutsGateway.ObserveRequest("http://127.0.0.1:"+server.getAddress().getPort(),"token","demo","checkout","checkout"));
        assertThat(result.phase()).isEqualTo("Paused");assertThat(result.currentStepIndex()).isZero();assertThat(result.image()).isEqualTo("target");assertThat(result.controllerObservedDesiredGeneration()).isTrue();
    }

    private ArgoRolloutsGateway.StartRequest request(String image) { return new ArgoRolloutsGateway.StartRequest("http://127.0.0.1:" + server.getAddress().getPort(), "token", "demo", "checkout", "checkout", image); }
    private String rollout(String resourceVersion, String sidecarImage, String appImage) { return "{\"metadata\":{\"uid\":\"rollout-uid\",\"resourceVersion\":\""+resourceVersion+"\"},\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"sidecar\",\"image\":\""+sidecarImage+"\"},{\"name\":\"checkout\",\"image\":\""+appImage+"\"}]}}}}"; }
    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException { byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close(); }
}
