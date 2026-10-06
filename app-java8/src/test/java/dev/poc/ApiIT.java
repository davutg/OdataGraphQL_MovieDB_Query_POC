package dev.poc;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"poc.rate-per-minute=1000"})
class ApiIT {
 @Autowired TestRestTemplate http;
 @Autowired JdbcTemplate jdbc;
 ResponseEntity<Map> odata(String query,String mode){HttpHeaders headers=new HttpHeaders();headers.set("X-Index-Policy",mode);return http.exchange(java.net.URI.create(http.getRootUri()+"/odata/Movies"+query),HttpMethod.GET,new HttpEntity<>(headers),Map.class);}
 ResponseEntity<Map> graphql(String query,String mode){HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Index-Policy",mode);return http.postForEntity("/graphql",new HttpEntity<>(Compat.map("query",query),headers),Map.class);}
 Map trace(ResponseEntity<?> response){return http.getForObject("/api/requests/"+response.getHeaders().getFirst("X-Request-Id"),Map.class);}
 @Test void metadataIsRealSapEdm(){ResponseEntity<String> r=http.getForEntity("/odata/$metadata",String.class);assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);assertThat(r.getBody()).contains("\"$Kind\":\"EntityType\"","\"Movies\":{\"$Kind\":\"EntitySet\"");}
 @Test void odataFilterSelectExpandAndCount(){ResponseEntity<Map> r=odata("?$filter=Year%20eq%202010&$select=Id,Title,Year&$expand=Director($select=Name)&$count=true&$top=2","OFF");assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);List<Map> rows=(List<Map>)r.getBody().get("value");assertThat(rows).hasSize(2);assertThat(rows.get(0)).containsKeys("Id","Title","Year","Director").doesNotContainKey("Rating");assertThat(r.getBody()).containsKey("@odata.count");}
 @Test void odataServerPagingRetainsBudget(){ResponseEntity<Map> r=odata("?$top=75&$orderby=Id%20asc","OFF");assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);assertThat((List<?>)r.getBody().get("value")).hasSize(50);String next=(String)r.getBody().get("@odata.nextLink");assertThat(next).isNotBlank();ResponseEntity<Map> page=http.getForEntity(java.net.URI.create(http.getRootUri()+"/odata/"+next),Map.class);assertThat(page.getStatusCode().value()).isEqualTo(200);assertThat((List<?>)page.getBody().get("value")).hasSize(25);assertThat(page.getBody()).doesNotContainKey("@odata.nextLink");}
 @Test void odataRejectsTooManyRows(){assertThat(odata("?$top=501","OFF").getStatusCode().value()).isEqualTo(422);}
 @Test void odataRejectsUnlistedSort(){assertThat(odata("?$orderby=Title","OFF").getStatusCode().value()).isEqualTo(422);}
 @Test void selectiveSapQueryUsesIndex(){ResponseEntity<Map> r=odata("?$filter=Genre%20eq%20'RareGenre'&$top=10","ENFORCE");assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);assertThat(trace(r).toString()).contains("Index","ix_movies_genre_id");}
 @Test void sapUnindexedTitleIsRejected(){ResponseEntity<Map> r=odata("?$filter=Title%20eq%20'Inception'","ENFORCE");assertThat(r.getStatusCode().value()).isEqualTo(422);assertThat(r.getBody().toString()).contains("INDEX_POLICY");}
 @Test void sapAuditExecutesUnindexedQuery(){ResponseEntity<Map> r=odata("?$filter=Title%20eq%20'Inception'","AUDIT");assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);assertThat(trace(r).toString()).contains("accepted=false","Seq Scan");}
 @Test void graphqlMatchesData(){ResponseEntity<Map> r=graphql("{movies(first:2, filter:{year:2010}){nodes{id title year director{name}} totalCount pageInfo{hasNextPage}}}","OFF");assertThat(r.getStatusCode().value()).as(String.valueOf(r.getBody())).isEqualTo(200);assertThat(r.getBody()).doesNotContainKey("errors");Map c=(Map)((Map)r.getBody().get("data")).get("movies");assertThat((List<?>)c.get("nodes")).hasSize(2);assertThat(c).containsKey("totalCount");}
 @Test void graphqlCursorBudget(){ResponseEntity<Map> first=graphql("{movies(first:75){nodes{id} pageInfo{hasNextPage endCursor}}}","OFF");Map c=(Map)((Map)first.getBody().get("data")).get("movies");assertThat((List<?>)c.get("nodes")).hasSize(50);String cursor=(String)((Map)c.get("pageInfo")).get("endCursor");ResponseEntity<Map> second=graphql("{movies(first:75,after:\""+cursor+"\"){nodes{id} pageInfo{hasNextPage}}}","OFF");Map page=(Map)((Map)second.getBody().get("data")).get("movies");assertThat((List<?>)page.get("nodes")).hasSize(25);assertThat(((Map)page.get("pageInfo")).get("hasNextPage")).isEqualTo(false);}
 @Test void graphqlRejectsTopAndSort(){assertThat(graphql("{movies(first:501){nodes{id}}}","OFF").getBody().toString()).contains("500");assertThat(graphql("{movies(orderBy:TITLE){nodes{id}}}","OFF").getBody().toString()).contains("allow-listed");}
 @Test void graphqlRejectsExpensiveAliases(){String q="{"+java.util.stream.IntStream.range(0,10).mapToObj(i->"q"+i+":movies(first:500){nodes{id title year genre rating director{id name}}}").collect(java.util.stream.Collectors.joining(" "))+"}";ResponseEntity<Map> r=graphql(q,"OFF");assertThat(r.getBody().toString()).containsIgnoringCase("complexity");}
 @Test void graphqlIndexAndEnforcement(){ResponseEntity<Map> good=graphql("{movies(first:5,filter:{genre:\"RareGenre\"}){nodes{id title}}}","ENFORCE");assertThat(good.getStatusCode().value()).isEqualTo(200);assertThat(good.getBody()).doesNotContainKey("errors");assertThat(trace(good).toString()).contains("Index");ResponseEntity<Map> bad=graphql("{movies(first:5,filter:{title:\"Inception\"}){nodes{id title}}}","ENFORCE");assertThat(bad.getStatusCode().value()).isEqualTo(422);}
 @Test void countIsAlsoChecked(){ResponseEntity<Map> bad=graphql("{movies(first:0){totalCount}}","ENFORCE");assertThat(bad.getStatusCode().value()).isEqualTo(422);}
 @Test void sqlInjectionStaysLiteral(){ResponseEntity<Map> r=graphql("{movies(filter:{title:\"x'; DROP TABLE movies; --\"}){nodes{id}}}","OFF");assertThat(r.getBody()).doesNotContainKey("errors");assertThat(jdbc.queryForObject("SELECT count(*) FROM movies",Integer.class)).isEqualTo(100010);}
 @Test void postgresCancelsSlowStatement(){assertThatThrownBy(()->jdbc.queryForObject("SELECT pg_sleep(4)",Object.class)).hasMessageContaining("statement timeout");}

 @Test void graphqlVariablesRespectLimitsAndFilter() {
  String query="query($genre:String!,$first:Int!){movies(first:$first,filter:{genre:$genre}){nodes{id genre}}}";
  ResponseEntity<Map> good=http.postForEntity("/graphql",Compat.map("query",query,"variables",Compat.map("genre","RareGenre","first",1)),Map.class);
  assertThat(good.getBody()).doesNotContainKey("errors");assertThat(good.getBody().toString()).contains("RareGenre");
  ResponseEntity<Map> bad=http.postForEntity("/graphql",Compat.map("query",query,"variables",Compat.map("genre","RareGenre","first",501)),Map.class);assertThat(bad.getStatusCode().value()).isEqualTo(422);assertThat(bad.getBody().toString()).contains("500");
 }
}
