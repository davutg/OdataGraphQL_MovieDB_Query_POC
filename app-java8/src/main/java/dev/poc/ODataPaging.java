package dev.poc;
import java.util.*;
import javax.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.apache.olingo.server.api.*;
import org.apache.olingo.server.api.uri.UriInfo;

import com.sap.olingo.jpa.processor.core.api.*;
import com.sap.olingo.jpa.processor.core.query.JPACountQuery;
/** Stateless paging: the controller verifies/restores signed query options before SAP parsing. */
@Component
public class ODataPaging implements JPAODataPagingProvider {
 private final Limits limits;
 private final dev.poc.core.QueryPolicy policy;
 private final dev.poc.core.ODataCursorCodec cursors;
 public ODataPaging(Limits limits,dev.poc.core.ODataCursorCodec cursors){this.limits=limits;this.policy=CoreConfig.policy(limits);this.cursors=cursors;}
 @Override public JPAODataPage getFirstPage(UriInfo uri,Integer preferred,JPACountQuery count,EntityManager em) throws ODataApplicationException {
  int top=limits.top(uri.getTopOption()==null?null:uri.getTopOption().getValue());
  int skip=limits.offset(uri.getSkipOption()==null?null:uri.getSkipOption().getValue());
  RequestState state=RequestState.CURRENT.get();
  if(state==null || state.odataOptions==null)throw new IllegalStateException("Missing validated OData request");
  if(state.odataPageSize!=null)preferred=preferred==null?state.odataPageSize:Math.min(preferred,state.odataPageSize);
  int size=policy.page(top,skip,preferred==null?null:Math.max(1,preferred)).size();
  int remaining=(int)Math.min(top,Math.max(0,count.countResults()-skip));
  size=Math.min(size,remaining);
  String next=null;
  if(remaining>size) {
   dev.poc.core.QueryPolicy.Page budget=policy.page(remaining,skip,size);
   Map<String,String> options=new LinkedHashMap<String,String>(state.odataOptions);
   options.put("$skip",Integer.toString(budget.nextOffset(size)));
   options.put("$top",Integer.toString(budget.remaining(size)));
   next=cursors.encode(options,size);
  }
  return new JPAODataPage(uri,skip,size,next);
 }
 @Override public JPAODataPage getNextPage(String token) {
  throw new IllegalStateException("Continuation must be restored before SAP processing");
 }
}
