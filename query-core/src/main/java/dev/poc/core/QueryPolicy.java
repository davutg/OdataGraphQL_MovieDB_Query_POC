package dev.poc.core;
import java.util.*;
/** Protocol adapters supply parsed fields and measured depth/complexity, never raw SQL. */
public final class QueryPolicy {
 private final Limits limits;
 private final Set<String> filters, orders;
 public QueryPolicy(Limits limits,Collection<String> filters,Collection<String> orders){
  if(limits==null)throw new IllegalArgumentException("Limits required");
  this.limits=limits;this.filters=copy(filters);this.orders=copy(orders);
 }
 private static Set<String> copy(Collection<String> fields){
  if(fields==null)throw new IllegalArgumentException("Allow-list required");
  Set<String> result=new HashSet<String>();
  for(String field:fields){if(field==null||field.trim().isEmpty())throw new IllegalArgumentException("Invalid field");result.add(field);}
  return Collections.unmodifiableSet(result);
 }
 public void filterField(String field){require(filters,field,"FILTER_FIELD");}
 public void orderField(String field){require(orders,field,"ORDER_FIELD");}
 private static void require(Set<String> fields,String field,String code){if(!fields.contains(field))throw new PolicyException(code,("ORDER_FIELD".equals(code)?"Order":"Filter")+" field is not allow-listed");}
 public void budget(int depth,long complexity){
  if(depth<0||depth>limits.maxExpandDepth())throw new PolicyException("EXPAND_DEPTH","Expansion depth exceeds budget");
  if(complexity<0||complexity>limits.maxQueryComplexity())throw new PolicyException("QUERY_COMPLEXITY","Query exceeds complexity budget");
 }
 public Page page(Integer top,Integer offset){return page(top,offset,null);}
 public Page page(Integer top,Integer offset,Integer preferred){
  if(preferred!=null && preferred<1)throw new IllegalArgumentException("Preferred page size must be positive");
  int remaining=limits.top(top),start=limits.offset(offset),size=Math.min(remaining,limits.pageSize());
  if(preferred!=null)size=Math.min(size,preferred);
  return new Page(start,size,remaining,limits.maxOffset());
 }
 public static final class Page {
  private final int offset,size,budget,maxOffset;
  private Page(int offset,int size,int budget,int maxOffset){this.offset=offset;this.size=size;this.budget=budget;this.maxOffset=maxOffset;}
  public int offset(){return offset;}public int size(){return size;}
  public int remaining(int returned){validate(returned);return budget-returned;}
  private void validate(int returned){if(returned<0||returned>size)throw new IllegalArgumentException("Invalid returned row count");}
  public Integer nextOffset(int returned){
   validate(returned);if(returned<size||size==0||remaining(returned)==0)return null;
   long next=(long)offset+returned;if(next>maxOffset)throw new PolicyException("OFFSET_LIMIT","Continuation offset overflow");return (int)next;
  }
 }
 public String indexMode(String requested){
  String configured=limits.indexPolicy().mode();if(requested==null)return configured;
  String normalized=requested.toUpperCase(Locale.ROOT);
  if(!Arrays.asList("OFF","AUDIT","ENFORCE").contains(normalized))throw new PolicyException("INDEX_MODE","Unknown index policy mode");
  if(!limits.indexPolicy().allowClientSelection()&&!configured.equals(normalized))throw new PolicyException("INDEX_MODE","Client selection disabled");return normalized;
 }
}
