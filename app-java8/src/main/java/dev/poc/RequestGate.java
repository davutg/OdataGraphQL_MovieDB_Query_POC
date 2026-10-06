package dev.poc;
import dev.poc.core.PolicyException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.annotation.PreDestroy;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
/** Synchronous HTTP budget with bounded workers, buffered responses and a database-side deadline. */
@Component
public class RequestGate extends OncePerRequestFilter {
 private final Limits limits;private final ObjectMapper json;
 private final ThreadPoolExecutor workers;
 private final Map<String,Window> rates=new HashMap<>();
 private final Map<String,Map<String,Object>> traces=new LinkedHashMap<>();
 private static final class Window {private final long start;private final int count;@com.fasterxml.jackson.annotation.JsonCreator public Window(@com.fasterxml.jackson.annotation.JsonProperty("start") long start,@com.fasterxml.jackson.annotation.JsonProperty("count") int count){this.start=start;this.count=count;}public long start(){return start;} public long getStart(){return start;}public int count(){return count;} public int getCount(){return count;}}
 public RequestGate(Limits limits,ObjectMapper json){this.limits=limits;this.json=json;
  workers=new ThreadPoolExecutor(limits.maxConcurrentRequests(),limits.maxConcurrentRequests(),0L,TimeUnit.MILLISECONDS,
   new SynchronousQueue<>(),r->{Thread t=new Thread(r,"query-worker");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 }
 @PreDestroy void close(){workers.shutdownNow();}
 public synchronized Map<String,Object> trace(String id){return traces.get(id);}
 private synchronized void save(RequestState state) {
  if(traces.size()>=200)traces.remove(traces.keySet().iterator().next());
  traces.put(state.id,Compat.map("requestId",state.id,"mode",state.mode,"plans",Compat.copy(state.plans)));
 }
 private synchronized boolean permit(String key) {
  long now=System.currentTimeMillis();rates.entrySet().removeIf(e->now-e.getValue().start>=60000);
  if(rates.size()>=10000 && !rates.containsKey(key))return false;
  Window old=rates.getOrDefault(key,new Window(now,0));
  if(old.count>=limits.ratePerMinute())return false;
  rates.put(key,new Window(old.start,old.count+1));return true;
 }
 @Override protected boolean shouldNotFilter(HttpServletRequest req){String path=req.getRequestURI();return !(path.startsWith("/odata")||path.equals("/graphql")||path.startsWith("/_disabled"));}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws IOException,ServletException {
  if(req.getRequestURI().startsWith("/_disabled")){error(res,404,"NOT_FOUND","Not found");return;}
  if(!permit(req.getRemoteAddr())){res.setHeader("Retry-After","60");error(res,429,"RATE_LIMIT","Rate limit exceeded");return;}
  String method=req.getMethod();
  if((req.getRequestURI().startsWith("/odata")&&!method.equals("GET")) || (req.getRequestURI().equals("/graphql")&&!method.equals("POST"))) {
   error(res,405,"READ_ONLY","Only GET OData and POST GraphQL are supported");return;
  }
  if(req.getQueryString()!=null && req.getQueryString().length()>8192){error(res,413,"QUERY_SIZE","Query string too large");return;}
  HttpServletRequest safe=req;
  if(method.equals("POST")) {
   byte[] body=readBounded(req.getInputStream(),32769);
   if(body.length>32768){error(res,413,"BODY_SIZE","Request body exceeds 32 KiB");return;}
   safe=new BodyRequest(req,body);
  }
  String mode;
  try {mode=CoreConfig.policy(limits).indexMode(req.getHeader("X-Index-Policy"));}
  catch(PolicyException e){error(res,"Client selection disabled".equals(e.getMessage())?403:422,e.code,e.getMessage());return;}
  RequestState state=new RequestState(mode,limits.requestTimeoutMs());
  res.setHeader("X-Request-Id",state.id);res.setHeader("X-Index-Policy",mode);
  BufferResponse buffer=new BufferResponse(res);HttpServletRequest request=safe;
  Future<?> job;
  try {
   job=workers.submit(()->{
    RequestState.CURRENT.set(state);
    try{chain.doFilter(request,buffer);}catch(Exception e){throw new CompletionException(e);}finally{RequestState.CURRENT.remove();}
   });
  }catch(RejectedExecutionException e){error(res,503,"CONCURRENCY_LIMIT","All query workers are busy");return;}
  try {
   job.get(limits.requestTimeoutMs(),TimeUnit.MILLISECONDS);
   save(state);
   if(state.violation!=null){error(res,422,state.violation.code,state.violation.getMessage());return;}
   buffer.copyTo(res);
  }catch(TimeoutException e){job.cancel(true);save(state);error(res,504,"REQUEST_TIMEOUT","Request exceeded time budget");}
  catch(InterruptedException e){job.cancel(true);Thread.currentThread().interrupt();error(res,503,"INTERRUPTED","Request interrupted");}
  catch(ExecutionException e){save(state);Throwable cause=e.getCause();while(cause.getCause()!=null)cause=cause.getCause();
   if(state.violation!=null)error(res,422,state.violation.code,state.violation.getMessage());
   else if(cause instanceof PolicyException)error(res,422,((PolicyException)cause).code,cause.getMessage());
   else error(res,500,"QUERY_FAILED","Query failed; request id "+state.id);
  }
 }
 private void error(HttpServletResponse res,int status,String code,String message) throws IOException {
  res.resetBuffer();res.setStatus(status);res.setContentType("application/json");json.writeValue(res.getOutputStream(),Compat.map("error",Compat.map("code",code,"message",message)));
 }
 static byte[] readBounded(InputStream in,int limit) throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];
  while(out.size()<limit){int n=in.read(block,0,Math.min(block.length,limit-out.size()));if(n<0)break;out.write(block,0,n);}
  return out.toByteArray();
 }
 static class BodyRequest extends HttpServletRequestWrapper {
  final byte[] body;BodyRequest(HttpServletRequest req,byte[] body){super(req);this.body=body;}
  @Override public ServletInputStream getInputStream(){ByteArrayInputStream in=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}};}
  @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
  @Override public int getContentLength(){return body.length;}@Override public long getContentLengthLong(){return body.length;}
 }
 static class BufferResponse extends HttpServletResponseWrapper {
  final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  final Map<String,List<String>> headers=new LinkedHashMap<>();int status=200;String contentType;PrintWriter writer;
  BufferResponse(HttpServletResponse raw){super(raw);}
  @Override public ServletOutputStream getOutputStream(){return new ServletOutputStream(){public void write(int b){bytes.write(b);}public boolean isReady(){return true;}public void setWriteListener(WriteListener listener){throw new UnsupportedOperationException();}};}
  @Override public PrintWriter getWriter(){if(writer==null)writer=new PrintWriter(new OutputStreamWriter(bytes,StandardCharsets.UTF_8));return writer;}
  @Override public void setStatus(int s){status=s;}@Override public int getStatus(){return status;}
  @Override public void setHeader(String k,String v){if(v==null){headers.remove(k);if(k.equalsIgnoreCase("Content-Type"))contentType=null;}else headers.put(k,new ArrayList<>(Compat.list(v)));}
  @Override public void addHeader(String k,String v){if(v!=null)headers.computeIfAbsent(k,x->new ArrayList<>()).add(v);}
  @Override public void setContentType(String s){contentType=s;}
  @Override public String getContentType(){return contentType;}
  @Override public void setContentLength(int n){}@Override public void setContentLengthLong(long n){}
  @Override public void flushBuffer(){if(writer!=null)writer.flush();}
  @Override public boolean isCommitted(){return false;}
  @Override public void resetBuffer(){bytes.reset();}
  @Override public void sendError(int s,String message) throws IOException {status=s;resetBuffer();getWriter().write(message);}
  @Override public void sendError(int s) throws IOException {sendError(s,"");}
  void copyTo(HttpServletResponse raw) throws IOException {
   flushBuffer();raw.setStatus(status);if(contentType!=null)raw.setContentType(contentType);
   headers.forEach((k,v)->{if(!k.equalsIgnoreCase("Content-Length"))for(String value:v)raw.addHeader(k,value);});
   raw.getOutputStream().write(bytes.toByteArray());
  }
 }
}
