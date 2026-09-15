package kr.releasepilot.controlplane.connection;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;
@Component
public class EnvironmentSecretResolver implements SecretResolver {
 private static final int MAX_TOKEN_BYTES=16384;
 private final Path fileRoot;
 public EnvironmentSecretResolver(@Value("${releasepilot.secrets.file-root:}") String fileRoot){
  this.fileRoot=fileRoot.isBlank()?null:Path.of(fileRoot);
 }
 public Optional<SecretMaterial> resolve(String reference){
  if(reference==null)return Optional.empty();
  if(reference.startsWith("env:")&&reference.length()>4){
   String value=System.getenv(reference.substring(4));
   return value==null||value.isBlank()?Optional.empty():Optional.of(new SecretMaterial(value));
  }
  if(fileRoot==null||!reference.startsWith("file:"))return Optional.empty();
  String name=reference.substring(5);
  if(!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))return Optional.empty();
  try{
   Path root=fileRoot.toRealPath();
   Path target=root.resolve(name).toRealPath();
   if(!target.startsWith(root)||!Files.isRegularFile(target))return Optional.empty();
   // Reopen on every call so Kubernetes projected-volume rotation is observed.
   try(var input=Files.newInputStream(target)){
    byte[] bytes=input.readNBytes(MAX_TOKEN_BYTES+1);
    if(bytes.length>MAX_TOKEN_BYTES)return Optional.empty();
    String token=new String(bytes,StandardCharsets.UTF_8).strip();
    if(token.isBlank()||token.chars().anyMatch(Character::isWhitespace))return Optional.empty();
    return Optional.of(new SecretMaterial(token));
   }
  }catch(IOException|SecurityException exception){return Optional.empty();}
 }
}
