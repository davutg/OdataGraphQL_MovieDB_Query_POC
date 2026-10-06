package dev.poc;
import dev.poc.core.CursorCodec;
import dev.poc.core.QueryPolicy;
import org.springframework.context.annotation.*;
import java.util.List;
@Configuration
public class CoreConfig {
 @Bean CursorCodec cursors(
  @org.springframework.beans.factory.annotation.Value("${poc.cursor-secret-base64:}") String secret,
  @org.springframework.beans.factory.annotation.Value("${poc.cursor-ttl-ms:300000}") long ttlMs){
  byte[] key;
  if(secret.isEmpty()){key=new byte[32];new java.security.SecureRandom().nextBytes(key);}
  else key=java.util.Base64.getDecoder().decode(secret);
  return new CursorCodec(key,java.time.Clock.systemUTC(),ttlMs);
 }
 static QueryPolicy policy(Limits limits){return new QueryPolicy(limits.core(),Compat.list("Id","Title","Year","Rating","Genre"),Compat.list("Id","Year","Rating","Genre"));}
}
