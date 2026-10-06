package dev.poc;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
public final class PlanInspector {
 public record Scan(String type,String schema,String relation,String index) {}
 public record Assessment(double cost,List<Scan> scans) {}
 public Assessment inspect(JsonNode json) {
  if(json==null || !json.isArray() || json.size()!=1 || !json.get(0).path("Plan").isObject())
   throw new IllegalArgumentException("Invalid EXPLAIN document");
  JsonNode plan=json.get(0).get("Plan");
  if(!plan.path("Total Cost").isNumber())throw new IllegalArgumentException("Missing plan cost");
  var scans=new ArrayList<Scan>(); walk(plan,scans);
  return new Assessment(plan.path("Total Cost").asDouble(),List.copyOf(scans));
 }
 private void walk(JsonNode node,List<Scan> scans) {
  String type=node.path("Node Type").asText();
  if(type.contains("Scan")) scans.add(new Scan(type,node.path("Schema").asText("public"),
   node.path("Relation Name").asText(""),node.path("Index Name").asText("")));
  for(JsonNode child:node.path("Plans")) walk(child,scans);
 }
}
