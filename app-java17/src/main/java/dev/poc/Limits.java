package dev.poc;
import org.springframework.boot.context.properties.ConfigurationProperties;
/** Spring binding adapter; all validation and policy decisions belong to query-core. */
@ConfigurationProperties("poc")
public record Limits(int defaultTop,int maxTop,int pageSize,int maxOffset,int maxExpandDepth,
 int maxQueryComplexity,long requestTimeoutMs,int dbTimeoutMs,int ratePerMinute,int maxConcurrentRequests,IndexPolicy indexPolicy) {
 public Limits { toCore(defaultTop,maxTop,pageSize,maxOffset,maxExpandDepth,maxQueryComplexity,requestTimeoutMs,dbTimeoutMs,ratePerMinute,maxConcurrentRequests,indexPolicy); }
 public record IndexPolicy(String mode,boolean allowClientSelection,long minTableRows,double maxPlanCost) {}
 private static dev.poc.core.Limits toCore(int a,int b,int c,int d,int e,int f,long g,int h,int i,int j,IndexPolicy p){
  return new dev.poc.core.Limits(a,b,c,d,e,f,g,h,i,j,p==null?null:new dev.poc.core.Limits.IndexPolicy(p.mode(),p.allowClientSelection(),p.minTableRows(),p.maxPlanCost()));
 }
 public dev.poc.core.Limits core(){return toCore(defaultTop,maxTop,pageSize,maxOffset,maxExpandDepth,maxQueryComplexity,requestTimeoutMs,dbTimeoutMs,ratePerMinute,maxConcurrentRequests,indexPolicy);}
 public int top(Integer value){return core().top(value);}
 public int offset(Integer value){return core().offset(value);}
}
