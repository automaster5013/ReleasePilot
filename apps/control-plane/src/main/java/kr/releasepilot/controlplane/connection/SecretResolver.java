package kr.releasepilot.controlplane.connection;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Optional;
public interface SecretResolver {
 Optional<SecretMaterial> resolve(String reference);
 record SecretMaterial(@JsonIgnore String bearerToken) {
  @Override public String toString() { return "SecretMaterial[bearerToken=[REDACTED]]"; }
 }
}
