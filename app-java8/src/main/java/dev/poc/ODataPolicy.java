package dev.poc;
import dev.poc.core.PolicyException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.servlet.http.*;
import org.springframework.stereotype.Component;
/** Conservative public surface. The SAP/Olingo parser still parses and executes the actual OData query. */
@Component
public class ODataPolicy {
 private final Limits limits;
 private final dev.poc.core.QueryPolicy policy;
 private static final Set<String> FIELDS=Compat.set("Id","Title","Year","Rating","Genre");
 private static final Set<String> OPTIONS=Compat.set("$top","$skip","$filter","$orderby","$select","$expand","$count","$skiptoken","$format");
 private static final Pattern TOKEN=Pattern.compile("'(?:''|[^'])*'|[A-Za-z_][A-Za-z_0-9]*|-?[0-9]+(?:\\.[0-9]+)?|[()]");
 public ODataPolicy(Limits limits){this.limits=limits;this.policy=CoreConfig.policy(limits);}
 public HttpServletRequest validate(HttpServletRequest req) {
  String path=req.getRequestURI().substring(req.getContextPath().length());
  if(path.equals("/odata") || path.equals("/odata/") || path.equals("/odata/$metadata")) {
   if(!req.getParameterMap().isEmpty())fail("No query options on metadata/service document");
   return req;
  }
  if(!path.matches("/odata/Movies(?:\\([0-9]+\\))?(?:/\\$count)?"))fail("Only Movies, Movies(id), Movies/$count are exposed");
  Map<String,String> params=new LinkedHashMap<String,String>();
  req.getParameterMap().forEach((k,v)->{if(!OPTIONS.contains(k)||v.length!=1)fail("Unknown or repeated option: "+k);params.put(k,v[0]);});
  if(params.containsKey("$skiptoken")) {
   if(params.size()!=1)fail("Continuation token must be used alone");
   if(!params.get("$skiptoken").matches("(?:[a-f0-9-]{36}|'[a-f0-9-]{36}')"))fail("Invalid continuation token");
   return req;
  }
  boolean collection=path.equals("/odata/Movies");
  int top=limits.top(integer(params.get("$top"))),skip=limits.offset(integer(params.get("$skip")));
  if(params.containsKey("$count") && !Compat.set("true","false").contains(params.get("$count")))fail("Invalid $count");
  if(params.containsKey("$format") && !params.get("$format").equals("json"))fail("Only JSON is supported");
  int depth=0;
  int cost=filter(params.get("$filter"));
  if(params.containsKey("$select"))for(String s:params.get("$select").split(",",-1))if(!FIELDS.contains(s.trim()))fail("Select field not allowed: "+s);
  String order=params.getOrDefault("$orderby","Id asc");
  Set<String> orderFields=new HashSet<>();
  for(String part:order.split(",",-1)) {
   String[] pieces=part.trim().split("\\s+");
   if(pieces.length<1 || pieces.length>2 || (pieces.length==2&&!Compat.set("asc","desc").contains(pieces[1])))fail("Order field not allowed");
   policy.orderField(pieces[0]);
   if(!orderFields.add(pieces[0]))fail("Duplicate order field");cost+=5;
  }
  if(!orderFields.contains("Id"))order+=",Id asc";
  if(params.containsKey("$expand")) {
   String expand=params.get("$expand");
   depth=(int)expand.chars().filter(c->c=='(').count()+1;
   policy.budget(depth,0);
   if(!expand.matches("Director(?:\\(\\$select=(?:Id|Name)(?:,(?:Id|Name))*\\))?"))fail("Only to-one Director expansion and its Id/Name selection are allowed");
   cost+=50;
  }
  cost+=Math.min(top,limits.pageSize())*(params.containsKey("$expand")?12:6);
  policy.budget(depth,cost);
  if(collection){params.put("$top",Integer.toString(top));params.put("$skip",Integer.toString(skip));params.put("$orderby",order);}
  return new NormalizedRequest(req,params);
 }
 int filter(String text) {
  if(text==null)return 0;
  if(text.length()>4096)fail("Filter is too long");
  List<String> tokens=new ArrayList<>();Matcher m=TOKEN.matcher(text);int pos=0;
  while(m.find()) {if(!text.substring(pos,m.start()).trim().isEmpty())fail("Unsupported filter token");tokens.add(m.group());pos=m.end();}
  if(!text.substring(pos).trim().isEmpty()||tokens.isEmpty()||tokens.size()>120)fail("Invalid or complex filter");
  // Validate a complete grammar: comparisons combined with and/or, optionally parenthesized.
  new FilterGrammar(tokens).parse();return tokens.size()*5;
 }
 private final class FilterGrammar {
  final List<String> t;int p;FilterGrammar(List<String> t){this.t=t;}
  void parse(){expr(0);if(p!=t.size())fail("Unexpected filter token");}
  void expr(int depth){if(depth>8)fail("Filter nesting exceeds 8");term(depth);while(p<t.size()&&Compat.set("and","or").contains(t.get(p))){p++;term(depth);}}
  void term(int depth){
   if(p<t.size()&&t.get(p).equals("(")){p++;expr(depth+1);take(")");return;}
   String field=next();policy.filterField(field);
   if(!Compat.set("eq","ne","gt","ge","lt","le").contains(next()))fail("Filter operator not allowed");
   String value=next();
   boolean string=value.startsWith("'");
   if(Compat.set("Title","Genre").contains(field)) {if(!string)fail("String literal required");}
   else if(!value.matches("-?[0-9]+(?:\\.[0-9]+)?"))fail("Numeric literal required");
  }
  String next(){if(p>=t.size())fail("Incomplete filter");return t.get(p++);}
  void take(String s){if(!next().equals(s))fail("Invalid parentheses");}
 }
 private Integer integer(String s){if(s==null)return null;try{return Integer.valueOf(s);}catch(NumberFormatException e){fail("Invalid integer option");return null;}}
 private static void fail(String message){throw new PolicyException("ODATA_POLICY",message);}
 static class NormalizedRequest extends HttpServletRequestWrapper {
  private final Map<String,String[]> params;private final String query;
  NormalizedRequest(HttpServletRequest req,Map<String,String> p){super(req);params=new LinkedHashMap<>();p.forEach((k,v)->params.put(k,new String[]{v}));
   query=p.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(java.util.stream.Collectors.joining("&"));}
  static String enc(String s){return org.springframework.web.util.UriUtils.encodeQueryParam(s,StandardCharsets.UTF_8).replace("+","%20");}
  @Override public String getQueryString(){return query;}
  @Override public String getParameter(String k){String[] v=params.get(k);return v==null?null:v[0];}
  @Override public Map<String,String[]> getParameterMap(){return Collections.unmodifiableMap(params);}
  @Override public String[] getParameterValues(String k){return params.get(k);}
  @Override public Enumeration<String> getParameterNames(){return Collections.enumeration(params.keySet());}
 }
}
