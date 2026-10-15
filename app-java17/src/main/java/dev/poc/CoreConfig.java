package dev.poc;
import dev.poc.core.CursorCodec;
import dev.poc.core.QueryPolicy;
import org.springframework.context.annotation.*;
import java.util.List;
@Configuration
public class CoreConfig {
 @Bean byte[] cursorSigningKey(
  @org.springframework.beans.factory.annotation.Value("${poc.cursor-secret-base64:}") String secret,
  @org.springframework.beans.factory.annotation.Value("${poc.require-stable-cursor-key:false}") boolean required) {
  if(secret.isEmpty()) {
   if(required)throw new IllegalArgumentException("Set CURSOR_SECRET_BASE64 to a shared Base64 key of at least 32 bytes");
   byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);return key;
  }
  byte[] key=java.util.Base64.getDecoder().decode(secret);
  if(key.length<32)throw new IllegalArgumentException("Cursor signing key must contain at least 32 bytes");return key;
 }
 @Bean CursorCodec cursors(byte[] cursorSigningKey,
  @org.springframework.beans.factory.annotation.Value("${poc.cursor-ttl-ms:300000}") long ttlMs) {
  return new CursorCodec(cursorSigningKey,java.time.Clock.systemUTC(),ttlMs);
 }
 @Bean dev.poc.core.ODataCursorCodec odataCursors(byte[] cursorSigningKey,
  @org.springframework.beans.factory.annotation.Value("${poc.cursor-ttl-ms:300000}") long ttlMs) {
  return new dev.poc.core.ODataCursorCodec(cursorSigningKey,java.time.Clock.systemUTC(),ttlMs);
 }
 static QueryPolicy policy(Limits limits){return new QueryPolicy(limits.core(),List.of("Id","Title","Year","Rating","Genre"),List.of("Id","Year","Rating","Genre"));}
}
