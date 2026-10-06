package dev.poc;
import dev.poc.core.PolicyException;
import java.util.*;
import graphql.ExecutionInput;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.web.bind.annotation.*;
@RestController
public class GraphqlHttpController {
 private final GraphQlSource source;
 public GraphqlHttpController(GraphQlSource source){this.source=source;}
 public static final class Request {private final String query;private final String operationName;private final Map<String,Object> variables;@com.fasterxml.jackson.annotation.JsonCreator public Request(@com.fasterxml.jackson.annotation.JsonProperty("query") String query,@com.fasterxml.jackson.annotation.JsonProperty("operationName") String operationName,@com.fasterxml.jackson.annotation.JsonProperty("variables") Map<String,Object> variables){this.query=query;this.operationName=operationName;this.variables=variables;}public String query(){return query;} public String getQuery(){return query;}public String operationName(){return operationName;} public String getOperationName(){return operationName;}public Map<String,Object> variables(){return variables;} public Map<String,Object> getVariables(){return variables;}}
 @PostMapping("/graphql")
 public Map<String,Object> graphql(@RequestBody Request request) {
  if(request.query()==null || request.query().length()>8192)throw new PolicyException("QUERY_SIZE","Query is missing or exceeds 8192 characters");
  RequestState state=RequestState.CURRENT.get();
  ExecutionInput.Builder builder=ExecutionInput.newExecutionInput().query(request.query()).variables(request.variables()==null?Compat.map():request.variables());
  if(request.operationName()!=null)builder.operationName(request.operationName());
  builder.graphQLContext(c->c.put("requestState",state));
  Map<String,Object> result=new LinkedHashMap<>(source.graphQl().execute(builder.build()).toSpecification());
  result.put("extensions",Compat.map("requestId",state.id,"indexPolicy",Compat.map("mode",state.mode,"plans",state.plans)));
  return result;
 }
}
