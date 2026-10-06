package dev.poc;
import org.junit.jupiter.api.Test;
import org.apache.olingo.server.api.uri.UriInfo;
import com.sap.olingo.jpa.processor.core.api.JPAODataPage;
import com.sap.olingo.jpa.processor.core.query.JPACountQuery;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ODataPagingTest {
 @Test void quotedSapTokenRetainsFinalBudget() throws Exception {
  ODataPaging pages=new ODataPaging(TestLimits.defaults());UriInfo uri=mock(UriInfo.class);
  JPACountQuery count=mock(JPACountQuery.class);when(count.countResults()).thenReturn(75L);
  JPAODataPage first=pages.getFirstPage(uri,null,count,null);
  assertThat(first.getTop()).isEqualTo(50);
  JPAODataPage last=pages.getNextPage("'"+first.getSkipToken()+"'");
  assertThat(last.getSkip()).isEqualTo(50);assertThat(last.getTop()).isEqualTo(25);assertThat(last.getSkipToken()).isNull();
 }
 @Test void unknownTokenDoesNotCreatePage(){assertThat(new ODataPaging(TestLimits.defaults()).getNextPage("'invalid'")).isNull();}
}
