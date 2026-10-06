package dev.poc;
import java.util.*;
/** Small Java 8 immutable-collection helpers; adapters keep framework dependencies out of core. */
final class Compat {
 private Compat() {}
 @SafeVarargs static <T> List<T> list(T... values) { return copy(Arrays.asList(values)); }
 @SafeVarargs static <T> Set<T> set(T... values) { return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(values))); }
 static <T> List<T> copy(Collection<? extends T> values) { return Collections.unmodifiableList(new ArrayList<T>(values)); }
 @SuppressWarnings("unchecked") static <K,V> Map<K,V> map(Object... values) {
  if(values.length%2!=0)throw new IllegalArgumentException("Expected key/value pairs");
  Map<K,V> result=new LinkedHashMap<K,V>();
  for(int i=0;i<values.length;i+=2)result.put((K)Objects.requireNonNull(values[i]),(V)Objects.requireNonNull(values[i+1]));
  return Collections.unmodifiableMap(result);
 }
}
