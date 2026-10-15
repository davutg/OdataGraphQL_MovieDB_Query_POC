package dev.poc.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Portable OData continuation. Signed query options replace process-local UriInfo storage. */
public final class ODataCursorCodec {
    public static final int MAX_TOKEN_LENGTH = 24000;
    private final byte[] secret;
    private final Clock clock;
    private final long ttl;
    public ODataCursorCodec(byte[] secret, Clock clock, long ttl) {
        if (secret == null || secret.length < 32 || clock == null || ttl <= 0)
            throw new IllegalArgumentException("Cursor requires a 32-byte key, clock and positive TTL");
        this.secret = secret.clone(); this.clock = clock; this.ttl = ttl;
    }
    public String encode(Map<String, String> options, int pageSize) {
        if (options.size() > 12 || pageSize < 1) throw new IllegalArgumentException("Invalid continuation");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeLong(Math.addExact(clock.millis(), ttl)); out.writeInt(pageSize); out.writeInt(options.size());
            for (Map.Entry<String,String> entry : new TreeMap<String,String>(options).entrySet()) {
                out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue());
            }
            String body = "od1." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
            String token = body + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
            if (token.length() > MAX_TOKEN_LENGTH) throw new PolicyException("QUERY_SIZE", "Continuation exceeds size limit");
            return token;
        } catch (IOException ex) { throw new IllegalArgumentException("Invalid continuation", ex); }
    }
    public State decode(String token) {
        try {
            if (token == null || token.length() > MAX_TOKEN_LENGTH) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("od1")) throw new IllegalArgumentException();
            if (!MessageDigest.isEqual(sign(parts[0]+"."+parts[1]), Base64.getUrlDecoder().decode(parts[2])))
                throw new IllegalArgumentException();
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getUrlDecoder().decode(parts[1])));
            long expiry = in.readLong(); int size = in.readInt(); int count = in.readInt();
            if (expiry <= clock.millis() || size < 1 || count < 0 || count > 12) throw new IllegalArgumentException();
            Map<String,String> options = new LinkedHashMap<String,String>();
            for (int i=0; i<count; i++) {
                String key=in.readUTF(), value=in.readUTF();
                if (key.length()>64 || value.length()>8192 || options.put(key,value)!=null) throw new IllegalArgumentException();
            }
            if (in.available()!=0 || options.containsKey("$skiptoken")) throw new IllegalArgumentException();
            return new State(options,size);
        } catch (Exception ex) { throw new PolicyException("CURSOR_INVALID", "Invalid or expired OData continuation"); }
    }
    private byte[] sign(String text) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            return mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) { throw new IllegalStateException(ex); }
    }
    public static final class State {
        private final Map<String,String> options;
        private final int pageSize;
        State(Map<String,String> options,int size) { this.options=Collections.unmodifiableMap(options);this.pageSize=size; }
        public Map<String,String> options(){return options;}
        public int pageSize(){return pageSize;}
    }
}
