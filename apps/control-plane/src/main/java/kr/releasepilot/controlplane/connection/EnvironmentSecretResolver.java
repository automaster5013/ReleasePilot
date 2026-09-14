package kr.releasepilot.controlplane.connection;
import org.springframework.stereotype.Component; import java.util.Optional;
@Component
public class EnvironmentSecretResolver implements SecretResolver {
 public Optional<SecretMaterial> resolve(String reference){
  if(reference==null||!reference.startsWith("env:")||reference.length()==4)return Optional.empty();
  String value=System.getenv(reference.substring(4));
  return value==null||value.isBlank()?Optional.empty():Optional.of(new SecretMaterial(value));
 }
}
