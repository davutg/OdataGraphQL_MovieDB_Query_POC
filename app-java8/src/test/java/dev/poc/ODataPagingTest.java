package dev.poc;
import dev.poc.core.ODataCursorCodec;
import java.util.*;
import java.time.Clock;
import org.junit.jupiter.api.*;
import org.apache.olingo.server.api.uri.UriInfo;
import com.sap.olingo.jpa.processor.core.api.JPAODataPage;
import com.sap.olingo.jpa.processor.core.query.JPACountQuery;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ODataPagingTest {
 @AfterEach void clear(){RequestState.CURRENT.remove();}
 @Test void signedTokenContainsFinalBudget() throws Exception {
  ODataCursorCodec codec=new ODataCursorCodec(new byte[32],Clock.systemUTC(),300000);
  ODataPaging pages=new ODataPaging(TestLimits.defaults(),codec);UriInfo uri=mock(UriInfo.class);
  RequestState state=new RequestState("OFF",5000);state.odataOptions=Collections.singletonMap("$orderby","Id asc");RequestState.CURRENT.set(state);
  JPACountQuery count=mock(JPACountQuery.class);when(count.countResults()).thenReturn(75L);
  JPAODataPage first=pages.getFirstPage(uri,null,count,null);
  assertThat(first.getTop()).isEqualTo(50);
  ODataCursorCodec.State next=codec.decode(first.getSkipToken().toString());
  assertThat(next.options()).containsEntry("$skip","50").containsEntry("$top","25");
 }
 @Test void sapMustNeverReceiveUnrestoredToken(){assertThatThrownBy(()->new ODataPaging(TestLimits.defaults(),new ODataCursorCodec(new byte[32],Clock.systemUTC(),300000)).getNextPage("invalid")).isInstanceOf(IllegalStateException.class);}
}
