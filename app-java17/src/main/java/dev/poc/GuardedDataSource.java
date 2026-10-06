package dev.poc;
import dev.poc.core.PolicyException;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.datasource.AbstractDataSource;
/** Intercepts the actual JDBC SQL from both SAP/EclipseLink and GraphQL/JdbcTemplate. */
public class GuardedDataSource extends AbstractDataSource implements AutoCloseable {
 private final DataSource delegate;private final Limits limits;private final ObjectMapper json;
 private final PlanInspector inspector=new PlanInspector();
 public GuardedDataSource(DataSource delegate,Limits limits,ObjectMapper json){this.delegate=delegate;this.limits=limits;this.json=json;}
 public Connection getConnection() throws SQLException {return wrap(delegate.getConnection());}
 public Connection getConnection(String u,String p) throws SQLException {return wrap(delegate.getConnection(u,p));}
 public void close() throws Exception {if(delegate instanceof AutoCloseable c)c.close();}
 private Connection wrap(Connection raw) {
  return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
   if(m.getName().equals("prepareStatement")) {
    budget(raw);
    return statement(raw,(PreparedStatement)invoke(raw,m,a),(String)a[0]);
   }
   if(m.getName().equals("createStatement")) {
    budget(raw);return statement(raw,(Statement)invoke(raw,m,a),null);
   }
   return invoke(raw,m,a);
  });
 }
 private void budget(Connection c) throws SQLException {
  RequestState state=RequestState.CURRENT.get();
  if(state==null)return;
  long left=Math.min(limits.dbTimeoutMs(),state.remainingMs());
  if(left<=0)throw new SQLException("Request deadline expired","57014");
  // The pool connection is reset for every request statement. Parameter binding avoids SQL interpolation.
  try(PreparedStatement s=c.prepareStatement("SELECT set_config('statement_timeout', ?, false)")) {
   s.setString(1,left+"ms");s.execute();
  }
 }
 private record Binding(Method method,Object[] args) {}
 private Object statement(Connection raw,Statement target,String sql) {
  Map<Integer,Binding> bindings=new TreeMap<>();
  Class<?> api=target instanceof PreparedStatement?PreparedStatement.class:Statement.class;
  return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{api},(p,m,a)->{
   String name=m.getName();
   if(sql!=null && name.startsWith("set") && a!=null && a.length>=2 && a[0] instanceof Integer i)
    bindings.put(i,new Binding(m,a.clone()));
   if(name.equals("clearParameters"))bindings.clear();
   if(name.equals("executeQuery") || name.equals("execute") || name.equals("executeUpdate")) {
    String text=sql!=null?sql:(a!=null && a.length>0 && a[0] instanceof String s?s:"");
    if(text.stripLeading().toLowerCase(Locale.ROOT).startsWith("select")){budget(raw);check(raw,text,bindings);budget(raw);}
   }
   return invoke(target,m,a);
  });
 }
 private void check(Connection c,String sql,Map<Integer,Binding> bindings) throws SQLException {
  RequestState state=RequestState.CURRENT.get();
  if(state==null || state.mode.equals("OFF"))return;
  try {
   JsonNodeHolder holder;
   try(PreparedStatement explain=c.prepareStatement("EXPLAIN (FORMAT JSON, VERBOSE TRUE) "+sql)) {
    for(Binding b:bindings.values()) {
     for(Object value:b.args())if(value instanceof java.io.InputStream || value instanceof java.io.Reader)
      throw new SQLException("Stream bindings cannot be replayed for EXPLAIN");
     invoke(explain,b.method(),b.args());
    }
    try(ResultSet rs=explain.executeQuery()){rs.next();holder=new JsonNodeHolder(rs.getString(1));}
   }
   PlanInspector.Assessment plan=inspector.inspect(json.readTree(holder.text));
   var scans=new ArrayList<dev.poc.core.IndexPolicyEvaluator.Scan>();
   for(var scan:plan.scans()) {
    double tableRows=0;
    if(scan.type().equals("Seq Scan") || scan.type().equals("Parallel Seq Scan")) {
     try(PreparedStatement table=c.prepareStatement("SELECT c.reltuples FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relname=?")) {
      table.setString(1,scan.schema());table.setString(2,scan.relation());
      try(ResultSet rs=table.executeQuery()){tableRows=rs.next()?rs.getDouble(1):-1;if(rs.wasNull())tableRows=-1;}
     }
    }
    scans.add(new dev.poc.core.IndexPolicyEvaluator.Scan(scan.type(),scan.schema(),scan.relation(),tableRows));
   }
   var configured=limits.core().indexPolicy();
   var effective=new dev.poc.core.Limits.IndexPolicy(state.mode,configured.allowClientSelection(),configured.minTableRows(),configured.maxPlanCost());
   var decision=new dev.poc.core.IndexPolicyEvaluator().evaluate(effective,plan.cost(),scans,true);
   List<String> reasons=decision.reasons();
   state.plans.add(Map.of("cost",plan.cost(),"scans",plan.scans(),"accepted",reasons.isEmpty(),"reasons",reasons));
   if(!decision.execute()) {
    state.violation=new PolicyException("INDEX_POLICY",String.join("; ",reasons));
    throw new SQLException(state.violation.getMessage(),"42501",state.violation);
   }
  } catch(SQLException e){
   if(state.violation!=null)throw e;
   failed(state,e); if(state.mode.equals("ENFORCE"))throw e;
  } catch(Throwable e){failed(state,e);if(state.mode.equals("ENFORCE"))throw new SQLException("Plan inspection failed closed","42501",e);}
 }
 private void failed(RequestState state,Throwable e) {
  state.plans.add(Map.of("accepted",false,"reasons",List.of("Plan inspection failed: "+e.getClass().getSimpleName())));
  if(state.mode.equals("ENFORCE"))state.violation=new PolicyException("INDEX_PLAN_UNAVAILABLE","Could not validate query plan");
 }
 private record JsonNodeHolder(String text) {}
 private static Object invoke(Object obj,Method m,Object[] a) throws Throwable {
  try{return m.invoke(obj,a);}catch(InvocationTargetException e){throw e.getCause();}
 }
}
