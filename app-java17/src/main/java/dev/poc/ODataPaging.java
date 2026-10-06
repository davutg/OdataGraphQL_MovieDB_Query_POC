package dev.poc;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.uri.UriInfo;
import com.sap.olingo.jpa.metadata.api.JPARequestParameterMap;
import com.sap.olingo.jpa.processor.core.api.*;
import com.sap.olingo.jpa.processor.core.query.JPACountQuery;
/** Bounded, expiring in-memory continuation store. Use shared persistence when scaling. */
@Component
public class ODataPaging implements JPAODataPagingProvider {
 private final Limits limits;
 private final dev.poc.core.QueryPolicy policy;
 private record Entry(JPAODataPage page,int remaining,long expires) {}
 private final Map<String,Entry> tokens=new LinkedHashMap<>();
 public ODataPaging(Limits limits){this.limits=limits;this.policy=CoreConfig.policy(limits);}
 @Override public synchronized Optional<JPAODataPage> getFirstPage(JPARequestParameterMap params,
  JPAODataPathInformation path,UriInfo uri,Integer preferred,JPACountQuery count,EntityManager em) throws ODataApplicationException {
  int top=limits.top(uri.getTopOption()==null?null:uri.getTopOption().getValue());
  int skip=limits.offset(uri.getSkipOption()==null?null:uri.getSkipOption().getValue());
  int size=policy.page(top,skip,preferred==null?null:Math.max(1,preferred)).size();
  long total=count.countResults();
  int remaining=(int)Math.min(top,Math.max(0,total-skip));
  return Optional.of(page(uri,skip,Math.min(size,remaining),remaining));
 }
 private JPAODataPage page(org.apache.olingo.server.api.uri.UriInfoResource uri,int skip,int size,int remaining) {
  tokens.entrySet().removeIf(e->e.getValue().expires<=System.currentTimeMillis());
  String next=null;
  if(remaining>size) {
   var budget=policy.page(remaining,skip,size);
   int nextOffset=budget.nextOffset(size);
   next=UUID.randomUUID().toString();
   if(tokens.size()>=1000) tokens.remove(tokens.keySet().iterator().next());
   int left=budget.remaining(size);
   tokens.put(next,new Entry(new JPAODataPage(uri,nextOffset,Math.min(size,left),null),left,System.currentTimeMillis()+300000));
  }
  return new JPAODataPage(uri,skip,size,next);
 }
 @Override public synchronized Optional<JPAODataPage> getNextPage(String token,OData odata,ServiceMetadata metadata,JPARequestParameterMap params,EntityManager em) {
  Entry e=tokens.get(token);
  if(e==null || e.expires<=System.currentTimeMillis())return Optional.empty();
  return Optional.of(page(e.page.uriInfo(),e.page.skip(),e.page.top(),e.remaining));
 }
}
