package dev.poc;
import dev.poc.core.ODataCursorCodec;
import dev.poc.core.PolicyException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class ODataContinuationTest {
 private final byte[] key=new byte[32];
 private final Clock clock=Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"),ZoneOffset.UTC);
 private final ODataCursorCodec codec=new ODataCursorCodec(key,clock,300000);
 Map<String,String> options() {
  Map<String,String> p=new LinkedHashMap<String,String>();p.put("$top","25");p.put("$skip","50");
  p.put("$orderby","Rating desc,Id asc");p.put("$filter","Genre eq 'RareGenre'");p.put("$expand","Director($select=Name)");return p;
 }
 @AfterEach void clear(){RequestState.CURRENT.remove();}
 @Test void independentPodRestoresQuotedTokenAndRevalidatesQuery() {
  String token=codec.encode(options(),25);
  ODataContinuation otherPod=new ODataContinuation(new ODataCursorCodec(key,clock,300000));
  RequestState.CURRENT.set(new RequestState("OFF",5000));
  MockHttpServletRequest req=new MockHttpServletRequest("GET","/odata/Movies");req.addParameter("$skiptoken","'"+token+"'");
  ODataPolicy policy=new ODataPolicy(TestLimits.defaults());
  Object safe=policy.validate(otherPod.restore(policy.validate(req)));
  assertThat(safe).isNotNull();
  assertThat(RequestState.CURRENT.get().odataPageSize).isEqualTo(25);
  assertThat(new ODataCursorCodec(key,clock,300000).decode(token).options()).isEqualTo(options());
 }
 @Test void modificationWrongKeyExpiryAndTruncationFail() {
  String token=codec.encode(options(),50);
  String[] parts=token.split("\\.");
  assertThatThrownBy(()->codec.decode(parts[0]+"."+parts[1]+"A."+parts[2])).isInstanceOf(PolicyException.class);
  byte[] wrong=new byte[32];wrong[0]=1;
  assertThatThrownBy(()->new ODataCursorCodec(wrong,clock,300000).decode(token)).isInstanceOf(PolicyException.class);
  assertThatThrownBy(()->new ODataCursorCodec(key,Clock.offset(clock,Duration.ofMinutes(5)),300000).decode(token)).isInstanceOf(PolicyException.class);
  assertThatThrownBy(()->codec.decode(token.substring(0,token.length()/2))).isInstanceOf(PolicyException.class);
 }
 @Test void queryOptionsCannotBeAddedToContinuation() {
  MockHttpServletRequest req=new MockHttpServletRequest("GET","/odata/Movies");
  req.addParameter("$skiptoken",codec.encode(options(),50));req.addParameter("$top","500");
  assertThatThrownBy(()->new ODataPolicy(TestLimits.defaults()).validate(req)).hasMessageContaining("alone");
 }
 @Test void currentPolicyRevalidatesSignedOptions() {
  Map<String,String> options=options();options.put("$top","501");
  RequestState.CURRENT.set(new RequestState("OFF",5000));
  MockHttpServletRequest req=new MockHttpServletRequest("GET","/odata/Movies");req.addParameter("$skiptoken",codec.encode(options,50));
  assertThatThrownBy(()->new ODataPolicy(TestLimits.defaults()).validate(new ODataContinuation(codec).restore(req))).isInstanceOf(PolicyException.class);
 }
 @Test void boundedTokensAndInvalidKeysFail() {
  assertThatThrownBy(()->codec.decode(String.join("",Collections.nCopies(24001,"a")))).isInstanceOf(PolicyException.class);
  assertThatThrownBy(()->new ODataCursorCodec(new byte[8],clock,300000)).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void clusterRequiresStableSigningKey() {
  assertThatThrownBy(()->new CoreConfig().cursorSigningKey("",true)).hasMessageContaining("CURSOR_SECRET_BASE64");
  assertThat(new CoreConfig().cursorSigningKey(Base64.getEncoder().encodeToString(key),true)).containsExactly(key);
 }
}
