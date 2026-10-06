package dev.poc;
import dev.poc.core.PolicyException;
import java.util.*;
public final class RequestState {
 public static final ThreadLocal<RequestState> CURRENT=new ThreadLocal<>();
 public final String id=UUID.randomUUID().toString();
 public final String mode;
 public final long deadlineNanos;
 public final List<Map<String,Object>> plans=Collections.synchronizedList(new ArrayList<>());
 public volatile PolicyException violation;
 public RequestState(String mode,long timeoutMs) {this.mode=mode;this.deadlineNanos=System.nanoTime()+timeoutMs*1_000_000;}
 public long remainingMs(){return Math.max(0,(deadlineNanos-System.nanoTime())/1_000_000);}
}
