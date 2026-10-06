package dev.poc.core;
import java.util.Arrays;
import java.util.Collections;
public final class CoreContractTest {
 private static int checks;
 private static void check(boolean value){checks++;if(!value)throw new AssertionError("Check "+checks);}
 private static void rejects(Runnable action){try{action.run();}catch(IllegalArgumentException e){checks++;return;}catch(PolicyException e){checks++;return;}throw new AssertionError("Expected rejection");}
 public static void main(String[] args) {
  Limits.IndexPolicy enforce=new Limits.IndexPolicy("ENFORCE",false,1000,10000);
  Limits limits=new Limits(100,500,50,10000,2,100,5000,3000,60,8,enforce);
  check(limits.top(null)==100);check(limits.top(500)==500);check(limits.top(0)==0);
  rejects(()->limits.top(501));rejects(()->limits.top(-1));rejects(()->limits.offset(-1));
  check(limits.offset(null)==0);check(limits.offset(10000)==10000);rejects(()->limits.offset(10001));
  rejects(()->new Limits(100,500,50,10000,2,100,5000,3000,60,8,new Limits.IndexPolicy("ENFORCE",false,0,Double.NaN)));
  CursorCodec codec=new CursorCodec();String f=codec.fingerprint("genre=Drama");String token=codec.encode(50,50,f);
  check(f.length()==64);check(codec.decode(token,f).offset()==50);check(codec.decode(token,f).remaining()==50);
  rejects(()->codec.decode(token,codec.fingerprint("other")));rejects(()->codec.decode("A"+token,f));
  rejects(()->new CursorCodec().decode(token,f));rejects(()->codec.decode(token+".",f));
  IndexPolicyEvaluator e=new IndexPolicyEvaluator();
  java.util.List<IndexPolicyEvaluator.Scan> large=Arrays.asList(new IndexPolicyEvaluator.Scan("Seq Scan","public","movie",1000));
  check(!e.evaluate(enforce,10,large,true).execute());
  check(e.evaluate(new Limits.IndexPolicy("AUDIT",false,1000,10000),10,large,true).execute());
  check(!e.evaluate(enforce,10,null,false).execute());
  check(e.evaluate(new Limits.IndexPolicy("OFF",false,1000,10000),10,null,false).execute());
  check(e.evaluate(enforce,10,Arrays.asList(new IndexPolicyEvaluator.Scan("Seq Scan","public","small",999)),true).execute());
  check(!e.evaluate(enforce,10001,Collections.<IndexPolicyEvaluator.Scan>emptyList(),true).execute());
  check(!e.evaluate(enforce,10,Arrays.asList(new IndexPolicyEvaluator.Scan("Seq Scan","public","unknown",-1)),true).execute());
  System.out.println("Core contract checks passed: "+checks);
 }
}
