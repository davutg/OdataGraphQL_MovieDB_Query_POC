package dev.poc.core;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Evaluates estimates; does not execute SQL or guarantee an actual runtime plan. */
public final class IndexPolicyEvaluator {
 public static final class Scan {
  public final String type, schema, relation;
  /** Negative values indicate unavailable/stale row statistics. */
  public final double estimatedRows;
  public Scan(String type,String schema,String relation,double estimatedRows) {
   if(type==null || schema==null || relation==null) throw new IllegalArgumentException("Scan identity required");
   this.type=type;this.schema=schema;this.relation=relation;this.estimatedRows=estimatedRows;
  }
 }
 public static final class Decision {
  private final boolean execute;
  private final List<String> reasons;
  private Decision(boolean execute,List<String> reasons) {
   this.execute=execute;this.reasons=Collections.unmodifiableList(new ArrayList<String>(reasons));
  }
  public boolean execute(){return execute;}
  public List<String> reasons(){return reasons;}
 }
 public Decision evaluate(Limits.IndexPolicy policy,double cost,List<Scan> scans,boolean planAvailable) {
  if(policy==null || !java.util.Arrays.asList("OFF","AUDIT","ENFORCE").contains(policy.mode()) ||
     policy.minTableRows()<0 || !Double.isFinite(policy.maxPlanCost()) || policy.maxPlanCost()<0)
   throw new IllegalArgumentException("Invalid index policy");
  List<String> reasons=new ArrayList<String>();
  if("OFF".equals(policy.mode())) return new Decision(true,reasons);
  if(!planAvailable || scans==null || !Double.isFinite(cost) || cost<0) reasons.add("Query plan unavailable");
  else {
   if(cost>policy.maxPlanCost()) reasons.add("Estimated cost exceeds configured maximum");
   for(Scan scan:scans) {
    if(scan==null) {reasons.add("Query plan unavailable");continue;}
    if("Seq Scan".equals(scan.type) || "Parallel Seq Scan".equals(scan.type)) {
     if(!Double.isFinite(scan.estimatedRows) || scan.estimatedRows<0 || scan.estimatedRows>=policy.minTableRows())
      reasons.add("Sequential scan on "+scan.schema+"."+scan.relation);
    }
   }
  }
  return new Decision(!"ENFORCE".equals(policy.mode()) || reasons.isEmpty(),reasons);
 }
}
