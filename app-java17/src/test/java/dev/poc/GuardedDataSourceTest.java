package dev.poc;
import java.sql.*;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class GuardedDataSourceTest {
 DataSource ds;Connection connection;PreparedStatement query,explain,budget,estimate;ResultSet plan,rows;
 final String sql="SELECT title FROM movies WHERE title = ?";
 @BeforeEach void setup() throws Exception {
  ds=mock(DataSource.class);connection=mock(Connection.class);query=mock(PreparedStatement.class);explain=mock(PreparedStatement.class);budget=mock(PreparedStatement.class);estimate=mock(PreparedStatement.class);plan=mock(ResultSet.class);rows=mock(ResultSet.class);
  when(ds.getConnection()).thenReturn(connection);when(connection.prepareStatement(sql)).thenReturn(query);
  when(connection.prepareStatement("EXPLAIN (FORMAT JSON, VERBOSE TRUE) "+sql)).thenReturn(explain);
  when(connection.prepareStatement("SELECT set_config('statement_timeout', ?, false)")).thenReturn(budget);
  when(connection.prepareStatement("SELECT c.reltuples FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relname=?")).thenReturn(estimate);
  when(explain.executeQuery()).thenReturn(plan);when(plan.next()).thenReturn(true);when(estimate.executeQuery()).thenReturn(rows);when(rows.next()).thenReturn(true);when(rows.getDouble(1)).thenReturn(100000d);
 }
 @AfterEach void clear(){RequestState.CURRENT.remove();}
 PreparedStatement guarded(String mode) throws Exception {RequestState.CURRENT.set(new RequestState(mode,5000));return new GuardedDataSource(ds,TestLimits.defaults(),new ObjectMapper()).getConnection().prepareStatement(sql);}
 void plan(String type) throws Exception {when(plan.getString(1)).thenReturn("[{\"Plan\":{\"Node Type\":\""+type+"\",\"Relation Name\":\"movies\",\"Schema\":\"public\",\"Total Cost\":80}}]");}
 @Test void enforceStopsSequentialScanBeforeExecution() throws Exception {plan("Seq Scan");var p=guarded("ENFORCE");p.setString(1,"Inception");assertThatThrownBy(p::executeQuery).isInstanceOf(SQLException.class);verify(query,never()).executeQuery();verify(explain).setString(1,"Inception");assertThat(RequestState.CURRENT.get().violation.code).isEqualTo("INDEX_POLICY");}
 @Test void auditReportsButExecutes() throws Exception {plan("Seq Scan");var p=guarded("AUDIT");p.setString(1,"Inception");p.executeQuery();verify(query).executeQuery();assertThat(RequestState.CURRENT.get().plans.get(0).get("accepted")).isEqualTo(false);}
 @Test void offDoesNotExplain() throws Exception {var p=guarded("OFF");p.executeQuery();verify(explain,never()).executeQuery();verify(query).executeQuery();}
 @Test void indexScanPassesAndBindsLiteralSafely() throws Exception {plan("Index Scan");var p=guarded("ENFORCE");String value="x'; DROP TABLE movies; --";p.setString(1,value);p.executeQuery();verify(explain).setString(1,value);verify(query).executeQuery();}
 @Test void smallTableSequentialScanPasses() throws Exception {plan("Seq Scan");when(rows.getDouble(1)).thenReturn(5d);guarded("ENFORCE").executeQuery();verify(query).executeQuery();}
 @Test void unknownStatisticsFailClosed() throws Exception {plan("Seq Scan");when(rows.getDouble(1)).thenReturn(-1d);assertThatThrownBy(()->guarded("ENFORCE").executeQuery()).isInstanceOf(SQLException.class);verify(query,never()).executeQuery();}
 @Test void explainFailureFailsClosed() throws Exception {when(explain.executeQuery()).thenThrow(new SQLException("Unavailable"));assertThatThrownBy(()->guarded("ENFORCE").executeQuery()).isInstanceOf(SQLException.class);verify(query,never()).executeQuery();}
 @Test void findsNestedScanInJoin() throws Exception {String json="[{\"Plan\":{\"Node Type\":\"Hash Join\",\"Total Cost\":100,\"Plans\":[{\"Node Type\":\"Index Only Scan\",\"Relation Name\":\"movies\",\"Index Name\":\"ix_movies_genre_id\"},{\"Node Type\":\"Seq Scan\",\"Relation Name\":\"directors\"}]}}]";var a=new PlanInspector().inspect(new ObjectMapper().readTree(json));assertThat(a.scans()).hasSize(2);assertThat(a.scans().get(0).index()).isEqualTo("ix_movies_genre_id");}
 @Test void malformedPlanDoesNotExecute() throws Exception {
  when(plan.getString(1)).thenReturn("[{\"Plan\":{\"Node Type\":\"Index Scan\"}}]");
  assertThatThrownBy(()->guarded("ENFORCE").executeQuery()).isInstanceOf(SQLException.class);
  verify(query,never()).executeQuery();
  assertThat(RequestState.CURRENT.get().violation.code).isEqualTo("INDEX_PLAN_UNAVAILABLE");
 }
 @Test void nullTableStatisticsFailClosed() throws Exception {
  plan("Seq Scan");when(rows.getDouble(1)).thenReturn(0d);when(rows.wasNull()).thenReturn(true);
  assertThatThrownBy(()->guarded("ENFORCE").executeQuery()).isInstanceOf(SQLException.class);
  verify(query,never()).executeQuery();
 }
}
