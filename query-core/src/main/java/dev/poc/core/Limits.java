package dev.poc.core;
public final class Limits {
private final int defaultTop;
private final int maxTop;
private final int pageSize;
private final int maxOffset;
private final int maxExpandDepth;
private final int maxQueryComplexity;
private final long requestTimeoutMs;
private final int dbTimeoutMs;
private final int ratePerMinute;
private final int maxConcurrentRequests;
private final IndexPolicy indexPolicy;
public Limits(int defaultTop,int maxTop,int pageSize,int maxOffset,int maxExpandDepth,  int maxQueryComplexity,long requestTimeoutMs,int dbTimeoutMs,int ratePerMinute,int maxConcurrentRequests,IndexPolicy indexPolicy) {
  if(defaultTop<0 || maxTop<defaultTop || maxTop>1000 || pageSize<1 || pageSize>maxTop || maxOffset<0 ||
     maxExpandDepth<0 || maxQueryComplexity<1 || requestTimeoutMs<1 || dbTimeoutMs<1 || ratePerMinute<1 || maxConcurrentRequests<1)
   throw new IllegalArgumentException("Invalid query limits");
  if(indexPolicy==null || !java.util.Arrays.asList("OFF","AUDIT","ENFORCE").contains(indexPolicy.mode()) || indexPolicy.minTableRows()<0 || !Double.isFinite(indexPolicy.maxPlanCost()) || indexPolicy.maxPlanCost()<0)
   throw new IllegalArgumentException("Invalid index policy configuration");
 this.defaultTop=defaultTop;this.maxTop=maxTop;this.pageSize=pageSize;this.maxOffset=maxOffset;this.maxExpandDepth=maxExpandDepth;this.maxQueryComplexity=maxQueryComplexity;this.requestTimeoutMs=requestTimeoutMs;this.dbTimeoutMs=dbTimeoutMs;this.ratePerMinute=ratePerMinute;this.maxConcurrentRequests=maxConcurrentRequests;this.indexPolicy=indexPolicy;}
public int defaultTop(){return defaultTop;}
public int getDefaultTop(){return defaultTop;}
public int maxTop(){return maxTop;}
public int getMaxTop(){return maxTop;}
public int pageSize(){return pageSize;}
public int getPageSize(){return pageSize;}
public int maxOffset(){return maxOffset;}
public int getMaxOffset(){return maxOffset;}
public int maxExpandDepth(){return maxExpandDepth;}
public int getMaxExpandDepth(){return maxExpandDepth;}
public int maxQueryComplexity(){return maxQueryComplexity;}
public int getMaxQueryComplexity(){return maxQueryComplexity;}
public long requestTimeoutMs(){return requestTimeoutMs;}
public long getRequestTimeoutMs(){return requestTimeoutMs;}
public int dbTimeoutMs(){return dbTimeoutMs;}
public int getDbTimeoutMs(){return dbTimeoutMs;}
public int ratePerMinute(){return ratePerMinute;}
public int getRatePerMinute(){return ratePerMinute;}
public int maxConcurrentRequests(){return maxConcurrentRequests;}
public int getMaxConcurrentRequests(){return maxConcurrentRequests;}
public IndexPolicy indexPolicy(){return indexPolicy;}
public IndexPolicy getIndexPolicy(){return indexPolicy;}
public static final class IndexPolicy {
private final String mode;
private final boolean allowClientSelection;
private final long minTableRows;
private final double maxPlanCost;
public IndexPolicy(String mode,boolean allowClientSelection,long minTableRows,double maxPlanCost) {this.mode=mode;this.allowClientSelection=allowClientSelection;this.minTableRows=minTableRows;this.maxPlanCost=maxPlanCost;}
public String mode(){return mode;}
public String getMode(){return mode;}
public boolean allowClientSelection(){return allowClientSelection;}
public boolean getAllowClientSelection(){return allowClientSelection;}
public long minTableRows(){return minTableRows;}
public long getMinTableRows(){return minTableRows;}
public double maxPlanCost(){return maxPlanCost;}
public double getMaxPlanCost(){return maxPlanCost;}
}
 public int top(Integer value) {
  int n=value==null?defaultTop:value;
  if(n<0 || n>maxTop) throw new PolicyException("TOP_LIMIT","top must be between 0 and "+maxTop);
  return n;
 }
 public int offset(Integer value) {
  int n=value==null?0:value;
  if(n<0 || n>maxOffset) throw new PolicyException("OFFSET_LIMIT","offset must be between 0 and "+maxOffset);
  return n;
 }
}
