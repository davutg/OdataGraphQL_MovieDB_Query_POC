package dev.poc;
import dev.poc.core.ODataCursorCodec;
import dev.poc.core.PolicyException;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Each pod reconstructs the validated request from a signed token, without a token lookup. */
@Component
public class ODataContinuation {
 private final ODataCursorCodec cursors;
 public ODataContinuation(ODataCursorCodec cursors){this.cursors=cursors;}
 public HttpServletRequest restore(HttpServletRequest req) {
  String token=req.getParameter("$skiptoken");
  if(token==null)return req;
  if(token.startsWith("'") && token.endsWith("'"))token=token.substring(1,token.length()-1);
  ODataCursorCodec.State decoded=cursors.decode(token);
  RequestState state=RequestState.CURRENT.get();
  if(state==null)throw new IllegalStateException("Missing request context");
  state.odataPageSize=decoded.pageSize();
  if(!decoded.options().containsKey("$top") || !decoded.options().containsKey("$skip"))
   throw new PolicyException("CURSOR_INVALID","Incomplete OData continuation");
  return new ODataPolicy.NormalizedRequest(req,decoded.options());
 }
 public void bind(HttpServletRequest safe) {
  Map<String,String> options=new LinkedHashMap<String,String>();
  safe.getParameterMap().forEach((key,value)->options.put(key,value[0]));
  RequestState.CURRENT.get().odataOptions=Collections.unmodifiableMap(options);
 }
}
