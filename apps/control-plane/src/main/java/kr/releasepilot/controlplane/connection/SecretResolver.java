package kr.releasepilot.controlplane.connection;
import java.util.Optional;
public interface SecretResolver {
 Optional<SecretMaterial> resolve(String reference);
 record SecretMaterial(String bearerToken) {}
}
