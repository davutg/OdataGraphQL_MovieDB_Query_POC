package dev.poc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import java.util.concurrent.CountDownLatch;
import static org.assertj.core.api.Assertions.*;
class RequestGateTest {
 Limits limits(long timeout,int rate){return new Limits(100,500,50,10000,2,2000,timeout,20,rate,1,new Limits.IndexPolicy("ENFORCE",false,10000,100000));}
 @Test void rejectsClientDowngrade() throws Exception {RequestGate gate=new RequestGate(limits(1000,60),new ObjectMapper());try{MockHttpServletRequest req=new MockHttpServletRequest("GET","/odata/Movies");req.addHeader("X-Index-Policy","OFF");MockHttpServletResponse res=new MockHttpServletResponse();gate.doFilterInternal(req,res,(a,b)->{throw new AssertionError();});assertThat(res.getStatus()).isEqualTo(403);}finally{gate.close();}}
 @Test void limitsRequestsPerIp() throws Exception {RequestGate gate=new RequestGate(limits(1000,1),new ObjectMapper());try{MockHttpServletRequest req=new MockHttpServletRequest("GET","/odata/Movies");MockHttpServletResponse first=new MockHttpServletResponse();gate.doFilterInternal(req,first,(a,b)->b.getWriter().write("ok"));assertThat(first.getContentAsString()).isEqualTo("ok");MockHttpServletResponse second=new MockHttpServletResponse();gate.doFilterInternal(req,second,(a,b)->{throw new AssertionError();});assertThat(second.getStatus()).isEqualTo(429);}finally{gate.close();}}
 @Test void enforcesWallClockTimeout() throws Exception {RequestGate gate=new RequestGate(limits(50,60),new ObjectMapper());CountDownLatch latch=new CountDownLatch(1);try{MockHttpServletResponse res=new MockHttpServletResponse();gate.doFilterInternal(new MockHttpServletRequest("GET","/odata/Movies"),res,(a,b)->{try{latch.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertThat(res.getStatus()).isEqualTo(504);assertThat(res.getContentAsString()).contains("REQUEST_TIMEOUT");}finally{latch.countDown();gate.close();}}
 @Test void boundsRequestBody() throws Exception {RequestGate gate=new RequestGate(limits(1000,60),new ObjectMapper());try{MockHttpServletRequest req=new MockHttpServletRequest("POST","/graphql");req.setContent(new byte[32769]);MockHttpServletResponse res=new MockHttpServletResponse();gate.doFilterInternal(req,res,(a,b)->{throw new AssertionError();});assertThat(res.getStatus()).isEqualTo(413);}finally{gate.close();}}

 @Test void removesNullHeaderWithoutBreakingErrorResponse() throws Exception {RequestGate gate=new RequestGate(limits(1000,60),new ObjectMapper());try{MockHttpServletResponse res=new MockHttpServletResponse();gate.doFilterInternal(new MockHttpServletRequest("GET","/odata/Movies"),res,(a,b)->{((javax.servlet.http.HttpServletResponse)b).setHeader("Content-Type",null);((javax.servlet.http.HttpServletResponse)b).setStatus(422);b.getWriter().write("policy rejected");});assertThat(res.getStatus()).isEqualTo(422);assertThat(res.getContentAsString()).isEqualTo("policy rejected");}finally{gate.close();}}
 @Test void rejectsConcurrentRequestInsteadOfQueueing() throws Exception {
  RequestGate gate=new RequestGate(limits(2000,60),new ObjectMapper());CountDownLatch entered=new CountDownLatch(1);CountDownLatch release=new CountDownLatch(1);java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{java.util.concurrent.Future<?> first=executor.submit(()->{try{gate.doFilterInternal(new MockHttpServletRequest("GET","/odata/Movies"),new MockHttpServletResponse(),(a,b)->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});}catch(Exception e){throw new RuntimeException(e);}});
   assertThat(entered.await(1,java.util.concurrent.TimeUnit.SECONDS)).isTrue();MockHttpServletResponse res=new MockHttpServletResponse();gate.doFilterInternal(new MockHttpServletRequest("GET","/odata/Movies"),res,(a,b)->{throw new AssertionError();});assertThat(res.getStatus()).isEqualTo(503);release.countDown();first.get(1,java.util.concurrent.TimeUnit.SECONDS);
  }finally{release.countDown();executor.shutdownNow();gate.close();}
 }
}
