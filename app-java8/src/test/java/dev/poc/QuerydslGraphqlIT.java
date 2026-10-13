package dev.poc;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.*;

/** HTTP + real PostgreSQL coverage for repository binding and ORM relationship loading. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"poc.rate-per-minute=1000"})
class QuerydslGraphqlIT {
    @Autowired TestRestTemplate http;

    ResponseEntity<Map> query(String query, Map<String, Object> variables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Index-Policy", "AUDIT");
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("query", query); body.put("variables", variables);
        return http.postForEntity("/graphql", new HttpEntity<Object>(body, headers), Map.class);
    }
    ResponseEntity<Map> query(String query) { return query(query, Collections.emptyMap()); }
    Map data(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().value()).as(String.valueOf(response.getBody())).isEqualTo(200);
        assertThat(response.getBody()).doesNotContainKey("errors");
        return (Map) response.getBody().get("data");
    }
    List plans(ResponseEntity<Map> response) {
        Map report = http.getForObject("/api/requests/" + response.getHeaders().getFirst("X-Request-Id"), Map.class);
        return (List) report.get("plans");
    }

    @Test void aliasesFragmentsAndDirectivesUseEntityRelationshipsWithoutNPlusOne() {
        String document = "query($include:Boolean!){films:movies(first:10){results:nodes{...Summary filmmaker:director @include(if:$include){label:name}}}} fragment Summary on Movie{id title}";
        ResponseEntity<Map> response = query(document, Collections.singletonMap("include", true));
        List<Map> movies = (List<Map>) ((Map) data(response).get("films")).get("results");
        assertThat(movies).hasSize(10);
        for (Map movie : movies) assertThat((Map) movie.get("filmmaker")).containsKey("label");
        assertThat(plans(response)).as("One ORM select, no per-movie director selects").hasSize(1);
        ResponseEntity<Map> excluded = query(document, Collections.singletonMap("include", false));
        List<Map> without = (List<Map>) ((Map) data(excluded).get("films")).get("results");
        for (Map movie : without) assertThat(movie).doesNotContainKey("filmmaker");
    }

    @Test void rangeAliasesAndExactBindingsCombineWithoutOverwriting() {
        ResponseEntity<Map> response = query("{movies(first:20,filter:{year:2010,yearFrom:2010,yearTo:2010,ratingMin:8,ratingMax:9},orderBy:RATING,descending:true){nodes{id year rating}}}");
        List<Map> movies = (List<Map>) ((Map) data(response).get("movies")).get("nodes");
        assertThat(movies).isNotEmpty();
        double previous = Double.MAX_VALUE;
        for (Map movie : movies) {
            assertThat(((Number) movie.get("year")).intValue()).isEqualTo(2010);
            double rating = ((Number) movie.get("rating")).doubleValue();
            assertThat(rating).isBetween(8.0, 9.0).isLessThanOrEqualTo(previous); previous = rating;
        }
        ResponseEntity<Map> impossible = query("{movies(filter:{year:2010,yearFrom:2011}){nodes{id}}}");
        assertThat((List) ((Map) data(impossible).get("movies")).get("nodes")).isEmpty();
    }

    @Test void scalarFiltersUseGeneratedEntityPaths() {
        ResponseEntity<Map> response = query("{movies(filter:{id:\"1\"}){nodes{id title rating genre}}}");
        List<Map> rows = (List<Map>) ((Map) data(response).get("movies")).get("nodes");
        assertThat(rows).hasSize(1); assertThat(rows.get(0).get("id")).isEqualTo("1");
        Map<String,Object> filter = new HashMap<String,Object>();
        filter.put("title", rows.get(0).get("title")); filter.put("rating", rows.get(0).get("rating"));
        filter.put("genre", rows.get(0).get("genre"));
        ResponseEntity<Map> matching = query("query($f:MovieFilter!){movies(filter:$f){nodes{id}}}", Collections.singletonMap("f",filter));
        assertThat((List) ((Map) data(matching).get("movies")).get("nodes")).isNotEmpty();
    }

    @Test void totalCountIsOptionalAndZeroBudgetDoesNotFetchEntities() {
        ResponseEntity<Map> noCount = query("{movies(first:5){nodes{id}}}");
        data(noCount); assertThat(plans(noCount)).hasSize(1);
        ResponseEntity<Map> count = query("{movies(first:5){nodes{id} totalCount}}");
        assertThat(((Map) data(count).get("movies")).get("totalCount")).isEqualTo(100010);
        assertThat(plans(count)).hasSize(2);
        ResponseEntity<Map> zero = query("{movies(first:0){nodes{id} pageInfo{hasNextPage endCursor}}}");
        assertThat((List) ((Map) data(zero).get("movies")).get("nodes")).isEmpty();
        assertThat(plans(zero)).isEmpty();
    }

    @Test void cursorBindsNormalizedFilterAndSortAndBudget() {
        ResponseEntity<Map> first = query("{movies(first:75,filter:{yearFrom:1900,yearTo:2100}){nodes{id} pageInfo{endCursor}}}");
        String cursor = (String) ((Map) ((Map) data(first).get("movies")).get("pageInfo")).get("endCursor");
        Map<String,Object> variables = Collections.singletonMap("cursor",cursor);
        ResponseEntity<Map> next = query("query($cursor:String!){movies(first:500,after:$cursor,filter:{yearTo:2100,yearFrom:1900}){nodes{id} pageInfo{hasNextPage}}}", variables);
        assertThat((List) ((Map) data(next).get("movies")).get("nodes")).hasSize(25);
        ResponseEntity<Map> changed = query("query($cursor:String!){movies(after:$cursor,filter:{yearFrom:2000}){nodes{id}}}",variables);
        assertThat(changed.getBody().toString()).contains("CURSOR_INVALID");
        ResponseEntity<Map> sorted = query("query($cursor:String!){movies(after:$cursor,filter:{yearFrom:1900,yearTo:2100},descending:true){nodes{id}}}",variables);
        assertThat(sorted.getBody().toString()).contains("CURSOR_INVALID");
    }

    @Test void invalidAndEmptyFiltersFailBeforeDatabaseAccess() {
        for (String filter : Arrays.asList("id:\"bad\"", "title:\"\"")) {
            ResponseEntity<Map> response = query("{movies(filter:{"+filter+"}){nodes{id}}}");
            assertThat(response.getBody().toString()).contains("FILTER_VALUE");
            assertThat(plans(response)).isEmpty();
        }
    }
}
