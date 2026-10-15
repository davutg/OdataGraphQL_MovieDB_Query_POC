package dev.poc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared operational state, separate from the ORM business-query path. PostgreSQL arbitrates concurrent updates. */
@Component
@ConditionalOnProperty(name="poc.shared-state",havingValue="true")
public class SharedRequestStore {
 private final JdbcTemplate jdbc;
 private final ObjectMapper json;
 private final String namespace;
 public SharedRequestStore(DataSource source,ObjectMapper json,
   @Value("${poc.state-namespace:movie-query}") String namespace) {
  if(namespace.isEmpty() || namespace.length()>120)throw new IllegalArgumentException("Invalid state namespace");
  this.jdbc=new JdbcTemplate(source);this.json=json;this.namespace=namespace;
 }
 public boolean permit(String client,int limit) {
  cleanup();
  List<Integer> result=jdbc.queryForList(
   "INSERT INTO poc_rate_window AS w(namespace,client_key,requests,expires_at) VALUES (?,?,1,CURRENT_TIMESTAMP + INTERVAL '60 seconds') "+
   "ON CONFLICT(namespace,client_key) DO UPDATE SET requests=CASE WHEN w.expires_at<=CURRENT_TIMESTAMP THEN 1 ELSE w.requests+1 END,"+
   "expires_at=CASE WHEN w.expires_at<=CURRENT_TIMESTAMP THEN CURRENT_TIMESTAMP + INTERVAL '60 seconds' ELSE w.expires_at END "+
   "WHERE w.expires_at<=CURRENT_TIMESTAMP OR w.requests<? RETURNING requests",Integer.class,namespace,client,limit);
  return !result.isEmpty();
 }
 public void save(Map<String,Object> report) {
  try {
   jdbc.update("INSERT INTO poc_request_trace(namespace,request_id,payload,expires_at) VALUES (?,?,CAST(? AS jsonb),CURRENT_TIMESTAMP + INTERVAL '5 minutes') "+
    "ON CONFLICT(namespace,request_id) DO UPDATE SET payload=EXCLUDED.payload,expires_at=EXCLUDED.expires_at",
    namespace,report.get("requestId"),json.writeValueAsString(report));
  } catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 @SuppressWarnings("unchecked")
 public Map<String,Object> trace(String id) {
  List<String> rows=jdbc.queryForList("SELECT payload::text FROM poc_request_trace WHERE namespace=? AND request_id=? AND expires_at>CURRENT_TIMESTAMP",String.class,namespace,id);
  if(rows.isEmpty())return null;
  try{return json.readValue(rows.get(0),Map.class);}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
 }
 private void cleanup() {
  // Indexed TTL cleanup bounds retained state. Each caller can safely delete expired rows.
  jdbc.update("DELETE FROM poc_rate_window WHERE namespace=? AND expires_at<=CURRENT_TIMESTAMP",namespace);
  jdbc.update("DELETE FROM poc_request_trace WHERE namespace=? AND expires_at<=CURRENT_TIMESTAMP",namespace);
 }
}
