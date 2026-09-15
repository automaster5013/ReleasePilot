package kr.releasepilot.controlplane.connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentSecretResolverTests {
 @TempDir Path root;
 @Test void observesReplacementWithoutRestartOrCaching() throws Exception {
  var resolver=new EnvironmentSecretResolver(root.toString());
  Files.writeString(root.resolve("connector-token"),"first-token\n");
  assertThat(resolver.resolve("file:connector-token").orElseThrow().bearerToken()).isEqualTo("first-token");
  Files.writeString(root.resolve("replacement"),"second-token\n");
  Files.move(root.resolve("replacement"),root.resolve("connector-token"),StandardCopyOption.REPLACE_EXISTING);
  assertThat(resolver.resolve("file:connector-token").orElseThrow().bearerToken()).isEqualTo("second-token");
  Files.delete(root.resolve("connector-token"));
  assertThat(resolver.resolve("file:connector-token")).isEmpty();
 }
 @Test void rejectsPathsAndUnavailableFiles() {
  var resolver=new EnvironmentSecretResolver(root.toString());
  for(String reference:new String[]{"file:../token","file:/etc/passwd","file:a/b","file:..","file:","file:missing","env:","unknown:token"})
   assertThat(resolver.resolve(reference)).isEmpty();
  assertThat(resolver.resolve(null)).isEmpty();
 }
 @Test void disabledUnlessRootIsConfigured() throws Exception {
  Files.writeString(root.resolve("token"),"secret-token");
  assertThat(new EnvironmentSecretResolver("").resolve("file:token")).isEmpty();
 }
 @Test void rejectsMalformedAndOversizedToken() throws Exception {
  var resolver=new EnvironmentSecretResolver(root.toString());
  for(String value:new String[]{" ","token\nother","x".repeat(16385)}){
   Files.writeString(root.resolve("token"),value);
   assertThat(resolver.resolve("file:token")).isEmpty();
  }
 }
}
