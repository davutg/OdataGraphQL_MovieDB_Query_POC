package dev.poc;
import java.util.*;
import javax.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.uri.UriInfo;

import com.sap.olingo.jpa.processor.core.api.*;
import com.sap.olingo.jpa.processor.core.query.JPACountQuery;
/** Bounded, expiring in-memory continuation store. Use shared persistence when scaling. */
@Component
public class ODataPaging implements JPAODataPagingProvider {
 private final Limits limits;
 private final dev.poc.core.QueryPolicy policy;
 private static final class Entry {private final JPAODataPage page;private final int remaining;private final long expires;@com.fasterxml.jackson.annotation.JsonCreator public Entry(@com.fasterxml.jackson.annotation.JsonProperty("page") JPAODataPage page,@com.fasterxml.jackson.annotation.JsonProperty("remaining") int remaining,@com.fasterxml.jackson.annotation.JsonProperty("expires") long expires){this.page=page;this.remaining=remaining;this.expires=expires;}public JPAODataPage page(){return page;} public JPAODataPage getPage(){return page;}public int remaining(){return remaining;} public int getRemaining(){return remaining;}public long expires(){return expires;} public long getExpires(){return expires;}}
 private final Map<String,Entry> tokens=new LinkedHashMap<>();
 public ODataPaging(Limits limits){this.limits=limits;this.policy=CoreConfig.policy(limits);}
 @Override public synchronized JPAODataPage getFirstPage(UriInfo uri,Integer preferred,JPACountQuery count,EntityManager em) throws ODataApplicationException {
  int top=limits.top(uri.getTopOption()==null?null:uri.getTopOption().getValue());
  int skip=limits.offset(uri.getSkipOption()==null?null:uri.getSkipOption().getValue());
  int size=policy.page(top,skip,preferred==null?null:Math.max(1,preferred)).size();
  long total=count.countResults();
  int remaining=(int)Math.min(top,Math.max(0,total-skip));
  return page(uri,skip,Math.min(size,remaining),remaining);
 }
 private JPAODataPage page(UriInfo uri,int skip,int size,int remaining) {
  tokens.entrySet().removeIf(e->e.getValue().expires<=System.currentTimeMillis());
  String next=null;
  if(remaining>size) {
   dev.poc.core.QueryPolicy.Page budget=policy.page(remaining,skip,size);
   int nextOffset=budget.nextOffset(size);
   next=UUID.randomUUID().toString();
   if(tokens.size()>=1000) tokens.remove(tokens.keySet().iterator().next());
   int left=budget.remaining(size);
   tokens.put(next,new Entry(new JPAODataPage(uri,nextOffset,Math.min(size,left),null),left,System.currentTimeMillis()+300000));
  }
  return new JPAODataPage(uri,skip,size,next);
 }
 @Override public synchronized JPAODataPage getNextPage(String token) {
  // SAP 1.1 passes the quoted OData literal through to the paging provider.
  if(token!=null && token.length()>1 && token.startsWith("'") && token.endsWith("'"))token=token.substring(1,token.length()-1);
  Entry e=tokens.get(token);
  if(e==null || e.expires<=System.currentTimeMillis())return null;
  return page(e.page.getUriInfo(),e.page.getSkip(),e.page.getTop(),e.remaining);
 }
}
