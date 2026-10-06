package dev.poc;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
public final class PlanInspector {
 public static final class Scan {private final String type;private final String schema;private final String relation;private final String index;@com.fasterxml.jackson.annotation.JsonCreator public Scan(@com.fasterxml.jackson.annotation.JsonProperty("type") String type,@com.fasterxml.jackson.annotation.JsonProperty("schema") String schema,@com.fasterxml.jackson.annotation.JsonProperty("relation") String relation,@com.fasterxml.jackson.annotation.JsonProperty("index") String index){this.type=type;this.schema=schema;this.relation=relation;this.index=index;}public String type(){return type;} public String getType(){return type;}public String schema(){return schema;} public String getSchema(){return schema;}public String relation(){return relation;} public String getRelation(){return relation;}public String index(){return index;} public String getIndex(){return index;}}
 public static final class Assessment {private final double cost;private final List<Scan> scans;@com.fasterxml.jackson.annotation.JsonCreator public Assessment(@com.fasterxml.jackson.annotation.JsonProperty("cost") double cost,@com.fasterxml.jackson.annotation.JsonProperty("scans") List<Scan> scans){this.cost=cost;this.scans=scans;}public double cost(){return cost;} public double getCost(){return cost;}public List<Scan> scans(){return scans;} public List<Scan> getScans(){return scans;}}
 public Assessment inspect(JsonNode json) {
  if(json==null || !json.isArray() || json.size()!=1 || !json.get(0).path("Plan").isObject())
   throw new IllegalArgumentException("Invalid EXPLAIN document");
  JsonNode plan=json.get(0).get("Plan");
  if(!plan.path("Total Cost").isNumber())throw new IllegalArgumentException("Missing plan cost");
  List<Scan> scans=new ArrayList<Scan>(); walk(plan,scans);
  return new Assessment(plan.path("Total Cost").asDouble(),Compat.copy(scans));
 }
 private void walk(JsonNode node,List<Scan> scans) {
  String type=node.path("Node Type").asText();
  if(type.contains("Scan")) scans.add(new Scan(type,node.path("Schema").asText("public"),
   node.path("Relation Name").asText(""),node.path("Index Name").asText("")));
  for(JsonNode child:node.path("Plans")) walk(child,scans);
 }
}
