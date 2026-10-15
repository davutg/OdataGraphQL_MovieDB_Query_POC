package dev.poc;
import dev.poc.core.CursorCodec;
import dev.poc.core.PolicyException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
class CoreAdapterTest {
 @Test void configurationRejectsNonFinitePlanCost(){
  assertThatThrownBy(()->new Limits(100,500,50,10000,2,2000,5000,3000,60,8,
   new Limits.IndexPolicy("ENFORCE",false,10000,Double.NaN))).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void configuredZeroExpansionDepthReachesCore(){
  var limits=new Limits(100,500,50,10000,0,2000,5000,3000,60,8,TestLimits.defaults().indexPolicy());
  var request=new MockHttpServletRequest("GET","/odata/Movies");request.addParameter("$expand","Director");
  assertThatThrownBy(()->new ODataPolicy(limits).validate(request)).isInstanceOf(PolicyException.class)
   .satisfies(e->assertThat(((PolicyException)e).code).isEqualTo("EXPAND_DEPTH"));
 }
 @Test void odataUsesCoreFieldFailure(){
  var request=new MockHttpServletRequest("GET","/odata/Movies");request.addParameter("$orderby","Title");
  assertThatThrownBy(()->new ODataPolicy(TestLimits.defaults()).validate(request)).isInstanceOf(PolicyException.class)
   .satisfies(e->assertThat(((PolicyException)e).code).isEqualTo("ORDER_FIELD"));
 }
 @Test void configuredKeySurvivesIndependentInstances(){
  byte[] key=new byte[32];java.util.Arrays.fill(key,(byte)42);
  String encoded=java.util.Base64.getEncoder().encodeToString(key);
  CursorCodec first=new CoreConfig().cursors(new CoreConfig().cursorSigningKey(encoded,true),300000),second=new CoreConfig().cursors(new CoreConfig().cursorSigningKey(encoded,true),300000);
  String fingerprint=first.fingerprint("same query");
  assertThat(second.decode(first.encode(50,25,fingerprint),fingerprint).remaining()).isEqualTo(25);
 }
 @Test void invalidConfiguredKeyFailsAtStartup(){assertThatThrownBy(()->new CoreConfig().cursorSigningKey("AA==",true)).isInstanceOf(IllegalArgumentException.class);}
 @Test void preferredPageRetainsRemainingBudget(){
  var page=CoreConfig.policy(TestLimits.defaults()).page(75,0,10);
  assertThat(page.size()).isEqualTo(10);assertThat(page.remaining(10)).isEqualTo(65);assertThat(page.nextOffset(10)).isEqualTo(10);
 }
}
