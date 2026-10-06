package dev.poc.core;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;


public final class CursorCodec {
 private final byte[] secret;
 private final java.time.Clock clock;
 private final long ttlMs;
 public CursorCodec(){this(randomKey(),java.time.Clock.systemUTC(),300000);}
 private static byte[] randomKey(){byte[] key=new byte[32];new SecureRandom().nextBytes(key);return key;}
 public CursorCodec(byte[] secret,java.time.Clock clock,long ttlMs){
  if(secret==null || secret.length<32 || clock==null || ttlMs<1)throw new IllegalArgumentException("32-byte key, clock and positive TTL required");
  this.secret=secret.clone();this.clock=clock;this.ttlMs=ttlMs;
 }
 public static final class Cursor {
private final int offset;
private final int remaining;
private final String fingerprint;
private final long expires;
public Cursor(int offset,int remaining,String fingerprint,long expires) {this.offset=offset;this.remaining=remaining;this.fingerprint=fingerprint;this.expires=expires;}
public int offset(){return offset;}
public int getOffset(){return offset;}
public int remaining(){return remaining;}
public int getRemaining(){return remaining;}
public String fingerprint(){return fingerprint;}
public String getFingerprint(){return fingerprint;}
public long expires(){return expires;}
public long getExpires(){return expires;}
}

 public String encode(int offset,int remaining,String fingerprint) {
  if(offset<0 || remaining<0 || fingerprint==null || !fingerprint.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid cursor payload");
  String data=offset+":"+remaining+":"+fingerprint+":"+Math.addExact(clock.millis(),ttlMs);
  String body=Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
  return body+"."+sign(body);
 }
 public Cursor decode(String token,String fingerprint) {
  try {
   if(token.length()>512)throw new IllegalArgumentException();
   String[] a=token.split("[.]",-1);
   if(a.length!=2 || !MessageDigest.isEqual(sign(a[0]).getBytes(StandardCharsets.US_ASCII),a[1].getBytes(StandardCharsets.US_ASCII)))throw new IllegalArgumentException();
   String[] p=new String(Base64.getUrlDecoder().decode(a[0]),StandardCharsets.UTF_8).split(":",-1);
   if(p.length!=4)throw new IllegalArgumentException();
   Cursor c=new Cursor(Integer.parseInt(p[0]),Integer.parseInt(p[1]),p[2],Long.parseLong(p[3]));
   if(!c.fingerprint.matches("[a-f0-9]{64}")||!c.fingerprint.equals(fingerprint)||c.expires<=clock.millis()||c.offset<0||c.remaining<0)throw new IllegalArgumentException();
   return c;
  }catch(Exception e){throw new PolicyException("CURSOR_INVALID","Cursor is invalid, expired, or belongs to another query");}
 }
 public String fingerprint(String query) {
  try{return hex(MessageDigest.getInstance("SHA-256").digest(query.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
 }
 private static String hex(byte[] bytes) { char[] digits="0123456789abcdef".toCharArray(); char[] out=new char[bytes.length*2]; for(int i=0;i<bytes.length;i++){out[i*2]=digits[(bytes[i]&255)>>>4];out[i*2+1]=digits[bytes[i]&15];}return new String(out);}
 private String sign(String text) {
  try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
 }
}
