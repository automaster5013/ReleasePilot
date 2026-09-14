package kr.releasepilot.controlplane.shared.config;
import org.springframework.context.annotation.*; import java.net.http.HttpClient; import java.time.Duration;
@Configuration
public class HttpClientConfiguration {
 @Bean HttpClient validationHttpClient(){return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();}
}
