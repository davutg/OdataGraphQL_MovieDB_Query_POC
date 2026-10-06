package dev.poc.core;
import java.util.*;
import java.time.*;
public final class ExtendedContractTest {
 private static int checks;
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Check "+checks);}
 private static void reject(String code,Runnable action){try{action.run();}catch(PolicyException e){check(code.equals(e.code));return;}throw new AssertionError("Expected "+code);}
 private static void invalid(Runnable action){try{action.run();}catch(IllegalArgumentException e){checks++;return;}throw new AssertionError("Expected invalid argument");}
 public static void main(String[] args){
  Limits l=new Limits(100,500,50,Integer.MAX_VALUE,2,100,5000,3000,60,8,new Limits.IndexPolicy("ENFORCE",false,1000,10000));
  List<String> fields=new ArrayList<String>(Arrays.asList("Id","Genre"));
  QueryPolicy q=new QueryPolicy(l,fields,Collections.singleton("Id"));fields.add("Secret");
  q.filterField("Genre");q.orderField("Id");q.budget(2,100);checks+=3;
  reject("FILTER_FIELD",()->q.filterField("Secret"));reject("FILTER_FIELD",()->q.filterField(null));
  reject("ORDER_FIELD",()->q.orderField("Genre"));reject("EXPAND_DEPTH",()->q.budget(3,1));
  reject("EXPAND_DEPTH",()->q.budget(-1,1));reject("QUERY_COMPLEXITY",()->q.budget(0,101));
  reject("QUERY_COMPLEXITY",()->q.budget(0,Long.MAX_VALUE));reject("QUERY_COMPLEXITY",()->q.budget(0,-1));
  QueryPolicy.Page p=q.page(null,null);check(p.size()==50);check(p.nextOffset(50)==50);check(p.remaining(50)==50);
  check(p.nextOffset(49)==null);check(q.page(0,0).nextOffset(0)==null);
  check(q.page(10,0).nextOffset(10)==null);invalid(()->p.remaining(51));invalid(()->p.nextOffset(-1));
  reject("OFFSET_LIMIT",()->q.page(100,Integer.MAX_VALUE).nextOffset(50));
  check(q.indexMode(null).equals("ENFORCE"));check(q.indexMode("enforce").equals("ENFORCE"));
  reject("INDEX_MODE",()->q.indexMode("OFF"));reject("INDEX_MODE",()->q.indexMode("unknown"));
  byte[] key=new byte[32];Arrays.fill(key,(byte)7);
  Clock start=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
  CursorCodec c=new CursorCodec(key,start,100);
  String f=c.fingerprint("Title='İstanbul'"),token=c.encode(50,50,f);
  Arrays.fill(key,(byte)9);check(c.decode(token,f).offset()==50);
  byte[] original=new byte[32];Arrays.fill(original,(byte)7);
  check(new CursorCodec(original,start,100).decode(token,f).remaining()==50);
  CursorCodec expired=new CursorCodec(original,Clock.fixed(Instant.ofEpochMilli(1100),ZoneOffset.UTC),100);
  reject("CURSOR_INVALID",()->expired.decode(token,f));reject("CURSOR_INVALID",()->c.decode(null,f));
  reject("CURSOR_INVALID",()->c.decode(String.join("",Collections.nCopies(513,"x")),f));
  invalid(()->c.encode(-1,1,f));invalid(()->c.encode(1,-1,f));invalid(()->c.encode(1,1,"a:b"));
  invalid(()->new CursorCodec(new byte[31],start,100));invalid(()->new CursorCodec(original,start,0));
  IndexPolicyEvaluator e=new IndexPolicyEvaluator();
  check(!e.evaluate(l.indexPolicy(),Double.NaN,Collections.<IndexPolicyEvaluator.Scan>emptyList(),true).execute());
  check(!e.evaluate(l.indexPolicy(),1,Arrays.asList((IndexPolicyEvaluator.Scan)null),true).execute());
  check(!e.evaluate(l.indexPolicy(),1,Arrays.asList(new IndexPolicyEvaluator.Scan("Parallel Seq Scan","public","movie",1000)),true).execute());
  check(e.evaluate(l.indexPolicy(),10000,Collections.<IndexPolicyEvaluator.Scan>emptyList(),true).execute());
  IndexPolicyEvaluator.Decision d=e.evaluate(l.indexPolicy(),10001,Collections.<IndexPolicyEvaluator.Scan>emptyList(),true);
  try{d.reasons().clear();throw new AssertionError("Mutable decision");}catch(UnsupportedOperationException expected){checks++;}
  new Limits(100,500,50,100,0,100,1,1,1,1,l.indexPolicy());checks++;
  check(q.page(100,0,10).size()==10);
  check(q.page(100,0,100).size()==50);
  invalid(()->q.page(100,0,0));
  Limits capped=new Limits(100,500,50,10,2,100,5000,3000,60,8,l.indexPolicy());
  reject("OFFSET_LIMIT",()->new QueryPolicy(capped,Collections.singleton("Id"),Collections.singleton("Id")).page(100,0).nextOffset(50));
  System.out.println("Extended contract checks passed: "+checks);
 }
}
