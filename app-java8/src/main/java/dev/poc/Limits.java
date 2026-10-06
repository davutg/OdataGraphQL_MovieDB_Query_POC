package dev.poc;
@org.springframework.boot.context.properties.ConfigurationProperties("poc")
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
 @org.springframework.boot.context.properties.ConstructorBinding
 public Limits(int defaultTop,int maxTop,int pageSize,int maxOffset,int maxExpandDepth,int maxQueryComplexity,long requestTimeoutMs,int dbTimeoutMs,int ratePerMinute,int maxConcurrentRequests,IndexPolicy indexPolicy){
this.defaultTop=defaultTop;this.maxTop=maxTop;this.pageSize=pageSize;this.maxOffset=maxOffset;this.maxExpandDepth=maxExpandDepth;this.maxQueryComplexity=maxQueryComplexity;this.requestTimeoutMs=requestTimeoutMs;this.dbTimeoutMs=dbTimeoutMs;this.ratePerMinute=ratePerMinute;this.maxConcurrentRequests=maxConcurrentRequests;this.indexPolicy=indexPolicy;core();}
 public int defaultTop(){return defaultTop;} public int getDefaultTop(){return defaultTop;}
 public int maxTop(){return maxTop;} public int getMaxTop(){return maxTop;}
 public int pageSize(){return pageSize;} public int getPageSize(){return pageSize;}
 public int maxOffset(){return maxOffset;} public int getMaxOffset(){return maxOffset;}
 public int maxExpandDepth(){return maxExpandDepth;} public int getMaxExpandDepth(){return maxExpandDepth;}
 public int maxQueryComplexity(){return maxQueryComplexity;} public int getMaxQueryComplexity(){return maxQueryComplexity;}
 public long requestTimeoutMs(){return requestTimeoutMs;} public long getRequestTimeoutMs(){return requestTimeoutMs;}
 public int dbTimeoutMs(){return dbTimeoutMs;} public int getDbTimeoutMs(){return dbTimeoutMs;}
 public int ratePerMinute(){return ratePerMinute;} public int getRatePerMinute(){return ratePerMinute;}
 public int maxConcurrentRequests(){return maxConcurrentRequests;} public int getMaxConcurrentRequests(){return maxConcurrentRequests;}
 public IndexPolicy indexPolicy(){return indexPolicy;} public IndexPolicy getIndexPolicy(){return indexPolicy;}
public static final class IndexPolicy {
 private final String mode;
 private final boolean allowClientSelection;
 private final long minTableRows;
 private final double maxPlanCost;
 @org.springframework.boot.context.properties.ConstructorBinding
 public IndexPolicy(String mode,boolean allowClientSelection,long minTableRows,double maxPlanCost){
this.mode=mode;this.allowClientSelection=allowClientSelection;this.minTableRows=minTableRows;this.maxPlanCost=maxPlanCost;}
 public String mode(){return mode;} public String getMode(){return mode;}
 public boolean allowClientSelection(){return allowClientSelection;} public boolean getAllowClientSelection(){return allowClientSelection;}
 public long minTableRows(){return minTableRows;} public long getMinTableRows(){return minTableRows;}
 public double maxPlanCost(){return maxPlanCost;} public double getMaxPlanCost(){return maxPlanCost;}
}
 public dev.poc.core.Limits core(){return new dev.poc.core.Limits(defaultTop,maxTop,pageSize,maxOffset,maxExpandDepth,maxQueryComplexity,requestTimeoutMs,dbTimeoutMs,ratePerMinute,maxConcurrentRequests,indexPolicy==null?null:new dev.poc.core.Limits.IndexPolicy(indexPolicy.mode(),indexPolicy.allowClientSelection(),indexPolicy.minTableRows(),indexPolicy.maxPlanCost()));}
 public int top(Integer value){return core().top(value);}
 public int offset(Integer value){return core().offset(value);}
}
